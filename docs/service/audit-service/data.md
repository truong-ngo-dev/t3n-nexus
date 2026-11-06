# Nhật ký kiểm toán: dữ liệu

> **Trạng thái:** Sống · **Cập nhật:** `2026-10-25` · Đã làm: `audit_entries` (feature 02, migration V1, bước 4).
> Cơ sở dữ liệu quan hệ riêng của BC: Postgres 16, cơ sở dữ liệu `audit_db`. Không có hộp thư event (Nhật ký không phát event).

## 1. Tài khoản cơ sở dữ liệu · Đã làm

Bất biến ở lưu trữ (ADR-0009) dựa vào hai tài khoản tách biệt, tạo một lần bởi `infra/audit/init-audit-roles.sql` khi cơ sở dữ liệu mới được tạo:

| Tài khoản | Vai trò | Quyền |
|---|---|---|
| `audit_owner` | Chủ cơ sở dữ liệu và bảng; chỉ chạy migration (Flyway) | Toàn quyền trên đối tượng của nó; không phải superuser |
| `audit_app` | Tài khoản chạy dịch vụ | `SELECT`, `INSERT` trên `audit_entries`; không `UPDATE`, `DELETE`, `TRUNCATE`; không sở hữu đối tượng nào, không tạo được đối tượng; không đọc được bảng lịch sử Flyway |

Dịch vụ cấu hình hai kết nối: `spring.datasource.*` là `audit_app`, `spring.flyway.user` và `spring.flyway.password` là `audit_owner`. Migration cấp quyền cho tài khoản dịch vụ qua placeholder `appRole`.

## 2. `audit_entries` (AGG-AUD-01) · Đã làm

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `UUID` | UUID v7 sinh ở ứng dụng (ADR-0001), khóa chính; thứ tự phụ khi phân trang |
| `source_event_id` | `VARCHAR(100)` | Mã event của BC phát (`eventId` của khung); duy nhất. Kiểu chuỗi vì mã event ở hộp thư là chuỗi, không bảo đảm là UUID với mọi bên phát |
| `occurred_at` | `TIMESTAMPTZ` | Giờ của BC phát. Ứng dụng cắt về micro giây trước khi ghi để con trỏ phân trang so sánh chính xác |
| `recorded_at` | `TIMESTAMPTZ` | Giờ Nhật ký lưu, do dịch vụ đặt |
| `actor_account_id` | `UUID` | Rỗng chỉ khi `actor_role = 'SYSTEM'` |
| `actor_role` | `VARCHAR(32)` | Vai tại thời điểm làm (bản chụp), không tra lại |
| `action` | `VARCHAR(100)` | Dạng `ĐỐI_TƯỢNG_HÀNH_ĐỘNG`; chỉ kiểm định dạng ở ứng dụng, không ràng buộc danh sách ở cơ sở dữ liệu (INV-AUD-07) |
| `target_type` | `VARCHAR(64)` | Loại đối tượng |
| `target_id` | `VARCHAR(100)` | Chỉ mã, không dữ liệu của đối tượng |
| `details` | `JSONB` | Dữ kiện bổ sung đã lọc (phẳng, tối đa 10 khóa); mặc định `{}`. Không lập chỉ mục, không lọc theo nội dung ở C1a |
| `source_service` | `VARCHAR(64)` | BC phát |
| `trace_id` | `VARCHAR(64)` | Tùy chọn; nối với log của yêu cầu gốc |

Ràng buộc:
- `pk_audit_entries (id)`
- `uq_audit_entries_source_event_id (source_event_id)`: nhận lặp không ghi đôi (INV-AUD-02); khử trùng bằng `INSERT ... ON CONFLICT DO NOTHING`
- `ck_audit_entries_actor (actor_account_id IS NOT NULL OR actor_role = 'SYSTEM')` (INV-AUD-03, 05)

Chỉ mục (cho tra cứu `ORDER BY occurred_at DESC, id DESC` và lọc):
- `ix_audit_entries_occurred (occurred_at DESC, id DESC)`: danh sách không lọc và lọc theo khoảng thời gian
- `ix_audit_entries_actor (actor_account_id, occurred_at DESC, id DESC) WHERE actor_account_id IS NOT NULL`: lọc theo người làm; chỉ mục một phần vì hành động của hệ thống không có mã
- `ix_audit_entries_target (target_type, target_id, occurred_at DESC, id DESC)`: lọc theo đối tượng
- `ix_audit_entries_action (action, occurred_at DESC, id DESC)`: lọc theo hành động

Bất biến (INV-AUD-01, ADR-0009), hai lớp độc lập:
1. **Quyền:** tài khoản dịch vụ chỉ `SELECT`, `INSERT`; `REVOKE ALL ... FROM PUBLIC`.
2. **Trigger:** `trg_audit_entries_reject_update_delete` (`BEFORE UPDATE OR DELETE`, theo hàng) và `trg_audit_entries_reject_truncate` (`BEFORE TRUNCATE`, theo câu lệnh) cùng gọi hàm `audit_entries_reject_change()`, ném lỗi `restrict_violation` (SQLSTATE `23001`). Chặn cả tài khoản chủ bảng; gỡ phải là lệnh `ALTER TABLE` tường minh của chủ bảng.

Tăng trưởng: bảng chỉ tăng. Phân vùng theo tháng và lưu lạnh là điểm nối chưa làm (`analysis.md` mục 12).
