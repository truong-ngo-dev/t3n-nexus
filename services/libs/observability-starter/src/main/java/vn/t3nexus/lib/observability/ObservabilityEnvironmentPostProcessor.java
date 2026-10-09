package vn.t3nexus.lib.observability;

import jakarta.annotation.Nonnull;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures default observability properties for Spring Boot applications.
 * <br>Sets up defaults for structured logging, tracing sampling, and actuator endpoint exposure.
 * <br>Log luôn được ghi ra file JSON theo ECS cho Filebeat; console luôn ra chữ, không có công tắc đổi sang JSON
 * (xem {@code observability-logback.xml} và {@code docs/global/4.convention/logging.md}).
 */
public class ObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "observabilityDefaults";

    /** Cổng quản trị mặc định = cổng ứng dụng + 10000 (8004 thành 18004) để các dịch vụ không trùng cổng; cổng ngẫu nhiên (0) thì quản trị cũng ngẫu nhiên. */
    private static int managementPort(ConfigurableEnvironment environment) {
        Integer serverPort = environment.getProperty("server.port", Integer.class);
        int port = serverPort != null ? serverPort : 8080;
        return port == 0 ? 0 : port + 10_000;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, @Nonnull SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();

        // Sample all traces by default; services can override to lower value in production
        defaults.put("management.tracing.sampling.probability", "1.0");

        // Expose health, info, metrics và loggers (đổi mức log lúc đang chạy). Các endpoint này nằm ở cổng quản trị riêng, không ở cổng công khai.
        defaults.put("management.endpoints.web.exposure.include", "health,info,metrics,prometheus,loggers");
        defaults.put("management.server.address", "127.0.0.1");
        if (environment.getProperty("management.server.port") == null) {
            defaults.put("management.server.port", String.valueOf(managementPort(environment)));
        }

        // File JSON (cho Filebeat) luôn ghi; không đặt format cho console nên console luôn ra chữ
        defaults.put("logging.structured.format.file", "ecs");
        defaults.put("logging.structured.ecs.service.name", "${spring.application.name:unknown}");
        defaults.put("logging.structured.ecs.service.environment", "${spring.profiles.active:default}");
        // Boot để nguyên tên MDC; đổi sang tên ECS
        defaults.put("logging.structured.json.rename.traceId", "trace.id");
        defaults.put("logging.structured.json.rename.spanId", "span.id");
        // Lưới an toàn cuối: che token, JWT, mã băm còn sót trong message và stack trace
        defaults.put("logging.structured.json.customizer", "vn.t3nexus.lib.observability.logging.SecretScrubbingCustomizer");

        // addLast = lowest priority, application properties always win
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }
}
