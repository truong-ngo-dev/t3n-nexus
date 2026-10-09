-- ============================================================
-- order-service — customerEmail snapshot (2026-09-22)
-- Capture email từ JWT lúc đặt hàng (claim `email`, xem oauth2-service.JwtTokenCustomizer) — snapshot
-- vào Order thay vì chỉ giữ customerId, để publish thẳng qua OrderConfirmed/OrderCancelled cho
-- notification-service dùng kênh Email mà không cần tra cứu lại (đúng nguyên tắc reference-vs-snapshot
-- đã áp dụng cho OrderLineItem — dữ liệu giao dịch tự chụp lại tại thời điểm phát sinh).
-- ============================================================

ALTER TABLE orders ADD COLUMN customer_email VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE orders ALTER COLUMN customer_email DROP DEFAULT;
