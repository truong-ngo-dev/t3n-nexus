package vn.t3nexus.lib.ratelimiter.reactive;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Phản hồi 429 chung của hai cổng (api-gateway, storefront-web-bff): khung lỗi chung của dự án với mã chữ ổn định
 * {@code TOO_MANY_REQUESTS} để giao diện rẽ nhánh và dịch lỗi, kèm {@code Retry-After} là thời gian còn lại đến khi giải phóng một
 * lượt (không phải độ dài cả cửa sổ). Viết tay JSON để thư viện không phụ thuộc thêm bộ đọc ghi JSON.
 */
public final class TooManyRequestsResponse {

    static final String BODY = "{\"success\":false,\"code\":\"TOO_MANY_REQUESTS\","
            + "\"message\":\"Bạn thao tác quá nhanh, vui lòng thử lại sau\",\"errors\":[]}";

    private TooManyRequestsResponse() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, RateLimitDecision decision) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(BODY.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
