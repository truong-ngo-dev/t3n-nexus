-- SMALLINT (int2) conflicts with Hibernate's INTEGER (int4) expectation for Java int.
-- Widening cast SMALLINT → INTEGER is safe, no data loss.
ALTER TABLE category ALTER COLUMN level TYPE INTEGER;
