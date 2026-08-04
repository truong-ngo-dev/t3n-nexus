-- ============================================================
-- order-service — initial schema
-- ============================================================

-- ------------------------------------------------------------
-- orders
-- CRUD aggregate — current-state row per Order, optimistic concurrency via `version`.
-- (Not event-sourced: no temporal-query need, no replay-dependent invariant for Order;
-- event-sourcing-starter reserved for a genuine ledger use case elsewhere, e.g. Loyalty.)
-- ------------------------------------------------------------
CREATE TABLE orders (
    id               VARCHAR(26)  NOT NULL,
    customer_id      VARCHAR(26)  NOT NULL,
    seller_id        VARCHAR(26)  NOT NULL,
    items            JSONB        NOT NULL,
    payment_method   VARCHAR(20)  NOT NULL,
    shipping_address JSONB        NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    cancel_reason    VARCHAR(30),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT chk_orders_status CHECK (status IN ('CREATED', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);

-- ------------------------------------------------------------
-- outbox_events  (managed by outbox-starter, monitored by Debezium CDC)
-- ------------------------------------------------------------
CREATE TABLE outbox_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id       VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(36)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_on    TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    trace_id       VARCHAR(64),
    span_id        VARCHAR(64),

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_events_created_at ON outbox_events (created_at);
