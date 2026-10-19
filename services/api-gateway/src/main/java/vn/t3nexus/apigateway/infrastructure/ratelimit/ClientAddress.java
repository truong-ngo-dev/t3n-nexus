package vn.t3nexus.apigateway.infrastructure.ratelimit;

import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * Địa chỉ client dùng làm khóa đếm: <b>địa chỉ của kết nối TCP tới cổng</b>, không đọc {@code X-Forwarded-For} hay tiêu đề nào do client
 * tự gửi (theo {@code 8. deployment.md}, {@code api-gateway} là hop ngoài cùng, không có bộ cân bằng tải hay CDN phía trước; tin tiêu đề
 * đó cho phép client giả địa chỉ để né giới hạn, R2). Chỉ đổi sang tin tiêu đề của bộ cân bằng tải khi có bộ này đứng trước cổng.
 */
public final class ClientAddress {

    static final String UNKNOWN = "unknown";

    private ClientAddress() {
    }

    public static String of(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return (remote != null && remote.getAddress() != null) ? remote.getAddress().getHostAddress() : UNKNOWN;
    }
}
