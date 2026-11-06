-- Sản phẩm và biến thể (feature 07): product, product_attribute, product_attribute_option, variant, variant_combination_item.
-- Giải pháp: docs/stage/c1a-listing/07-product-variant/tech.md T1; dữ liệu: docs/service/catalog-service/data.md mục 10.
-- Không có khóa ngoại từ sản phẩm sang danh mục, thương hiệu, mẫu thuộc tính (tham chiếu giữa aggregate bằng mã, kiểm ở ứng dụng).
-- Không có khóa ngoại từ biến thể tới giá trị trục: tổ hợp nằm trong tập trục do ứng dụng kiểm dưới khóa dòng sản phẩm (ADR-0012).

-- Sản phẩm (AGG-CAT-04). version dùng chung cho sản phẩm và biến thể (INV-CAT-36, ADR-0012).
CREATE TABLE product (
    id          UUID          NOT NULL,
    seller_id   UUID          NOT NULL,
    category_id UUID          NOT NULL,
    brand_id    UUID,
    title       VARCHAR(200)  NOT NULL,
    description TEXT,
    status      VARCHAR(20)   NOT NULL,
    version     BIGINT        NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT ck_product_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED')),
    CONSTRAINT ck_product_title_length CHECK (char_length(title) BETWEEN 5 AND 200),
    CONSTRAINT ck_product_description_length CHECK (description IS NULL OR char_length(description) <= 5000)
);

CREATE INDEX idx_product_seller ON product (seller_id, created_at DESC, id);

-- Giá trị thuộc tính: một dòng cho mỗi cặp (sản phẩm, mẫu). axis = thuộc tính làm trục biến thể (kiểu chọn một, tối đa 2: miền kiểm).
-- value giữ giá trị rời của chữ, số, đúng/sai, ngày (chuỗi chuẩn hóa); kiểu chọn để rỗng và dùng product_attribute_option.
CREATE TABLE product_attribute (
    product_id  UUID         NOT NULL,
    template_id UUID         NOT NULL,
    axis        BOOLEAN      NOT NULL,
    value       VARCHAR(255),

    CONSTRAINT pk_product_attribute PRIMARY KEY (product_id, template_id),
    CONSTRAINT fk_pa_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT ck_product_attribute_axis_no_value CHECK (NOT axis OR value IS NULL)
);

-- Tùy chọn đã chọn của thuộc tính kiểu chọn (lưu theo mã tùy chọn, không lưu chữ). Với dòng cha làm trục: tập giá trị của trục.
CREATE TABLE product_attribute_option (
    product_id  UUID NOT NULL,
    template_id UUID NOT NULL,
    option_id   UUID NOT NULL,

    CONSTRAINT pk_product_attribute_option PRIMARY KEY (product_id, template_id, option_id),
    CONSTRAINT fk_pao_attribute FOREIGN KEY (product_id, template_id)
        REFERENCES product_attribute (product_id, template_id) ON DELETE CASCADE
);

-- Biến thể (AGG-CAT-05). Không có version riêng: mọi lệnh biến thể tuần tự qua dòng sản phẩm. seller_id sao từ sản phẩm.
CREATE TABLE variant (
    id              UUID         NOT NULL,
    product_id      UUID         NOT NULL,
    seller_id       UUID         NOT NULL,
    combination_key VARCHAR(80)  NOT NULL,
    price           BIGINT       NOT NULL,
    sku             VARCHAR(50),
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_variant PRIMARY KEY (id),
    CONSTRAINT fk_variant_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT,
    CONSTRAINT uq_variant_product_combination UNIQUE (product_id, combination_key),
    CONSTRAINT ck_variant_price CHECK (price BETWEEN 1 AND 1000000000),
    CONSTRAINT ck_variant_sku_length CHECK (sku IS NULL OR char_length(sku) BETWEEN 1 AND 50),
    CONSTRAINT ck_variant_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_variant_seller_sku ON variant (seller_id, sku) WHERE sku IS NOT NULL;
CREATE INDEX idx_variant_product ON variant (product_id);

-- Tổ hợp của biến thể: mỗi trục một tùy chọn. Bất biến sau khi tạo (INV-CAT-41).
CREATE TABLE variant_combination_item (
    variant_id  UUID NOT NULL,
    template_id UUID NOT NULL,
    option_id   UUID NOT NULL,

    CONSTRAINT pk_variant_combination_item PRIMARY KEY (variant_id, template_id),
    CONSTRAINT fk_vci_variant FOREIGN KEY (variant_id) REFERENCES variant (id) ON DELETE CASCADE
);
