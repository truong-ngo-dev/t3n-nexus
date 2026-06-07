-- status không được persist: existence = ACTIVE, phân biệt REVOKED/EXPIRED carry bởi event type
ALTER TABLE oauth_sessions DROP COLUMN status;
