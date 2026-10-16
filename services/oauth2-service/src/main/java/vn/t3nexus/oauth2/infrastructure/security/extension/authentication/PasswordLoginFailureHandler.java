package vn.t3nexus.oauth2.infrastructure.security.extension.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/**
 * Chuyển người dùng về trang đăng nhập sau khi xác thực mật khẩu thất bại. Việc ghi nhận đăng nhập thất bại (event) đã làm trong
 * use case xác thực; ở đây chỉ chọn thông điệp. Sai email và sai mật khẩu cùng một kết quả ({@code ?error}, INV-ATH-03);
 * {@code ?locked} chỉ ra sau khi mật khẩu đã đúng (bộ xác thực chỉ ném {@link LockedException} lúc đó); lỗi hệ thống
 * ({@link InternalAuthenticationServiceException}) có thông điệp riêng để không bị nhầm với sai mật khẩu. Không đưa email lên
 * địa chỉ chuyển hướng.
 */
public class PasswordLoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, @NonNull HttpServletResponse response,
                                        @NonNull AuthenticationException exception) throws IOException {
        String base = request.getContextPath() + "/login";
        if (exception instanceof LockedException) {
            response.sendRedirect(base + "?locked");
        } else if (exception instanceof InternalAuthenticationServiceException) {
            response.sendRedirect(base + "?unavailable");
        } else {
            response.sendRedirect(base + "?error");
        }
    }
}
