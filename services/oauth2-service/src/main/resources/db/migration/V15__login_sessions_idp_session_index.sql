-- Đăng xuất tìm phiên người dùng còn sống theo mã cookie đăng nhập của Spring (feature 03): chỉ mục để không quét bảng.
CREATE INDEX idx_login_sessions_idp_session ON login_sessions (idp_session_id);
