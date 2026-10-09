# Xác thực: dữ liệu

> **Trạng thái:** Sống · **Cập nhật:** `2026-10-09` · Đã làm: `user_accounts` (feature 01, migration V12). Mục dữ liệu khởi tạo còn là Dự kiến.
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
(đăng nhập mạng xã hội, xác thực nhiều lớp) tiếp tục chạy; thiết kế lại ở feature đăng nhập và C1b (xem `service.md`).

Cột sẽ thêm sau (không có ở C0): `locked_at` (C1b), `email_verified_at` (khi có xác minh).

## 2. Dữ liệu khởi tạo · Dự kiến

Một dòng `user_accounts` vai `ADMIN`, trạng thái `ACTIVE`, email thuộc miền nội bộ, `password_hash` do script của môi trường
cung cấp. Chi tiết ở feature 04.
