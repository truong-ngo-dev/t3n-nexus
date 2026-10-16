package vn.t3nexus.oauth2.infrastructure.security.oauth2.key;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.*;

import java.time.Duration;
import java.time.Instant;

@Configuration
@RequiredArgsConstructor
public class KeyConfiguration {

    @Bean
    public ApplicationListener<@NonNull ApplicationReadyEvent> applicationReadyListener(
            ApplicationEventPublisher publisher, RsaKeyPairRepository repository) {
        return event -> {
            if (repository.findKeyPairs().isEmpty()) {
                publisher.publishEvent(new RsaKeyPairGenerationEvent(Instant.now()));
            }
        };
    }

    @Bean
    public ApplicationListener<@NonNull RsaKeyPairGenerationEvent> keyPairGenerationRequestListener(
            RsaKeyPairGenerator generator, RsaKeyPairRepository repository,
            @Value("${jwt.key.id}") String keyId) {
        return event -> repository.save(generator.generateKeyPair(keyId, event.getSource()));
    }

    @Bean
    public TextEncryptor textEncryptor(
            @Value("${jwt.persistence.password}") String pw,
            @Value("${jwt.persistence.salt}") String salt) {
        return Encryptors.text(pw, salt);
    }

    @Bean
    public RsaPublicKeyConverter rsaPublicKeyConverter(TextEncryptor textEncryptor) {
        return new RsaPublicKeyConverter(textEncryptor);
    }

    @Bean
    public RsaPrivateKeyConverter rsaPrivateKeyConverter(TextEncryptor textEncryptor) {
        return new RsaPrivateKeyConverter(textEncryptor);
    }

    @Bean
    public NimbusJwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Bộ giải mã mã đăng nhập dựng từ chính bộ khóa của dịch vụ (cho chuỗi /api/**), không tải JWKS qua HTTP. Độ lệch đồng hồ chấp nhận
     * khi kiểm {@code exp}, {@code nbf} là 5 giây (mặc định của Spring là 60 giây, làm token sống thêm tới 60 giây sau hạn phiên).
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource,
                                 @Value("${app.security.jwt.clock-skew:5s}") Duration clockSkew) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        decoder.setJwtValidator(new JwtTimestampValidator(clockSkew));
        return decoder;
    }

    @Bean
    public OAuth2TokenGenerator<OAuth2Token> delegatingOAuth2TokenGenerator(
            NimbusJwtEncoder encoder, OAuth2TokenCustomizer<JwtEncodingContext> customizer,
            OAuth2TokenGenerator<OAuth2RefreshToken> refreshTokenGenerator) {
        JwtGenerator generator = new JwtGenerator(encoder);
        generator.setJwtCustomizer(customizer);
        return new DelegatingOAuth2TokenGenerator(generator,
                new OAuth2AccessTokenGenerator(), refreshTokenGenerator);
    }
}
