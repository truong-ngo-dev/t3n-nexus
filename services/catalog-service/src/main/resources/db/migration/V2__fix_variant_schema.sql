-- ============================================================
-- catalog-service V2 — align variant table with JPA entity
-- ============================================================

-- Add sku_code and stock columns to variant (missing from V1)
ALTER TABLE variant
    ADD COLUMN sku_code VARCHAR(100),
    ADD COLUMN stock    INT NOT NULL DEFAULT 0;

-- Rename variant_image → sku_image to match SkuImageJpaEntity
ALTER TABLE variant_image RENAME TO sku_image;
