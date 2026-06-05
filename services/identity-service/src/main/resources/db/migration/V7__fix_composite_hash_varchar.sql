ALTER TABLE devices
    ALTER COLUMN composite_hash TYPE VARCHAR(64);

ALTER TABLE login_activities
    ALTER COLUMN composite_hash TYPE VARCHAR(64);
