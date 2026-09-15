-- ============================================================
-- scheduler-service — initial schema
-- ============================================================

-- ------------------------------------------------------------
-- outbox_events  (managed by outbox-starter, monitored by Debezium CDC)
-- scheduled_job se duoc them o migration ke tiep (implementation.md Phase 2) khi ScheduledJob
-- aggregate duoc code (Phase 1) — bootstrap nay chi tao du schema cho outbox-starter's OutboxEvent
-- entity de app boot duoc voi spring.jpa.hibernate.ddl-auto=validate.
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
