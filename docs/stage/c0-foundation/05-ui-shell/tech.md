# Feature 05: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do, điều chưa kiểm. Quyết định đúng cho cả BC hoặc mọi BC (cấu trúc `frontends/`) đi vào ADR-0007 ở bước 12; file này ghi kết quả thử và chi tiết riêng của feature.
> Nhãn "Đã chốt" là người đã quyết; "Đề xuất" là đề xuất của agent chờ người chốt. Viết ngày `2026-10-19` (bước 2 của `plan.md`).

## Kết quả thử (spike, ngoài repo)

Dựng thử một workspace Angular CLI 19 tạm với ứng dụng và ba thư viện, để kiểm công cụ trước khi chốt:

| Ca | Kết quả |
|---|---|
| Jest với `jest-preset-angular` 14.6.2 (`setupZoneTestEnv`, `tsconfig.spec.json`) | Chạy được test của dịch vụ, bộ chặn, thư viện |
| `@playwright/test` với `channel: 'chrome'` | Chạy được trên Chrome đã cài, không tải trình duyệt riêng |
| Thư viện logic dùng qua `paths` trỏ `public-api.ts` | Ứng dụng và test import được `@t3n/*` không cần build thư viện |
| ESLint flat config: `no-restricted-imports`, `no-restricted-syntax` | Chặn được import chéo sai hướng và từ vựng vai cũ (`CUSTOMER`, `SELLER`, `SHIPPER`, `GUEST`) |
| Angular Material 19.2.19 | Dựng được |
| XSRF có sẵn của Angular (kiểm bằng Jest) | Chỉ thêm `X-XSRF-TOKEN` cho URL **tương đối** với phương thức không phải GET, HEAD; không thêm cho URL tuyệt đối (khác nguồn gốc) |
| Bộ chặn thử lại | `HttpClient` phát `Sent` rồi `Response`; không lọc thì chạy hai lần (R4). Lọc `HttpResponse` và `take(1)` thì đúng |
| Node 26 | Angular 19 không chính thức hỗ trợ nhưng build và test chạy được; ghi vào README (xem T1) |

## T1. Workspace và công cụ · Đề xuất (trừ R17: người chọn)

**Quyết định.**
- `frontends/` là một workspace **Angular CLI 19 nhiều dự án**, không dùng Nx (người đã chốt: chỉ có một ứng dụng và ba thư viện logic, chưa cần đồ thị tác vụ và bộ nhớ đệm của Nx).
- Dự án: ứng dụng `storefront-web`; thư viện `model`, `auth`, `http` (chỉ chứa logic, không có thành phần giao diện; người đã chốt).
- Thư viện dùng qua `tsconfig` `paths` trỏ vào mã nguồn (`@t3n/model` → `projects/model/src/public-api.ts`), không trỏ `dist`. Lý do: không phải build thư viện trước khi chạy ứng dụng hay test.
- Hướng phụ thuộc: `http` (bộ chặn) cần biết phiên để xử lý 401 nên import `auth`; `auth` chỉ gọi API đăng ký qua `HttpClient` có sẵn nên **không import `http`**. Quy tắc ESLint (`no-restricted-imports` theo thư mục): `model` không import gì của dự án; `auth` chỉ import `model`; `http` import `model` và `auth`; ứng dụng import cả ba; không ai import ứng dụng. Bước 3 viết quy tắc, bước 4 và 5 chạy lint để chứng minh.
- Test: Jest cho logic (dịch vụ, guard, bộ chặn, thành phần), Playwright cho đầu-cuối trên Chrome (`channel: 'chrome'`).
- ESLint flat config có thêm `no-restricted-syntax` chặn chuỗi vai cũ (R13, KT-18).
- Phiên bản Node: ghi trong README là đã chạy được trên Node 26 dù Angular 19 chưa hỗ trợ chính thức; nếu gặp lỗi lạ thì thử Node LTS trước khi tìm nguyên nhân khác.

**Đã loại.** Nx (xem trên); thư viện build ra `dist` (thêm bước build, `paths` tới nguồn đủ cho một nhà phát triển); Vitest/Karma (người chọn Jest).

**R17: giới hạn tần suất làm bộ đầu-cuối chỉ xanh lần đầu.** Đăng ký bị giới hạn 10 lần mỗi giờ mỗi địa chỉ ở cổng; đăng nhập 5 lần mỗi 5 phút mỗi (email, địa chỉ) ở Xác thực. Hai cách:

| Cách | Làm gì | Ưu | Nhược |
|---|---|---|---|
| **A. Nâng luật bằng cấu hình khi chạy bộ đầu-cuối** (đề xuất) | Chạy cổng với `APP_RATELIMIT_RULES_0_LIMIT` đủ cao và Xác thực với `APP_LOGIN_RATE_LIMIT_PAIR_ATTEMPTS`, `APP_LOGIN_RATE_LIMIT_EMAIL_ATTEMPTS` cao; luật 429 vẫn được test riêng ở feature 06 và 03 | Không đụng Redis, không thêm mã; chạy lại bao nhiêu lần cũng xanh | Bộ đầu-cuối không chứng minh luật 429 (đã có test ở chỗ khác); cần kiểm biến môi trường có thật sự ghi đè (xem "chưa kiểm") |

**Đã làm theo cách A ở bước 11 (người rà xác nhận).** Phát hiện khi chạy: một biến `APP_RATELIMIT_RULES_0_LIMIT` đơn lẻ **làm cổng không khởi động** ("luật thiếu tên"), vì danh sách luật không gộp giữa nguồn cấu hình: biến môi trường thay cả phần tử. Phải khai báo đủ bảy trường (`_NAME`, `_HOST`, `_METHOD`, `_PATH`, `_LIMIT`, `_WINDOWSECONDS`, `_ENABLED`); lệnh đầy đủ ở README. Bộ thật mỗi lần gửi hơn mười yêu cầu đăng ký (đếm từ các ca), nên luật mặc định 10 lần mỗi giờ sẽ trả 429 giữa chừng (tính, chưa chạy thử mặc định). Giới hạn đăng nhập ở Xác thực không cần nâng: mỗi ca dùng email riêng, tối đa hai lần đăng nhập mỗi email.
| B. Xóa khóa đếm trong Redis trước mỗi lần chạy | `globalSetup` của Playwright xóa `ratelimit:*`, `login_pair:*`, `login_email:*` | Chạy đúng cấu hình thật | Xóa khóa Redis dev (phải báo trước); đụng cả dữ liệu đếm của người đang dùng dev |

**Dữ liệu thử.** Mỗi lần chạy bộ đầu-cuối sinh một mã chạy; email thử có tiền tố cố định kèm mã đó. Sau mỗi lần chạy (kể cả lỗi, `globalTeardown`) xóa các tài khoản có tiền tố ấy khỏi cơ sở dữ liệu Xác thực dev và các khóa phiên liên quan ở Redis. Cách đã chọn ở bước 11: tiền tố `e2e-`; `globalSetup` ghi lại số dòng, danh sách `id` của `oauth2_authorization` và `spring_session`, `max(id)` của `outbox_events` và tập khóa Redis; teardown chạy SQL qua `docker exec` vào container Postgres rồi **kiểm số dòng từng bảng về đúng lúc đầu, lệch thì ném lỗi** (bắt được hai bảng không gắn tài khoản thử: phiên HTTP ẩn danh của trang đăng nhập và token `client_credentials` nội bộ). Tài khoản Quản trị thử tạo bằng `infra/seed/admin.sql` có sẵn. **Báo trước khi ghi vào cơ sở dữ liệu dev.**

**Chưa kiểm.**
- Biến môi trường `APP_RATELIMIT_RULES_0_LIMIT` ghi đè được luật chỉ số 0 của cổng (quy tắc nới lỏng của Spring Boot cho danh sách đánh chỉ số; chưa chạy thử). Với Xác thực, thuộc tính là record `LoginRateLimitProperties` tiền tố `app.login.rate-limit`, tên biến suy ra `APP_LOGIN_RATE_LIMIT_PAIR_ATTEMPTS` (chưa chạy thử). Kiểm ở bước 11 trước khi dựa vào.
- Email thử có qua được kiểm `EMAIL_RESERVED` không (miền nào bị giữ chỗ): xem `oauth2-service` ở bước 11.
- Angular 19 trên Node 26 lâu dài (chỉ có kết quả thử một lần).

## T2. Mô hình phiên · Đề xuất

**Quyết định.**
- Dịch vụ phiên (`projects/auth`) giữ **một** trạng thái ba giá trị: `loading` (đang thăm dò), `anonymous` (khách), `authenticated` với `{userId, role}`, cộng giá trị lỗi `unknown` (không xác định). Ba nghĩa với người dùng: đang kiểm, khách hoặc người dùng, lỗi. Dùng signal của Angular 19.
- `Role` là kiểu định nghĩa một chỗ ở `projects/model`: `'USER' | 'ADMIN'`. Phản hồi phiên có vai lạ hoặc thiếu `userId` coi là **lỗi** (`unknown`), không cho vào (R1, KT-01).
- Phân loại phản hồi thăm dò `GET /bff/auth/session`: 200 hợp lệ → `authenticated`; 401 → `anonymous`; mọi thứ khác (5xx, mạng, 200 sai khuôn) → `unknown` (R10, KT-02). Không bao giờ coi lỗi là khách.
- Thăm dò lại phiên ở ba lúc: (1) khởi động ứng dụng (một lần, chặn định tuyến đến khi có kết quả, xem T3); (2) sau khi quay về từ đăng nhập (trang tải lại hoàn toàn nên trùng lúc 1); (3) sau đăng xuất thành công. Không thăm dò định kỳ ở C0. Nút "thử lại" ở trạng thái `unknown` gọi lại thăm dò.

**Đã loại.** Lưu phiên ở `localStorage` (R11: SPA không giữ token, chỉ đọc cookie `XSRF-TOKEN`); thăm dò mỗi lần đổi route (tốn lời gọi, 401 ở API đã đủ báo hết phiên).

**Chưa kiểm.** Không có gì ngoài kiểm trong Jest; cờ "vừa đăng nhập" ở T4 phụ thuộc đúng thứ tự khởi động, kiểm bằng KT-09 đến KT-13 ở bước 11.

## T3. Định tuyến và guard · Đề xuất

| Route | Truy cập | Ghi chú |
|---|---|---|
| `/` | Mọi người | Trang chủ |
| `/register` | Mọi người (người đã đăng nhập: về `/account`) | |
| `/login` | Khách; người đã đăng nhập bị đẩy về `/account` | Sửa vòng lặp R1 |
| `/account` | Chỉ người đã đăng nhập | Khách → đi đăng nhập |
| `**` | Mọi người | Trang không tìm thấy trong khung (R16), không chuyển hướng |

- Bộ khởi động (`provideAppInitializer`) chờ thăm dò phiên xong (kể cả lỗi) trước khi định tuyến đầu tiên, nên guard luôn đọc được trạng thái có nghĩa. Ở `unknown`, ứng dụng hiện màn lỗi kèm nút thử lại, không đi tiếp (R10, KT-20).
- Guard là hàm (`CanActivateFn`) đọc dịch vụ phiên. **Khách vào trang cần đăng nhập** thì điều hướng **cấp cao** (`window.location`) tới `/bff/auth/login`: đăng nhập là luồng chuyển hướng 302 của Spring, không phải lời gọi `fetch` (ADR-0006, `flow.md` mục 3).
- `/login` không tự động chuyển đi đăng nhập; trang có nút bấm để người dùng thấy `?error` trước (xem T5).

**Đã loại.** Guard theo vai ở C0 (chỉ có `USER` và `ADMIN`, chưa màn nào cần phân biệt; nhãn vai chỉ để hiển thị); `NavigationEnd` để thăm dò lại.

**Chưa kiểm.** Hành vi nút Back sau khi đăng xuất (KT đầu-cuối ở bước 11 chỉ kiểm "mọi trang cần đăng nhập lại bị chặn").

## T4. Bộ chặn HTTP · Đề xuất

Hai bộ chặn hàm trong `projects/http`, đăng ký theo thứ tự ở `provideHttpClient(withInterceptors([...]))`:

1. **Phục hồi CSRF (R3, R4).** Nếu phản hồi là 403 với mã `CSRF_TOKEN_INVALID` và yêu cầu chưa thử lại: thăm dò lại phiên (cấp lại `XSRF-TOKEN`) rồi **gửi lại đúng một lần**. Chỉ xử lý sự kiện `HttpResponse` (`filter(event instanceof HttpResponse)`, `take(1)`) vì `HttpClient` phát cả `Sent`.
2. **Ánh xạ lỗi theo khung lỗi chung.** 401 `UNAUTHENTICATED`: nếu `sessionStorage` có cờ "vừa đăng nhập" thì xóa cờ, **dừng**, trả lỗi để giao diện hiện (R2, chống vòng lặp); nếu không có cờ thì đặt cờ rồi điều hướng cấp cao tới đăng nhập. 429: giữ `Retry-After` để hiện thông báo; 503: lỗi tạm thời, hiện thử lại; không tự thử lại ở C0.

- Cờ "vừa đăng nhập" chỉ chứa cờ, không chứa token (R11, KT-17). Đặt ngay trước khi điều hướng đi đăng nhập, xóa khi gặp phản hồi thành công đầu tiên hoặc khi gặp 401 lần hai.
- **Gửi `X-XSRF-TOKEN`: dùng cơ chế có sẵn của Angular** (`withXsrfConfiguration` mặc định đọc cookie `XSRF-TOKEN`). Đã kiểm bằng Jest ở spike: chỉ cho URL tương đối với phương thức ghi. Hệ quả: lời gọi đăng ký tới host `auth` (URL tuyệt đối, khác nguồn gốc, miễn CSRF) **không** mang header, đúng ý. Cần tên header đúng với BFF (`X-XSRF-TOKEN`, trùng).
- Đường `/api/**` đi tương đối cùng nguồn gốc nên được bộ chặn xử lý đủ.

**Đã loại.** Tự viết bộ chặn gắn `X-XSRF-TOKEN` (trùng cơ chế sẵn); thử lại tự động 429/503 (che lỗi thật, để thiết kế ở feature sau); gọi đăng nhập ngay ở mọi 401 như giao diện cũ (vòng lặp vô hạn, R2).

**Chưa kiểm.** Cơ chế XSRF có sẵn đã kiểm bằng Jest và Playwright (Chrome thật, cookie giả, `POST /bff/auth/logout` mang `X-XSRF-TOKEN`), **chưa kiểm** với BFF thật (cookie `XSRF-TOKEN` không `HttpOnly`, cùng nguồn gốc qua cổng): kiểm ở bước 11 (KT-14 đến KT-17) và 12.

## T5. Màn hình · Đề xuất

- **Đăng ký.** Form phản ứng (reactive form) có email, mật khẩu, nhập lại mật khẩu. Kiểm ở giao diện chỉ để **gợi ý** (định dạng email, nhập lại khớp, độ dài tối thiểu); quyết định đúng sai ở Xác thực (không trùng lặp luật mật khẩu quá chi tiết ở giao diện). Gửi `{email, password}` (không gửi ô nhập lại) tới `{auth}/register`, ánh xạ mã lỗi thành thông báo: `EMAIL_INVALID`, `EMAIL_RESERVED`, `PASSWORD_INVALID`, `VALIDATION_FAILED` (400), `EMAIL_TAKEN` (409), 429 (kèm số giây chờ từ `Retry-After`); lỗi lạ hoặc mạng → thông báo chung. 201 → thông báo thành công và liên kết sang `/login`.
- **Đăng nhập.** Nút điều hướng cấp cao tới `/bff/auth/login`. Route đọc `?error`: có thì hiện **một** thông báo chung ("đăng nhập không thành công, thử lại"); **không** cố đoán lý do vì BFF giữ mặc định Spring, `/login?error` không kèm lý do (`06-access-gateway/deferred.md`).
- **Đăng xuất.** Hàm `logout` ở dịch vụ phiên: `POST /bff/auth/logout` (đi qua bộ chặn CSRF), nhận 202 thì điều hướng cấp cao tới `Location` (chỉ nhận đường dẫn tương đối hoặc `http(s)://`; thiếu hoặc lạ thì coi là thất bại). Khóa nút khi đang gửi. Lỗi mạng hoặc 5xx **không** đổi trạng thái phiên và hiện thông báo (R9: phiên vẫn có thể còn hiệu lực ở BFF).
- Thông báo lỗi lấy từ một bảng ánh xạ mã → chuỗi ở `projects/auth`, thành phần không tự so chuỗi.

**Đã loại.** Kiểm mật khẩu đầy đủ ở giao diện (hai nơi giữ luật); hiện lý do chi tiết cho `?error` (cần BFF ánh xạ, đã hoãn).

**Chưa kiểm.** Thông điệp 400 `VALIDATION_FAILED` có thể kèm danh sách trường: chưa đọc kỹ khuôn phản hồi của `oauth2-service` cho trường hợp này, đọc `api.yaml` của Xác thực khi làm bước 7.

## T6. Bố cục · Đề xuất

Angular Material 19.2.19 (đã dựng thử). Khung tối giản: thanh trên có logo, liên kết Trang chủ; khách thấy Đăng ký, Đăng nhập; người đã đăng nhập thấy nhãn vai ("Người dùng" hoặc "Quản trị") và nút Đăng xuất. Chủ đề Material mặc định; màu và cỡ chữ ở mức "ổn" (`5-ui-analysis.md` nguyên tắc 3), không tinh chỉnh thẩm mỹ. Chỉ nhập mô-đun Material đang dùng (nút, thẻ, ô nhập, thanh công cụ, thông báo).

Kiểm khung nhìn điện thoại **hoãn** (xem `deferred.md`); dùng thành phần Material có sẵn bố cục co giãn nhưng không cam kết.

**Cấu trúc tệp trong ứng dụng** (áp dụng cho `storefront-web` và các ứng dụng sau, như `hr-web`):
- Mỗi thành phần ở thư mục riêng, tách ba tệp `x.component.ts`, `x.component.html`, `x.component.scss` (`templateUrl`, `styleUrl`), không viết `template`/`styles` trong `.ts`. Tệp `.spec.ts` nằm cạnh thành phần.
- Thành phần trang ở `app/pages/<tên>/`; khung gốc (`app.component.*`), `app.config.ts`, `app.routes.ts` ở `app/`; mã dùng chung trong ứng dụng ở `app/shared/`; công cụ chỉ dùng cho test ở `app/testing/`.
- Thành phần nhỏ, không có kiểu dáng riêng (ví dụ chỉ vài thẻ) vẫn theo cùng quy tắc để dễ tìm; không có ngoại lệ "viết gọn trong `.ts`".

**Đã loại.** Tailwind, thư viện thành phần tự viết (ngoài phạm vi khung C0).

**Chưa kiểm.** Kích thước gói sản xuất (không đo ở C0).

## T7. Cấu hình dev và BFF · Đề xuất

- `ng serve` chạy ở cổng **4201** với `--host 127.0.0.1 --live-reload=false --hmr=false` (`--host` do bước 11 thêm: mặc định Node 26 chỉ nghe `::1`, cổng viết bằng Java nối `127.0.0.1` nên bị từ chối và trả 500): trang được truy cập qua cổng (`http://shop.localhost:8000`), proxy thẳng WebSocket của live-reload không đi qua cổng được và làm trình duyệt tải lại vòng lặp (đã gặp ở giao diện cũ ở feature 06; kiểm lại ở bước 12).
- Cổng (`api-gateway`) trỏ route host `shop` tới `${SHOP_FRONTEND_URL:http://localhost:4200}` (biến có sẵn; mặc định hiện là 4200 của `web/`, chưa đổi). Chạy giao diện mới thì đặt `SHOP_FRONTEND_URL=http://localhost:4201` (ghi ở README); đổi mặc định khi `web/` bị xóa. Người mở thẳng `localhost:4201` sẽ mất phiên vì khác nguồn gốc: chỉ ghi README (R12), không test.
- `storefront-web-bff`: đổi mặc định `app.login.success-redirect-uri` từ `.../customer` thành `.../account` (R15). Viết KT-22 đỏ trước (kiểm đúng địa chỉ, không chỉ mã 302), rồi đổi.
- Địa chỉ Xác thực cho lời gọi đăng ký: host `auth` qua cổng (`http://auth.localhost:8000`), nạp vào ứng dụng bằng một tệp môi trường, không cứng trong mã.

**Đã loại.** `proxy.conf.json` của Angular (cổng đã là proxy duy nhất; hai lớp proxy che lỗi cấu hình).

**Chưa kiểm.** Tên biến `SHOP_FRONTEND_URL` và route host `shop` hiện có trong cấu hình cổng hay phải thêm: kiểm ở bước 10 trước khi dựa vào. Tắt `--live-reload` có đủ cho Angular 19 với `application` builder không (dùng cờ `--live-reload=false`; cần chạy thật ở bước 12).

## Việc cần người chốt

1. **R17:** đã làm theo cách A (đề xuất) ở bước 11; người rà xác nhận hoặc đổi sang B.
2. Hướng phụ thuộc ở T1: `auth` **không** import `http`; `http` import `auth` (để bộ chặn 401 biết phiên). Đồng ý?

## Ghi nhận khi làm bước 3 đến 12

(Điền khi làm.)
