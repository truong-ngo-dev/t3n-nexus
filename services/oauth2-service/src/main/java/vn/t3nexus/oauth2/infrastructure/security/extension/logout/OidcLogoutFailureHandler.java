package vn.t3nexus.oauth2.infrastructure.security.extension.logout;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Lỗi của yêu cầu đăng xuất (feature 03 {@code tech.md} T4): <b>{@code invalid_token}</b> (hint không tra được, đã mất bản ghi vì đã
 * đăng xuất, ký sai, sai chủ) thì <b>không động tới session nào</b> và chuyển về {@code /}: người dùng thật ở trạng thái này đã đăng
 * xuất rồi (bản ghi mất kéo theo phiên mất), nên đăng xuất lần hai không được hiện lỗi (INV-ATH-05); còn hủy session khi hint sai
 * là mở đường cho trang ngoài đăng xuất ngầm. Lỗi khác ({@code invalid_request}: trang đích lạ, client không khớp, thiếu hint) giữ cách
 * trả mặc định của Spring (400, không chuyển hướng) để không thành chuyển hướng mở.
 */
@Component
public class OidcLogoutFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (exception instanceof OAuth2AuthenticationException oauth2
                && OAuth2ErrorCodes.INVALID_TOKEN.equals(oauth2.getError().getErrorCode())) {
            new DefaultRedirectStrategy().sendRedirect(request, response, "/");
            return;
        }
        String message = exception instanceof OAuth2AuthenticationException oauth2
                ? oauth2.getError().toString() : exception.getMessage();
        response.sendError(HttpStatus.BAD_REQUEST.value(), message);
    }
}
