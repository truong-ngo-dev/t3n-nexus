-- Fix schema drift: nội dung V11 bị sửa lại SAU KHI đã apply trên môi trường dev (từng thêm cột
-- is_variant_defining trực tiếp vào product_attribute_value trước khi tách sang bảng riêng
-- product_variant_defining_attribute) — không sửa lại V11 vì đã chạy thật, hội tụ schema bằng migration
-- mới này thay vì amend migration cũ. Idempotent (IF EXISTS/IF NOT EXISTS) — an toàn dù DB đang ở trạng
-- thái nào (đã có cột cũ hay chưa, đã có bảng mới hay chưa).
ALTER TABLE product_attribute_value DROP COLUMN IF EXISTS is_variant_defining;

CREATE TABLE IF NOT EXISTS product_variant_defining_attribute (
    product_id  VARCHAR(26) NOT NULL,
    template_id VARCHAR(26) NOT NULL,

    CONSTRAINT pk_product_variant_defining_attribute PRIMARY KEY (product_id, template_id),
    CONSTRAINT fk_pvda_product  FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT fk_pvda_template FOREIGN KEY (template_id)
        REFERENCES attribute_template (id) ON DELETE RESTRICT
);
