package vn.t3nexus.lib.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
@Import(OutboxJpaConfiguration.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxEventStore outboxEventStore(OutboxEventRepository repository, ObjectMapper objectMapper) {
        return new OutboxEventStore(repository, objectMapper);
    }
}
