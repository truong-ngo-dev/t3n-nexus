-- ============================================================
-- notification-service — initial schema
-- ============================================================

-- ------------------------------------------------------------
-- notification_log
-- CDC source (Debezium reads WAL). Immutable after INSERT.
-- ------------------------------------------------------------
CREATE TABLE notification_log (
    id                  VARCHAR(26)     NOT NULL,
    event_id            VARCHAR(26)     NOT NULL,
    notification_type   VARCHAR(50)     NOT NULL,
    channel             VARCHAR(10)     NOT NULL,
    tier                VARCHAR(20)     NOT NULL,
    user_id             VARCHAR(26)     NOT NULL,
    recipient           VARCHAR(255)    NULL,
    payload             JSONB           NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_notification_log                      PRIMARY KEY (id),
    CONSTRAINT uq_notification_log_event_id_channel     UNIQUE (event_id, channel),
    CONSTRAINT chk_notification_log_channel             CHECK (channel IN ('EMAIL', 'IN_APP')),
    CONSTRAINT chk_notification_log_tier                CHECK (tier IN ('TRANSACTIONAL', 'BULK'))
);

CREATE INDEX idx_notification_log_event_id_channel  ON notification_log (event_id, channel);
CREATE INDEX idx_notification_log_user_id_created   ON notification_log (user_id, created_at DESC);

-- ------------------------------------------------------------
-- notification_inbox
-- User-facing inbox. Populated only for channel = IN_APP.
-- ------------------------------------------------------------
CREATE TABLE notification_inbox (
    id                      VARCHAR(26)     NOT NULL,
    notification_log_id     VARCHAR(26)     NOT NULL,
    user_id                 VARCHAR(26)     NOT NULL,
    title                   VARCHAR(255)    NOT NULL,
    body                    VARCHAR(500)    NOT NULL,
    action_url              VARCHAR(500)    NULL,
    is_read                 BOOLEAN         NOT NULL DEFAULT false,
    created_at              TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_notification_inbox                    PRIMARY KEY (id),
    CONSTRAINT fk_notification_inbox_log_id             FOREIGN KEY (notification_log_id)
                                                            REFERENCES notification_log (id)
                                                            ON DELETE CASCADE
);

CREATE INDEX idx_notification_inbox_user_id_is_read     ON notification_inbox (user_id, is_read);
CREATE INDEX idx_notification_inbox_user_id_created     ON notification_inbox (user_id, created_at DESC);
