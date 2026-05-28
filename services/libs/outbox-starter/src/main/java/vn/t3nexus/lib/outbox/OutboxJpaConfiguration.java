package vn.t3nexus.lib.outbox;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackageClasses = OutboxEventRepository.class)
class OutboxJpaConfiguration {
}
