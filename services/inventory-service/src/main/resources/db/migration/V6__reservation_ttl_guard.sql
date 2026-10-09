-- ============================================================
-- inventory-service — Reservation TTL self-guard (Phase R1, place-order feature)
-- Đảo ngược có chủ đích V5 (drop expires_at) — lúc đó chưa có state COMMITTED nên TTL sweep ngây thơ
-- sẽ release nhầm cả đơn đã confirm. Nay thêm COMMITTED trước (ReservationStatus) nên an toàn để
-- làm lại: PENDING quá hạn mới bị sweep, COMMITTED thì dừng đồng hồ vĩnh viễn.
-- ============================================================

ALTER TABLE reservation ADD COLUMN expires_at TIMESTAMPTZ;

CREATE INDEX idx_reservation_pending_expires_at ON reservation (expires_at) WHERE status = 'PENDING';
