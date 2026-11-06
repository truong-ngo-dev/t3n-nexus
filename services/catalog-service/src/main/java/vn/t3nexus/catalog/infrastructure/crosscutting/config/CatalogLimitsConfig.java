package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.catalog.application.product.ImageLimits;
import vn.t3nexus.catalog.domain.product.ProductLimits;

/** Dựng {@link ProductLimits} từ cấu hình; giá trị sai (nới quá ràng buộc cơ sở dữ liệu) làm dịch vụ không khởi động. */
@Configuration
@EnableConfigurationProperties(CatalogLimitsProperties.class)
public class CatalogLimitsConfig {

    @Bean
    ImageLimits imageLimits(CatalogLimitsProperties properties) {
        return properties.toImageLimits();
    }

    @Bean
    ProductLimits productLimits(CatalogLimitsProperties properties) {
        return properties.toLimits();
    }
}
