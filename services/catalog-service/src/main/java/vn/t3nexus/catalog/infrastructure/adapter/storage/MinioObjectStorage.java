package vn.t3nexus.catalog.infrastructure.adapter.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.application.product.ObjectStoragePort;
import vn.t3nexus.catalog.infrastructure.crosscutting.config.MinioProperties;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioObjectStorage implements ObjectStoragePort {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public String generatePresignedPutUrl(String objectKey, int ttlSeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Http.Method.PUT)
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .expiry(ttlSeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new ObjectStorageException("Failed to generate presigned PUT URL for key: " + objectKey, e);
        }
    }

    @Override
    public boolean objectExists(String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new ObjectStorageException("Failed to check existence of key: " + objectKey, e);
        } catch (Exception e) {
            throw new ObjectStorageException("Failed to check existence of key: " + objectKey, e);
        }
    }
}
