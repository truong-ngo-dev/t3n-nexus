-- Reference DDL for event_store table.
-- Copy into your Flyway/Liquibase migration and adjust dialect as needed.

CREATE TABLE event_store (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_id   VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    revision       BIGINT       NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    event_id       VARCHAR(100) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_on    TIMESTAMP    NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE (aggregate_id, revision),
    INDEX idx_event_store_aggregate_id (aggregate_id)
);
