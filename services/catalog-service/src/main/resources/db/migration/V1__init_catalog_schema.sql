-- ============================================================
-- catalog-service — initial schema
-- ============================================================

-- ------------------------------------------------------------
-- brand
-- ------------------------------------------------------------
CREATE TABLE brand (
    id          VARCHAR(26)            NOT NULL,
    name        VARCHAR(255)    NOT NULL,
    slug        VARCHAR(255)    NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_brand        PRIMARY KEY (id),
    CONSTRAINT uq_brand_slug   UNIQUE (slug),
    CONSTRAINT chk_brand_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- ------------------------------------------------------------
-- attribute_template
-- ------------------------------------------------------------
CREATE TABLE attribute_template (
    id           VARCHAR(26)            NOT NULL,
    name         VARCHAR(255)    NOT NULL,
    display_name VARCHAR(255)    NOT NULL,
    input_type   VARCHAR(20)     NOT NULL,
    scope        VARCHAR(20)     NOT NULL,
    created_at   TIMESTAMPTZ     NOT NULL,
    updated_at   TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_attribute_template        PRIMARY KEY (id),
    CONSTRAINT uq_attribute_template_name   UNIQUE (name),
    CONSTRAINT chk_attribute_template_input_type CHECK (input_type IN ('SELECT', 'TEXT', 'NUMBER', 'BOOLEAN')),
    CONSTRAINT chk_attribute_template_scope      CHECK (scope IN ('GLOBAL', 'CATEGORY'))
);

-- ------------------------------------------------------------
-- attribute_option
-- ------------------------------------------------------------
CREATE TABLE attribute_option (
    id            VARCHAR(26)            NOT NULL,
    template_id   VARCHAR(26)            NOT NULL,
    value         VARCHAR(255)    NOT NULL,
    display_value VARCHAR(255)    NOT NULL,
    status        VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_attribute_option        PRIMARY KEY (id),
    CONSTRAINT fk_attribute_option_template FOREIGN KEY (template_id)
        REFERENCES attribute_template (id) ON DELETE CASCADE,
    CONSTRAINT chk_attribute_option_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- ------------------------------------------------------------
-- category
-- ------------------------------------------------------------
CREATE TABLE category (
    id          VARCHAR(26)            NOT NULL,
    name        VARCHAR(255)    NOT NULL,
    slug        VARCHAR(255)    NOT NULL,
    parent_id   VARCHAR(26),
    level       SMALLINT        NOT NULL,
    image_url   VARCHAR(512),
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_category         PRIMARY KEY (id),
    CONSTRAINT uq_category_slug    UNIQUE (slug),
    CONSTRAINT fk_category_parent  FOREIGN KEY (parent_id)
        REFERENCES category (id) ON DELETE RESTRICT,
    CONSTRAINT chk_category_level  CHECK (level BETWEEN 1 AND 3),
    CONSTRAINT chk_category_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- ------------------------------------------------------------
-- category_closure  (closure table for category tree traversal)
-- ------------------------------------------------------------
CREATE TABLE category_closure (
    ancestor_id   VARCHAR(26)    NOT NULL,
    descendant_id VARCHAR(26)    NOT NULL,
    depth         INT     NOT NULL,

    CONSTRAINT pk_category_closure PRIMARY KEY (ancestor_id, descendant_id),
    CONSTRAINT fk_category_closure_ancestor   FOREIGN KEY (ancestor_id)
        REFERENCES category (id) ON DELETE CASCADE,
    CONSTRAINT fk_category_closure_descendant FOREIGN KEY (descendant_id)
        REFERENCES category (id) ON DELETE CASCADE
);

CREATE INDEX idx_closure_ancestor   ON category_closure (ancestor_id);
CREATE INDEX idx_closure_descendant ON category_closure (descendant_id);

-- ------------------------------------------------------------
-- category_attribute_assignment
-- ------------------------------------------------------------
CREATE TABLE category_attribute_assignment (
    category_id         VARCHAR(26)        NOT NULL,
    template_id         VARCHAR(26)        NOT NULL,
    is_variant_defining BOOLEAN     NOT NULL DEFAULT FALSE,
    is_required         BOOLEAN     NOT NULL DEFAULT FALSE,
    is_filterable       BOOLEAN     NOT NULL DEFAULT FALSE,
    display_order       INT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_category_attribute_assignment PRIMARY KEY (category_id, template_id),
    CONSTRAINT fk_caa_category FOREIGN KEY (category_id)
        REFERENCES category (id) ON DELETE CASCADE,
    CONSTRAINT fk_caa_template FOREIGN KEY (template_id)
        REFERENCES attribute_template (id) ON DELETE RESTRICT
);

-- ------------------------------------------------------------
-- product
-- ------------------------------------------------------------
CREATE TABLE product (
    id                VARCHAR(26)            NOT NULL,
    seller_id         VARCHAR(26)            NOT NULL,
    category_id       VARCHAR(26)            NOT NULL,
    brand_id          VARCHAR(26),
    name              VARCHAR(500)    NOT NULL,
    description       TEXT,
    status            VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    warranty_months   INT,
    warranty_type     VARCHAR(50),
    warranty_coverage TEXT,
    created_at        TIMESTAMPTZ     NOT NULL,
    updated_at        TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_product          PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id)
        REFERENCES category (id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_brand    FOREIGN KEY (brand_id)
        REFERENCES brand (id) ON DELETE SET NULL,
    CONSTRAINT chk_product_status  CHECK (status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED', 'BLOCKED'))
);

CREATE INDEX idx_product_seller   ON product (seller_id);
CREATE INDEX idx_product_category ON product (category_id);
CREATE INDEX idx_product_status   ON product (status);

-- ------------------------------------------------------------
-- product_attribute_value
-- ------------------------------------------------------------
CREATE TABLE product_attribute_value (
    product_id  VARCHAR(26)            NOT NULL,
    template_id VARCHAR(26)            NOT NULL,
    value       VARCHAR(1000)   NOT NULL,

    CONSTRAINT pk_product_attribute_value PRIMARY KEY (product_id, template_id),
    CONSTRAINT fk_pav_product  FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT fk_pav_template FOREIGN KEY (template_id)
        REFERENCES attribute_template (id) ON DELETE RESTRICT
);

-- ------------------------------------------------------------
-- product_image
-- ------------------------------------------------------------
CREATE TABLE product_image (
    id            VARCHAR(26)            NOT NULL,
    product_id    VARCHAR(26)            NOT NULL,
    object_key    VARCHAR(512)    NOT NULL,
    display_order INT             NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_product_image         PRIMARY KEY (id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE CASCADE
);

CREATE INDEX idx_product_image_product ON product_image (product_id);

-- ------------------------------------------------------------
-- variant
-- ------------------------------------------------------------
CREATE TABLE variant (
    id               VARCHAR(26)            NOT NULL,
    product_id       VARCHAR(26)            NOT NULL,
    combination_hash VARCHAR(64)     NOT NULL,
    price            BIGINT          NOT NULL,
    original_price   BIGINT,
    weight           DECIMAL(10, 3),
    length           DECIMAL(10, 2),
    width            DECIMAL(10, 2),
    height           DECIMAL(10, 2),
    barcode          VARCHAR(100),
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ     NOT NULL,
    updated_at       TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_variant              PRIMARY KEY (id),
    CONSTRAINT uq_variant_combination  UNIQUE (product_id, combination_hash),
    CONSTRAINT fk_variant_product      FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT chk_variant_status         CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_variant_price          CHECK (price > 0),
    CONSTRAINT chk_variant_original_price CHECK (original_price IS NULL OR original_price > price)
);

CREATE INDEX idx_variant_product ON variant (product_id);

-- ------------------------------------------------------------
-- variant_combination_item
-- ------------------------------------------------------------
CREATE TABLE variant_combination_item (
    variant_id  VARCHAR(26)    NOT NULL,
    template_id VARCHAR(26)    NOT NULL,
    option_id   VARCHAR(26)    NOT NULL,

    CONSTRAINT pk_variant_combination_item PRIMARY KEY (variant_id, template_id),
    CONSTRAINT fk_vci_variant  FOREIGN KEY (variant_id)
        REFERENCES variant (id) ON DELETE CASCADE,
    CONSTRAINT fk_vci_template FOREIGN KEY (template_id)
        REFERENCES attribute_template (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vci_option   FOREIGN KEY (option_id)
        REFERENCES attribute_option (id) ON DELETE RESTRICT
);

-- ------------------------------------------------------------
-- variant_image
-- ------------------------------------------------------------
CREATE TABLE variant_image (
    id            VARCHAR(26)            NOT NULL,
    variant_id    VARCHAR(26)            NOT NULL,
    object_key    VARCHAR(512)    NOT NULL,
    display_order INT             NOT NULL DEFAULT 0,

    CONSTRAINT pk_variant_image         PRIMARY KEY (id),
    CONSTRAINT fk_variant_image_variant FOREIGN KEY (variant_id)
        REFERENCES variant (id) ON DELETE CASCADE
);

-- ------------------------------------------------------------
-- outbox_events  (managed by outbox-starter, monitored by Debezium CDC)
-- ------------------------------------------------------------
CREATE TABLE outbox_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id       VARCHAR(100)    NOT NULL,
    aggregate_type VARCHAR(100)    NOT NULL,
    aggregate_id   VARCHAR(36)     NOT NULL,
    event_type     VARCHAR(100)    NOT NULL,
    routing_key    VARCHAR(255)    NOT NULL,
    payload        TEXT            NOT NULL,
    occurred_on    TIMESTAMPTZ     NOT NULL,
    created_at     TIMESTAMPTZ     NOT NULL,
    trace_id       VARCHAR(64),
    span_id        VARCHAR(64),

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_events_created_at ON outbox_events (created_at);
