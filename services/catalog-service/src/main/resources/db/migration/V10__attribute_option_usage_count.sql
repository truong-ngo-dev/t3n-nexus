-- Thay cho query existsByOptionId (full scan variant_combination_item — bảng ghi liên tục, không có
-- index trên option_id, Postgres không tự index cột FK). usageCount bump cùng transaction với AddVariant,
-- guard OPTION_IN_USE giờ là in-aggregate check (AttributeOption.deactivate()), không cần hỏi VariantRepository.
ALTER TABLE attribute_option ADD COLUMN usage_count INT NOT NULL DEFAULT 0;
