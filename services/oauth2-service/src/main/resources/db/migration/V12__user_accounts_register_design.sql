-- ============================================================
-- V12 — Tài khoản đăng nhập theo thiết kế feature 01 (Đăng ký)
--
-- Bỏ bảng user_credentials và tạo lại user_accounts với cột khớp aggregate UserAccount:
--   - id là uuid (UUID v7, ADR-0001), không còn VARCHAR(26) ULID;
--   - email lưu dạng đã chuẩn hóa, ràng buộc duy nhất là chốt cuối của INV-ATH-01;
--   - vai chỉ còn USER, ADMIN (INV-ATH-06).
--
-- Dữ liệu tài khoản cũ KHÔNG được giữ. Các bản ghi tham chiếu mã tài khoản cũ cũng bị xóa vì không còn khớp:
-- phiên đăng nhập, ủy quyền đã cấp, phiên HTTP (lớp UserCredentialDetails đã đổi tên nên không đọc lại được).
-- Không đụng oauth2_registered_client và rsa_key_pairs. Mọi người dùng phải đăng ký và đăng nhập lại.
-- ============================================================

DELETE FROM oauth_sessions;
DELETE FROM oauth2_authorization;
DELETE FROM oauth2_authorization_consent;
DELETE FROM SPRING_SESSION;   -- SPRING_SESSION_ATTRIBUTES xóa theo (ON DELETE CASCADE)

DROP TABLE user_credentials;

CREATE TABLE user_accounts (
    id                  UUID            NOT NULL,
    email               VARCHAR(254)    NOT NULL,
    password_hash       VARCHAR(255),
    role                VARCHAR(20)     NOT NULL,
    registration_method VARCHAR(20)     NOT NULL,
    status              VARCHAR(10)     NOT NULL,
    mfa_enabled         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_user_accounts                 PRIMARY KEY (id),
    CONSTRAINT uq_user_accounts_email           UNIQUE (email),
    CONSTRAINT chk_user_accounts_email_norm     CHECK (email = lower(btrim(email))),
    CONSTRAINT chk_user_accounts_role           CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_user_accounts_reg_method     CHECK (registration_method IN ('CREDENTIAL', 'OAUTH')),
    CONSTRAINT chk_user_accounts_status         CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED')),
    CONSTRAINT chk_user_accounts_credential_pwd CHECK (registration_method <> 'CREDENTIAL' OR password_hash IS NOT NULL)
);

-- Phiên đăng nhập tham chiếu mã tài khoản bằng chuỗi UUID 36 ký tự (kiểu chốt lại ở feature 02)
ALTER TABLE oauth_sessions
    ALTER COLUMN user_id TYPE VARCHAR(36);
