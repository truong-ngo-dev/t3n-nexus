package vn.t3nexus.oauth2.domain.user_credential;

public interface PasswordSetupTokenService {

    /** Generate token cho lần đầu (registration) — không enforce cooldown. */
    String generate(String userId);

    /** Generate token cho resend — enforce cooldown. */
    String generateForResend(String userId);

    /**
     * Verify token, xóa nonce (single-use), trả về userId được encode trong token.
     * Throws nếu invalid/expired/đã dùng.
     */
    String verify(String token);
}
