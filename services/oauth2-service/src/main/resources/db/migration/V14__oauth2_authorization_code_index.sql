-- ============================================================
-- V14 — Chỉ mục cho tra cứu mã ủy quyền (feature 02, ADR-0003 mục 8)
--
-- Đổi mã lấy token tra bản ghi ủy quyền theo giá trị mã (cột authorization_code_value). Mã chỉ dài 16 ký tự và sống 5 phút
-- nên chỉ mục nhỏ; khác với access token và refresh token (chuỗi dài hoặc JWT), các token đó KHÔNG có chỉ mục mà tra theo
-- khóa chính qua sid của phiên ứng dụng. Chỉ mục một phần: chỉ các hàng có mã.
-- ============================================================

CREATE INDEX idx_oauth2_authorization_code
    ON oauth2_authorization (authorization_code_value)
    WHERE authorization_code_value IS NOT NULL;
