-- Chỉ mục một phần cho hàng đợi hồ sơ chờ duyệt theo thời điểm nộp và id
CREATE INDEX idx_seller_profiles_pending_queue ON seller_profiles (submitted_at, id) WHERE status = 'PENDING_REVIEW';
