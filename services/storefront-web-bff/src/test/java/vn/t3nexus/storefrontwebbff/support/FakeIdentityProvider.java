package vn.t3nexus.storefrontwebbff.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Phía ký token của Xác thực giả: một cặp khóa RSA, đăng khóa công khai lên máy chủ giả ({@code /oauth2/jwks}) để BFF kiểm chữ ký thật,
 * và ký JWT theo hợp đồng danh tính (ADR-0003: {@code sub}, {@code sid}, {@code role}, {@code exp}).
 */
public final class FakeIdentityProvider {

    private final RSAKey key;

    public FakeIdentityProvider() {
        try {
            this.key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public void publishKeys(FakeHttpServer server) {
        server.respond("GET", "/oauth2/jwks", 200, "application/json", new JWKSet(key.toPublicJWK()).toString());
    }

    /** Phát hành trước hạn, không bao giờ sau hạn (token hết hạn dùng để thử độ lệch đồng hồ vẫn phải hợp lệ về mặt cấu trúc). */
    private static Instant issuedAt(Instant expiresAt) {
        Instant fiveSecondsAgo = Instant.now().minusSeconds(5);
        Instant beforeExpiry = expiresAt.minusSeconds(600);
        return beforeExpiry.isBefore(fiveSecondsAgo) ? beforeExpiry : fiveSecondsAgo;
    }

    /** JWT đã ký với các claim cho trước; {@code exp} và {@code iat} tính từ {@code expiresAt}. */
    public String jwt(Map<String, Object> claims, Instant expiresAt) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().issueTime(Date.from(issuedAt(expiresAt)))
                    .expirationTime(Date.from(expiresAt));
            claims.forEach(builder::claim);
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), builder.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
