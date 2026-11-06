-- Cấu trúc danh mục hàng: thương hiệu, mẫu thuộc tính, tùy chọn, danh mục, dòng gán, hộp thư event.
-- Bản gốc: hình cuối của feature 05 (docs/service/catalog-service/data.md). Bảng sản phẩm, biến thể, ảnh thêm bằng migration sau.
-- Khóa chính UUID v7 sinh ở ứng dụng (ADR-0001). Ràng buộc có tên để lớp trình bày đổi sang mã lỗi (ADR-0011).

-- Thương hiệu (AGG-CAT-03)
CREATE TABLE brand (
    id         UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    name_key   VARCHAR(100) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    version    BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_brand PRIMARY KEY (id),
    CONSTRAINT uq_brand_name_key UNIQUE (name_key),
    CONSTRAINT ck_brand_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Mẫu thuộc tính (AGG-CAT-02)
CREATE TABLE attribute_template (
    id           UUID         NOT NULL,
    name         VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    hint         VARCHAR(200),
    input_type   VARCHAR(20)  NOT NULL,
    unit         VARCHAR(20),
    status       VARCHAR(20)  NOT NULL,
    version      BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_attribute_template PRIMARY KEY (id),
    CONSTRAINT uq_attribute_template_name UNIQUE (name),
    CONSTRAINT ck_attribute_template_name_format CHECK (name ~ '^[a-z0-9]+(_[a-z0-9]+)*$'),
    CONSTRAINT ck_attribute_template_input_type
        CHECK (input_type IN ('SINGLE_SELECT', 'MULTI_SELECT', 'TEXT', 'NUMBER', 'BOOLEAN', 'DATE')),
    CONSTRAINT ck_attribute_template_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_attribute_template_unit CHECK (unit IS NULL OR input_type = 'NUMBER')
);

-- Tùy chọn của mẫu (thực thể con của attribute_template)
CREATE TABLE attribute_option (
    id            UUID         NOT NULL,
    template_id   UUID         NOT NULL,
    value         VARCHAR(100) NOT NULL,
    display_value VARCHAR(100) NOT NULL,
    sort_order    INT          NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_attribute_option PRIMARY KEY (id),
    CONSTRAINT fk_attribute_option_template FOREIGN KEY (template_id)
        REFERENCES attribute_template (id) ON DELETE RESTRICT,
    CONSTRAINT uq_attribute_option_template_value UNIQUE (template_id, value),
    CONSTRAINT ck_attribute_option_value_format CHECK (value ~ '^[a-z0-9]+([_-][a-z0-9]+)*$'),
    CONSTRAINT ck_attribute_option_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Danh mục (AGG-CAT-01): cây cố định ba cấp, cha không đổi sau khi tạo
CREATE TABLE category (
    id         UUID         NOT NULL,
    parent_id  UUID,
    level      SMALLINT     NOT NULL,
    name       VARCHAR(100) NOT NULL,
    name_key   VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    version    BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id) ON DELETE RESTRICT,
    CONSTRAINT ck_category_level CHECK (level BETWEEN 1 AND 3),
    CONSTRAINT ck_category_root_level CHECK ((level = 1) = (parent_id IS NULL)),
    CONSTRAINT ck_category_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Tên duy nhất trong cùng cha (INV-CAT-02). Cấp một có parent_id rỗng nên cần hai chỉ mục duy nhất từng phần
CREATE UNIQUE INDEX uq_category_parent_name_key ON category (parent_id, name_key) WHERE parent_id IS NOT NULL;
CREATE UNIQUE INDEX uq_category_root_name_key ON category (name_key) WHERE parent_id IS NULL;
CREATE INDEX idx_category_parent ON category (parent_id, sort_order);

-- Dòng gán mẫu cho danh mục lá (thuộc aggregate danh mục, ghi thay toàn bộ)
CREATE TABLE category_attribute_assignment (
    category_id   UUID    NOT NULL,
    template_id   UUID    NOT NULL,
    required      BOOLEAN NOT NULL,
    display_order INT     NOT NULL,
    constraints   JSONB   NOT NULL,

    CONSTRAINT pk_category_attribute_assignment PRIMARY KEY (category_id, template_id),
    CONSTRAINT fk_caa_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE CASCADE,
    CONSTRAINT fk_caa_template FOREIGN KEY (template_id) REFERENCES attribute_template (id) ON DELETE RESTRICT
);

-- Hộp thư event: hình bảng chung của outbox-starter (libs/outbox-starter/src/main/resources/db/outbox-schema.sql), không sửa
CREATE TABLE outbox_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id       VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_on    TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    trace_id       VARCHAR(64),
    span_id        VARCHAR(64),

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT uq_outbox_events_event_id UNIQUE (event_id)
);

CREATE INDEX idx_outbox_events_created_at ON outbox_events (created_at);
