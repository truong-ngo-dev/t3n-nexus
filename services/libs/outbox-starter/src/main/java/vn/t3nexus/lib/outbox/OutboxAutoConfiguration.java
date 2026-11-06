package vn.t3nexus.lib.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.events.EventEnvelopeFactory;

@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
@Import({OutboxJpaConfiguration.class, OutboxEnvelopeConfiguration.class})
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxEventStore outboxEventStore(OutboxEventRepository repository, EventEnvelopeFactory envelopeFactory) {
        return new OutboxEventStore(repository, envelopeFactory);
    }
}
