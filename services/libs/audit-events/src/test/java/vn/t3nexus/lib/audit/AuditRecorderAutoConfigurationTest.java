package vn.t3nexus.lib.audit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.lib.outbox.OutboxEventStore;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuditRecorderAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditRecorderAutoConfiguration.class));

    @Configuration
    static class MockStoreConfig {
        @Bean
        OutboxEventStore outboxEventStore() {
            return mock(OutboxEventStore.class);
        }
    }

    @Configuration
    static class CatalogConfig {
        @Bean
        AuditActionCatalog auditActionCatalog() {
            return AuditActionCatalog.of(new AuditActionDeclaration("ACTION_ONE", Set.of()));
        }
    }

    @Test
    void createsAuditRecorderWhenStoreAndCatalogPresentAndServiceNameValid() {
        contextRunner
                .withUserConfiguration(MockStoreConfig.class, CatalogConfig.class)
                .withPropertyValues("spring.application.name=seller-service")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditRecorder.class);
                });
    }

    @Test
    void doesNotCreateAuditRecorderWhenCatalogIsMissing() {
        contextRunner
                .withUserConfiguration(MockStoreConfig.class)
                .withPropertyValues("spring.application.name=seller-service")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AuditRecorder.class);
                });
    }

    @Test
    void doesNotCreateAuditRecorderWhenStoreIsMissingAndDoesNotFail() {
        contextRunner
                .withUserConfiguration(CatalogConfig.class)
                .withPropertyValues("spring.application.name=seller-service")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AuditRecorder.class);
                });
    }

    @Test
    void runsWithoutErrorWhenOutboxEventStoreFilteredFromClasspath() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(OutboxEventStore.class))
                .withPropertyValues("spring.application.name=seller-service")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AuditRecorder.class);
                });
    }

    @Test
    void failsStartupWhenApplicationNameDoesNotMatchPattern() {
        contextRunner
                .withUserConfiguration(MockStoreConfig.class, CatalogConfig.class)
                .withPropertyValues("spring.application.name=Seller Service")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }
}
