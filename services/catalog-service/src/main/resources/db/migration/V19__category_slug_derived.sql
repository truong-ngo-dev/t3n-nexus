-- Slug danh mục tự sinh từ tên, không duy nhất, đổi theo tên; mọi tra cứu theo ID (service.md T-10).
-- Tên danh mục chỉ duy nhất trong cùng cha → 2 nhánh "Phụ kiện" sinh cùng slug là hợp lệ.
ALTER TABLE category DROP CONSTRAINT uq_category_slug;
