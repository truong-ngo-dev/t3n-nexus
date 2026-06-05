-- ============================================================
-- identity-service — devices & login_activities
-- ============================================================

-- ------------------------------------------------------------
-- devices
-- ------------------------------------------------------------
CREATE TABLE devices (
    id              VARCHAR(26)     NOT NULL,
    user_id         VARCHAR(26)     NOT NULL,
    device_hash     VARCHAR(255)    NOT NULL,
    user_agent      TEXT            NOT NULL,
    accept_language VARCHAR(255),
    composite_hash  CHAR(64)        NOT NULL,
    device_name     VARCHAR(255)    NOT NULL,
    device_type     VARCHAR(20)     NOT NULL,
    trusted         BOOLEAN         NOT NULL DEFAULT FALSE,
    status          VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    registered_at   TIMESTAMPTZ     NOT NULL,
    last_seen_at    TIMESTAMPTZ     NOT NULL,
    last_ip_address VARCHAR(45)     NOT NULL,

    CONSTRAINT pk_devices                   PRIMARY KEY (id),
    CONSTRAINT uq_devices_user_fingerprint  UNIQUE (user_id, composite_hash),
    CONSTRAINT fk_devices_user              FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_devices_type   CHECK (device_type IN ('ANDROID', 'IOS', 'WEB', 'DESKTOP_APP', 'OTHER')),
    CONSTRAINT chk_devices_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_devices_user_id ON devices (user_id);

-- ------------------------------------------------------------
-- login_activities
-- ------------------------------------------------------------
CREATE TABLE login_activities (
    id             VARCHAR(26)     NOT NULL,
    user_id        VARCHAR(26)     NOT NULL,
    username       VARCHAR(255)    NOT NULL,
    result         VARCHAR(20)     NOT NULL,
    ip_address     VARCHAR(45)     NOT NULL,
    user_agent     TEXT            NOT NULL,
    composite_hash CHAR(64),
    device_id      VARCHAR(26),
    session_id     VARCHAR(26),
    provider       VARCHAR(10)     NOT NULL,
    created_at     TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_login_activities        PRIMARY KEY (id),
    CONSTRAINT fk_login_activities_user   FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_login_activities_result   CHECK (result IN ('SUCCESS', 'WRONG_PASSWORD', 'ACCOUNT_LOCKED', 'MFA_FAILED')),
    CONSTRAINT chk_login_activities_provider CHECK (provider IN ('LOCAL', 'GOOGLE'))
);

CREATE INDEX idx_login_activities_user_id    ON login_activities (user_id);
CREATE INDEX idx_login_activities_created_at ON login_activities (created_at);

-- Partial unique index: 1 OAuthSession → at most 1 LoginActivity
-- Required for ON CONFLICT (session_id) WHERE session_id IS NOT NULL DO NOTHING
CREATE UNIQUE INDEX idx_login_activities_session_id
    ON login_activities (session_id)
    WHERE session_id IS NOT NULL;
