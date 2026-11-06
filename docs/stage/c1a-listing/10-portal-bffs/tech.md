# Feature 10: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Nền: [ADR-0003](../../../global/2.architecture/adr/0003-login-session-and-identity-contract.md) (phiên, `sid`), [ADR-0005](../../../global/2.architecture/adr/0005-browser-request-protection.md) (CSRF, PKCE), [ADR-0006](../../../global/2.architecture/adr/0006-application-origins-and-host-routing.md) (nguồn gốc theo `Host`, đặt tên `<ứng dụng>-<kênh>-bff`), [ADR-0007](../../../global/2.architecture/adr/0007-frontends-workspace-structure.md).

## Câu hỏi đã chốt

| # | Câu hỏi | Chốt |
|---|---|---|
| Q1 | Mỗi cổng một BFF riêng hay dùng chung? | **Riêng** (`seller-web-bff`, `ops-web-bff`), người dùng chốt `2026-11-06`; ADR-0006 đã quy định |
| Q2 | Mã dùng chung giữa các BFF làm thế nào? | **Thư viện `libs/web-bff-starter`** (người dùng đồng ý `2026-11-06`); `storefront-web-bff` chuyển sang dùng |
| Q3 | Tên ứng dụng ở khóa Redis, phạm vi nội bộ, `registrationId` lấy từ đâu? | Một thuộc tính `bff.app-name` (`storefront`, `seller`, `ops`); suy ra `registrationId = <app-name>-web`, tiền tố khóa `<app-name>:`, phạm vi nội bộ `<app-name>.internal` |
| Q4 | `oauth2-service` biết BFF nào cần báo khi kết thúc phiên? | Danh sách client BFF trong cấu hình `app.clients.<mã>.*` (redirect URI, URI đăng xuất, bí mật, PKCE, `bff-base-url`); seeder và bộ báo đọc cùng danh sách |
| Q5 | Chặn vai sai ngay ở BFF (`USER` vào Vận hành)? | **Không ở C1a**: service quyết định (mặc định từ chối); ghi `deferred` |
| Q6 | Bảng tuyến của BFF mỗi ứng dụng | Khai báo bằng cấu hình `bff.routes.<dịch vụ>.uri`; starter dựng `/api/<dịch vụ>/**` → `/api/**` cho từng dịch vụ có khai báo |

## T1. Thư viện `libs/web-bff-starter` · Dự kiến

- Module WebFlux (gói `vn.t3nexus.lib.webbff`), bật bằng chú thích `@EnableWebBff` trên lớp khởi động của từng BFF (không dùng tự cấu hình của Boot: cấu hình của thư viện ghi đè bean của WebFlux và Session như `webSessionIdResolver`, nên phải được xử lý cùng đợt với cấu hình người dùng; thử tự cấu hình thì hỏng ngữ cảnh vì trùng bean). Chuyển nguyên từ `storefront-web-bff`: `SecurityConfiguration` (chuỗi lọc web và chuỗi nội bộ), CSRF, `SessionMappingStore` và dọn dẹp, `SessionAuthorizedClientManager`, làm mới token và đồng hồ lệch, `AccessTokenClaims`, `LogoutSuccessHandler`, `RedirectStrategy`, khung lỗi và bộ xử lý lỗi, lọc IP khách, `UserRateLimitFilter`, CORS, `RouteConfiguration` dựng động, `AuthController` (`/bff/auth/login`, `/bff/auth/session`), `SessionRevokeController` (`/bff/internal/sessions/revoke`).
- Mọi chỗ viết cứng `storefront` thay bằng thuộc tính `BffProperties` (Q3). Đổi tên lớp bỏ chữ `Storefront`.
- `StorefrontWebBffApplication` thành lớp khởi động mỏng; còn lại trong module `storefront-web-bff` chỉ `application.properties` và test đặc thù.
- Giữ nguyên hành vi: tên khóa Redis mặc định `storefront:` cho Cửa hàng, nên **không migrate dữ liệu** (phiên đang chạy vẫn hợp lệ).
- `ws-ticket` (vé WebSocket) nằm cùng `AuthController` trong thư viện: điểm cuối cần đăng nhập, vô hại với BFF không dùng; không tách riêng.
- Tên cookie phiên cũng suy từ tên ứng dụng: `STOREFRONT_SESSION`, `SELLER_SESSION`, `OPS_SESSION` (ADR-0006: cookie theo máy, tên khác nhau làm lớp phòng thủ thứ hai).
- Bộ test tích hợp của Cửa hàng (53 ca) ở lại module Cửa hàng làm bộ kiểm cho thư viện; mỗi BFF mới có bộ 6 ca riêng (đăng nhập, cookie, khóa Redis, phạm vi nội bộ, tuyến, 401).
- Tên tuyến bỏ hậu tố `-service`: `seller-service` thành `/api/seller/**`, nên từ trình duyệt Người bán gọi hồ sơ là `/api/seller/seller/profile` còn danh mục là `/api/catalog/seller/products`. Xấu nhưng nhất quán; có thể thêm thuộc tính đặt tên đoạn khi giao diện thấy vướng.

Đã loại: sao chép module hai lần (sửa lỗi bảo mật phải sửa ba nơi); một BFF chung phục vụ nhiều host (ADR-0006: chung phiên và CSRF giữa các ứng dụng, mất lý do tách nguồn gốc).

## T2. `oauth2-service` · Dự kiến

- `RegisteredClientSeeder`: thay các trường `storefrontWeb*` bằng danh sách `BffClientProperties` đọc từ `app.clients.<mã>.*`; mỗi mục đăng ký một client (bí mật bắt buộc ngoài dev, PKCE mặc định bật). Mã `storefront-web` giữ nguyên giá trị mặc định, thêm `seller-web`, `ops-web`.
- `StorefrontWebBffRevocationClient` → `BffRevocationClient`: nhận `clientId` và gọi `base-url` của client đó với token `client_credentials` có phạm vi `<app-name>.internal` (migration cho client dịch vụ `oauth2-service` thêm phạm vi `seller.internal`, `ops.internal`, như V10).
- `StorefrontWebBffEndedSessionNotifier` → `BffEndedSessionNotifier`: tra client của phiên (đã tra `RegisteredClient.getId()` → `clientId`), nếu có cấu hình BFF thì gọi, nếu không bỏ qua.
- Không đổi hợp đồng token (ADR-0003). Vai vẫn từ `role` của tài khoản.

## T3. `api-gateway` · Dự kiến

- Thêm host `seller` (`SELLER_HOST`, mặc định `seller.localhost`) và tuyến: đường dẫn BFF (`/api/**`, `/bff/**`, `/oauth2/authorization/**`, `/login/oauth2/code/**`) tới `seller-web-bff`, còn lại tới máy chủ giao diện (`SELLER_FRONTEND_URL`, mặc định cổng 4202).
- Host `ops`: bỏ ghi chú "giữ chỗ", thêm tuyến tới `ops-web-bff` và giao diện (`OPS_FRONTEND_URL`, cổng 4203).
- Luật tần suất nhận khóa host `seller`, `ops` (đã đọc từ `app.gateway.hosts`).

## T4. Hai BFF mới · Dự kiến

- `services/seller-web-bff` (cổng 8091) và `services/ops-web-bff` (cổng 8092): lớp khởi động, `application.properties` (`bff.app-name`, thông tin client, tuyến, địa chỉ chuyển hướng đăng nhập thành công và đăng xuất theo host), Maven phụ thuộc starter. Thêm vào `services/pom.xml`.
- Tuyến: Người bán → `oauth2-service`, `seller-service`, `catalog-service`; Vận hành → `oauth2-service`, `seller-service`, `catalog-service`, `audit-service`.
- Client OAuth2: `seller-web` redirect `http://seller.localhost:8000/login/oauth2/code/seller-web`; `ops-web` tương tự theo host `ops`.

## T5. Bảo mật · Dự kiến

- CSRF, PKCE, cookie theo máy, `sid` theo cặp (người dùng, client): giữ nguyên từ Cửa hàng (ADR-0005, ADR-0006).
- Khóa Redis và phạm vi nội bộ tách theo ứng dụng (E4, E5, E6, E8).
- Bí mật client mỗi BFF khác nhau, lấy từ biến môi trường; dev có mặc định, ngoài dev thiếu thì không khởi động.

## T6. Kiểm chứng · Dự kiến

Theo `flow.md` mục 6. Phá thử (làm theo mẫu feature 07 đến 09): đổi tiền tố khóa về cố định `storefront`; bỏ lọc phạm vi nội bộ theo tên; bộ báo luôn gọi BFF Cửa hàng; seeder bỏ PKCE; bảng tuyến thiếu `/api/` viết lại. Mỗi lỗi phải làm một test đỏ.

## T7. Thứ tự làm · Dự kiến

1. Tách starter, chuyển `storefront-web-bff` (test cũ phải xanh nguyên).
2. `oauth2-service` (seeder, bộ báo thu hồi, migration phạm vi).
3. Hai BFF mới và cấu hình.
4. `api-gateway` (host, tuyến, luật).
5. Chạy thật trên ngăn xếp `probe-`, phá thử, đóng tài liệu.
