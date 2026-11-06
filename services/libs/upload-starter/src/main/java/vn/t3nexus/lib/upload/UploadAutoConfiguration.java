package vn.t3nexus.lib.upload;

import io.minio.MinioClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;

/**
 * Tự cấu hình cơ chế tải tệp lên dùng chung (ADR-0013). Dịch vụ dùng cần: bean {@link IdGenerator} và {@link Clock},
 * bảng {@code upload_slot} (mẫu {@code db/upload-schema.sql}), và thêm gói {@code vn.t3nexus.lib.upload} vào {@code @EntityScan}.
 */
@AutoConfiguration
@ConditionalOnClass(MinioClient.class)
@EnableConfigurationProperties({UploadProperties.class, MinioConnectionProperties.class})
@Import({UploadJpaConfiguration.class, UploadAutoConfiguration.SchedulingSupport.class})
public class UploadAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MinioClient uploadMinioClient(MinioConnectionProperties connection) {
        return MinioClient.builder()
                .endpoint(connection.getEndpoint())
                .credentials(connection.getAccessKey(), connection.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStore.class)
    ObjectStore uploadObjectStore(MinioClient client, MinioConnectionProperties connection) {
        return new MinioObjectStore(client, connection);
    }

    @Bean
    @ConditionalOnMissingBean(UploadSlotStore.class)
    UploadSlotStore uploadSlotStore(UploadSlotJpaRepository repository) {
        return new JpaUploadSlotStore(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public UploadSlots uploadSlots(UploadProperties properties, ObjectStore store, UploadSlotStore slots,
                                   IdGenerator ids, Clock clock) {
        return new UploadSlots(properties, store, slots, ids, clock);
    }

    /** Tạo bucket (và policy đọc công khai) của từng mục đích khi khởi động. */
    @Bean
    @ConditionalOnProperty(prefix = "upload", name = "ensure-buckets", havingValue = "true", matchIfMissing = true)
    ApplicationRunner uploadBucketInitializer(UploadProperties properties, ObjectStore store) {
        // Kho đối tượng chưa chạy không được làm dịch vụ không khởi động: chỉ cảnh báo, lần tải lên đầu sẽ báo 503
        return args -> properties.getPurposes().values().forEach(purpose -> {
            try {
                store.ensureBucket(purpose.getBucket(), purpose.isPublicRead());
            } catch (RuntimeException e) {
                System.getLogger(UploadAutoConfiguration.class.getName()).log(System.Logger.Level.WARNING,
                        "Chưa chuẩn bị được bucket " + purpose.getBucket() + ": " + e.getMessage());
            }
        });
    }

    @Bean
    @ConditionalOnProperty(prefix = "upload", name = "cleaner-enabled", havingValue = "true", matchIfMissing = true)
    UploadCleaner uploadCleaner(UploadSlots slots) {
        return new UploadCleaner(slots);
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "upload", name = "cleaner-enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingSupport {
    }
}
