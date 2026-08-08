package vn.t3nexus.identity.infrastructure.cross_cutting.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
        // Default SDK timeout thực chất không giới hạn — nếu MinIO treo/chậm, request upload/delete
        // avatar giữ thread vô thời hạn (cùng loại rủi ro đã gặp ở WebGatewayRevocationClient,
        // 03-logout). Write timeout nới hơn connect/read vì avatar tối đa 5MB, cần đủ thời gian
        // truyền, đặc biệt trên máy dev chậm.
        client.setTimeout(3_000, 10_000, 8_000);
        return client;
    }

    @Bean
    public ApplicationRunner initMinioBuckets(MinioClient minioClient) {
        return args -> {
            String bucket = minioProperties.getUserAvatarsBucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[MinIO] bucket created: {}", bucket);
            }
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [{
                        "Effect": "Allow",
                        "Principal": {"AWS": "*"},
                        "Action": ["s3:GetObject"],
                        "Resource": ["arn:aws:s3:::%s/*"]
                      }]
                    }
                    """.formatted(bucket);
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
            log.info("[MinIO] bucket policy set to public-read: {}", bucket);
        };
    }
}
