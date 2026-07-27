-- ============================================================
-- order-service — initial schema
-- ============================================================

-- ------------------------------------------------------------
-- event_store
-- Append-only. (aggregate_id, revision) is the optimistic-concurrency guard.
-- ------------------------------------------------------------
CREATE TABLE event_store (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    aggregate_id   VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    revision       BIGINT       NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    event_id       VARCHAR(100) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_on    TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_event_store PRIMARY KEY (id),
    CONSTRAINT uq_event_store_aggregate_revision UNIQUE (aggregate_id, revision)
);

CREATE INDEX idx_event_store_aggregate_id ON event_store (aggregate_id);

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
