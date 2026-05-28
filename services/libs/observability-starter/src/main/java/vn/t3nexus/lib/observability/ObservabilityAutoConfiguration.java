package vn.t3nexus.lib.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Auto-configuration for system-wide observability.
 * <br>Enables distributed tracing via Micrometer OTel and structured logging.
 * <br>Spring Boot autoconfigures OTel tracing via micrometer-tracing-bridge-otel.
 * This class anchors the ordering and serves as an extension point for future custom beans.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class ObservabilityAutoConfiguration {
}
