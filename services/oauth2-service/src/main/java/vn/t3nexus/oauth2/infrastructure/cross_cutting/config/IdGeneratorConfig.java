package vn.t3nexus.oauth2.infrastructure.cross_cutting.config;

import com.github.f4b6a3.ulid.UlidCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

@Configuration
public class IdGeneratorConfig {

    @Bean
    public ULIDGenerator ulidGenerator() {
        return () -> UlidCreator.getMonotonicUlid().toString();
    }
}
