package vn.t3nexus.seller.infrastructure.crosscutting.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;

import java.time.Clock;

/**
 * Cấu hình khởi tạo bean sinh mã định danh và đồng hồ hệ thống.
 */
@Configuration
public class IdGeneratorConfig {

    /**
     * Khởi tạo IdGenerator dùng chuẩn UUID v7 nếu chưa có bean khác.
     */
    @Bean
    @ConditionalOnMissingBean
    public IdGenerator idGenerator() {
        return new UuidV7IdGenerator();
    }

    /**
     * Khởi tạo Clock chuẩn UTC nếu chưa có bean khác.
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
