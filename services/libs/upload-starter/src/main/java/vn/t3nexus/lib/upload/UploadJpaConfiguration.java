package vn.t3nexus.lib.upload;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackageClasses = UploadSlotJpaRepository.class)
class UploadJpaConfiguration {
}
