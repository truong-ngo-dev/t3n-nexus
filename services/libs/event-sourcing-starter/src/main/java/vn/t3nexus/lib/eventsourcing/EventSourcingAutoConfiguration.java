package vn.t3nexus.lib.eventsourcing;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.common.domain.service.EventStore;

@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
@Import(EventSourcingJpaConfiguration.class)
public class EventSourcingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventStore eventStore(EventStoreJpaRepository repository, ObjectMapper objectMapper) {
        return new JpaEventStore(repository, objectMapper);
    }
}
