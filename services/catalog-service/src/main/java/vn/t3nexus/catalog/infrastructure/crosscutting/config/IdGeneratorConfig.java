package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;

import java.time.Clock;

@Configuration
public class IdGeneratorConfig {

    /** Mã UUID v7 (ADR-0001) cho mọi aggregate. */
    @Bean
    @ConditionalOnMissingBean
    public IdGenerator idGenerator() {
        return new UuidV7IdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
