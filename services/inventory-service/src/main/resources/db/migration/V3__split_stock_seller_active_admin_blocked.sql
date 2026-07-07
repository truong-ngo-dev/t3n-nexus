-- Add new columns, migrating data from status before dropping it
ALTER TABLE stock
    ADD COLUMN seller_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN admin_blocked BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE stock SET seller_active = FALSE WHERE status = 'INACTIVE';
UPDATE stock SET admin_blocked = TRUE    WHERE status = 'BLOCKED';

ALTER TABLE stock
    DROP CONSTRAINT chk_stock_status,
    DROP COLUMN status;
