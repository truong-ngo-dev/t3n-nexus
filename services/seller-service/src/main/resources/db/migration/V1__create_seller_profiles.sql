-- Bảng hồ sơ người bán (seller_profiles)
CREATE TABLE seller_profiles (
    id                  UUID         NOT NULL,
    owner_account_id    UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    seller_type         VARCHAR(20)  NOT NULL,
    legal_name          VARCHAR(150) NOT NULL,
    representative_name VARCHAR(100),
    identity_number     VARCHAR(12)  NOT NULL,
    tax_code            VARCHAR(13),
    business_address    VARCHAR(300) NOT NULL,
    identity_key        VARCHAR(13)  NOT NULL,
    shop_name           VARCHAR(60)  NOT NULL,
    shop_name_key       VARCHAR(60)  NOT NULL,
    description         VARCHAR(500),
    contact_name        VARCHAR(100) NOT NULL,
    contact_phone       VARCHAR(10)  NOT NULL,
    contact_address     VARCHAR(300),
    rejection_reason    VARCHAR(500),
    submitted_at        TIMESTAMPTZ  NOT NULL,
    reviewed_at         TIMESTAMPTZ,
    reviewed_by         UUID,
    version             BIGINT       NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_seller_profiles PRIMARY KEY (id),
    CONSTRAINT uq_seller_profiles_owner UNIQUE (owner_account_id),
    CONSTRAINT uq_seller_profiles_shop_name_key UNIQUE (shop_name_key),
    CONSTRAINT ck_seller_profiles_status CHECK (status IN ('PENDING_REVIEW','APPROVED','REJECTED')),
    CONSTRAINT ck_seller_profiles_type CHECK (seller_type IN ('INDIVIDUAL','HOUSEHOLD','BUSINESS')),
    CONSTRAINT ck_seller_profiles_representative CHECK (seller_type <> 'BUSINESS' OR representative_name IS NOT NULL),
    CONSTRAINT ck_seller_profiles_tax_code CHECK (seller_type = 'INDIVIDUAL' OR tax_code IS NOT NULL),
    CONSTRAINT ck_seller_profiles_rejection_reason CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    CONSTRAINT ck_seller_profiles_review CHECK (status = 'PENDING_REVIEW' OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL))
);

CREATE UNIQUE INDEX uq_seller_profiles_identity_key_effective ON seller_profiles (identity_key) WHERE status <> 'REJECTED';
