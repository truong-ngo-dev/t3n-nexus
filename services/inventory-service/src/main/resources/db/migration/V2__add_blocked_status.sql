ALTER TABLE stock
    DROP CONSTRAINT chk_stock_status,
    ADD  CONSTRAINT chk_stock_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'));
