ALTER TABLE oauth_sessions ADD COLUMN registered_client_id VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE oauth_sessions ADD COLUMN version              BIGINT       NOT NULL DEFAULT 0;

ALTER TABLE oauth_sessions DROP CONSTRAINT IF EXISTS uq_oauth_session_active;
ALTER TABLE oauth_sessions
    ADD CONSTRAINT uq_oauth_session_idp_client_active
    UNIQUE (idp_session_id, registered_client_id);
