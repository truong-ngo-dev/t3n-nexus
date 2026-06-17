-- ============================================================
-- V3: Drop cross-aggregate FK constraints, add brand index
-- ============================================================
-- Variant is an Aggregate Root — cascade delete from product is incorrect.
-- Product → Category and Product → Brand are cross-AR references
-- that must not be enforced at DB level per hexagonal architecture convention.

ALTER TABLE variant DROP CONSTRAINT fk_variant_product;
ALTER TABLE product DROP CONSTRAINT fk_product_category;
ALTER TABLE product DROP CONSTRAINT fk_product_brand;

-- Allow existsByBrandId check in DeactivateBrand to run in O(log n)
CREATE INDEX idx_product_brand ON product (brand_id);
