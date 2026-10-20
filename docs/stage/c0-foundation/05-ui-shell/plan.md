# Feature 05: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định thiết kế; **Agent** viết phần
thường, người rà), điều cần trao đổi (nếu có), và cách biết bước đã xong. Chọn cách thực hiện khi bắt đầu từng bước.

Quy tắc giống các feature trước: mỗi bước chỉ làm và kiểm đúng phạm vi của nó; test viết từ `flow.md` mục 6 trước khi viết mã (đỏ trước, rồi xanh); quyết định kỹ thuật đặt ngay trước bước cần dùng nó;
sau mỗi bước ghi trạng thái, test mới và điều tìm thấy. Thêm hai quy tắc riêng của feature giao diện:
- **Copy từ `web/` đến đâu quét đến đó:** mỗi tệp đưa vào `frontends/` được rà theo `flow.md` mục 2 đến 4 (R13). Không copy thứ chưa có màn hình dùng; không sửa `web/`.
- **Kiểm chứng bằng thứ chạy được:** logic bằng Jest, hành vi bằng Playwright chạy trên ba dịch vụ thật; không kiểm bằng mắt rồi ghi "đã xong".

## Từ luồng đến giải pháp

Mỗi dòng là một bước của luồng (`flow.md` mục 3). Ba cột đầu viết **trước khi code**; cột cuối điền **sau khi làm xong**
(quy trình: `docs/global/4.convention/work-process.md` mục 3.4).

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai (điền sau khi xong) |
|---|---|---|---|
| Khởi động: thăm dò phiên, phân biệt khách và "không xác định" | Dịch vụ phiên giữ một trạng thái ba giá trị (đang kiểm, khách hoặc người dùng, lỗi), 401 là khách (`tech.md` T2) | `projects/auth` (dịch vụ phiên) + `projects/model` (kiểu vai, phiên) | Chưa làm |
| Vai `USER`, `ADMIN`; vai lạ bị từ chối | Kiểu `Role` một chỗ, kiểm khi đọc phản hồi phiên (`tech.md` T2) | `projects/model` | Chưa làm |
| Guard trang cần đăng nhập; đã đăng nhập vào `/login` | Guard hàm trên dịch vụ phiên, đi đăng nhập bằng điều hướng cấp cao (`tech.md` T3) | `projects/auth` (guard) + cấu hình định tuyến của ứng dụng | Chưa làm |
| Lỗi lời gọi: CSRF tự phục hồi, 401 không vòng lặp, 429, 503 | Hai bộ chặn hàm, chỉ xử lý `HttpResponse` (`tech.md` T4); cờ "vừa đăng nhập" trong `sessionStorage` | `projects/http` | Chưa làm |
| Gửi `X-XSRF-TOKEN` cho yêu cầu ghi cùng nguồn gốc | Dùng cơ chế sẵn của Angular, xác nhận ở bước 2 và 11 (`tech.md` T4) | Cấu hình `provideHttpClient` của ứng dụng | Chưa làm |
| Đăng ký: gửi `{email, password}`, ánh xạ mã lỗi | Dịch vụ đăng ký thuần (gọi host `auth`), ánh xạ mã thành thông báo ở thành phần (`tech.md` T5) | `projects/auth` (gọi API) + màn Đăng ký của ứng dụng | Chưa làm |
| Đăng nhập: điều hướng tới BFF; `?error` | Nút điều hướng cấp cao, đọc `?error` ở route `/login` (`tech.md` T5) | Màn Đăng nhập của ứng dụng + `projects/auth` (hàm `login`) | Chưa làm |
| Đăng xuất: `POST` rồi điều hướng tới `Location` | Hàm `logout` ở dịch vụ phiên, đi qua bộ chặn CSRF, lỗi mạng không đổi trạng thái (`tech.md` T5) | `projects/auth` + nút trong khung | Chưa làm |
| Khung trang, trang chủ, tài khoản, không tìm thấy | Bố cục tối giản bằng Angular Material, nhãn vai (`tech.md` T6) | `projects/storefront-web` | Chưa làm |
| Quay về `/account` sau đăng nhập | BFF đổi mặc định `app.login.success-redirect-uri` thành `/account` (`tech.md` T7) | `storefront-web-bff` (cấu hình, `OriginsTest`) | Chưa làm |
| Chạy dev qua cổng, cổng 4201 | Biến `SHOP_FRONTEND_URL`, cờ `ng serve` (`tech.md` T7) | Cấu hình `frontends/` và README | Chưa làm |
| Ranh giới thư viện logic | ESLint `no-restricted-imports` cho `projects/auth`, `http`, `model` (`tech.md` T1) | Cấu hình ESLint của workspace | Chưa làm |

## Điều kiện bắt đầu

Feature 01 đến 04, 06 xong: Xác thực, BFF và cổng chạy được đúng hợp đồng ở `flow.md` mục 2 (đã chạy thật qua Chrome ở feature 06). Nghiệp vụ đã chốt ở `stage.md` (S1, S2, S4, S5) và `analysis.md` của Xác thực; thiết kế giao diện ở `flow.md` này; quyết định cấu trúc
ở `flow.md` mục 1 (đã chốt với người dùng `2026-10-19`), không bàn lại.

Cần chạy được Redis, Postgres, Kafka dev và ba dịch vụ (`api-gateway`, `storefront-web-bff`, `oauth2-service`) cho bước 11 và 12 (xem cách chạy ở `06-access-gateway/tech.md`, mục bước 11). Từ bước 3 đến 10 chỉ cần Node và Jest. Tài khoản Quản trị có sẵn (feature 04) dùng cho KT-11.

## Các bước

### Bước 1. Rà kịch bản test · Người

Đọc `flow.md` mục 4 và 6: mỗi rủi ro R1 đến R16 đã có kịch bản test hoặc lý do không cần chưa; mỗi kịch bản S1, S2, S4, S5 có ít nhất một test đầu-cuối; các ca "hai lần" (R4) và "vòng lặp" (R2) có test đỏ.

**Trạng thái:** Đã rà `2026-10-19`. Phát hiện 6 chỗ thiếu, đã sửa vào `flow.md`: (1) **R17 mới**: luật giới hạn tần suất (đăng ký 10 lần mỗi giờ ở cổng, đăng nhập 5 lần mỗi 5 phút ở Xác thực) làm bộ đầu-cuối nhận 429 hoặc chỉ xanh lần đầu, cách xử lý chọn ở `tech.md` T1, chứng minh bằng chạy bộ đầu-cuối hai lần liên tiếp; (2) **KT-20 mới** cho R10 (thăm dò phiên 503 ở mức giao diện, trước đó chỉ có test đơn vị); (3) **KT-21 (khung nhìn điện thoại) đã đưa vào rồi bỏ theo yêu cầu**, hoãn sang `deferred.md`; (4) **KT-22 mới** ở BFF cho R15 (hiện không test nào kiểm địa chỉ chuyển hướng sau đăng nhập); (5) KT-18 mở rộng sang lint từ vựng cũ (R13 trước đó chỉ rà bằng mắt); (6) R10, R13, R15 ghi lại cột "Chứng minh". Danh sách nay là KT-01 đến KT-22 trừ KT-21 (số giữ nguyên để tham chiếu không lệch). R12 (mở thẳng cổng 4201) giữ không test, chỉ ghi README.

Xong khi: người rà đồng ý danh sách KT-01 đến KT-22 (không có KT-21); thêm hoặc bớt thì sửa `flow.md` mục 6.

### Bước 2. Chốt giải pháp kỹ thuật (`tech.md`) · Người rồi Agent

Viết `tech.md` cho từng điểm sau, mỗi điểm một đoạn kèm phương án đã loại và điều chưa kiểm:
- **T1. Workspace:** Angular CLI 19 nhiều dự án, `tsconfig` `paths` trỏ vào mã nguồn thư viện (`@t3n/auth` trỏ `projects/auth/src/public-api.ts`, không trỏ `dist`), Jest (`jest-preset-angular` 14, `setupZoneTestEnv`), Playwright dùng Chrome cài sẵn (`channel: 'chrome'`), ESLint ranh giới thư viện.
- **T2. Mô hình phiên:** trạng thái ba giá trị; kiểu `Role`; khi nào thăm dò lại phiên (sau quay về từ đăng nhập, sau khi đăng xuất).
- **T3. Định tuyến và guard:** bảng route, guard theo trạng thái phiên, cách đi đăng nhập (điều hướng cấp cao).
- **T4. Bộ chặn HTTP:** CSRF tự phục hồi (chỉ xử lý `HttpResponse`), 401 kèm cờ, ánh xạ 429/503; **thử** xem cơ chế XSRF có sẵn của Angular có tự gửi `X-XSRF-TOKEN` cho đường tương đối với phương thức ghi hay không (spike nhỏ, ghi kết quả).
- **T5. Màn hình:** form đăng ký (kiểm hợp lệ phía giao diện chỉ để gợi ý, quyết định ở Xác thực), thông báo lỗi theo mã, xử lý `?error`.
- **T6. Bố cục:** Angular Material, màu và cỡ chữ ở mức "ổn" theo `5-ui-analysis.md` nguyên tắc 3 (không tinh chỉnh thẩm mỹ).
- **T7. Cấu hình dev và BFF:** `SHOP_FRONTEND_URL`, cổng 4201, cờ `ng serve`, mặc định chuyển hướng của BFF.
- Ở T1 thêm: cách bộ đầu-cuối tránh giới hạn tần suất (R17) và cách tạo, dọn dữ liệu thử.

Xong khi: `tech.md` có T1 đến T7 với điểm "chưa kiểm" ghi rõ; người chốt các điểm có chọn lựa.

**Trạng thái:** Đã viết `tech.md` T1 đến T7 `2026-10-19`, kèm kết quả thử công cụ (Jest, Playwright, ESLint, Material, XSRF có sẵn của Angular). Còn chờ người chốt hai điểm (R17: cách A hay B; hướng phụ thuộc `http` → `auth`). KT-21 (khung nhìn điện thoại) bỏ theo yêu cầu, ghi ở `deferred.md`.

### Bước 3. Dựng khung `frontends/` · Agent, Người rà

Tạo workspace Angular CLI 19 (`frontends/`), ứng dụng `storefront-web`, thư viện `auth`, `http`, `model`; cấu hình `paths`, Jest, Playwright, ESLint ranh giới; `README` (cách chạy, cổng 4201, mở qua `shop.localhost:8000`).
Viết **trước** hai test đỏ cho khung: một spec Jest trong `auth` và một smoke Playwright mở `app-root`; ESLint phải báo lỗi khi `projects/auth` import `@angular/material` (thử và hoàn tác).

Xong khi: `ng build storefront-web`, Jest và smoke Playwright xanh; vi phạm ranh giới bị lint báo; `git status` không có tệp trong `web/`.
**Trạng thái:** Xong `2026-10-19`. Khung `frontends/` (Angular CLI 19, ứng dụng `storefront-web`, thư viện `model`, `auth`, `http` qua `paths`). Hai test đỏ trước khi có mã: Jest `skeleton.spec.ts` (không tìm thấy `public-api.ts`) và Playwright smoke (webServer không dựng được vì chưa có `main.ts`); sau khi thêm mã: Jest 1 xanh, `ng build storefront-web` xanh, smoke xanh. ESLint sạch trên mã hiện có và báo lỗi đúng ở sáu vi phạm thử rồi hoàn tác: `auth` import `@angular/material`, `auth` import `@t3n/http`, `model` import `@t3n/auth`, import tương đối sang thư viện khác, thư viện import mã ứng dụng, từ vựng `CUSTOMER`; `http` import `@t3n/auth` được phép. Phát hiện khi làm: Jest phải có `moduleNameMapper` cho `@t3n/*` (chỉ `paths` của tsconfig không đủ) và `roots` giới hạn vào `projects` (nếu không Jest nhặt cả spec của Playwright). Không có tệp nào đổi trong `web/`. Placeholder `AUTH_LIBRARY`, `app.component` sẽ được thay ở bước 4 và 6.

### Bước 4. `model` và dịch vụ phiên, guard · Agent

Viết KT-01, 02, 03 (đỏ trước), rồi `projects/model` (`Role`, `Session`) và `projects/auth` (dịch vụ phiên ba trạng thái, guard). Copy từ `web/libs/shared/data-access` chỉ phần còn đúng (rà R13).

Xong khi: KT-01, 02, 03 xanh; phá thử (coi 5xx là khách, bỏ kiểm vai lạ, guard không phân biệt đã đăng nhập) làm đúng test đỏ.

**Trạng thái:** Xong `2026-10-19`. Viết 3 bộ test trước (`model/session.spec.ts`, `auth/session.service.spec.ts`, `auth/guards.spec.ts`, 47 ca), chạy đỏ vì thiếu export, rồi viết mã: `model` (`Role`, `ROLES`, `isRole`, `Session`, `parseSession`), `auth` (`SessionService` với `SessionState` bốn nhánh `loading|anonymous|authenticated|unknown` và `probe()`, token `PAGE_NAVIGATION` cho điều hướng cấp cao, `authenticatedGuard`, `guestOnlyGuard`). Bỏ placeholder `AUTH_LIBRARY` và `skeleton.spec.ts` của bước 3. Kết quả: Jest 47 xanh, ESLint sạch, `ng build storefront-web` xanh, smoke Playwright xanh, từ vựng cũ không còn trong `projects/`, không đổi gì trong `web/`. Phá thử 4 kiểu, đều bị test bắt: coi lỗi là khách (5 đỏ), bỏ kiểm vai lạ (7 đỏ), `guestOnlyGuard` luôn cho vào (3 đỏ), guard đi đăng nhập khi `unknown` (1 đỏ).
Quyết định lúc làm, cần người rà: (1) `tech.md` T3 loại guard theo vai nhưng KT-03 yêu cầu "không đủ vai thì không vào" nên `authenticatedGuard` đọc `route.data.roles` tuỳ chọn, không route nào dùng ở C0; (2) `probe()` không đặt lại về `loading` khi thăm dò lại, để bộ chặn CSRF (bước 5) gọi lại mà giao diện không nháy; (3) `guestOnlyGuard` cho `unknown` vào `/login`, `/register` (màn lỗi chặn ở bộ khởi động, bước 6); (4) không copy gì từ `web/` vì mã cũ gắn vai/`fullName`/`webgw` (R13), viết mới theo `flow.md`.

### Bước 5. Bộ chặn HTTP · Agent, Người rà

Viết KT-04, 05, 06 (đỏ trước, kể cả ca gửi hai lần và vòng lặp), rồi `projects/http`: bộ chặn CSRF tự phục hồi, bộ chặn 401 với cờ, ánh xạ lỗi.

Xong khi: KT-04, 05, 06 xanh; phá thử (không lọc sự kiện `Sent`, bỏ cờ, thử lại không giới hạn) làm đúng test đỏ.

**Trạng thái:** Xong `2026-10-20`. Viết 3 bộ test trước (`csrf-recovery.interceptor.spec.ts`, `unauthorized.interceptor.spec.ts`, `api-error.spec.ts`, 42 ca, máy chủ giả phát `Sent` rồi `Response` như `HttpXhrBackend`), chạy đỏ, rồi viết `projects/http`: `csrfRecoveryInterceptor`, `unauthorizedInterceptor` (+ `JUST_SIGNED_IN_FLAG`), `toApiError`. Kết quả: Jest 89 xanh toàn bộ, ESLint sạch, `ng build storefront-web` xanh, từ vựng cũ không có, không đổi gì trong `web/`.
Hai lỗi thiết kế `tech.md` T4 lộ ra khi test, đã sửa trong mã (cần người rà, và sửa lại T4):
(a) Bộ XSRF có sẵn của Angular chạy **trước** bộ chặn của ta, nên bản gửi lại mang mã cũ; phải đọc lại cookie bằng `HttpXsrfTokenExtractor` và gắn lại `X-XSRF-TOKEN`.
(b) "Xóa cờ khi gặp phản hồi thành công đầu tiên" gây vòng lặp: thăm dò phiên lúc khởi động sau đăng nhập thành công sẽ xóa cờ, 401 kế tiếp lại đi đăng nhập. Thăm dò phiên (cả 401 và thành công của nó) bị loại khỏi logic cờ. Thêm: nhiều 401 song song chỉ đi đăng nhập một lần (nếu không chúng đảo cờ theo chẵn lẻ).
Quyết định lúc làm: (1) ánh xạ lỗi là hàm thuần `toApiError`, **không** là bộ chặn, vì đổi kiểu lỗi trong chuỗi bộ chặn làm `probe()` không còn nhận ra 401; (2) thứ tự đăng ký đề xuất `[unauthorizedInterceptor, csrfRecoveryInterceptor]` (phục hồi CSRF ở trong, 401 ở ngoài); (3) "đúng một lần" của CSRF do cấu trúc (lỗi bản gửi lại nằm trong `switchMap`, ngoài `catchError`), nên bỏ cờ đánh dấu thử lại; (4) R4 (gửi hai lần) không xảy ra trong cấu trúc này vì chỉ bắt lỗi chứ không `switchMap` trên luồng sự kiện; test vẫn ghim (đúng 3 lời gọi, một phản hồi).
Phá thử 8 kiểu, đều bị test bắt: thử lại không giới hạn (đệ quy, 4 đỏ), thử lại cả 403 `FORBIDDEN` (2 đỏ), không gắn lại mã XSRF (1 đỏ), bỏ cờ 401 (2 đỏ), thăm dò phiên xóa cờ (1 đỏ), 401 của thăm dò phiên đi đăng nhập (1 đỏ), bỏ chặn 401 song song (1 đỏ), coi 503 là lỗi khác (1 đỏ). Lần phá "bỏ cờ thử lại" ban đầu **không** bị bắt (cờ thừa do cấu trúc), nên cờ đã bị bỏ.

### Bước 6. Khung ứng dụng: định tuyến, trang chủ, tài khoản, không tìm thấy · Agent, Người rà

Cấu hình `storefront-web`: `provideHttpClient` kèm hai bộ chặn, khởi tạo phiên lúc khởi động, định tuyến theo T3, khung trang (đầu trang có nhãn vai và nút Đăng xuất), trang chủ, `/account`, `**`. Copy bố cục cần từ `web/` rồi rà.

Xong khi: KT-19 xanh (Playwright); ứng dụng chạy được ở cổng 4201 và mở được qua `shop.localhost:8000`.

**Trạng thái:** Phần mã xong `2026-10-20`, **còn thiếu** mục "mở được qua `shop.localhost:8000`" (cần cổng và BFF chạy, làm ở bước 10 đến 12). Viết test trước (`app.component.spec.ts`, `app.routes.spec.ts`, `app.config.spec.ts`, 17 ca; chạy đỏ 16/17), rồi mã: `app.config.ts` (hai bộ chặn theo thứ tự `[unauthorizedInterceptor, csrfRecoveryInterceptor]`, `provideAppInitializer` chờ `probe()`), `app.routes.ts` (`/`, `/account` có `authenticatedGuard`, `**`), khung trang (`app.component.ts`: đầu trang có nhãn vai, màn lỗi phiên kèm Thử lại), `pages/home`, `pages/account`, `pages/not-found` (trang chủ, tài khoản, không tìm thấy), `styles.scss` (chủ đề Material). Playwright: 3 ca mới trong `e2e/shell.spec.ts` (KT-19; đã đăng nhập; phiên 503 rồi Thử lại), phiên giả bằng `page.route` vì chạy không có BFF. Kết quả: Jest 106 xanh, Playwright 4 xanh, ESLint sạch, `ng build` xanh, không đổi gì trong `web/`.
Phát hiện khi làm (đã sửa trong mã): khi phiên lỗi lúc mở thẳng `/account`, guard hủy định tuyến đầu tiên và **trình duyệt bị đưa về `/`**, nên "Thử lại" không thể đọc lại địa chỉ từ thanh địa chỉ. Khung ghi lại địa chỉ ở sự kiện `NavigationCancel` rồi điều hướng tới đó khi thử lại thành công (Playwright bắt được; Jest với `Location` giả không thấy).
Quyết định lúc làm, cần người rà: (1) nút Đăng xuất có trong khung nhưng **vô hiệu hóa**, nối ở bước 9 cùng hàm `logout`; (2) `/login`, `/register` chưa có route (bước 7, 8 thêm), tạm rơi vào trang không tìm thấy nên khung chưa có nút Đăng ký, Đăng nhập cho khách (flow.md mục 2 sẽ đạt sau bước 8); (3) không copy bố cục từ `web/` (viết mới, tối giản, R13); (4) nhãn vai (`ROLE_LABEL`) nằm ở ứng dụng chứ không ở `model`, vì là chữ hiển thị; (5) chưa nạp phông Roboto qua mạng (dùng phông hệ thống thay thế).
Phá thử 8 kiểu, đều bị test bắt: `**` chuyển hướng về trang chủ (3 đỏ), không hiện màn lỗi khi `unknown` (3), khởi động không chờ thăm dò (1), thiếu bộ chặn CSRF (1), thiếu bộ chặn 401 (1), sai nhãn Quản trị (2), `/account` bỏ guard (2), Thử lại không đi tiếp trang bị chặn (1).
Còn nợ (người yêu cầu để sau): chưa tái hiện trường hợp mở thẳng `localhost:4201` bằng `ng serve` (dev server trả HTML 200 cho `/bff/auth/session` nên ra `unknown`, R12); test ở bước 10 đến 12 cùng với cổng thật.

### Bước 7. Màn Đăng ký · Agent

Viết KT-07 (đỏ trước), rồi màn `/register` và dịch vụ đăng ký (gọi host `auth`, chỉ gửi `{email, password}`).

Xong khi: KT-07 xanh; thử tay với Xác thực thật một lần ra 201.

**Trạng thái:** Phần mã xong `2026-10-20`, **còn thiếu** thử tay với Xác thực thật (cần cổng và Xác thực chạy). Viết test trước (`registration.service.spec.ts`, `register.component.spec.ts`, thêm ca vào `app.routes.spec.ts`; chạy đỏ 19/36), rồi mã: `projects/auth` có `RegistrationService` (`register(email, password)` trả kết quả, không ném lỗi), token `AUTH_BASE_URL`, bảng thông báo `registerErrorMessage`; ứng dụng có `RegisterComponent` (form phản ứng, Material), route `/register` với `guestOnlyGuard`, `environments/environment.ts` (`authBaseUrl`). Kết quả: Jest 136 xanh, Playwright 6 xanh (2 ca mới, Xác thực giả bằng `page.route` kèm CORS), ESLint sạch, `ng build` xanh, không đổi gì trong `web/`.
Quyết định lúc làm, cần người rà: (1) mã lỗi 400 lạ hoặc thiếu mã, và mọi lỗi 5xx hoặc mạng, đều ra `other` "không đăng ký được, hãy thử lại sau"; không đoán từ chữ thông điệp (R6); (2) `auth` không import `http` (tech.md T1) nên phân tích `Retry-After` và mã lỗi viết riêng, trùng một đoạn nhỏ với `toApiError`; (3) `authBaseUrl` là hằng ở `environment.ts` (chưa tách theo môi trường); (4) liên kết sang `/login` sau khi đăng ký thành công sẽ ra "không tìm thấy" đến khi có bước 8; (5) giao diện chỉ gợi ý (email đúng dạng, mật khẩu tối thiểu 8, nhập lại khớp), không lặp luật mật khẩu đầy đủ.
**Chưa kiểm:** gọi chéo nguồn gốc từ `shop.localhost:8000` tới `auth.localhost:8000` có qua CORS ở cổng không (Playwright chỉ kiểm với phản hồi giả có header CORS). Nếu cổng không cho, đăng ký sẽ lỗi mạng ở trình duyệt thật; kiểm ở bước 10 đến 12.
Sau bước 7, theo yêu cầu của người dùng: tách `.html`/`.scss` khỏi `.ts` và chia thư mục `pages/<tên>/`, `shared/`, `testing/` (quy ước ghi ở `tech.md` T6); không đổi hành vi (Jest 136, Playwright 6, ESLint, `ng build` vẫn xanh). `web/` cũ không đụng tới.
Phá thử 8 kiểu, 7 bị test bắt: gửi dù form sai (3 đỏ), bỏ chặn bấm đúp (1), gửi kèm vai (2), mang cookie (1), không nhận 409 (2), `/register` bỏ guard khách (1), đoán lỗi từ thông điệp (1). Kiểu thứ tám, "gửi kèm ô nhập lại", thử không hợp lệ (đổi đối số mà hàm bỏ qua nên không có tác dụng) và không tính; ràng buộc "đúng hai trường" được ca gửi kèm vai và test kiểm khóa của body bảo vệ.

### Bước 8. Màn Đăng nhập · Agent

Viết KT-14 (đỏ trước), rồi màn `/login`: nút điều hướng tới `/bff/auth/login`, thông báo khi `?error`, không tự chuyển lại. Đổi mặc định của BFF `app.login.success-redirect-uri` thành `/account` kèm sửa test (`OriginsTest` và bất kỳ test nào đọc `/customer`).

Xong khi: KT-14 và KT-22 xanh (KT-22 viết đỏ trước ở `storefront-web-bff`); test của `storefront-web-bff` xanh.

**Trạng thái:** Xong `2026-10-20`. **BFF:** viết `LoginRedirectTest` (KT-22, 2 ca: callback về `http://shop.localhost:8000/account`, không còn `/customer`) chạy đỏ (nhận `/customer`), rồi đổi mặc định `app.login.success-redirect-uri` sang `/account`; cả bộ test BFF 55 ca xanh (Redis Testcontainers, không chạm cơ sở dữ liệu dev); `docs/global/technical-debt.md` đổi `/customer` thành `/account`. `OriginsTest` không cần sửa (không đọc `/customer`). **Giao diện:** viết test trước (`login.component.spec.ts` 5 ca, thêm ca vào `app.routes.spec.ts`, `app.component.spec.ts`; chạy đỏ 4/35), rồi `pages/login` (nút là liên kết `/bff/auth/login`, điều hướng cấp cao; `?error` hiện một thông báo chung, không in giá trị, không tự đi đăng nhập), route `/login` với `guestOnlyGuard`, liên kết Đăng ký/Đăng nhập cho khách ở đầu trang và trang chủ (flow.md mục 2). Playwright thêm 3 ca (KT-14; bấm nút; khách vào `/account` bị đưa tới `/bff/auth/login` đúng một lần). Kết quả: Jest 146 xanh, Playwright 9 xanh, ESLint sạch, `ng build` xanh, không đổi gì trong `web/`.
**Chưa kiểm:** callback thật của Spring về `/account` qua cổng ở trình duyệt thật và `/login?error` thật do BFF trả (BFF giữ mặc định Spring, cần xem có đúng `/login?error` không): bước 11, 12. Test BFF chỉ kiểm địa chỉ trong tiêu đề `Location`.
Quyết định lúc làm, cần người rà: (1) liên kết sang Xác thực là `<a href>` thường chứ không phải nút gọi `PAGE_NAVIGATION`, để chuột giữa, mở tab mới vẫn đúng; (2) KT-22 là test mới thay vì sửa `OriginsTest`; (3) route ở BFF vẫn tên `customer-service` và `/api/customer/**` (đó là dịch vụ, không phải vai; ngoài phạm vi); (4) sau đăng nhập luôn về `/account` bất kể trang đang mở (chưa có `returnUrl`; ghi vào `deferred.md` nếu bạn muốn làm sau).
Phá thử 7 kiểu, đều bị test bắt: `?error` tự đi đăng nhập (3 đỏ), không nhận `?error` (2), in lý do ra màn hình (1, sau khi sửa kiểu thử cho hợp lệ), nút trỏ sai địa chỉ (2), `/login` bỏ guard khách (1), đầu trang không hiện liên kết cho khách (1), trang chủ hiện nút cho người đã đăng nhập (1); BFF đặt lại `/customer` thì KT-22 đỏ (đã thấy ở lần chạy đỏ trước).

### Bước 9. Đăng xuất và hết phiên · Agent

Viết KT-08 (đỏ trước), rồi hàm `logout` ở dịch vụ phiên và nút trong khung; xử lý 401 giữa phiên theo bộ chặn.

Xong khi: KT-08 xanh.

**Trạng thái:** Xong `2026-10-20`. Viết test trước (`auth/logout.spec.ts` 14 ca, thêm 1 ca ở `csrf-recovery.interceptor.spec.ts`, 5 ca ở `app.component.spec.ts`; chạy đỏ 20 ca), rồi `SessionService.logout()`: `POST /bff/auth/logout` (qua bộ chặn CSRF), 202 kèm `Location` thì `PAGE_NAVIGATION.assign(Location)`; mọi thất bại (mạng, 401, 403, 429, 5xx, 202 thiếu `Location`, `Location` lạ) trả `{kind:'failed'}`, không điều hướng, không đổi trạng thái phiên. Nút Đăng xuất hết bị vô hiệu: khóa khi đang gửi (bấm đúp chỉ một yêu cầu), lỗi thì hiện thông báo và mở lại nút. Playwright thêm 3 ca: 202 điều hướng tới `Location` **kèm `X-XSRF-TOKEN` đọc từ cookie**; 403 CSRF thì phục hồi và gửi lại; 503 thì báo lỗi, vẫn đăng nhập. Kết quả: Jest 166 xanh, Playwright 12 xanh, ESLint sạch, `ng build` xanh.
Phá thử 8 kiểu, đều bị test bắt: bỏ kiểm `Location` (3 đỏ), không điều hướng (6), dùng GET (3), cho `//host` (1), lỗi coi là đã đăng xuất (8), không mở lại nút sau lỗi (2), không xóa lỗi cũ khi bấm lại (1), không khóa khi đang gửi (1).
**Hết phiên giữa chừng (401):** không thêm mã mới; bộ chặn 401 ở bước 5 đã bao (KT-16, đi đăng nhập một lần, cờ chặn vòng lặp). Hiện chưa trang nào gọi `/api/**`, nên chưa có ca giao diện cho nó; kiểm đầu-cuối ở bước 11.
**Quyết định lúc làm, cần người rà:** (1) `Location` chỉ nhận đường dẫn tương đối một dấu `/` hoặc `http(s)://`, chặn `javascript:`, `data:`, `//host` (thêm ngoài flow.md, vì chuyển hướng tới giá trị do phản hồi cấp); (2) thăm dò phiên sau đăng xuất (tech.md T2 điểm 3) không gọi riêng, vì điều hướng cấp cao tải lại trang nên trùng lúc khởi động; (3) 401 ở đăng xuất coi là thất bại như lỗi khác (BFF hứa 202 kể cả khi phiên mất, nên 401 là bất thường).
**Đã kiểm được ở trình duyệt thật:** cơ chế XSRF có sẵn của Angular gắn `X-XSRF-TOKEN` cho `POST` đường tương đối từ cookie `XSRF-TOKEN` (tech.md T4 "chưa kiểm trên trình duyệt thật" nay đã thấy ở Chrome với cookie giả; BFF thật vẫn ở bước 11).
**Chưa kiểm:** 202 và `Location` thật của BFF; trình duyệt thật nhận chuỗi `connect/logout` của Xác thực rồi về trang chủ ở trạng thái khách (KT-09, KT-12, KT-13); nút Back sau đăng xuất (tech.md T3): bước 11, 12.

### Bước 10. Cấu hình dev và README · Agent

Hoàn thiện cờ `ng serve`, biến môi trường, README ở `frontends/`; kiểm KT-18 (ranh giới) chạy trong lint.

Xong khi: `npm run lint` và `npm test` (Jest) xanh từ thư mục `frontends/`.

**Trạng thái:** Xong `2026-10-20`. `npm run lint` nay quét cả `projects/` và `e2e/` (trước chỉ `projects/`); lint và Jest (166) xanh. Cờ `ng serve` (`--port 4201 --live-reload=false --hmr=false`) đã có trong `npm start` từ bước 1, không đổi. KT-18 kiểm bằng 7 tệp vi phạm tạm (Material trong `auth`, `auth` import `http`, `model` import `auth`, import tương đối sang thư viện khác, import mã ứng dụng, `CUSTOMER` ở `projects/`, `SELLER` ở `e2e/`): cả 7 đỏ, đúng thông điệp; đã xóa tệp tạm, lint lại sạch. README ghi cách chạy qua cổng, bảng địa chỉ, ý nghĩa mở thẳng `localhost:4201`, và nơi cấu hình `authBaseUrl`.
**Phát hiện, cần người chốt:** `api-gateway` có sẵn biến `SHOP_FRONTEND_URL` nhưng mặc định là `http://localhost:4200` (giao diện cũ `web/`), không phải 4201 như tech.md T7 viết. Tôi **không đổi** mặc định của cổng (đổi sẽ làm `web/` chạy mặc định hỏng, và là dịch vụ ngoài phạm vi bước này, có test `RateLimitPolicyTest`, `GatewayCorsTest` dùng 4200); README bảo đặt `SHOP_FRONTEND_URL=http://localhost:4201` khi chạy cổng. Đổi mặc định khi `web/` bị xóa (bước 12) nếu bạn đồng ý.
**Chưa kiểm:** lệnh `npm start` cùng cổng thật chạy ra `shop.localhost:8000` (chưa dựng ba dịch vụ), và lint chưa quét `.html`: bước 11, 12.

### Bước 11. Đầu-cuối bằng Playwright trên ba dịch vụ thật · Agent, Người rà

Viết KT-09 đến KT-17 và KT-20 (đỏ trước khi ghép đủ), chạy trên ba dịch vụ thật (Postgres, Redis, Kafka dev; dữ liệu thử có tiền tố riêng và được dọn sau mỗi lần chạy, báo trước khi ghi vào cơ sở dữ liệu dev).
Cần: tài khoản Quản trị có sẵn (feature 04) cho KT-11; công cụ xóa phiên BFF và cookie (Redis, Playwright) cho KT-12, 13, 16.

Xong khi: KT-09 đến KT-17 và KT-20 xanh, và **bộ chạy hai lần liên tiếp đều xanh** (R17); log của ba dịch vụ không có token, mật khẩu hay lỗi bất thường; không còn dữ liệu thử trong cơ sở dữ liệu dev và Redis.

**Trạng thái:** Xong `2026-10-20`. Bộ thật `npm run e2e:real` (`playwright.real.config.ts`, thư mục `e2e-real/`, một worker): `shell.real.spec.ts` 9 ca (KT-09, 10, 11, 12, 13, 14, 16 qua tải lại, 17, 20) và `api.real.spec.ts` 2 ca (KT-16 và KT-15 gọi API bằng `HttpClient` thật của ứng dụng). **11/11 xanh, chạy hai lần liên tiếp đều xanh** (R17 theo cách A). Đã dựng thật: `oauth2-service` (profile dev), `storefront-web-bff`, `api-gateway`, `ng serve`, qua `shop.localhost:8000`; Postgres, Redis, Kafka dev có sẵn.
**Lỗi thật tìm ra khi chạy:** (1) `ng serve` mặc định chỉ nghe `::1`, cổng nối `127.0.0.1` nên 500: thêm `--host 127.0.0.1` vào `npm start`; (2) biến môi trường đơn lẻ cho luật tần suất làm cổng không khởi động (xem tech.md T1); (3) KT-13 viết lần đầu **chạy đua**: thăm dò phiên đầu còn bay, phản hồi của nó cấp lại cookie `XSRF-TOKEN` sau khi test đã xóa, nên ca xanh dù bỏ bộ chặn phục hồi CSRF; sửa bằng cách đợi nhãn vai trước khi thao tác. (4) Lần dọn đầu bỏ sót hai bảng không gắn tài khoản thử (xem tech.md T1); kiểm số dòng sau dọn bắt được.
**Phá thử trên ứng dụng thật, đều bị bắt:** nút Đăng xuất không làm gì (KT-09, 11, 12, 13), bỏ bộ chặn phục hồi CSRF (KT-13, sau khi sửa chạy đua), khách vào `/account` không bị đưa đi đăng nhập (KT-09, 16), bỏ bộ chặn 401 (KT-15, 16), cờ chặn vòng lặp không bao giờ bật (KT-15).
**Log ba dịch vụ:** không có mật khẩu thử, JWT hay `access_token`/`refresh_token`; không `ERROR` nào trong lúc chạy bộ (hai `ERROR` của cổng là hai lần `curl` thử trước khi sửa `--host`).
**Khác với flow.md, cần người rà:** KT-16 gốc là "thao tác"; vì chưa trang nào gọi API, ca gọi bằng `HttpClient` lấy qua `ng.getComponent` (chỉ chạy với bản dev). KT-14 phiên thật chỉ kiểm màn `/login?error` qua cổng; chưa tạo `?error` thật từ Spring (cần callback OAuth hỏng, chưa dựng). KT-12 xóa phiên BFF bằng cách xóa mọi khóa `spring:session:*` ở Redis (chỉ đúng khi Redis dev không có phiên của người khác, đúng lúc này).
**Chưa kiểm:** `?error` thật của Spring; nút Back sau đăng xuất (tech.md T3); luật 429 của đăng ký trong bộ thật (cố ý nâng); trên mạng khác `localhost`.
**Dọn dữ liệu thử `2026-10-20`:** đã xóa 3 dòng `oauth2_authorization` còn sót (`principal_name = 'oauth2-service-internal'`) trong DB dev, có đếm trước và kiểm lại sau; `spring_session` đã 0 dòng và Redis không còn khóa `spring:session:*` (khác con số 6 dòng ghi trước đó). Cả hai bảng và Redis về 0.
**Người đã chấp nhận `2026-10-20`:** ba chỗ khác `flow.md` (KT-16 gọi `HttpClient` qua `ng.getComponent`, KT-14 chỉ kiểm màn `/login?error`, KT-12 xóa mọi khóa `spring:session:*`); phần chưa kiểm ghi ở `deferred.md`.

### Bước 12. Chạy thật bằng trình duyệt và đóng feature · Người, Agent

Người dùng thử tay một lượt S1 và S4 bằng Chrome thật qua `shop.localhost:8000`. Sau đó cập nhật: `5-ui-analysis.md` mục 3.2 (chỉ dùng chung logic), ADR-0007 (cấu trúc `frontends/`), `tech-stack.md`, `deferred.md`, hàng 05 và điều học được ở `00-features.md`, các tài liệu nhắc `web/`,
`flow.md` đổi trạng thái sang Đã làm. Quyết định giữ hay xóa `web/`.

Xong khi: không còn chỗ nào trong tài liệu lệch với mã và test.

**Trạng thái:** Xong `2026-10-20`. **Người quyết định giữ `web/`**: còn nhiều phân hệ cần copy sang `frontends/` và style của `frontends/` chưa tối ưu; xóa sau khi hết giá trị tham chiếu (lịch sử vẫn ở git), nên mặc định `SHOP_FRONTEND_URL` của cổng vẫn trỏ 4200 của `web/`. Người đã thử tay S1 và S4 bằng Chrome qua `shop.localhost:8000` (dịch vụ dựng bằng jar + `npm start`), kết quả ổn. Đã cập nhật: `5-ui-analysis.md` mục 3.2 (chỉ dùng chung logic), ADR-0007 mới (Accepted, có trong `adr/README.md`), `tech-stack.md` mục Frontend (Angular CLI nhiều dự án, bỏ Nx, thêm Jest và Playwright), `deferred.md` (bốn phần hoãn), hàng 05 và mục "Feature 05" ở `00-features.md`, `flow.md` đổi sang Đã làm. Đã dừng dịch vụ và dọn dữ liệu thử của buổi thử tay (tài khoản `e2e-admin`, token nội bộ, khóa Redis `ratelimit:*` và `login_*`); còn lại tài khoản `ducthinh@dev.aplus.vn` do người đăng ký tay, người quyết định giữ. Các tài liệu cũ nhắc `web/` (`feature/*`, `adr/old`, `06-access-gateway/tech.md`) là lịch sử, không sửa.
