package vn.t3nexus.apigateway.infrastructure.configuration;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import vn.t3nexus.apigateway.infrastructure.ratelimit.ClientAddress;
import reactor.core.publisher.Mono;

/**
 * Tiêu đề chuyển tiếp của cổng (R22, ADR-0006). Cổng là hop ngoài cùng nên <b>không tin giá trị nào do client tự gửi</b>: xóa
 * {@code Forwarded}, {@code X-Forwarded-*} và {@code X-Client-Ip} rồi đặt lại
 * {@code X-Forwarded-Host}, {@code -Proto}, {@code -Port} từ chính yêu cầu thật: host là tiêu đề {@code Host} trình duyệt đã gửi (mỗi
 * ứng dụng một giá trị), giao thức là giao thức của kết nối tới cổng. {@code X-Client-Ip} là địa chỉ của kết nối tới cổng (dịch vụ sau chỉ tin
 * tiêu đề này khi kết nối tới từ cổng, xem {@code ClientIpResolver}). {@code X-Forwarded-Prefix} không còn vì không có tiền tố.
 * Xác thực và BFF dựng địa chỉ công khai (chuyển hướng, redirect URI) từ các tiêu đề này.
 *
 * <p>Giao thức lấy từ kết nối tới cổng; khi có bộ kết thúc TLS đứng trước cổng thì phải đổi sang tin tiêu đề của nó (nợ ghi ở
 * {@code docs/service/api-gateway/service.md}).
 */
@Component
public class ForwardedHeadersSanitizer implements GlobalFilter, Ordered {

    static final String[] CLIENT_SUPPLIED = {
            "Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Forwarded-Port",
            "X-Forwarded-Prefix", "X-Forwarded-Ssl", "X-Client-Ip"
    };

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String host = exchange.getRequest().getHeaders().getFirst("Host");
        String scheme = exchange.getRequest().getURI().getScheme();
        String clientAddress = ClientAddress.of(exchange);
        ServerWebExchange sanitized = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    for (String name : CLIENT_SUPPLIED) headers.remove(name);
                    if (host != null && !host.isBlank()) {
                        headers.set("X-Forwarded-Host", host);
                        headers.set("X-Forwarded-Port", portOf(host, scheme));
                    }
                    if (scheme != null) headers.set("X-Forwarded-Proto", scheme);
                    // Địa chỉ tin cậy cho log của dịch vụ sau (T7): địa chỉ kết nối tới cổng, cùng giá trị cổng dùng làm khóa giới hạn tần suất
                    headers.set("X-Client-Ip", clientAddress);
                }))
                .build();
        return chain.filter(sanitized);
    }

    private static String portOf(String hostHeader, String scheme) {
        int end = hostHeader.indexOf(']');                         // IPv6 dạng [::1]:8000
        int colon = hostHeader.indexOf(':', Math.max(end, 0));
        if (colon >= 0 && colon + 1 < hostHeader.length()) return hostHeader.substring(colon + 1);
        return "https".equalsIgnoreCase(scheme) ? "443" : "80";
    }
}
