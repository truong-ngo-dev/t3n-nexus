-- search_version: version tăng đơn điệu của ProductSearchSnapshot (search-service guard theo version này).
-- Tăng mỗi lần Product HOẶC bất kỳ Variant nào của nó đổi. Row lock của UPDATE serialize các lần tăng
-- → đơn điệu thật, không phụ thuộc đồng hồ. KHÔNG map vào ProductJpaEntity (ProductMapper.toJpaEntity
-- dựng entity mới mỗi lần save — nếu map, merge sẽ ghi đè về 0).
ALTER TABLE product ADD COLUMN search_version BIGINT NOT NULL DEFAULT 0;

-- published_at: thời điểm publish LẦN ĐẦU — tín hiệu độ mới + sort "Mới nhất" bên search. Giữ nguyên khi
-- unpublish rồi publish lại. NULL = chưa từng publish (DRAFT) → không phát snapshot.
ALTER TABLE product ADD COLUMN published_at TIMESTAMPTZ;

-- Backfill: product đã từng publish (PUBLISHED/UNPUBLISHED) không còn lưu thời điểm publish gốc —
-- lấy tạm updated_at (xấp xỉ, chấp nhận được cho data dev).
UPDATE product SET published_at = updated_at WHERE status IN ('PUBLISHED', 'UNPUBLISHED');
