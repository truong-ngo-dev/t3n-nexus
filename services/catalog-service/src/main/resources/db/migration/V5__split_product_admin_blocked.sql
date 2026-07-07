ALTER TABLE product
    ADD COLUMN admin_blocked BOOLEAN NOT NULL DEFAULT FALSE;

-- Migrate existing BLOCKED rows: restore to UNPUBLISHED, mark adminBlocked = true
UPDATE product
SET    admin_blocked = TRUE,
       status        = 'UNPUBLISHED'
WHERE  status = 'BLOCKED';
