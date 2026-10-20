# frontends

Giao diện của t3n-nexus, viết lại từ `web/` (Angular CLI 19 nhiều dự án, không dùng Nx). Thiết kế: `docs/stage/c0-foundation/05-ui-shell/` (`flow.md`, `tech.md`, `plan.md`).

| Dự án | Loại | Vai trò |
|---|---|---|
| `storefront-web` | ứng dụng | Cửa hàng (Trang chủ, Đăng ký, Đăng nhập, Tài khoản) |
| `model` | thư viện logic | Kiểu dùng chung (`Role`, `Session`) |
| `auth` | thư viện logic | Dịch vụ phiên, guard, gọi API đăng ký |
| `http` | thư viện logic | Bộ chặn HTTP |

Thư viện chỉ chứa logic (không thành phần giao diện) và được dùng qua `tsconfig` `paths` trỏ vào mã nguồn (`@t3n/auth` → `projects/auth/src/public-api.ts`), không build riêng. Hướng phụ thuộc, ESLint kiểm: `model` ← `auth` ← `http` ← `storefront-web`; thư viện không import `@angular/material` hay mã ứng dụng.

## Chạy

Cần Node (đã chạy được trên v26 dù Angular 19 chưa hỗ trợ chính thức; nếu gặp lỗi lạ thì thử Node LTS trước).

```
npm install
npm start          # ng serve storefront-web ở cổng 4201, tắt live-reload và hmr
npm run build      # ng build storefront-web
npm test           # Jest (logic)
npm run lint       # ESLint: ranh giới thư viện, từ vựng cũ
npm run e2e        # Playwright (Chrome đã cài trên máy)
```

Mở giao diện **qua cổng**: `http://shop.localhost:8000` (cần `api-gateway`, `storefront-web-bff`, `oauth2-service` chạy). `--live-reload=false --hmr=false` bắt buộc vì kênh WebSocket của live-reload không đi qua cổng được; sửa mã xong phải tải lại trang bằng tay.

## Chạy qua cổng

1. Chạy `npm start` (cổng 4201).
2. Chạy `api-gateway` với biến môi trường `SHOP_FRONTEND_URL=http://localhost:4201`. Mặc định của cổng vẫn là `http://localhost:4200` (giao diện cũ `web/`), nên **không đặt biến này thì `shop.localhost:8000` hiện giao diện cũ hoặc lỗi 502**.
3. Chạy `storefront-web-bff` và `oauth2-service` như thường lệ (cùng `FRONTEND_URL` mặc định `http://shop.localhost:8000`, sau đăng nhập BFF đưa về `/account`).
4. Mở `http://shop.localhost:8000`.

| Địa chỉ | Dùng để |
|---|---|
| `http://shop.localhost:8000` | Dùng thật: ứng dụng, `/bff/**`, `/api/**` cùng một nguồn gốc nên có phiên và cookie CSRF |
| `http://auth.localhost:8000` | Xác thực (đăng ký gọi tới đây, khác nguồn gốc; cấu hình ở `src/environments/environment.ts`) |
| `http://localhost:4201` | Chỉ để Playwright chạy riêng giao diện (`npm run e2e`, phiên được giả) |

**Mở thẳng `http://localhost:4201` không có phiên.** Dev server không có `/bff/**` nên trả HTML cho `/bff/auth/session`; ứng dụng coi là lỗi (trạng thái "không xác định") và hiện màn lỗi kèm nút Thử lại, không coi là khách (R10, R12). Không phải lỗi mã: hãy mở qua cổng.

## Bộ đầu-cuối thật (`npm run e2e:real`)

Chạy Chrome qua cổng tới ba dịch vụ thật, **ghi vào cơ sở dữ liệu Xác thực dev và Redis dev** (tài khoản `e2e-*`, dọn sau mỗi lần chạy; teardown kiểm số dòng từng bảng về đúng lúc đầu và báo lỗi nếu lệch). `npm run e2e` (không có `:real`) vẫn là bộ nhanh có giả lập, không cần hạ tầng.

Cần sẵn: container Docker `infra-postgres-oauth2-1` và `infra-redis-1` (tên đổi được bằng `E2E_POSTGRES_CONTAINER`, `E2E_REDIS_CONTAINER`), Kafka, rồi bốn tiến trình sau (jar đóng gói bằng `./mvnw.cmd -DskipTests -pl oauth2-service,storefront-web-bff,api-gateway -am package` ở `services/`, `JAVA_HOME` là JDK 21):

```
# oauth2-service (profile dev), storefront-web-bff
java -Dspring.profiles.active=dev -jar oauth2-service/target/oauth2-service-1.0.0.jar
java -jar storefront-web-bff/target/storefront-web-bff-1.0.0.jar

# api-gateway: trỏ giao diện mới và nâng luật đăng ký. Phải khai báo ĐỦ bảy trường của luật:
# một biến _LIMIT đơn lẻ làm cổng không khởi động (danh sách luật không gộp giữa các nguồn cấu hình).
SHOP_FRONTEND_URL=http://localhost:4201 \
APP_RATELIMIT_RULES_0_NAME=register APP_RATELIMIT_RULES_0_HOST=auth APP_RATELIMIT_RULES_0_METHOD=POST \
APP_RATELIMIT_RULES_0_PATH=/register APP_RATELIMIT_RULES_0_LIMIT=100000 APP_RATELIMIT_RULES_0_WINDOWSECONDS=3600 \
APP_RATELIMIT_RULES_0_ENABLED=true java -jar api-gateway/target/api-gateway-1.0.0.jar

npm start    # ở frontends/
```

`globalSetup` kiểm trước từng thứ và báo rõ cái nào thiếu. Một ca (KT-15) mở cổng `8002` (customer-service giả trả 401); cổng này phải trống. Hai ca `api.real` gọi API bằng `HttpClient` thật của ứng dụng qua công cụ gỡ lỗi Angular (`ng.getComponent`), nên chỉ chạy với bản `ng serve` (không phải bản build sản xuất), và phụ thuộc tên trường `SessionService.http`.

## Kiểm ranh giới (KT-18)

`npm run lint` kiểm cả `projects/` và `e2e/`:
- hướng phụ thuộc `model` ← `auth` ← `http` ← `storefront-web`; `auth` không import `http`;
- thư viện không import `@angular/material`, `@angular/cdk`, mã ứng dụng, hay đường dẫn tương đối sang thư viện khác;
- từ vựng cũ (`CUSTOMER`, `SELLER`, `GUEST`, `SHIPPER`, `webgw`, `oss_id`, `fullName`) không quay lại trong `.ts`. Vai chỉ là `USER` hoặc `ADMIN`. (Chưa quét `.html`.)

## Địa chỉ cấu hình

Địa chỉ Xác thực nằm ở `projects/storefront-web/src/environments/environment.ts` (`authBaseUrl`), không cứng trong thư viện. Đổi host `auth` thì sửa ở đó. Chưa có tệp môi trường sản xuất riêng.
