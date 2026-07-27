package vn.t3nexus.lib.eventsourcing;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackageClasses = EventStoreJpaRepository.class)
class EventSourcingJpaConfiguration {
}
