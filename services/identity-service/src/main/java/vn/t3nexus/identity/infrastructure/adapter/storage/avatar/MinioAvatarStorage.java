package vn.t3nexus.identity.infrastructure.adapter.storage.avatar;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.application.user_account.upload_avatar.AvatarStorage;
import vn.t3nexus.identity.infrastructure.cross_cutting.config.MinioProperties;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioAvatarStorage implements AvatarStorage {

    private final MinioClient     minioClient;
    private final MinioProperties minioProperties;
    private final ULIDGenerator   ulidGenerator;

    @Override
    public String upload(String userId, InputStream content, String contentType, long size) {
        String bucket    = minioProperties.getUserAvatarsBucket();
        String objectKey = userId + "/" + ulidGenerator.generate() + "." + extensionFor(contentType);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, size, (long) -1)
                    .contentType(contentType)
                    .build());
            return minioProperties.getEndpoint() + "/" + bucket + "/" + objectKey;
        } catch (Exception e) {
            throw new AvatarStorageException("Failed to upload avatar", e);
        }
    }

    @Override
    public void delete(String avatarUrl) {
        String bucket = minioProperties.getUserAvatarsBucket();
        String prefix = minioProperties.getEndpoint() + "/" + bucket + "/";
        if (!avatarUrl.startsWith(prefix)) {
            log.warn("[MinioAvatarStorage] unexpected avatarUrl format, skip delete: {}", avatarUrl);
            return;
        }
        String objectKey = avatarUrl.substring(prefix.length());
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("[MinioAvatarStorage] failed to delete old avatar key={}: {}", objectKey, e.getMessage());
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported content type: " + contentType);
        };
    }
}
