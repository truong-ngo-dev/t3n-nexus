package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Quét entity của dịch vụ, của hộp thư event và của chỗ tải lên (upload-starter). Hộp thư tự khai báo {@code @EnableJpaRepositories} riêng nên
 * Spring Boot không quét kho của dịch vụ nữa; khai báo lại ở đây.
 */
@Configuration
@EntityScan({"vn.t3nexus.catalog", "vn.t3nexus.lib.outbox", "vn.t3nexus.lib.upload"})
@EnableJpaRepositories(basePackages = "vn.t3nexus.catalog.infrastructure.persistence")
public class JpaConfig {
}
