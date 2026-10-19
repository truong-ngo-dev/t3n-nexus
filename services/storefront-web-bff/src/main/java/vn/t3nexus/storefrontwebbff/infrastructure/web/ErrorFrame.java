package vn.t3nexus.storefrontwebbff.infrastructure.web;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Viết khung lỗi chung của dự án ({@code success}, {@code code}, {@code message}, {@code errors}) cho phản hồi lỗi do chính BFF sinh ra
 * (chưa có phiên, CSRF, đường dẫn lạ, làm mới thất bại), để giao diện rẽ nhánh theo {@code code} ổn định thay vì thân lỗi thô của Boot.
 * Chỉ dùng cho người gọi loại fetch; lời gọi điều hướng của luồng đăng nhập là 302. Viết tay JSON, mọi giá trị là hằng của mã nguồn nên không cần thoát ký tự.
 */
public final class ErrorFrame {

    private ErrorFrame() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        var response = exchange.getResponse();
        if (response.isCommitted()) return Mono.empty();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"errors\":[]}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
