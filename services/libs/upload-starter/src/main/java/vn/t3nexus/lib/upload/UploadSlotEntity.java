package vn.t3nexus.lib.upload;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Dòng bảng {@code upload_slot} (mẫu schema ở {@code db/upload-schema.sql}). */
@Entity
@Table(name = "upload_slot")
class UploadSlotEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "purpose", nullable = false, updatable = false, length = 40)
    private String purpose;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(name = "object_key", nullable = false, updatable = false, length = 200)
    private String objectKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 40)
    private String contentType;

    @Column(name = "declared_size", nullable = false, updatable = false)
    private long declaredSize;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UploadSlotEntity() {
    }

    UploadSlotEntity(UploadSlot slot) {
        this.id = slot.id();
        this.purpose = slot.purpose();
        this.accountId = slot.accountId();
        this.targetId = slot.targetId();
        this.objectKey = slot.objectKey();
        this.contentType = slot.contentType();
        this.declaredSize = slot.declaredSize();
        this.expiresAt = slot.expiresAt();
        this.createdAt = slot.createdAt();
    }

    UploadSlot toSlot() {
        return new UploadSlot(id, purpose, accountId, targetId, objectKey, contentType, declaredSize, expiresAt,
                createdAt);
    }
}
