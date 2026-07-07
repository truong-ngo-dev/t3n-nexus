-- ============================================================
-- inventory-service — initial schema
-- ============================================================

-- ------------------------------------------------------------
-- stock
-- One row per SKU. reservedQuantity managed by service, never by seller.
-- ------------------------------------------------------------
CREATE TABLE stock (
    id                   VARCHAR(26)     NOT NULL,
    sku_id               VARCHAR(26)     NOT NULL,
    product_id           VARCHAR(26)     NOT NULL,
    seller_id            VARCHAR(26)     NOT NULL,
    total_quantity       INT             NOT NULL DEFAULT 0,
    reserved_quantity    INT             NOT NULL DEFAULT 0,
    status               VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    low_stock_threshold  INT             NOT NULL DEFAULT 0,
    version              BIGINT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ     NOT NULL,
    updated_at           TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_stock PRIMARY KEY (id),
    CONSTRAINT uq_stock_sku_id UNIQUE (sku_id),
    CONSTRAINT chk_stock_total_quantity CHECK (total_quantity >= 0),
    CONSTRAINT chk_stock_reserved_quantity CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_stock_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_stock_product_id ON stock (product_id);
CREATE INDEX idx_stock_seller_id  ON stock (seller_id);

-- ------------------------------------------------------------
-- reservation
-- One row per Order. order_id is the business dedup key.
-- ------------------------------------------------------------
CREATE TABLE reservation (
    id          VARCHAR(26)     NOT NULL,
    order_id    VARCHAR(26)     NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    expires_at  TIMESTAMPTZ     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_reservation PRIMARY KEY (id),
    CONSTRAINT uq_reservation_order_id UNIQUE (order_id),
    CONSTRAINT chk_reservation_status CHECK (status IN ('PENDING', 'RELEASED', 'CANCELLED'))
);

CREATE INDEX idx_reservation_status_expires ON reservation (status, expires_at);

-- ------------------------------------------------------------
-- reservation_item
-- Append-only. No updates after creation.
-- ------------------------------------------------------------
CREATE TABLE reservation_item (
    id              VARCHAR(26) NOT NULL,
    reservation_id  VARCHAR(26) NOT NULL,
    sku_id          VARCHAR(26) NOT NULL,
    qty             INT     NOT NULL,

    CONSTRAINT pk_reservation_item PRIMARY KEY (id),
    CONSTRAINT fk_reservation_item_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservation (id) ON DELETE CASCADE,
    CONSTRAINT chk_reservation_item_qty CHECK (qty > 0)
);

CREATE INDEX idx_reservation_item_reservation_id ON reservation_item (reservation_id);

-- ------------------------------------------------------------
-- limited_offer
-- Shadow of Promotion BC config. Enforced via Redis slot counter.
-- ------------------------------------------------------------
CREATE TABLE limited_offer (
    id              VARCHAR(26)     NOT NULL,
    sku_id          VARCHAR(26)     NOT NULL,
    max_quantity    INT             NOT NULL,
    window_seconds  INT             NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'INACTIVE',
    activated_at    TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_limited_offer PRIMARY KEY (id),
    CONSTRAINT uq_limited_offer_sku_id UNIQUE (sku_id),
    CONSTRAINT chk_limited_offer_max_quantity CHECK (max_quantity > 0),
    CONSTRAINT chk_limited_offer_window_seconds CHECK (window_seconds > 0),
    CONSTRAINT chk_limited_offer_status CHECK (status IN ('INACTIVE', 'ACTIVE', 'ENDED'))
);

CREATE INDEX idx_limited_offer_status ON limited_offer (status);

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
