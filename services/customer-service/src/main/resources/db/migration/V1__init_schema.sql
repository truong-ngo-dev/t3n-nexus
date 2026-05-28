-- ============================================================
-- customer-service — initial schema
-- ============================================================

-- ------------------------------------------------------------
-- customer_profiles
-- ------------------------------------------------------------
CREATE TABLE customer_profiles (
    id          VARCHAR(26)     NOT NULL,
    user_id     VARCHAR(26)     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_customer_profiles         PRIMARY KEY (id),
    CONSTRAINT uq_customer_profiles_user_id UNIQUE (user_id)
);

CREATE INDEX idx_customer_profiles_user_id ON customer_profiles (user_id);
