-- Category reshape — docs/service/catalog-service/analysis.md §5.3.2

-- 1. sortOrder — thứ tự anh em cùng cha trên storefront (thay xếp theo tên). Backfill theo thứ tự tạo;
--    PARTITION BY parent_id: NULL (root/L1) gộp thành 1 nhóm, đúng ý muốn.
ALTER TABLE category ADD COLUMN sort_order INT;
UPDATE category c SET sort_order = r.rn
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY created_at, id) - 1 AS rn
      FROM category) r
WHERE c.id = r.id;
ALTER TABLE category ALTER COLUMN sort_order SET NOT NULL;

-- 2. CategoryAttributeAssignment: is_filterable/is_searchable (2 cờ phẳng) -> constraints + discovery (jsonb).
--    Shape thay đổi theo inputType của template (AttributeConstraints/Discovery, domain). Dữ liệu cũ không đủ
--    thông tin suy ra lại discovery đúng kiểu (thiếu inputType tại thời điểm migrate) -> reset về rỗng, Admin
--    cấu hình lại qua PUT (đây là dữ liệu dev, seed lại rẻ hơn suy luận sai).
ALTER TABLE category_attribute_assignment ADD COLUMN constraints JSONB;
ALTER TABLE category_attribute_assignment ADD COLUMN discovery JSONB;
UPDATE category_attribute_assignment SET
    constraints = '{"maxLength":null,"min":null,"max":null,"integerOnly":null,"inputPrecision":null,"minDate":null,"maxDate":null,"maxSelections":null}'::jsonb,
    discovery   = '{"search":null,"filter":null,"sort":null}'::jsonb;
ALTER TABLE category_attribute_assignment ALTER COLUMN constraints SET NOT NULL;
ALTER TABLE category_attribute_assignment ALTER COLUMN discovery SET NOT NULL;
ALTER TABLE category_attribute_assignment DROP COLUMN is_filterable;
ALTER TABLE category_attribute_assignment DROP COLUMN is_searchable;

-- 3. Category.name duy nhất trong cùng cha (không phân biệt hoa/thường) — 2 "Phụ kiện" dưới 2 cha khác nhau
--    vẫn hợp lệ nên không thể là unique index toàn cục; kiểm ở tầng ứng dụng (CreateCategory/UpdateCategory),
--    không thêm constraint DB vì parent_id NULL (root) không so sánh bằng nhau được bằng UNIQUE index thường.
