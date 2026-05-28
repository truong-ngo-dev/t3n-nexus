ALTER TABLE outbox_events
    ADD COLUMN trace_id VARCHAR(64),
    ADD COLUMN span_id  VARCHAR(64);
