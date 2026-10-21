# Xác thực: dữ liệu

> **Trạng thái:** Sống · **Cập nhật:** `2026-10-17` · Đã làm: `user_accounts` (feature 01, migration V12), `login_sessions` và `client_sessions` (feature 02, migration V13), chỉ mục theo mã cookie đăng nhập (feature 03, V15) và bỏ bảng phiên cũ `oauth_sessions` (feature 03, V16). Dữ liệu khởi tạo Quản trị: script `infra/seed/admin.sql` chạy tay, ngoài Flyway (feature 04).
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

## 2. `login_sessions` và `client_sessions` (AGG-ATH-02) · Đã làm (lược đồ và kho; migration V13, V15)

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

Chỉ mục: `(user_account_id)`, `(user_account_id, device_key)`, `(expires_at)`, `(idp_session_id)` (V15: đăng xuất tìm phiên còn sống theo cookie đăng nhập).

**`client_sessions`** (phiên ứng dụng, thực thể con)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` khóa chính | Mã phiên ứng dụng = `sid` trong hợp đồng danh tính |
| `login_session_id` | `UUID` không rỗng | Khóa ngoại tới `login_sessions`, `ON DELETE CASCADE` |
| `client_id` | `VARCHAR(100)` | Mã **nội bộ** của client đã đăng ký (`RegisteredClient.getId()`, ví dụ `storefront-web-client-id`), không phải `client_id` công khai (`storefront-web-bff`); muốn biết ứng dụng nào thì tra lại client đã đăng ký |
| `authorization_id` | `VARCHAR(100)` | `oauth2_authorization.id` hiện hành, **đổi được** khi xác thực im lặng; không có khóa ngoại vì bảng đó do Spring quản lý |
| `issued_at` | `TIMESTAMPTZ` | |

Ràng buộc duy nhất: `(login_session_id, client_id)` (INV-ATH-10, chốt cuối khi hai yêu cầu đua nhau; kho dịch thành
`CLIENT_SESSION_CONFLICT`) và `authorization_id`.

Cách ghi: upsert gốc theo khóa chính; chỉ `idp_session_id` (phiên người dùng) và `authorization_id` (phiên ứng dụng) đổi được. Nơi sắp ghi lên một phiên đang sống (cấp phiên ứng dụng ở điểm móc lúc cấp token, đăng xuất) nạp phiên bằng `SELECT id ... FOR UPDATE` rồi đọc, để các thao tác ghi lên cùng một phiên nối tiếp nhau; nếu không, `save` (là `INSERT ... ON CONFLICT DO UPDATE`) sẽ tạo lại phiên vừa bị đăng xuất xóa.
Cách đọc: JPQL kèm phiên ứng dụng bằng một câu truy vấn; tra theo `sid` qua `client_sessions.id`.
Tra phiên còn sống theo cookie đăng nhập (đăng xuất): khóa hàng theo `idp_session_id` và `expires_at > now` (mới nhất trước), rồi nạp phiên kèm phiên ứng dụng.

Bảng `oauth_sessions` của mô hình phiên cũ đã bỏ ở V16 (không còn ai đọc hay ghi). Bảng `oauth2_authorization` do Spring quản lý; mỗi phiên ứng dụng trỏ tới một bản ghi qua `authorization_id`, và đăng xuất xóa các bản ghi đó cùng giao dịch với việc xóa phiên. Bảng có `fillfactor = 70` (V18) vì làm mới token cập nhật cùng một dòng nên chừa chỗ trong trang cho phiên bản dòng mới (cập nhật tại chỗ); 70 là số khởi đầu, chưa đo (ADR-0003 mục 8). Chỉ mục một phần cho mã ủy quyền ở V14.

## 3. Dữ liệu khởi tạo · Đã làm

Một dòng `user_accounts` vai `ADMIN`, trạng thái `ACTIVE`, `registration_method` `CREDENTIAL`, `mfa_enabled` `false`, email thuộc miền nội bộ,
`password_hash` là chuỗi bcrypt do người vận hành truyền vào. Tạo bằng script `infra/seed/admin.sql` chạy tay (feature 04), **không** nằm trong
migration Flyway; không có migration nào chèn `user_accounts` (có test quét). Chạy lại không thêm dòng, không đổi mật khẩu đã có.
