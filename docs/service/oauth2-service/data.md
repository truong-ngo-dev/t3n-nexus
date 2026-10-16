# Xác thực: dữ liệu

> **Trạng thái:** Sống · **Cập nhật:** `2026-10-16` · Đã làm: `user_accounts` (feature 01, migration V12), `login_sessions` và `client_sessions` (feature 02, migration V13). Mục dữ liệu khởi tạo còn là Dự kiến.
> Cơ sở dữ liệu quan hệ, một lược đồ riêng của BC.

## 1. `user_accounts` (AGG-ATH-01) · Đã làm

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | UUID v7 sinh ở ứng dụng, khóa chính |
| `email` | `VARCHAR(254)` | Dạng chuẩn hóa (chữ thường, đã cắt khoảng trắng), duy nhất |
| `password_hash` | `VARCHAR(255)` | Chuỗi băm bcrypt tự mô tả (ADR-0002). Không bao giờ ghi dạng đọc được. Cho phép rỗng chỉ với tài khoản tạo không bằng mật khẩu (xem Tạm) |
| `role` | `VARCHAR(20)` | `USER`, `ADMIN` |
| `status` | `VARCHAR(10)` | `ACTIVE` khi đăng ký. Cho phép `LOCKED` (C1b), `PENDING` |
| `registration_method` | `VARCHAR(20)` | Tạm: `CREDENTIAL`, `OAUTH`. Đăng ký công khai luôn `CREDENTIAL` |
| `mfa_enabled` | `BOOLEAN` | Tạm: mặc định `false` |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | |

Ràng buộc:
- `pk_user_accounts (id)`
- `uq_user_accounts_email (email)`, đủ cho tra cứu; không tạo thêm chỉ mục trùng
- `chk_user_accounts_role (role IN ('USER','ADMIN'))`
- `chk_user_accounts_email_norm (email = lower(btrim(email)))`: chỉ lưu email đã chuẩn hóa
- `chk_user_accounts_status (status IN ('PENDING','ACTIVE','LOCKED'))`
- `chk_user_accounts_reg_method (registration_method IN ('CREDENTIAL','OAUTH'))`
- `chk_user_accounts_credential_pwd`: tài khoản `CREDENTIAL` bắt buộc có `password_hash`

**Tạm:** `registration_method`, `mfa_enabled` và trạng thái `PENDING` nằm ngoài thiết kế C0, giữ cho các luồng chưa thiết kế lại
(đăng nhập mạng xã hội, xác thực nhiều lớp) tiếp tục chạy; thiết kế lại ở feature đăng nhập và C3c (xác thực nhiều lớp; xem `service.md`).

Cột sẽ thêm sau (không có ở C0): `locked_at` (C1b), `email_verified_at` (khi có xác minh).

## 2. `login_sessions` và `client_sessions` (AGG-ATH-02) · Đã làm (lược đồ và kho; migration V13)

Phiên đăng nhập hai tầng (ADR-0003). Chỉ giữ phiên đang hoạt động; hết hạn suy ra từ `expires_at` (INV-ATH-08), mọi truy vấn
"đang sống" lọc theo hạn; dòng quá hạn dọn theo lô sau (Lập lịch, C2b).

**`login_sessions`** (phiên người dùng, gốc)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` khóa chính | Mã phiên người dùng (UUID v7, ADR-0001) |
| `user_account_id` | `UUID` không rỗng | Khóa ngoại tới `user_accounts` |
| `device_key` | `VARCHAR(64)` | Bản băm mã thiết bị (SHA-256); có thể rỗng; mã thô không lưu |
| `idp_session_id` | `VARCHAR(255)` không rỗng | Mã cookie đăng nhập của Spring; bí mật, không ra token, event, log |
| `issued_at` | `TIMESTAMPTZ` | Lúc nhập mật khẩu |
| `expires_at` | `TIMESTAMPTZ` | Hạn phiên, đặt lúc mở, không bao giờ đổi; ràng buộc `expires_at > issued_at` |

Chỉ mục: `(user_account_id)`, `(user_account_id, device_key)`, `(expires_at)`.

**`client_sessions`** (phiên ứng dụng, thực thể con)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` khóa chính | Mã phiên ứng dụng = `sid` trong hợp đồng danh tính |
| `login_session_id` | `UUID` không rỗng | Khóa ngoại tới `login_sessions`, `ON DELETE CASCADE` |
| `client_id` | `VARCHAR(100)` | Client đã đăng ký của ứng dụng |
| `authorization_id` | `VARCHAR(100)` | `oauth2_authorization.id` hiện hành, **đổi được** khi xác thực im lặng; không có khóa ngoại vì bảng đó do Spring quản lý |
| `issued_at` | `TIMESTAMPTZ` | |

Ràng buộc duy nhất: `(login_session_id, client_id)` (INV-ATH-10, chốt cuối khi hai yêu cầu đua nhau; kho dịch thành
`CLIENT_SESSION_CONFLICT`) và `authorization_id`.

Cách ghi: upsert gốc theo khóa chính; chỉ `idp_session_id` (phiên người dùng) và `authorization_id` (phiên ứng dụng) đổi được.
Cách đọc: JPQL kèm phiên ứng dụng bằng một câu truy vấn; tra theo `sid` qua `client_sessions.id`.

## 3. Dữ liệu khởi tạo · Dự kiến

Một dòng `user_accounts` vai `ADMIN`, trạng thái `ACTIVE`, email thuộc miền nội bộ, `password_hash` do script của môi trường
cung cấp. Chi tiết ở feature 04.
