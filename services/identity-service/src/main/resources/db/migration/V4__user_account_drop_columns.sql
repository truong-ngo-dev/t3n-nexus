-- Remove password and role from users table — now owned by oauth2-service (ADR-001)
ALTER TABLE users DROP COLUMN IF EXISTS hashed_password;
ALTER TABLE users DROP COLUMN IF EXISTS role;
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'PENDING';
