# Feature 11: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do, điều chưa kiểm. Nền: [ADR-0006](../../../global/2.architecture/adr/0006-application-origins-and-host-routing.md) (nguồn gốc theo `Host`), [ADR-0007](../../../global/2.architecture/adr/0007-frontends-workspace-structure.md) (workspace giao diện). Viết `2026-11-11` cho lát 11a; các lát sau bổ sung khi bắt đầu.
> Nhãn "Đã chốt" là người đã quyết; "Đề xuất" là đề xuất của agent chờ người chốt.

## T1. Dùng lại ba thư viện logic nguyên trạng · Đã chốt

`seller-web` import `@t3n/model`, `@t3n/auth`, `@t3n/http` qua `tsconfig` `paths` như `storefront-web`. Không sao chép, không sửa thư viện cho riêng Người bán; thay đổi thư viện chỉ khi cả hai ứng dụng cần.

Phiên (`SessionService`), guard, hai bộ chặn (CSRF, 401), `toApiError` đã đủ cho 11a. `SellerStatusService` (T4) nằm trong ứng dụng vì chỉ Người bán dùng.

Cách chia thư viện và cấu trúc workspace (một workspace, thư viện trỏ vào mã nguồn) giữ nguyên tới feature 12; việc chuyển sang gói có phiên bản bàn lại khi rút thư viện giao diện dùng chung (ghi vào kế hoạch feature 12).

**Chưa kiểm.** `RegistrationService` cần `AUTH_BASE_URL`: ứng dụng này không đăng ký tài khoản (T3) nên không cung cấp token đó; kiểm `ng build` không lỗi vì thư viện không được kéo vào ngoài thứ dùng tới.

## T2. Cấu hình dự án `seller-web` · Đề xuất

Dự án đã sinh bằng `ng generate application`. Chỉnh để khớp `storefront-web`:

- Giữ `builder` `@angular-devkit/build-angular:application` (đã khớp).
- **Bỏ** target `test` kiểu Karma (repo dùng Jest chung ở gốc workspace), bỏ `tsconfig.spec.json` và `app.component.spec.ts` sinh sẵn nếu không dùng.
- Thêm chủ đề Material vào `styles` (cùng chủ đề với `storefront-web`).
- Script: `start:seller` = `ng serve seller-web --host 127.0.0.1 --port 4202 --live-reload=false --hmr=false`; `build:seller`. Lý do cờ: giống `storefront-web` (T7 của feature 05: WebSocket live-reload không đi qua cổng; Node chỉ nghe `::1` nếu thiếu `--host`).
- Cổng: mặc định `SELLER_FRONTEND_URL` đã là cổng 4202 (feature 10), không cần đặt biến.
- ESLint (`eslint.config.js` hiện chỉ có quy tắc cho thư viện; ứng dụng chưa có quy tắc nào): thêm `seller-web` vào mẫu `appCode` (thư viện không import mã ứng dụng); thêm hai khối cho ứng dụng cấm `storefront-web` import `seller-web` và ngược lại (ứng dụng không import ứng dụng khác, ADR-0007).
- README ghi cách chạy qua cổng và R6.

## T3. Phiên, vai, guard · Đề xuất

- Bộ khởi động như `storefront-web`: `provideAppInitializer` chờ `SessionService.probe()`; hai bộ chặn theo thứ tự `[unauthorizedInterceptor, csrfRecoveryInterceptor]`.
- Trang cần đăng nhập dùng `authenticatedGuard` kèm `data: { roles: ['USER'] }`.
- **Bẫy vòng lặp:** guard hiện chuyển người sai vai về `/`. Nếu `/` cũng cần vai `USER` thì `ADMIN` bị đẩy vòng quanh. Vì vậy `/` và trang "tài khoản này không bán hàng" **không** có ràng buộc vai; `/` hiển thị theo trạng thái (mục 2 của `flow.md`).
- `ADMIN` đăng nhập ở Người bán (**đã chốt**, người dùng `2026-11-11`): BFF không chặn (hoãn ở feature 10), nên giao diện hiện trang **"không có quyền"** kèm nút đăng xuất; không để `ADMIN` thấy biểu mẫu nộp hồ sơ (service sẽ từ chối bằng 403 `SELLER_ROLE_NOT_ALLOWED`).
- Ứng dụng **không có trang đăng ký tài khoản** (**đã chốt**, người dùng `2026-11-11`): tài khoản tạo ở Cửa hàng; trang đăng nhập có liên kết sang Cửa hàng cho người chưa có tài khoản.

**Đã loại.** Guard riêng cho từng vai (một guard đọc `route.data.roles` đã đủ); thăm dò lại phiên mỗi lần đổi route (tốn lời gọi; 401 từ API đã đủ báo hết phiên).

## T4. Trạng thái người bán · Đã chốt (nạp trong bộ khởi động, người dùng chốt `2026-11-11`; xét chuyển sang nạp lười sau nếu đo thấy tối ưu hơn, xem `deferred.md`)

- Một dịch vụ `SellerStatusService` trong ứng dụng, giữ một trạng thái (signal): `loading`, `none` (chưa là người bán), `pending`, `approved`, `rejected` (kèm lý do), `unknown` (lỗi).
- Nạp bằng `GET /api/seller/seller/profile`: 200 theo `status`; 404 **có `code` = `SELLER_PROFILE_NOT_FOUND`** thành `none`; mọi thứ khác (5xx, mạng, 404 mã khác, khuôn lạ) thành `unknown`. Cùng nguyên tắc với phiên: lỗi không bao giờ bị hiểu thành một trạng thái có nghĩa nghiệp vụ.
- Nạp trong bộ khởi động, sau phiên, chỉ khi phiên là `USER`. Lý do: mọi trang đều cần biết trạng thái để quyết định hiển thị; nạp ở từng trang thì lặp lời gọi và có khoảng nháy. Phương thức `refresh()` để gọi lại sau khi nộp hồ sơ (11b) và khi người dùng bấm thử lại.
- Không giữ trạng thái ở `localStorage` (trạng thái do máy chủ quyết, có thể đổi giữa các lần mở, R8).

**Đã loại.** Dùng `resolver` ở từng route (lặp lời gọi); thăm dò định kỳ để bắt thay đổi do quản trị duyệt (hoãn: `deferred.md`).

## T5. Gọi API · Đã chốt

- Đường dẫn giữ đúng như backend đã thiết kế (người dùng chốt `2026-11-11`): `/api/seller/seller/profile` (đoạn đầu chọn `seller-service` ở BFF, đoạn sau là đường dẫn riêng của dịch vụ), `/api/catalog/seller/products`...
- Mọi lời gọi `/api/...` đặt trong các dịch vụ API của ứng dụng (một `SellerApiService` hoặc vài dịch vụ nhỏ theo nhóm); thành phần gọi hàm có tên nghiệp vụ, không viết chuỗi đường dẫn.
- Lỗi chuyển qua `toApiError` của `@t3n/http` rồi ánh xạ `code` → thông báo ở ứng dụng, thành phần không tự so chuỗi.

**Đã loại.** Đổi tên đoạn tuyến ở BFF (sửa thư viện dùng chung cho một lợi ích thẩm mỹ; xem `deferred.md`).

## T6. Bố cục và cấu trúc tệp · Đề xuất

- Angular Material, bố cục tối giản: thanh trên (tên ứng dụng, tên hoặc trạng thái người bán, đăng xuất), thanh điều hướng bên (các mục mở theo trạng thái người bán), vùng nội dung.
- Cấu trúc tệp theo quy ước của feature 05 (T6 ở đó): mỗi thành phần ba tệp `.ts`, `.html`, `.scss`; trang ở `app/pages/<tên>/`; mã dùng chung trong ứng dụng ở `app/shared/`; công cụ riêng của test ở `app/testing/`.
- Route lazy-load theo vùng (`loadChildren` / `loadComponent`) để các lát 11b đến 11e thêm trang mà không phình gói đầu.

## T7. Kiểm chứng · Đề xuất

- Jest cho logic: dịch vụ trạng thái, guard, ánh xạ lỗi, bộ khởi động. Không viết test cho mẫu HTML không có logic.
- Playwright bản giả (`page.route`) cho từng trạng thái ở `flow.md` mục 2, theo mẫu của `storefront-web`.
- Chạy thật qua cổng **một lần khi đóng mỗi lát** trên ngăn xếp cách ly; bộ Playwright đầy đủ qua cổng để feature 13.
- Test tương xứng: không chạy cả bộ nhiều lần; phá thử hẹp cho điểm có rủi ro (R2, R3, R4, R5).

## Việc cần người chốt

Đã chốt `2026-11-11`: (1) không có trang đăng ký tài khoản ở Người bán; (2) `ADMIN` thấy trang "không có quyền" kèm đăng xuất; (3) nạp trạng thái trong bộ khởi động (nạp luôn), làm trước, xét nạp lười sau.
