package vn.t3nexus.storefrontwebbff.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Mọi lỗi chưa được xử lý ở BFF (đường dẫn lạ 404, tham số sai 400, làm mới thất bại 401 hoặc 503, lỗi bất ngờ 500) thành khung lỗi chung
 * với mã chữ ổn định, thay cho trang Whitelabel hoặc JSON thô của Boot. Chạy trước bộ xử lý lỗi mặc định của Boot (order -1). Chỉ cho
 * phản hồi kiểu fetch; lỗi của luồng đăng nhập (điều hướng trình duyệt) do Spring OAuth2 Client chuyển hướng như cũ.
 */
@Slf4j
@Component
@Order(-2)
public class BffErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) return Mono.error(error);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (error instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            if (resolved != null) status = resolved;
        } else {
            log.error("[Error] unhandled {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), error);
        }
        return ErrorFrame.write(exchange, status, codeOf(status), messageOf(status));
    }

    private static String codeOf(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHENTICATED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS";
            case INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR";
            case BAD_GATEWAY -> "BAD_GATEWAY";
            case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            case GATEWAY_TIMEOUT -> "GATEWAY_TIMEOUT";
            default -> status.name();
        };
    }

    private static String messageOf(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Yêu cầu không hợp lệ";
            case UNAUTHORIZED -> "Bạn chưa đăng nhập hoặc phiên đã hết hạn";
            case FORBIDDEN -> "Bạn không có quyền thực hiện thao tác này";
            case NOT_FOUND -> "Không tìm thấy tài nguyên";
            case SERVICE_UNAVAILABLE -> "Hệ thống tạm thời không phản hồi, vui lòng thử lại sau";
            default -> "Đã có lỗi xảy ra";
        };
    }
}
