package vn.t3nexus.lib.upload;

import java.time.Instant;
import java.util.UUID;

/** "Chỗ đã cấp": bằng chứng tệp được xin cho đúng người và đúng đối tượng. Dùng xong thì bị xóa. */
public record UploadSlot(UUID id, String purpose, UUID accountId, UUID targetId, String objectKey,
                         String contentType, long declaredSize, Instant expiresAt, Instant createdAt) {
}
