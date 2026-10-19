# Technical Debt

> **Mục đích**: Danh sách sống các điểm biết là chưa ổn nhưng chưa đủ ưu tiên / chưa đủ rõ để
> xử lý ngay. Khác với ADR (quyết định đã chốt) và `architecture/*.md` (hiện trạng đã đúng) —
> đây là nơi ghi lại "biết có vấn đề, chưa quyết cách sửa" hoặc "sửa tạm, còn thiếu triệt để".
>
> Mỗi mục: **Vấn đề** → **Vì sao chưa fix** → **Hướng khi cần fix**. Xoá mục khỏi file này khi
> đã fix xong (không giữ lại làm changelog — git history đã làm việc đó).

---

## 1. `post_logout_redirect_uris` bị seed cứng trong Flyway migration

**Vấn đề**: `oauth2_registered_client.post_logout_redirect_uris` (client `storefront-web`) được seed
qua `V4__seed_oauth2_client.sql` với giá trị `http://localhost:4200` hard-code trong câu `INSERT`.
Toàn bộ các nơi cấu hình frontend URL khác (CORS, email link, login/logout redirect — xem mục 2)
đều đọc từ 1 env var `FRONTEND_URL` lúc runtime; riêng giá trị này nằm trong DB, ghi 1 lần lúc
migration chạy, **không có cách override qua env var**.

Lên production, nếu chỉ set `FRONTEND_URL=https://t3nexus.vn` mà quên xử lý riêng dòng này:
`storefront-web-bff` build đúng mọi redirect khác, nhưng Spring Authorization Server sẽ reject
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
  `redirect_uris`/`post_logout_redirect_uris` của `storefront-web-bff` mỗi lần app khởi động — migration
  chỉ còn seed *cấu trúc* (client tồn tại, grant types, scopes), không seed giá trị
  environment-specific.
- **B. Migration riêng cho từng môi trường**: thêm `V{n}__update_prod_redirect_uri.sql` chỉ chạy
  ở profile `production` (Flyway `placeholders`/`locations` theo profile) — đơn giản hơn A nhưng
  lặp lại đúng pattern đã gây bug ở V4/V11 (dễ quên chạy, dễ lệch giữa các môi trường).

---

## 2. Chuẩn hoá `FRONTEND_URL` (đã fix — ghi lại để nhớ phạm vi)

**Vấn đề (đã fix 2026-08-08)**: FE URL trước đây rải ở 8 chỗ, 2 tên env var khác nhau cho cùng 1
khái niệm (`email-worker` dùng `FRONTEND_BASE_URL`, `oauth2-service` dùng `FRONTEND_URL`), và
`app.cors.allowed-origins` ở cả 3 service (`oauth2-service`, `storefront-web-bff`, `api-gateway`) hard-code
literal `http://localhost:4200` không wrap `${VAR:default}` — về lý thuyết vẫn override được qua
Spring relaxed binding (`APP_CORS_ALLOWED_ORIGINS`) nhưng không tường minh, dễ bỏ sót khi review.

**Đã fix**: toàn bộ 7/8 chỗ (trừ mục 1 ở trên) giờ derive từ 1 env var `FRONTEND_URL` duy nhất:

| Service | Property |
|---|---|
| `email-worker` | `app.frontend.url` |
| `oauth2-service` | `app.frontend.url`, `app.cors.allowed-origins` |
| `storefront-web-bff` | `app.cors.allowed-origins`, `app.login.success-redirect-uri` (+ `/customer`), `app.logout.post-redirect-uri` |
| `api-gateway` | `app.cors.allowed-origins` |

Set 1 biến `FRONTEND_URL` lúc deploy là đủ cho 7 chỗ này. Chỗ còn thiếu là mục 1 ở trên (DB-seeded).

## 3. Phát hiện khi đóng feature 06 (`2026-10-18`)

Ba việc nảy sinh khi chạy thật qua cổng, nằm ngoài phạm vi feature 06. Cả ba việc đã sửa (`2026-10-18`).

| Việc | Hiện trạng đo được | Hệ quả | Hướng xử lý |
|---|---|---|---|
| **Hạn refresh token ở `oauth2-service` không theo `app.session.<vai>.refresh-ttl`** (feature 02) · **Đã sửa `2026-10-18`** | Với profile `dev` (refresh-ttl 3 phút), cột `refresh_token_expires_at` ghi phát hành cộng **24 giờ** (TTL cố định của client ở seed V4), làm mới sau 3 phút 21 giây vẫn thành công | **Nguyên nhân gốc:** Spring Authorization Server tra bộ sinh token bằng `getBeanProvider(OAuth2TokenGenerator.class).getIfUnique()`. Dự án có hai bean kiểu này (bộ sinh gộp `delegatingOAuth2TokenGenerator` và `SessionBoundRefreshTokenGenerator` là `@Component`), nên `getIfUnique()` trả `null` và máy chủ âm thầm dùng bộ sinh mặc định; `SessionBoundRefreshTokenGenerator` chưa bao giờ được gọi (chứng minh bằng log tạm: bean gộp được tạo nhưng `generate` không được gọi lần nào). Access token vẫn đúng hạn vì bộ tùy biến JWT là bean riêng mà bộ sinh mặc định cũng dùng, nên triệu chứng chỉ lộ ở refresh token | **Sửa:** `@Primary` cho `delegatingOAuth2TokenGenerator` (`KeyConfiguration`). Test `the_stored_refresh_token_expiry_follows_the_role_ttl_not_the_clients_fixed_ttl` (đỏ: 86400 so với 604800 giây, xanh sau sửa; kiểm cả lần cấp đầu và lần làm mới). Chạy thật ở dev: 180 giây trong DB, làm mới trong hạn thành công, bỏ không 200 giây thì `invalid_grant` và BFF trả 401 rồi SSO im lặng cấp lại. Bộ `oauth2-service` (trừ ba lớp dùng DB dev) 330 test xanh |
| **Hành vi lỗi của `oauth2-service` theo chuỗi bảo mật** · **Đã làm `2026-10-18`** | Đã đo trước khi sửa: `GET /nonexistent-page` nhận 302 `/login` cả khi `Accept: application/json`; `GET /userinfo` và `POST /oauth2/token` thiếu xác thực nhận 302 `/login` khi `Accept` là HTML hoặc thiếu; 403 của CSRF với trình duyệt là trang Whitelabel | Client OIDC và công cụ dòng lệnh nhận chuyển hướng thay vì 401 theo RFC 6749 và 6750 | **Sửa:** `AuthenticationEntryPoints` dùng chung cho chuỗi máy chủ ủy quyền và chuỗi mặc định (endpoint giao thức luôn 401 với `WWW-Authenticate`; chỉ `Accept` nêu rõ `text/html` mới được chuyển tới `/login`, `*/*` và thiếu `Accept` không phải điều hướng; còn lại 401); trang lỗi riêng `templates/error/4xx.html`, `5xx.html`. Bảng ở `service/oauth2-service/service.md` mục 7.1, kiểm bằng `ErrorBehaviorTest` (mỗi hàng là một cặp (yêu cầu, `Accept`)); phá thử bốn quy tắc và trang lỗi: mỗi lần đúng test đỏ. Bộ `oauth2-service` (trừ ba lớp dùng DB dev) 333 test xanh |
| **Tài liệu `spring-security-logout-bff.md` còn từ vựng của mô hình phiên cũ** · **Đã viết lại `2026-10-18`** | Nói `OAuthSession`, `OAuth2Authorization`, "[A1][A2]", `oss_id`, `EndIdpSession` | Người đọc nhầm với mô hình hai tầng phiên hiện tại (ADR-0003) | Viết lại theo `LoginSession`, `ClientSession`, `sid`, các lớp thật của hai dịch vụ, và ba điều Spring không làm như tên gọi (chuỗi đăng xuất WebFlux mặc định không xóa phiên, đổi mã phiên lúc xóa ngữ cảnh, xóa trước khi lưu gây 500). Các tài liệu cũ khác còn nói mô hình đã gỡ: `03-logout/flow.md` mục "Tài liệu cũ" |
