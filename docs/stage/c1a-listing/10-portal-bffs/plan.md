# Feature 10: kế hoạch

> **Trạng thái:** Đã làm `2026-11-06` · Nguồn: [`flow.md`](flow.md), [`tech.md`](tech.md) (Q1 đến Q6 đã chốt). Người dùng cần xong nhanh để làm giao diện nên mỗi bước chạy liền, rà ở cuối.

## Từ luồng đến giải pháp

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai |
|---|---|---|---|
| Phần cơ chế dùng chung (3.1 đến 3.3) | Chuyển từ `storefront-web-bff` vào starter, tham số hóa theo tên ứng dụng | `libs/web-bff-starter` | Đã làm: `libs/web-bff-starter` (`@EnableWebBff`, `BffProperties`), 46 lớp chuyển nguyên, tham số hóa tên ứng dụng (khóa Redis, cookie phiên, phạm vi nội bộ, mã đăng ký, tuyến) |
| Cửa hàng dùng starter (E10) | Module mỏng, test cũ chạy nguyên | `storefront-web-bff` | Đã làm: `storefront-web-bff` chỉ còn lớp khởi động + cấu hình; bộ test 53 ca (19 lớp) xanh như trước |
| Đăng ký client BFF theo cấu hình | Seeder đọc danh sách | `RegisteredClientSeeder`, `BffClientProperties` | Đã làm: `BffClientsProperties` (`app.bffs.*`), `RegisteredClientSeeder` tạo/đồng bộ client; `BffClientsPropertiesBindingTest`, `RegisteredClientSeederTest` |
| Báo thu hồi đúng BFF (E5) | Bộ báo tra client, gọi `bff-base-url` kèm phạm vi theo tên | `BffRevocationClient`, `BffEndedSessionNotifier`, migration phạm vi | Đã làm: `BffRevocationClient`, `BffEndedSessionNotifier`, phạm vi `<ứng dụng>.internal` thêm cho client nội bộ ở seeder (không cần migration) |
| BFF Người bán, Vận hành | Module mỏng + cấu hình tuyến | `seller-web-bff`, `ops-web-bff` | Đã làm: `seller-web-bff` (8091), `ops-web-bff` (8092); `SellerWebBffTest`, `OpsWebBffTest` (6 ca mỗi bộ) |
| Host và tuyến ở cổng (E7) | Thêm host `seller`, tuyến cho `ops` | `api-gateway` | Đã làm: `GatewayProperties` (`seller`), `RouteConfiguration` (4 tuyến mới), `RateLimitFilter` (miễn giới hạn cho 3 tuyến giao diện); `HostRoutingTest`, `FrontendRateLimitExemptionTest` |

## Điều kiện bắt đầu

Feature 03 (đăng xuất), 06 của C0 (cổng, BFF) đã đóng. **Không chạm Docker dev, Redis dev, Postgres dev.** Test dùng Testcontainers; chạy thật trên ngăn xếp `probe-`. `oauth2-service`: bốn lớp test nối Postgres dev (`UserAccountRepositoryAdapterTest`, `UserAccountApiTest`, `UserAccountConcurrencyTest`, `RequestLoggingIntegrationTest`) loại ra bằng `-Dtest='!...'`.

## Các bước

> Nguyên tắc chạy test (người dùng nhắc `2026-11-06`): tách thư viện chỉ cần chứng minh luồng thông, không chạy lại cả bộ nhiều lần; phá thử hẹp theo lớp test liên quan.

### Bước 1. Rà kịch bản · Người
`flow.md` mục 4, 6. Đã làm khi viết tài liệu.

### Bước 2. Starter và Cửa hàng · Agent + Người rà
Tách `libs/web-bff-starter`; `storefront-web-bff` chuyển sang; chạy toàn bộ test cũ của Cửa hàng (E10).

### Bước 3. `oauth2-service` · Agent
Seeder theo danh sách, `BffRevocationClient`, `BffEndedSessionNotifier`, test (E5, E9). Phạm vi nội bộ thêm ở seeder, không cần migration.

### Bước 4. Hai BFF mới · Agent
Module, cấu hình, `services/pom.xml`; test khởi động và bảng tuyến.

### Bước 5. Cổng · Agent
Host `seller`, tuyến `ops`, luật tần suất; test cổng.

### Bước 6. Chạy thật · Người
Ngăn xếp `probe-bff` (Postgres, Redis, Xác thực thật, ba BFF, cổng, backend giả): E1 đến E8, E11 bằng HTTP. Phá thử diện rộng đã bỏ vì thừa (xem Kết quả).

### Bước 7. Đóng · Người, Agent
ADR-0006 ghi chú Người bán/Vận hành đã chạy; `00-features.md`; `deferred.md` (Q5); `service`/`overview` nếu nhắc BFF.

## Kết quả (`2026-11-06`)

- **Test:** `storefront-web-bff` 53 ca xanh sau khi chuyển sang thư viện (cùng số ca như trước); `seller-web-bff` 6, `ops-web-bff` 6; `api-gateway` 33; `oauth2-service` 342 (loại 4 lớp nối Postgres dev), trong đó mới: `BffRevocationClientTest`, `BffEndedSessionNotifierTest`, `RegisteredClientSeederTest`, `BffClientsPropertiesBindingTest` (3).
- **Chạy thật** 46/46 trên ngăn xếp cách ly `probe-bff` (Postgres 16, Redis 7 có thông báo keyspace, Xác thực thật, ba BFF, cổng, backend giả ghi nhật ký): đăng ký, đăng nhập đầy đủ qua biểu mẫu ở ba host (seeder tạo `seller-web`, `ops-web` trên cơ sở dữ liệu trống); cookie phiên và khóa Redis tách theo ứng dụng; năm lời gọi qua cổng tới dịch vụ đúng đường dẫn viết lại, mang Bearer, không phiên thì 401; CSRF theo host; token dịch vụ sai phạm vi bị 403 ở cả ba chiều; đăng xuất ở Người bán kết thúc cả Cửa hàng cùng thiết bị, Xác thực báo đúng BFF có phiên (Cửa hàng 1, Người bán 1, Vận hành 0), phiên Vận hành của người khác nguyên vẹn; tuyến Cửa hàng không tồn tại ở BFF Người bán. Ngăn xếp đã gỡ; không chạm `infra-*`.
- **Phá thử:** không làm diện rộng. Người dùng chỉ ra chạy lại cả bộ test cho việc tách là thừa; kịch bản 15 lỗi cố ý chạy cả mô-đun mỗi lỗi bị dừng giữa chừng và mã được khôi phục (đã kiểm). Bù lại bộ test mỗi BFF mới đã bắt các điểm tham số hóa (khóa, cookie, phạm vi, tuyến); chưa có phá thử chứng minh đỏ cho từng điểm.

## Chỗ lệch đã biết

- Tài liệu bản đầu viết đăng xuất chỉ kết thúc phiên một ứng dụng; thực tế (feature 03) là đăng xuất một lần trên cả thiết bị, Xác thực báo từng BFF có phiên. `flow.md` đã sửa.
- Kịch bản dọn khóa khi phiên BFF hết hạn (script Lua dùng tiền tố theo ứng dụng) chỉ được kiểm bằng bộ test của Cửa hàng, chưa có ca riêng cho Người bán, Vận hành.
- Môi trường dev: client `seller-web`, `ops-web` chỉ có khi `oauth2-service` dev khởi động lại (seeder); cần `SELLER_WEB_CLIENT_SECRET`, `OPS_WEB_CLIENT_SECRET` ngoài dev.

## Bài học

- **Cờ `Accept` quyết định có lưu yêu cầu để quay lại sau đăng nhập:** gửi `Accept: text/html,application/json` cho bước điều hướng làm Spring Security không lưu yêu cầu, đăng nhập xong rơi về `/`. Kịch bản thử phải dùng `text/html` cho điều hướng như trình duyệt.
- **Tự cấu hình của Boot chạy sau cấu hình người dùng:** thư viện ghi đè bean của WebFlux/Session phải bật bằng `@Import` (`@EnableWebBff`), không bằng `AutoConfiguration.imports`.
- **Đừng cho agy xóa tệp:** nó làm rỗng tệp thay vì xóa (để lại tệp 2 byte); phải xóa tay sau `apply`.
