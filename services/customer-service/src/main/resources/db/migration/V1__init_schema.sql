-- ============================================================
-- customer-service — initial schema
-- ============================================================

-- ------------------------------------------------------------
-- customer_profiles
-- ------------------------------------------------------------
-- id = UserAccount.id (identity-service) — quan hệ identifying 1-1, dùng thẳng userId làm PK,
-- không sinh ULID riêng + không cần cột user_id/unique constraint/index riêng (PK đã đủ).
CREATE TABLE customer_profiles (
    id          VARCHAR(26)     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_customer_profiles PRIMARY KEY (id)
);
