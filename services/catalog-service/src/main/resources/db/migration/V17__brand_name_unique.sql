-- Brand.name duy nhất — docs/service/catalog-service/analysis.md §5.3.3 (B2)
-- Trước đây chỉ slug là unique; name do admin nhập tự do -> 2 brand khác slug có thể trùng tên hiển thị.

-- Dữ liệu dev cũ có thể trùng tên (không phân biệt hoa/thường): đổi tên bản trùng (giữ bản tạo sớm nhất),
-- an toàn vì mọi tham chiếu (Product.brandId) dùng id, không dùng name.
UPDATE brand b SET name = b.name || ' (' || RIGHT(b.id, 6) || ')'
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY LOWER(name) ORDER BY created_at, id) AS rn
      FROM brand) d
WHERE b.id = d.id AND d.rn > 1;

CREATE UNIQUE INDEX uq_brand_name ON brand (LOWER(name));
