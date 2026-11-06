package vn.t3nexus.lib.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.events.EventEnvelopeFactory;
import vn.t3nexus.lib.events.SensitiveFieldPolicy;

/** Bộ dựng khung của cổng ghi hộp thư, với chính sách kiểm tên trường nhạy cảm chọn bằng cấu hình. */
@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxEnvelopeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OutboxEnvelopeConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    EventEnvelopeFactory eventEnvelopeFactory(ObjectMapper objectMapper, OutboxProperties properties) {
        SensitiveFieldPolicy policy = switch (properties.getSensitiveFieldPolicy()) {
            case STRICT -> SensitiveFieldPolicy.strict();
            case SECRETS_ONLY -> SensitiveFieldPolicy.secretsOnly();
            case OFF -> {
                log.warn("[Outbox] app.outbox.sensitive-field-policy=off: nội dung event không được kiểm tên trường nhạy cảm");
                yield SensitiveFieldPolicy.none();
            }
        };
        return new EventEnvelopeFactory(objectMapper, policy);
    }
}
