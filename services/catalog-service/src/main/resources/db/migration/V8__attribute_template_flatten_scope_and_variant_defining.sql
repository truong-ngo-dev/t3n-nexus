-- AttributeTemplate trở thành master data thuần, bỏ khái niệm scope (GLOBAL/CATEGORY) — mọi template
-- đều phải được assign tường minh vào category (chỉ leaf/L3) mới áp dụng được cho Product.
ALTER TABLE attribute_template DROP COLUMN scope;

-- variantDefining không còn là 1 khái niệm được khai báo trước ở đâu cả — nó chỉ là hệ quả của việc
-- attribute có được dùng trong Variant.combination hay không, xem service.md § Attribute Value Model.
ALTER TABLE category_attribute_assignment DROP COLUMN is_variant_defining;
