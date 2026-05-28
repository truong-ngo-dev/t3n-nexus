-- Reference DDL for outbox_events table.
-- Copy into your Flyway/Liquibase migration and adjust dialect as needed.

CREATE TABLE outbox_events (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    event_id       VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_on    TIMESTAMP    NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id       VARCHAR(64),
    span_id        VARCHAR(64),

    PRIMARY KEY (id),
    INDEX idx_outbox_created_at (created_at)
);
