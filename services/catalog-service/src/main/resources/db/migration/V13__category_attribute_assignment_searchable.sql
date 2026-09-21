-- isSearchable: quyết định curation của Admin (giống isRequired/isFilterable), catalog-service chỉ lưu
-- và phát ra cho 1 search-service tương lai đồng bộ (build full-text index) — catalog không tự dùng flag
-- này để query. Đặt ở category_attribute_assignment (không phải attribute_template) vì đây là hành vi
-- theo NGỮ CẢNH SỬ DỤNG (cùng 1 attribute có thể searchable ở category này nhưng không ở category khác),
-- khớp isRequired/isFilterable. Xem service.md § Attribute Value Model.
ALTER TABLE category_attribute_assignment ADD COLUMN is_searchable BOOLEAN NOT NULL DEFAULT FALSE;
