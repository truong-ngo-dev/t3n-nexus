package vn.t3nexus.oauth2.domain.user_account;

/** Cổng băm mật khẩu. Cài đặt ở hạ tầng (ADR-0002 chờ chốt thuật toán). */
public interface PasswordHasher {

    PasswordHash hash(RawPassword rawPassword);

    /**
     * @param attempt giá trị người dùng gõ lúc đăng nhập; không phải {@link RawPassword} vì luật mật khẩu chỉ áp dụng
     *                khi đặt mật khẩu, không áp dụng khi kiểm
     */
    boolean verify(String attempt, PasswordHash hash);

    /** Giá trị băm đã lưu có hệ số thấp hơn mức hiện hành không (KT-07, ADR-0002). Mặc định: không. */
    default boolean needsRehash(PasswordHash hash) {
        return false;
    }

    /**
     * Băm lại một mật khẩu <b>vừa được kiểm đúng</b> ở mức hiện hành. Không qua {@link RawPassword} vì luật mật khẩu có thể đã đổi
     * sau khi mật khẩu cũ được đặt; mật khẩu đúng thì không bị luật mới từ chối đăng nhập.
     */
    default PasswordHash rehash(String verifiedAttempt) {
        return hash(RawPassword.of(verifiedAttempt));
    }
}
