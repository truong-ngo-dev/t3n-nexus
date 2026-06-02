-- ============================================================
-- oauth2-service — Spring Authorization Server + domain + Spring Session
-- ============================================================

-- ------------------------------------------------------------
-- Spring Authorization Server
-- Source: https://github.com/spring-projects/spring-authorization-server/blob/main/oauth2-authorization-server/src/main/resources/org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql
-- ------------------------------------------------------------

CREATE TABLE oauth2_registered_client (
    id                              VARCHAR(100)        NOT NULL,
    client_id                       VARCHAR(100)        NOT NULL,
    client_id_issued_at             TIMESTAMPTZ         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret                   VARCHAR(200)        DEFAULT NULL,
    client_secret_expires_at        TIMESTAMPTZ         DEFAULT NULL,
    client_name                     VARCHAR(200)        NOT NULL,
    client_authentication_methods   VARCHAR(1000)       NOT NULL,
    authorization_grant_types       VARCHAR(1000)       NOT NULL,
    redirect_uris                   VARCHAR(1000)       DEFAULT NULL,
    post_logout_redirect_uris       VARCHAR(1000)       DEFAULT NULL,
    scopes                          VARCHAR(1000)       NOT NULL,
    client_settings                 VARCHAR(2000)       NOT NULL,
    token_settings                  VARCHAR(2000)       NOT NULL,

    CONSTRAINT pk_oauth2_registered_client PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id    VARCHAR(100)    NOT NULL,
    principal_name          VARCHAR(200)    NOT NULL,
    authorities             VARCHAR(1000)   NOT NULL,

    CONSTRAINT pk_oauth2_authorization_consent PRIMARY KEY (registered_client_id, principal_name)
);

CREATE TABLE oauth2_authorization (
    id                              VARCHAR(100)    NOT NULL,
    registered_client_id            VARCHAR(100)    NOT NULL,
    principal_name                  VARCHAR(200)    NOT NULL,
    authorization_grant_type        VARCHAR(100)    NOT NULL,
    authorized_scopes               VARCHAR(1000)   DEFAULT NULL,
    attributes                      TEXT            DEFAULT NULL,
    state                           VARCHAR(500)    DEFAULT NULL,
    authorization_code_value        TEXT            DEFAULT NULL,
    authorization_code_issued_at    TIMESTAMPTZ     DEFAULT NULL,
    authorization_code_expires_at   TIMESTAMPTZ     DEFAULT NULL,
    authorization_code_metadata     TEXT            DEFAULT NULL,
    access_token_value              TEXT            DEFAULT NULL,
    access_token_issued_at          TIMESTAMPTZ     DEFAULT NULL,
    access_token_expires_at         TIMESTAMPTZ     DEFAULT NULL,
    access_token_metadata           TEXT            DEFAULT NULL,
    access_token_type               VARCHAR(100)    DEFAULT NULL,
    access_token_scopes             VARCHAR(1000)   DEFAULT NULL,
    oidc_id_token_value             TEXT            DEFAULT NULL,
    oidc_id_token_issued_at         TIMESTAMPTZ     DEFAULT NULL,
    oidc_id_token_expires_at        TIMESTAMPTZ     DEFAULT NULL,
    oidc_id_token_metadata          TEXT            DEFAULT NULL,
    oidc_id_token_claims            TEXT            DEFAULT NULL,
    refresh_token_value             TEXT            DEFAULT NULL,
    refresh_token_issued_at         TIMESTAMPTZ     DEFAULT NULL,
    refresh_token_expires_at        TIMESTAMPTZ     DEFAULT NULL,
    refresh_token_metadata          TEXT            DEFAULT NULL,
    user_code_value                 TEXT            DEFAULT NULL,
    user_code_issued_at             TIMESTAMPTZ     DEFAULT NULL,
    user_code_expires_at            TIMESTAMPTZ     DEFAULT NULL,
    user_code_metadata              TEXT            DEFAULT NULL,
    device_code_value               TEXT            DEFAULT NULL,
    device_code_issued_at           TIMESTAMPTZ     DEFAULT NULL,
    device_code_expires_at          TIMESTAMPTZ     DEFAULT NULL,
    device_code_metadata            TEXT            DEFAULT NULL,

    CONSTRAINT pk_oauth2_authorization PRIMARY KEY (id)
);

-- ------------------------------------------------------------
-- rsa_key_pairs — rotating JWT signing keys
-- ------------------------------------------------------------

CREATE TABLE rsa_key_pairs (
    id          VARCHAR(100)    NOT NULL,
    private_key TEXT            NOT NULL,
    public_key  TEXT            NOT NULL,
    created     DATE            NOT NULL,

    CONSTRAINT pk_rsa_key_pairs PRIMARY KEY (id)
);

-- ------------------------------------------------------------
-- oauth_sessions
-- ------------------------------------------------------------

CREATE TABLE oauth_sessions (
    id                      VARCHAR(26)     NOT NULL,
    user_id                 VARCHAR(26)     NOT NULL,
    device_id               VARCHAR(26)     NOT NULL,
    idp_session_id          VARCHAR(100),
    authorization_id VARCHAR(100)    NOT NULL,
    ip_address              VARCHAR(50),
    status                  VARCHAR(20)     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL,
    updated_at              TIMESTAMPTZ,

    CONSTRAINT pk_oauth_sessions PRIMARY KEY (id)
);

CREATE UNIQUE INDEX idx_oauth_sessions_authorization_id  ON oauth_sessions (authorization_id);
CREATE INDEX idx_oauth_sessions_user_id                  ON oauth_sessions (user_id);
CREATE INDEX idx_oauth_sessions_device_id                ON oauth_sessions (device_id);
CREATE INDEX idx_oauth_sessions_idp_session_id           ON oauth_sessions (idp_session_id);

-- ------------------------------------------------------------
-- Spring Session JDBC — PostgreSQL
-- Source: https://github.com/spring-projects/spring-session/blob/main/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-postgresql.sql
-- ------------------------------------------------------------

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID              CHAR(36)        NOT NULL,
    SESSION_ID              CHAR(36)        NOT NULL,
    CREATION_TIME           BIGINT          NOT NULL,
    LAST_ACCESS_TIME        BIGINT          NOT NULL,
    MAX_INACTIVE_INTERVAL   INT             NOT NULL,
    EXPIRY_TIME             BIGINT          NOT NULL,
    PRINCIPAL_NAME          VARCHAR(100),

    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX        SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX        SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID  CHAR(36)        NOT NULL,
    ATTRIBUTE_NAME      VARCHAR(200)    NOT NULL,
    ATTRIBUTE_BYTES     BYTEA           NOT NULL,

    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);
