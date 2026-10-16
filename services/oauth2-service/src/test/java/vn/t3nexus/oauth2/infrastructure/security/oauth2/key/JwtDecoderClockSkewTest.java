package vn.t3nexus.oauth2.infrastructure.security.oauth2.key;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bộ giải mã của /api chấp nhận lệch đồng hồ 5 giây (mặc định Spring là 60 giây). */
class JwtDecoderClockSkewTest {

    private static JWKSource<SecurityContext> jwkSource;

    @BeforeAll
    static void keys() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("test").generate();
        jwkSource = new ImmutableJWKSet<>(new JWKSet(key));
    }

    private static String tokenExpiredSecondsAgo(long seconds) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("http://issuer").subject("u")
                .issuedAt(now.minusSeconds(seconds + 600)).expiresAt(now.minusSeconds(seconds)).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build();
        return new NimbusJwtEncoder(jwkSource).encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static JwtDecoder decoder(Duration skew) {
        return new KeyConfiguration().jwtDecoder(jwkSource, skew);
    }

    @Test
    void a_token_expired_within_the_skew_is_still_accepted_and_beyond_it_is_rejected() {
        JwtDecoder decoder = decoder(Duration.ofSeconds(5));

        assertThat(decoder.decode(tokenExpiredSecondsAgo(2)).getSubject()).isEqualTo("u");
        assertThatThrownBy(() -> decoder.decode(tokenExpiredSecondsAgo(10))).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void a_token_expired_thirty_seconds_ago_is_rejected_unlike_the_sixty_second_spring_default() {
        assertThatThrownBy(() -> decoder(Duration.ofSeconds(5)).decode(tokenExpiredSecondsAgo(30)))
                .isInstanceOf(JwtValidationException.class);
    }
}
