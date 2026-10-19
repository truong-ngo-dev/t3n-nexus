package vn.t3nexus.apigateway.infrastructure.configuration;

import org.springframework.web.server.ServerWebExchange;

import java.util.Locale;

/**
 * So khớp tên máy của yêu cầu với tên máy cấu hình: không phân biệt chữ hoa, bỏ cổng. Host thiếu hoặc khác tên thì không khớp,
 * nên tên máy lạ không chọn được tuyến nào (không có tuyến khớp thì Gateway trả 404 trước mọi bộ lọc, không đếm gì).
 */
final class HostMatcher {

    private HostMatcher() {
    }

    /** Tên máy trong tiêu đề {@code Host}, chữ thường, không cổng; rỗng nếu không có. */
    static String hostName(String hostHeader) {
        if (hostHeader == null) return "";
        String host = hostHeader.strip().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {                       // IPv6 dạng [::1]:8000
            int end = host.indexOf(']');
            return end < 0 ? host : host.substring(0, end + 1);
        }
        int colon = host.indexOf(':');
        return colon < 0 ? host : host.substring(0, colon);
    }

    static boolean matches(ServerWebExchange exchange, String configuredHost) {
        if (configuredHost == null || configuredHost.isBlank()) return false;
        return hostName(exchange.getRequest().getHeaders().getFirst("Host")).equals(hostName(configuredHost));
    }
}
