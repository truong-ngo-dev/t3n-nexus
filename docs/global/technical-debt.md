# Technical Debt

> **Mục đích**: Danh sách sống các điểm biết là chưa ổn nhưng chưa đủ ưu tiên / chưa đủ rõ để
> xử lý ngay. Khác với ADR (quyết định đã chốt) và `architecture/*.md` (hiện trạng đã đúng) —
> đây là nơi ghi lại "biết có vấn đề, chưa quyết cách sửa" hoặc "sửa tạm, còn thiếu triệt để".
>
> Mỗi mục: **Vấn đề** → **Vì sao chưa fix** → **Hướng khi cần fix**. Xoá mục khỏi file này khi
> đã fix xong (không giữ lại làm changelog — git history đã làm việc đó).

---

## 1. `post_logout_redirect_uris` bị seed cứng trong Flyway migration

**Vấn đề**: `oauth2_registered_client.post_logout_redirect_uris` (client `web-gateway`) được seed
qua `V4__seed_oauth2_client.sql` với giá trị `http://localhost:4200` hard-code trong câu `INSERT`.
Toàn bộ các nơi cấu hình frontend URL khác (CORS, email link, login/logout redirect — xem mục 2)
đều đọc từ 1 env var `FRONTEND_URL` lúc runtime; riêng giá trị này nằm trong DB, ghi 1 lần lúc
migration chạy, **không có cách override qua env var**.

Lên production, nếu chỉ set `FRONTEND_URL=https://t3nexus.vn` mà quên xử lý riêng dòng này:
`web-gateway` build đúng mọi redirect khác, nhưng Spring Authorization Server sẽ reject
`end_session` request với `invalid post_logout_redirect_uri` (giá trị request gửi lên là domain
prod, giá trị trong DB vẫn là `localhost:4200`) — lỗi hiện ra ở tận bước OIDC logout, xa nguồn gốc
thật (biến môi trường), khó liên hệ ngay khi debug.

Đây cùng dạng lỗi đã từng gặp với `client_secret` bị seed `{noop}...}` sai ở `V4`, phải vá bằng
`V11__fix_internal_client_secret_encoding.sql` — seed data environment-specific trong migration
là điểm rủi ro lặp lại nhiều lần trong service này.

**Vì sao chưa fix**: Chưa quyết cơ chế thay thế — 2 hướng đều có đánh đổi, chưa đủ áp lực
(production chưa deploy) để chốt ngay.

**Hướng khi cần fix** (chọn 1, chưa quyết):
- **A. Startup upsert từ config**: 1 `ApplicationRunner`/`CommandLineRunner` đọc
  `app.frontend.url` lúc start, `RegisteredClientRepository.save(...)` cập nhật lại
  `redirect_uris`/`post_logout_redirect_uris` của `web-gateway` mỗi lần app khởi động — migration
  chỉ còn seed *cấu trúc* (client tồn tại, grant types, scopes), không seed giá trị
  environment-specific.
- **B. Migration riêng cho từng môi trường**: thêm `V{n}__update_prod_redirect_uri.sql` chỉ chạy
  ở profile `production` (Flyway `placeholders`/`locations` theo profile) — đơn giản hơn A nhưng
  lặp lại đúng pattern đã gây bug ở V4/V11 (dễ quên chạy, dễ lệch giữa các môi trường).

---

## 2. Chuẩn hoá `FRONTEND_URL` (đã fix — ghi lại để nhớ phạm vi)

**Vấn đề (đã fix 2026-08-08)**: FE URL trước đây rải ở 8 chỗ, 2 tên env var khác nhau cho cùng 1
khái niệm (`email-worker` dùng `FRONTEND_BASE_URL`, `oauth2-service` dùng `FRONTEND_URL`), và
`app.cors.allowed-origins` ở cả 3 service (`oauth2-service`, `web-gateway`, `api-gateway`) hard-code
literal `http://localhost:4200` không wrap `${VAR:default}` — về lý thuyết vẫn override được qua
Spring relaxed binding (`APP_CORS_ALLOWED_ORIGINS`) nhưng không tường minh, dễ bỏ sót khi review.

**Đã fix**: toàn bộ 7/8 chỗ (trừ mục 1 ở trên) giờ derive từ 1 env var `FRONTEND_URL` duy nhất:

| Service | Property |
|---|---|
| `email-worker` | `app.frontend.url` |
| `oauth2-service` | `app.frontend.url`, `app.cors.allowed-origins` |
| `web-gateway` | `app.cors.allowed-origins`, `app.login.success-redirect-uri` (+ `/customer`), `app.logout.post-redirect-uri` |
| `api-gateway` | `app.cors.allowed-origins` |

Set 1 biến `FRONTEND_URL` lúc deploy là đủ cho 7 chỗ này. Chỗ còn thiếu là mục 1 ở trên (DB-seeded).
