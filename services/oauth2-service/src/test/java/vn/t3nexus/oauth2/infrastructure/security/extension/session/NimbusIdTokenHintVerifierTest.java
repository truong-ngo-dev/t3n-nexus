package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm `id_token_hint`: chữ ký và issuer của chính ta, không kiểm hạn (KT-18, `tech.md` T1). */
class NimbusIdTokenHintVerifierTest {

    private static final String ISSUER = "http://auth.localhost:8000";

    private static JWKSource<SecurityContext> ours;
    private static JWKSource<SecurityContext> someoneElses;

    @BeforeAll
    static void keys() throws Exception {
        ours = new ImmutableJWKSet<>(new JWKSet(new RSAKeyGenerator(2048).keyID("ours").generate()));
        someoneElses = new ImmutableJWKSet<>(new JWKSet(new RSAKeyGenerator(2048).keyID("ours").generate()));   // cùng kid, khóa khác
    }

    private static String token(JWKSource<SecurityContext> signingKeys, String issuer, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("user").claim("sid", "sid-1")
                .issuedAt(expiresAt.minusSeconds(900)).expiresAt(expiresAt).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("ours").build();
        return new NimbusJwtEncoder(signingKeys).encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private final NimbusIdTokenHintVerifier verifier = new NimbusIdTokenHintVerifier(ours, ISSUER);

    @Test
    void a_token_signed_by_us_with_our_issuer_is_accepted() {
        assertThat(verifier.verify(token(ours, ISSUER, Instant.now().plusSeconds(600))))
                .get().satisfies(jwt -> assertThat(jwt.getClaimAsString("sid")).isEqualTo("sid-1"));
    }

    @Test
    void an_expired_token_is_still_accepted_as_a_hint() {
        assertThat(verifier.verify(token(ours, ISSUER, Instant.now().minusSeconds(3600)))).isPresent();
    }

    @Test
    void a_token_signed_with_another_key_is_rejected() {
        assertThat(verifier.verify(token(someoneElses, ISSUER, Instant.now().plusSeconds(600)))).isEmpty();
    }

    @Test
    void a_token_from_another_issuer_is_rejected() {
        assertThat(verifier.verify(token(ours, "http://evil.example", Instant.now().plusSeconds(600)))).isEmpty();
    }

    @Test
    void garbage_and_blank_input_is_rejected() {
        assertThat(verifier.verify("not-a-jwt")).isEmpty();
        assertThat(verifier.verify("")).isEmpty();
        assertThat(verifier.verify(null)).isEmpty();
    }
}
