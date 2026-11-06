# Người bán: dữ liệu

> **Trạng thái:** Sống · **Cập nhật:** `2026-10-31` · Các mục dưới đây **Đã làm** theo migration `V1`, `V2` (feature 03) và `V3` (feature 04); các cột ghi ở duyệt, từ chối do feature 04 ghi.
> Cơ sở dữ liệu quan hệ riêng của BC: Postgres 16, cơ sở dữ liệu `seller_db`, cổng dev `5441`, `wal_level=logical` (hộp thư event chuyển bằng CDC, ADR-0008). Giải pháp: [`tech.md` của feature 03](../../stage/c1a-listing/03-seller-profile-submit/tech.md) T3, T4; [ADR-0011](../../global/2.architecture/adr/0011-optimistic-lock-and-storage-uniqueness.md).

## 1. Tài khoản cơ sở dữ liệu · Đã làm (khai báo trong `infra/docker-compose.yml`; container dev chưa dựng)

Một tài khoản chủ cơ sở dữ liệu `seller_app` vừa chạy migration vừa chạy dịch vụ. Khác Nhật ký kiểm toán (ADR-0009): bảng này sửa được nên không cần tách quyền. Mật khẩu đọc từ biến môi trường, không viết cứng.

## 2. `seller_profiles` (AGG-SEL-01) · Đã làm

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | UUID v7 sinh ở ứng dụng (ADR-0001), khóa chính |
| `owner_account_id` | `UUID` | Mã tài khoản chủ hồ sơ; BC khác tham chiếu bằng mã này |
| `status` | `VARCHAR(20)` | `PENDING_REVIEW`, `APPROVED`, `REJECTED` |
| `seller_type` | `VARCHAR(20)` | `INDIVIDUAL`, `HOUSEHOLD`, `BUSINESS` |
| `legal_name` | `VARCHAR(150)` | Tên pháp lý (họ tên, tên chủ hộ, tên doanh nghiệp). **Dữ liệu cá nhân** |
| `representative_name` | `VARCHAR(100)` | Người đại diện; chỉ doanh nghiệp |
| `identity_number` | `VARCHAR(12)` | Số căn cước, 12 chữ số. **Dữ liệu cá nhân** (mã hóa cột chưa làm) |
| `tax_code` | `VARCHAR(13)` | Mã số thuế, 10 hoặc 13 chữ số liền, bỏ gạch. **Dữ liệu cá nhân** |
| `business_address` | `VARCHAR(300)` | Địa chỉ kinh doanh. **Dữ liệu cá nhân** |
| `identity_key` | `VARCHAR(13)` | Khóa định danh đã chuẩn hóa: số căn cước (cá nhân), mã số thuế (hộ, doanh nghiệp); không lộ ra ngoài BC |
| `shop_name` | `VARCHAR(60)` | Tên hiển thị, đã bỏ khoảng trắng thừa |
| `shop_name_key` | `VARCHAR(60)` | Khóa so sánh: bỏ dấu (`đ` thành `d`), chữ thường |
| `description` | `VARCHAR(500)` | Tùy chọn |
| `contact_name` | `VARCHAR(100)` | Người liên hệ |
| `contact_phone` | `VARCHAR(10)` | Số điện thoại di động Việt Nam, chuẩn hóa dạng `0xxxxxxxxx` |
| `contact_address` | `VARCHAR(300)` | Địa chỉ liên hệ; tùy chọn, rỗng thì coi là địa chỉ kinh doanh |
| `rejection_reason` | `VARCHAR(500)` | Lý do từ chối gần nhất; chỉ có khi `REJECTED` (ghi ở feature 04) |
| `submitted_at` | `TIMESTAMPTZ` | Lần nộp hoặc nộp lại gần nhất |
| `reviewed_at`, `reviewed_by` | `TIMESTAMPTZ`, `UUID` | Thời điểm và người quyết định (ghi ở feature 04) |
| `version` | `BIGINT` | Khóa lạc quan `@Version`, **bắt đầu từ 0 khi lưu hồ sơ mới**, tăng đúng một mỗi lần cập nhật có đổi dữ liệu; chạy ngầm với người bán (không thấy), quản trị nhận qua chi tiết để duyệt đúng bản (ADR-0011) |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | |

Ràng buộc (tên cố định vì lớp trình bày đổi lỗi theo tên, ADR-0011):
- `pk_seller_profiles (id)`
- `uq_seller_profiles_owner (owner_account_id)`: mỗi tài khoản tối đa một hồ sơ (INV-SEL-01) → `SELLER_PROFILE_EXISTS`
- `uq_seller_profiles_shop_name_key (shop_name_key)`: tên gian hàng duy nhất toàn sàn (INV-SEL-04) → `SHOP_NAME_TAKEN`
- `uq_seller_profiles_identity_key_effective (identity_key) WHERE status <> 'REJECTED'`: chỉ mục duy nhất một phần, mỗi khóa định danh một hồ sơ hiệu lực (INV-SEL-09) → `IDENTITY_ALREADY_REGISTERED`
- `ck_seller_profiles_status`, `ck_seller_profiles_type`: giá trị thuộc tập cho phép
- `ck_seller_profiles_representative (seller_type <> 'BUSINESS' OR representative_name IS NOT NULL)`
- `ck_seller_profiles_tax_code (seller_type = 'INDIVIDUAL' OR tax_code IS NOT NULL)`: hộ và doanh nghiệp bắt buộc có mã số thuế
- `ck_seller_profiles_rejection_reason (status <> 'REJECTED' OR rejection_reason IS NOT NULL)`
- `ck_seller_profiles_review (status = 'PENDING_REVIEW' OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL))`

Chỉ mục: ba ràng buộc duy nhất đã phục vụ tra theo chủ, tên, khóa định danh. **Đã làm** (migration `V3`, feature 04): `idx_seller_profiles_pending_queue ON seller_profiles (submitted_at, id) WHERE status = 'PENDING_REVIEW'`, chỉ mục một phần cho hàng chờ duyệt (cũ nhất trước, `id` phá hòa); hàng chờ nhỏ so với cả bảng nên chỉ mục một phần đủ.

Truy vấn danh sách hồ sơ chờ duyệt chỉ chọn `id`, `seller_type`, `shop_name`, `submitted_at` (không chọn cột dữ liệu cá nhân). Duyệt, từ chối đọc hồ sơ bằng `SELECT ... FOR UPDATE` rồi cập nhật `status`, `reviewed_at`, `reviewed_by`, `rejection_reason` (chỉ khi từ chối), `updated_at` và tăng `version`.

Dữ liệu cá nhân: `legal_name`, `identity_number`, `tax_code`, `business_address` không xuất hiện trong log, event, danh sách (INV-SEL-11). Mã hóa cột là điểm nối chưa làm (`analysis.md` mục 13).

## 3. `outbox_events` · Đã làm

Hộp thư event theo mẫu `outbox-starter` (`db/outbox-schema.sql`), hình bảng chung mọi BC (ADR-0008). Ghi cùng giao dịch với thay đổi hồ sơ. Connector Debezium `seller-outbox-connector` đọc bảng này; đăng ký **trước** khi ghi hàng đầu tiên. Không xóa hàng khi chuyển; dọn chạy qua Lập lịch (C2b).
