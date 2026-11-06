package vn.t3nexus.lib.audit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;
import vn.t3nexus.lib.outbox.OutboxEventStore;

import java.time.Clock;

/**
 * Tự động cấu hình bean {@link AuditRecorder} nếu có outbox-starter trên classpath.
 */
@AutoConfiguration(afterName = "vn.t3nexus.lib.outbox.OutboxAutoConfiguration")
@ConditionalOnClass(name = "vn.t3nexus.lib.outbox.OutboxEventStore")
public class AuditRecorderAutoConfiguration {

    @Bean
    @ConditionalOnBean({OutboxEventStore.class, AuditActionCatalog.class})
    @ConditionalOnMissingBean
    public AuditRecorder auditRecorder(
            OutboxEventStore store,
            AuditActionCatalog catalog,
            Environment env,
            ObjectProvider<Clock> clockProvider,
            ObjectProvider<IdGenerator> idGeneratorProvider
    ) {
        String sourceService = env.getProperty("spring.application.name");
        if (sourceService == null || !AuditContract.isSourceService(sourceService)) {
            throw new IllegalStateException("Cấu hình spring.application.name không hợp lệ: sourceService");
        }

        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);
        IdGenerator idGenerator = idGeneratorProvider.getIfAvailable(UuidV7IdGenerator::new);

        return new AuditRecorder(store, catalog, sourceService, clock, idGenerator);
    }
}
