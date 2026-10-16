-- ============================================================
-- V13 — Phiên đăng nhập hai tầng theo thiết kế feature 02 (ADR-0003)
--
--   login_sessions  = phiên người dùng (gốc aggregate LoginSession): một lần nhập mật khẩu trên một thiết bị
--   client_sessions = phiên ứng dụng (thực thể con): mỗi (phiên người dùng, client) tối đa một (INV-ATH-10)
--
-- Chỉ giữ phiên đang hoạt động; hết hạn suy ra từ expires_at (INV-ATH-08), dòng quá hạn dọn theo lô sau.
-- Bảng oauth_sessions (mô hình phiên cũ) vẫn còn tới khi nối với Spring Authorization Server (bước 9a).
-- ============================================================

CREATE TABLE login_sessions (
    id               UUID            NOT NULL,
    user_account_id  UUID            NOT NULL,
    device_key       VARCHAR(64),
    idp_session_id   VARCHAR(255)    NOT NULL,
    issued_at        TIMESTAMPTZ     NOT NULL,
    expires_at       TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_login_sessions            PRIMARY KEY (id),
    CONSTRAINT fk_login_sessions_account    FOREIGN KEY (user_account_id) REFERENCES user_accounts (id),
    CONSTRAINT chk_login_sessions_expiry    CHECK (expires_at > issued_at)
);

COMMENT ON COLUMN login_sessions.device_key     IS 'Bản băm mã thiết bị (SHA-256 hex); mã thô không lưu';
COMMENT ON COLUMN login_sessions.idp_session_id IS 'Mã cookie đăng nhập của Spring (SESSION_ID): giá trị bí mật, không ra token, event, log';

CREATE INDEX idx_login_sessions_account         ON login_sessions (user_account_id);
CREATE INDEX idx_login_sessions_account_device  ON login_sessions (user_account_id, device_key);
CREATE INDEX idx_login_sessions_expires_at      ON login_sessions (expires_at);

CREATE TABLE client_sessions (
    id                 UUID            NOT NULL,
    login_session_id   UUID            NOT NULL,
    client_id          VARCHAR(100)    NOT NULL,
    authorization_id   VARCHAR(100)    NOT NULL,
    issued_at          TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_client_sessions               PRIMARY KEY (id),
    CONSTRAINT fk_client_sessions_login_session FOREIGN KEY (login_session_id) REFERENCES login_sessions (id) ON DELETE CASCADE,
    CONSTRAINT uq_client_sessions_login_client  UNIQUE (login_session_id, client_id),
    CONSTRAINT uq_client_sessions_authorization UNIQUE (authorization_id)
);

-- Không có khóa ngoại tới oauth2_authorization: bảng đó do Spring Authorization Server quản lý (xóa, dọn riêng).
COMMENT ON COLUMN client_sessions.authorization_id IS 'oauth2_authorization.id hiện hành; đổi được khi xác thực im lặng; không ra token, event';
