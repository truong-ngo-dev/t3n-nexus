package vn.t3nexus.lib.observability;

import jakarta.annotation.Nonnull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures default observability properties for Spring Boot applications.
 * <br>Sets up defaults for structured logging, tracing sampling, and actuator endpoint exposure.
 */
public class ObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "observabilityDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, @Nonnull SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();

        // Sample all traces by default; services can override to lower value in production
        defaults.put("management.tracing.sampling.probability", "1.0");

        // Expose health and info endpoints
        defaults.put("management.endpoints.web.exposure.include", "health,info,metrics,prometheus");

        // addLast = lowest priority, application properties always win
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }
}
