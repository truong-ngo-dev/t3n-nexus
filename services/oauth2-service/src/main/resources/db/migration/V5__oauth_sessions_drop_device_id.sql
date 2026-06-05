DROP INDEX IF EXISTS idx_oauth_sessions_device_id;

ALTER TABLE oauth_sessions
    DROP COLUMN device_id;
