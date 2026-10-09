-- AttributeTemplate reshape — docs/service/catalog-service/analysis.md §5.3.1

-- 1. inputType: SELECT tách thành SINGLE_SELECT / MULTI_SELECT, thêm DATE.
--    Chuyển dữ liệu cũ: SELECT có sản phẩm khai > 1 giá trị cho thuộc tính KHÔNG phải trục biến thể → MULTI_SELECT;
--    còn lại → SINGLE_SELECT (trục biến thể khai nhiều giá trị là tập lựa chọn cho SKU, vẫn là SINGLE_SELECT).
ALTER TABLE attribute_template DROP CONSTRAINT chk_attribute_template_input_type;

UPDATE attribute_template t SET input_type = 'MULTI_SELECT'
WHERE t.input_type = 'SELECT'
  AND EXISTS (
      SELECT 1
      FROM product_attribute_value pav
      WHERE pav.template_id = t.id
        AND NOT EXISTS (
            SELECT 1 FROM product_variant_defining_attribute vd
            WHERE vd.product_id = pav.product_id AND vd.template_id = pav.template_id)
      GROUP BY pav.product_id
      HAVING COUNT(*) > 1);

UPDATE attribute_template SET input_type = 'SINGLE_SELECT' WHERE input_type = 'SELECT';

ALTER TABLE attribute_template ADD CONSTRAINT chk_attribute_template_input_type
    CHECK (input_type IN ('SINGLE_SELECT', 'MULTI_SELECT', 'TEXT', 'NUMBER', 'BOOLEAN', 'DATE'));

-- 2. unit (chỉ NUMBER, bất biến — ý nghĩa của con số), hint (gợi ý cho seller), version (optimistic lock).
ALTER TABLE attribute_template ADD COLUMN unit    VARCHAR(20);
ALTER TABLE attribute_template ADD COLUMN hint    VARCHAR(200);
ALTER TABLE attribute_template ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE attribute_template ADD CONSTRAINT chk_attribute_template_unit
    CHECK (unit IS NULL OR input_type = 'NUMBER');

-- 3. Option: thứ tự chuẩn (khởi tạo theo thứ tự tạo).
ALTER TABLE attribute_option ADD COLUMN sort_order INT;
UPDATE attribute_option o SET sort_order = r.rn
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY template_id ORDER BY created_at, id) - 1 AS rn
      FROM attribute_option) r
WHERE o.id = r.id;
ALTER TABLE attribute_option ALTER COLUMN sort_order SET NOT NULL;

-- 4. Bỏ usage_count — guard "không tắt option đang dùng" đã bỏ (tắt chỉ chặn lựa chọn mới).
ALTER TABLE attribute_option DROP COLUMN usage_count;

-- 5. value duy nhất trong template (không phân biệt hoa/thường). Dữ liệu dev cũ có thể trùng: đổi value của bản trùng
--    (giữ bản tạo sớm nhất) — an toàn vì mọi tham chiếu dùng id, không dùng value.
UPDATE attribute_option o SET value = o.value || '_' || RIGHT(o.id, 6)
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY template_id, LOWER(value) ORDER BY created_at, id) AS rn
      FROM attribute_option) d
WHERE o.id = d.id AND d.rn > 1;
CREATE UNIQUE INDEX uq_attribute_option_template_value ON attribute_option (template_id, LOWER(value));
