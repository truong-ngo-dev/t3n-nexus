package vn.t3nexus.apigateway.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình định tuyến theo {@code Host} (ADR-0006): tên máy của từng ứng dụng (không kèm cổng) và địa chỉ máy chủ giao diện của
 * ứng dụng web. Miền gốc thật nằm trong chính các giá trị này, nên đổi môi trường chỉ đổi cấu hình.
 *
 * @param hosts     tên máy cho phép: {@code auth} (Xác thực), {@code shop} (Cửa hàng), {@code ops} (Vận hành, chưa có tuyến)
 * @param frontends địa chỉ máy chủ giao diện của từng ứng dụng web (đường dẫn không thuộc BFF chuyển tới đây)
 */
@ConfigurationProperties(prefix = "app.gateway")
public record GatewayProperties(Hosts hosts, Frontends frontends) {

    public record Hosts(String auth, String shop, String ops) {
    }

    public record Frontends(String shop) {
    }
}
