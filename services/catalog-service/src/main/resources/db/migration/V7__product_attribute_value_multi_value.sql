-- Cho phép Product khai nhiều value cho 1 attribute (Options + Variants model):
-- variant-defining attribute cần 1 tập option con của master pool, không phải 1 giá trị duy nhất.
-- PK mở rộng (product_id, template_id) -> (product_id, template_id, value).
ALTER TABLE product_attribute_value DROP CONSTRAINT pk_product_attribute_value;
ALTER TABLE product_attribute_value ADD CONSTRAINT pk_product_attribute_value
    PRIMARY KEY (product_id, template_id, value);
