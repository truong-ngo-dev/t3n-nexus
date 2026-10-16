package vn.t3nexus.oauth2.infrastructure.security.extension.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.t3nexus.lib.observability.logging.LogContext;

import java.io.IOException;

/**
 * Đặt {@code user.id} vào ngữ cảnh log ngay khi người gọi đã được xác thực (KT-08): với token Bearer đó là {@code sub} của JWT vừa được
 * phân tích, với đăng nhập bằng form đó là mã tài khoản của phiên. Đứng sau bước kiểm quyền nên yêu cầu bị từ chối không mang
 * {@code user.id}. Dòng hoàn tất của {@code RequestLoggingFilter} (đứng ngoài chuỗi bảo mật) nhờ đó mang đúng người gọi, kể cả khi use case
 * không tự đặt. Lấy từ tên của xác thực, vốn là mã tài khoản ở cả hai trường hợp.
 */
public class UserIdLogContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName() != null) {
            LogContext.user(authentication.getName());
        }
        chain.doFilter(request, response);
    }
}
