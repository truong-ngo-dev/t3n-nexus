package vn.t3nexus.oauth2.infrastructure.cross_cutting.config;

import com.github.f4b6a3.ulid.UlidCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;

import java.time.Clock;

@Configuration
public class IdGeneratorConfig {

    /** Mã phiên và các thực thể ngoài tài khoản vẫn dùng ULID cho tới khi tới lượt chúng (ADR-0001). */
    @Bean
    public ULIDGenerator ulidGenerator() {
        return () -> UlidCreator.getMonotonicUlid().toString();
    }

    /** Mã tài khoản: UUID v7 (ADR-0001). */
    @Bean
    public IdGenerator idGenerator() {
        return new UuidV7IdGenerator();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
