package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Optional;

/**
 * Kiểm {@code id_token_hint} bằng chính bộ khóa của dịch vụ (không tải JWKS qua HTTP): chữ ký và issuer, <b>không có bộ kiểm hạn</b>
 * (khác bộ giải mã của {@code /api}). Không đăng ký làm bean {@link JwtDecoder} để không lẫn với bộ giải mã của chuỗi {@code /api/**}.
 */
@Component
public class NimbusIdTokenHintVerifier implements IdTokenHintVerifier {

    private final JwtDecoder decoder;

    public NimbusIdTokenHintVerifier(JWKSource<SecurityContext> jwkSource,
                                    @Value("${spring.security.oauth2.authorizationserver.issuer}") String issuer) {
        NimbusJwtDecoder nimbus = (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        nimbus.setJwtValidator(new JwtIssuerValidator(issuer));
        this.decoder = nimbus;
    }

    @Override
    public Optional<Jwt> verify(String idTokenHint) {
        if (idTokenHint == null || idTokenHint.isBlank()) return Optional.empty();
        try {
            return Optional.of(decoder.decode(idTokenHint));
        } catch (JwtException notAValidHint) {
            return Optional.empty();
        }
    }
}
