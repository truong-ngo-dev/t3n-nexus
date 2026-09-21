-- ============================================================
-- order-service — CREATED-state timeout (Phase 5, place-order feature)
-- Lớp 1 (Redis ZSET, tốc độ) ghi/đọc riêng phía order-service, không thuộc migration này.
-- Lớp 2 (Postgres backstop) — cột + partial index dưới đây.
-- ============================================================

ALTER TABLE orders ADD COLUMN inventory_reply_deadline TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE orders ALTER COLUMN inventory_reply_deadline DROP DEFAULT;

CREATE INDEX idx_orders_inventory_timeout ON orders (inventory_reply_deadline) WHERE status = 'CREATED';
