-- AttributeTemplate cần soft-delete (deactivate) như Brand/Category/AttributeOption đã có — thay cho
-- hard delete (vốn sẽ vỡ FK RESTRICT từ category_attribute_assignment/product_attribute_value/
-- variant_combination_item nếu đã có dữ liệu tham chiếu).
ALTER TABLE attribute_template ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE attribute_template ALTER COLUMN status DROP DEFAULT;
