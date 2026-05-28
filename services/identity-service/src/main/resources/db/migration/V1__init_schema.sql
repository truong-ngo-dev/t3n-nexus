-- ============================================================
-- identity-service — initial schema
-- ============================================================

-- ------------------------------------------------------------
-- users
-- ------------------------------------------------------------
CREATE TABLE users (
    id              VARCHAR(26)     NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    phone_number    VARCHAR(255),
    hashed_password VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(100),
    role            VARCHAR(20)     NOT NULL,
    status          VARCHAR(10)     NOT NULL DEFAULT 'PENDING',
    locked_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone_number UNIQUE (phone_number),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'SELLER', 'SHIPPER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'PENDING'))
);

CREATE INDEX idx_users_email ON users (email);

-- ------------------------------------------------------------
-- email_verifications
-- ------------------------------------------------------------
CREATE TABLE email_verifications (
    seq         BIGINT GENERATED ALWAYS AS IDENTITY,
    id          VARCHAR(26)     NOT NULL,
    user_id     VARCHAR(26)     NOT NULL,
    email       VARCHAR(255)    NOT NULL,
    token       VARCHAR(50)     NOT NULL,
    expires_at  TIMESTAMPTZ     NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    verified_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_email_verifications    PRIMARY KEY (seq),
    CONSTRAINT uq_email_verifications_id      UNIQUE (id),
    CONSTRAINT uq_email_verifications_user_id UNIQUE (user_id),
    CONSTRAINT uq_email_verifications_token   UNIQUE (token),
    CONSTRAINT fk_email_verifications_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_email_verifications_status CHECK (status IN ('PENDING', 'VERIFIED'))
);

CREATE INDEX idx_email_verifications_token   ON email_verifications (token);
CREATE INDEX idx_email_verifications_user_id ON email_verifications (user_id);

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
    occurred_on    TIMESTAMPTZ     NOT NULL,
    created_at     TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_events_created_at ON outbox_events (created_at);

-- ------------------------------------------------------------
-- ABAC — authx_named_expression
-- ------------------------------------------------------------
CREATE TABLE authx_named_expression (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    code        VARCHAR(255)    NOT NULL,
    description TEXT            NOT NULL,
    spel        TEXT            NOT NULL,

    CONSTRAINT pk_authx_named_expression PRIMARY KEY (id),
    CONSTRAINT uq_authx_named_expression_code UNIQUE (code),
    CONSTRAINT uq_authx_named_expression_spel UNIQUE (spel)
);

-- ------------------------------------------------------------
-- ABAC — authx_expression
-- ------------------------------------------------------------
CREATE TABLE authx_expression (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY,
    parent_id             BIGINT,
    node_type             VARCHAR(20)     NOT NULL,
    operator              VARCHAR(10),
    named_expression_id   BIGINT,

    CONSTRAINT pk_authx_expression PRIMARY KEY (id),
    CONSTRAINT fk_authx_expression_parent FOREIGN KEY (parent_id)
        REFERENCES authx_expression (id) ON DELETE CASCADE,
    CONSTRAINT fk_authx_expression_named FOREIGN KEY (named_expression_id)
        REFERENCES authx_named_expression (id),
    CONSTRAINT chk_authx_expression_node_type CHECK (node_type IN ('LITERAL', 'COMPOSITION')),
    CONSTRAINT chk_authx_expression_operator   CHECK (operator IN ('AND', 'OR') OR operator IS NULL)
);

-- ------------------------------------------------------------
-- ABAC — authx_policy_set
-- ------------------------------------------------------------
CREATE TABLE authx_policy_set (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY,
    parent_id             BIGINT,
    code                  VARCHAR(255)    NOT NULL,
    description           TEXT            NOT NULL,
    target_expression_id  BIGINT,
    combine_algorithm     VARCHAR(50)     NOT NULL,
    attributes            JSONB,
    created_at            BIGINT          NOT NULL,
    updated_at            BIGINT          NOT NULL,

    CONSTRAINT pk_authx_policy_set PRIMARY KEY (id),
    CONSTRAINT uq_authx_policy_set_code UNIQUE (code),
    CONSTRAINT fk_authx_policy_set_parent FOREIGN KEY (parent_id)
        REFERENCES authx_policy_set (id) ON DELETE CASCADE,
    CONSTRAINT fk_authx_policy_set_target FOREIGN KEY (target_expression_id)
        REFERENCES authx_expression (id)
);

-- ------------------------------------------------------------
-- ABAC — authx_policy
-- ------------------------------------------------------------
CREATE TABLE authx_policy (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY,
    policy_set_id         BIGINT          NOT NULL,
    code                  VARCHAR(255)    NOT NULL,
    description           TEXT            NOT NULL,
    target_expression_id  BIGINT,
    combine_algorithm     VARCHAR(50)     NOT NULL,
    attributes            JSONB,
    created_at            BIGINT          NOT NULL,
    updated_at            BIGINT          NOT NULL,

    CONSTRAINT pk_authx_policy PRIMARY KEY (id),
    CONSTRAINT uq_authx_policy_code UNIQUE (code),
    CONSTRAINT fk_authx_policy_policy_set FOREIGN KEY (policy_set_id)
        REFERENCES authx_policy_set (id) ON DELETE CASCADE,
    CONSTRAINT fk_authx_policy_target FOREIGN KEY (target_expression_id)
        REFERENCES authx_expression (id)
);

-- ------------------------------------------------------------
-- ABAC — authx_rule
-- ------------------------------------------------------------
CREATE TABLE authx_rule (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY,
    policy_id                BIGINT          NOT NULL,
    code                     VARCHAR(255)    NOT NULL,
    description              TEXT            NOT NULL,
    target_expression_id     BIGINT,
    condition_expression_id  BIGINT,
    effect                   VARCHAR(10)     NOT NULL,
    order_index              INT             NOT NULL DEFAULT 0,
    attributes               JSONB,
    created_at               BIGINT          NOT NULL,
    updated_at               BIGINT          NOT NULL,

    CONSTRAINT pk_authx_rule PRIMARY KEY (id),
    CONSTRAINT uq_authx_rule_code UNIQUE (code),
    CONSTRAINT fk_authx_rule_policy FOREIGN KEY (policy_id)
        REFERENCES authx_policy (id) ON DELETE CASCADE,
    CONSTRAINT fk_authx_rule_target FOREIGN KEY (target_expression_id)
        REFERENCES authx_expression (id),
    CONSTRAINT fk_authx_rule_condition FOREIGN KEY (condition_expression_id)
        REFERENCES authx_expression (id),
    CONSTRAINT chk_authx_rule_effect CHECK (effect IN ('PERMIT', 'DENY'))
);
