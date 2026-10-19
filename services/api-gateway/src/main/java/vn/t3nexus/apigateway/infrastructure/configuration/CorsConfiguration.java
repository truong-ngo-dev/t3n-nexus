package vn.t3nexus.apigateway.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Bắt buộc có ở cổng: Spring Cloud Gateway tự trả lời yêu cầu OPTIONS (preflight) ở tầng định tuyến trước khi chuyển xuống dịch vụ
 * sau nếu cổng không có cấu hình CORS. Nguồn gốc cho phép đọc từ {@code app.cors.allowed-origins} (danh sách mẫu, cách nhau dấu
 * phẩy); mặc định ở dev là {@code http://*.localhost:*}; đưa lên host thật chỉ đổi biến này. Dùng mẫu nguồn gốc chứ không dùng
 * {@code *}: {@code *} kèm cookie bị trình duyệt từ chối và mở mọi nguồn gốc là lỗ hổng. Bộ lọc {@code dedupeResponseHeader} ở
 * từng tuyến xử lý việc cả cổng lẫn dịch vụ sau cùng đặt tiêu đề CORS.
 */
@Configuration
public class CorsConfiguration {

    @Value("${app.cors.allowed-origins:http://*.localhost:*}")
    private String allowedOrigins;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        config.setAllowCredentials(true);
        config.setMaxAge(7200L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
