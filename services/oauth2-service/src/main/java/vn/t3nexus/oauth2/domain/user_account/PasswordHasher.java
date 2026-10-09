package vn.t3nexus.oauth2.domain.user_account;

/** Cổng băm mật khẩu. Cài đặt ở hạ tầng (ADR-0002 chờ chốt thuật toán). */
public interface PasswordHasher {

    PasswordHash hash(RawPassword rawPassword);

    /**
     * @param attempt giá trị người dùng gõ lúc đăng nhập; không phải {@link RawPassword} vì luật mật khẩu chỉ áp dụng
     *                khi đặt mật khẩu, không áp dụng khi kiểm
     */
    boolean verify(String attempt, PasswordHash hash);
}
