-- ============================================================
-- oauth2-service — initial schema
-- ============================================================

-- ------------------------------------------------------------
-- user_credentials
-- ------------------------------------------------------------
CREATE TABLE user_credentials (
    id                  VARCHAR(26)     NOT NULL,
    email               VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255),
    role                VARCHAR(20)     NOT NULL,
    registration_method VARCHAR(20)     NOT NULL,
    status              VARCHAR(10)     NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_user_credentials                  PRIMARY KEY (id),
    CONSTRAINT uq_user_credentials_email            UNIQUE (email),
    CONSTRAINT chk_user_credentials_role            CHECK (role IN ('CUSTOMER', 'SELLER', 'SHIPPER', 'ADMIN')),
    CONSTRAINT chk_user_credentials_reg_method      CHECK (registration_method IN ('CREDENTIAL', 'OAUTH')),
    CONSTRAINT chk_user_credentials_status          CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED'))
);

CREATE INDEX idx_user_credentials_email ON user_credentials (email);

-- ------------------------------------------------------------
-- outbox_events  (managed by outbox-starter)
-- ------------------------------------------------------------
CREATE TABLE outbox_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id       VARCHAR(100)    NOT NULL,
    aggregate_type VARCHAR(100)    NOT NULL,
    aggregate_id   VARCHAR(26)     NOT NULL,
    event_type     VARCHAR(100)    NOT NULL,
    payload        TEXT            NOT NULL,
    routing_key    VARCHAR(255)    NOT NULL,
    occurred_on    TIMESTAMPTZ     NOT NULL,
    created_at     TIMESTAMPTZ     NOT NULL,
    trace_id       VARCHAR(64),
    span_id        VARCHAR(64),

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_events_created_at ON outbox_events (created_at);
