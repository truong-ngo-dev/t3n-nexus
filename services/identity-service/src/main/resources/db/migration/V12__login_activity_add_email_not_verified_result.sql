-- Thêm EMAIL_NOT_VERIFIED vào LoginResult — trước đây login khi UserCredential.status=PENDING
-- (chưa verify email) bị gộp chung message "ACCOUNT_LOCKED" với case bị khóa thật, gây hiểu nhầm
-- cho user tự xử lý được (verify email) thành phải liên hệ hỗ trợ (xem docs/feature/02-login).
ALTER TABLE login_activities DROP CONSTRAINT chk_login_activities_result;

ALTER TABLE login_activities ADD CONSTRAINT chk_login_activities_result
    CHECK (result IN ('SUCCESS', 'WRONG_PASSWORD', 'ACCOUNT_LOCKED', 'MFA_FAILED', 'EMAIL_NOT_VERIFIED'));
