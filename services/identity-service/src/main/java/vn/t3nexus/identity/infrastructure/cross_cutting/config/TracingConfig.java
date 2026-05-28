package vn.t3nexus.identity.infrastructure.cross_cutting.config;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
public class TracingConfig {

    @Bean
    public SpanProcessor loggingSpanProcessor() {
        return SimpleSpanProcessor.create(LoggingSpanExporter.create());
    }
}
