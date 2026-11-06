-- Hộp thư event theo mẫu outbox-starter (ADR-0008); Debezium đọc bảng này.
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
