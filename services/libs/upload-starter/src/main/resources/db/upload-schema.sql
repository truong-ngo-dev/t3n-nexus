-- Mẫu schema của upload-starter (ADR-0013). Mỗi dịch vụ dùng thư viện CHÉP đoạn này vào migration của mình
-- (như outbox-schema.sql); không sửa tên cột và tên ràng buộc. Thêm gói vn.t3nexus.lib.upload vào @EntityScan.
CREATE TABLE upload_slot (
    id            UUID         NOT NULL,
    purpose       VARCHAR(40)  NOT NULL,
    account_id    UUID         NOT NULL,
    target_id     UUID         NOT NULL,
    object_key    VARCHAR(200) NOT NULL,
    content_type  VARCHAR(40)  NOT NULL,
    declared_size BIGINT       NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_upload_slot PRIMARY KEY (id),
    CONSTRAINT uq_upload_slot_object_key UNIQUE (object_key),
    CONSTRAINT ck_upload_slot_size CHECK (declared_size > 0)
);
CREATE INDEX idx_upload_slot_target ON upload_slot (target_id);
CREATE INDEX idx_upload_slot_expires ON upload_slot (expires_at);
