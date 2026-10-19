package vn.t3nexus.storefrontwebbff.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;

/**
 * Ba bộ xử lý lỗi bảo mật của BFF, đều trả khung lỗi chung với mã chữ ổn định (người gọi là fetch của SPA, không bao giờ 302):
 * chưa có phiên {@code UNAUTHENTICATED} (401), CSRF sai {@code CSRF_TOKEN_INVALID} (403), thiếu quyền {@code FORBIDDEN} (403).
 */
public final class SecurityErrorHandlers {

    private SecurityErrorHandlers() {
    }

    public static ServerAuthenticationEntryPoint unauthenticated() {
        return (exchange, e) -> ErrorFrame.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Bạn chưa đăng nhập hoặc phiên đã hết hạn");
    }

    public static ServerAccessDeniedHandler csrfDenied() {
        return (exchange, e) -> ErrorFrame.write(exchange, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID", "Mã CSRF thiếu hoặc không hợp lệ");
    }

    public static ServerAccessDeniedHandler forbidden() {
        return (exchange, e) -> ErrorFrame.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này");
    }
}
