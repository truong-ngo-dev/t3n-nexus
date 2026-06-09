package vn.t3nexus.oauth2.infrastructure.adapter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.t3nexus.oauth2.domain.user_credential.PasswordSetupTokenService;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisPasswordSetupTokenService implements PasswordSetupTokenService {

    private static final String NONCE_KEY_PREFIX    = "password_setup_nonce:";
    private static final String COOLDOWN_KEY_PREFIX = "password_setup_cooldown:";

    @Value("${app.password-setup.hmac-secret}")
    private String hmacSecret;

    @Value("${app.password-setup.token-ttl-seconds:86400}")
    private long tokenTtlSeconds;

    @Value("${app.password-setup.cooldown-seconds:60}")
    private long cooldownSeconds;

    private final StringRedisTemplate redisTemplate;

    @Override
    public String generate(String userId) {
        return doGenerate(userId);
    }

    @Override
    public String generateForResend(String userId) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY_PREFIX + userId))) {
            throw UserCredentialException.setupRateLimited();
        }
        String token = doGenerate(userId);
        redisTemplate.opsForValue().set(COOLDOWN_KEY_PREFIX + userId, "1", cooldownSeconds, TimeUnit.SECONDS);
        return token;
    }

    @Override
    public String verify(String token) {
        TokenPayload payload = parseAndVerify(token);

        if (Instant.now().getEpochSecond() > payload.exp()) {
            throw UserCredentialException.setupTokenInvalid();
        }

        String storedNonce = redisTemplate.opsForValue().get(NONCE_KEY_PREFIX + payload.userId());
        if (storedNonce == null || !storedNonce.equals(payload.nonce())) {
            throw UserCredentialException.setupTokenInvalid();
        }

        redisTemplate.delete(NONCE_KEY_PREFIX + payload.userId());
        return payload.userId();
    }

    // ───────────────────────────────────────

    private String doGenerate(String userId) {
        String nonce = UUID.randomUUID().toString();
        long   exp   = Instant.now().plusSeconds(tokenTtlSeconds).getEpochSecond();

        String token = buildToken(userId, nonce, exp);
        redisTemplate.opsForValue().set(NONCE_KEY_PREFIX + userId, nonce, tokenTtlSeconds, TimeUnit.SECONDS);
        return token;
    }

    private String buildToken(String userId, String nonce, long exp) {
        String payload    = userId + "|" + nonce + "|" + exp;
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return payloadB64 + "." + sign(payloadB64);
    }

    private TokenPayload parseAndVerify(String token) {
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) throw UserCredentialException.setupTokenInvalid();

        String payloadB64 = parts[0];
        String signature  = parts[1];

        if (!sign(payloadB64).equals(signature)) {
            throw UserCredentialException.setupTokenInvalid();
        }

        String   decoded = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
        String[] fields  = decoded.split("\\|", 3);
        if (fields.length != 3) throw UserCredentialException.setupTokenInvalid();

        return new TokenPayload(fields[0], fields[1], Long.parseLong(fields[2]));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private record TokenPayload(String userId, String nonce, long exp) {}
}
