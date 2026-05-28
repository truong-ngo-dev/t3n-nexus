ALTER TABLE outbox_events
    ADD COLUMN routing_key VARCHAR(255) NOT NULL;
