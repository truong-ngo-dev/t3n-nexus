package vn.t3nexus.seller.infrastructure.crosscutting.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileJpaRepository;

/**
 * Quét entity của dịch vụ và của hộp thư event. Hộp thư tự khai báo {@code @EnableJpaRepositories} riêng nên
 * Spring Boot không quét kho của dịch vụ nữa; khai báo lại ở đây.
 */
@Configuration
@EntityScan({"vn.t3nexus.seller", "vn.t3nexus.lib.outbox"})
@EnableJpaRepositories(basePackageClasses = SellerProfileJpaRepository.class)
public class JpaConfig {
}
