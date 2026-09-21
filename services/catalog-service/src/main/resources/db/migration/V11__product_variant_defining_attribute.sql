-- isVariantDefining quay lại làm 1 khái niệm khai báo tường minh (V8 từng bỏ ở category level) — nhưng
-- lần này do Product/Seller tự khai per-listing, không phải Admin áp đặt toàn category (khớp Shopee/
-- Lazada: Seller tự chọn "Phân loại 1/2" khi tạo listing, không phải category quyết định sẵn).
--
-- Bảng riêng theo đúng grain (product_id, template_id) — KHÔNG nhét vào product_attribute_value (grain
-- per value), vì đó sẽ lặp lại cùng 1 giá trị boolean trên mọi row cùng template, vi phạm chuẩn hoá và
-- tốn thêm 1 hàng lặp mỗi option khi attribute có cardinality cao. Tồn tại row = true, không cần cột
-- boolean, giống cách variant_combination_item không lưu "false" cho pair không dùng.
CREATE TABLE product_variant_defining_attribute (
    product_id  VARCHAR(26) NOT NULL,
    template_id VARCHAR(26) NOT NULL,

    CONSTRAINT pk_product_variant_defining_attribute PRIMARY KEY (product_id, template_id),
    CONSTRAINT fk_pvda_product  FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT fk_pvda_template FOREIGN KEY (template_id)
        REFERENCES attribute_template (id) ON DELETE RESTRICT
);
