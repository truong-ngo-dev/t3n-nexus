-- Ảnh sản phẩm (feature 08): upload_slot (mẫu của upload-starter, ADR-0013) và product_image.
-- Giải pháp: docs/stage/c1a-listing/08-product-image/tech.md T2; dữ liệu: docs/service/catalog-service/data.md mục 10.

-- Chỗ tải lên đã cấp (bằng chứng "ảnh được xin cho đúng người và đúng sản phẩm"). Chép nguyên từ
-- libs/upload-starter/src/main/resources/db/upload-schema.sql, không sửa. Dùng xong thì bị xóa.
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

-- Ảnh của sản phẩm (entity của AGG-CAT-04). Tối đa 9 ảnh không có CHECK (đếm theo hàng): giữ bằng khóa dòng sản phẩm (ADR-0012).
CREATE TABLE product_image (
    id           UUID         NOT NULL,
    product_id   UUID         NOT NULL,
    object_key   VARCHAR(200) NOT NULL,
    content_type VARCHAR(20)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    position     INTEGER      NOT NULL,

    CONSTRAINT pk_product_image PRIMARY KEY (id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT uq_product_image_position UNIQUE (product_id, position),
    CONSTRAINT uq_product_image_object_key UNIQUE (object_key),
    CONSTRAINT ck_product_image_position CHECK (position >= 0),
    CONSTRAINT ck_product_image_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_product_image_product ON product_image (product_id);
