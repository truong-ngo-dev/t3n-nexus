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

Xong khi: người rà đồng ý danh sách KT-01 đến KT-19; thêm hoặc bớt thì sửa `flow.md` mục 6.

### Bước 2. Chốt giải pháp kỹ thuật (`tech.md`) · Người rồi Agent

Viết `tech.md` cho từng điểm sau, mỗi điểm một đoạn kèm phương án đã loại và điều chưa kiểm:
- **T1. Workspace:** Angular CLI 19 nhiều dự án, `tsconfig` `paths` trỏ vào mã nguồn thư viện (`@t3n/auth` trỏ `projects/auth/src/public-api.ts`, không trỏ `dist`), Jest (`jest-preset-angular` 14, `setupZoneTestEnv`), Playwright dùng Chrome cài sẵn (`channel: 'chrome'`), ESLint ranh giới thư viện.
- **T2. Mô hình phiên:** trạng thái ba giá trị; kiểu `Role`; khi nào thăm dò lại phiên (sau quay về từ đăng nhập, sau khi đăng xuất).
- **T3. Định tuyến và guard:** bảng route, guard theo trạng thái phiên, cách đi đăng nhập (điều hướng cấp cao).
- **T4. Bộ chặn HTTP:** CSRF tự phục hồi (chỉ xử lý `HttpResponse`), 401 kèm cờ, ánh xạ 429/503; **thử** xem cơ chế XSRF có sẵn của Angular có tự gửi `X-XSRF-TOKEN` cho đường tương đối với phương thức ghi hay không (spike nhỏ, ghi kết quả).
- **T5. Màn hình:** form đăng ký (kiểm hợp lệ phía giao diện chỉ để gợi ý, quyết định ở Xác thực), thông báo lỗi theo mã, xử lý `?error`.
- **T6. Bố cục:** Angular Material, màu và cỡ chữ ở mức "ổn" theo `5-ui-analysis.md` nguyên tắc 3 (không tinh chỉnh thẩm mỹ).
- **T7. Cấu hình dev và BFF:** `SHOP_FRONTEND_URL`, cổng 4201, cờ `ng serve`, mặc định chuyển hướng của BFF.

Xong khi: `tech.md` có T1 đến T7 với điểm "chưa kiểm" ghi rõ; người chốt các điểm có chọn lựa.

### Bước 3. Dựng khung `frontends/` · Agent, Người rà

Tạo workspace Angular CLI 19 (`frontends/`), ứng dụng `storefront-web`, thư viện `auth`, `http`, `model`; cấu hình `paths`, Jest, Playwright, ESLint ranh giới; `README` (cách chạy, cổng 4201, mở qua `shop.localhost:8000`).
Viết **trước** hai test đỏ cho khung: một spec Jest trong `auth` và một smoke Playwright mở `app-root`; ESLint phải báo lỗi khi `projects/auth` import `@angular/material` (thử và hoàn tác).

Xong khi: `ng build storefront-web`, Jest và smoke Playwright xanh; vi phạm ranh giới bị lint báo; `git status` không có tệp trong `web/`.

### Bước 4. `model` và dịch vụ phiên, guard · Agent

Viết KT-01, 02, 03 (đỏ trước), rồi `projects/model` (`Role`, `Session`) và `projects/auth` (dịch vụ phiên ba trạng thái, guard). Copy từ `web/libs/shared/data-access` chỉ phần còn đúng (rà R13).

Xong khi: KT-01, 02, 03 xanh; phá thử (coi 5xx là khách, bỏ kiểm vai lạ, guard không phân biệt đã đăng nhập) làm đúng test đỏ.

### Bước 5. Bộ chặn HTTP · Agent, Người rà

Viết KT-04, 05, 06 (đỏ trước, kể cả ca gửi hai lần và vòng lặp), rồi `projects/http`: bộ chặn CSRF tự phục hồi, bộ chặn 401 với cờ, ánh xạ lỗi.

Xong khi: KT-04, 05, 06 xanh; phá thử (không lọc sự kiện `Sent`, bỏ cờ, thử lại không giới hạn) làm đúng test đỏ.

### Bước 6. Khung ứng dụng: định tuyến, trang chủ, tài khoản, không tìm thấy · Agent, Người rà

Cấu hình `storefront-web`: `provideHttpClient` kèm hai bộ chặn, khởi tạo phiên lúc khởi động, định tuyến theo T3, khung trang (đầu trang có nhãn vai và nút Đăng xuất), trang chủ, `/account`, `**`. Copy bố cục cần từ `web/` rồi rà.

Xong khi: KT-19 xanh (Playwright); ứng dụng chạy được ở cổng 4201 và mở được qua `shop.localhost:8000`.

### Bước 7. Màn Đăng ký · Agent

Viết KT-07 (đỏ trước), rồi màn `/register` và dịch vụ đăng ký (gọi host `auth`, chỉ gửi `{email, password}`).

Xong khi: KT-07 xanh; thử tay với Xác thực thật một lần ra 201.

### Bước 8. Màn Đăng nhập · Agent

Viết KT-14 (đỏ trước), rồi màn `/login`: nút điều hướng tới `/bff/auth/login`, thông báo khi `?error`, không tự chuyển lại. Đổi mặc định của BFF `app.login.success-redirect-uri` thành `/account` kèm sửa test (`OriginsTest` và bất kỳ test nào đọc `/customer`).

Xong khi: KT-14 xanh; test của `storefront-web-bff` xanh.

### Bước 9. Đăng xuất và hết phiên · Agent

Viết KT-08 (đỏ trước), rồi hàm `logout` ở dịch vụ phiên và nút trong khung; xử lý 401 giữa phiên theo bộ chặn.

Xong khi: KT-08 xanh.

### Bước 10. Cấu hình dev và README · Agent

Hoàn thiện cờ `ng serve`, biến môi trường, README ở `frontends/`; kiểm KT-18 (ranh giới) chạy trong lint.

Xong khi: `npm run lint` và `npm test` (Jest) xanh từ thư mục `frontends/`.

### Bước 11. Đầu-cuối bằng Playwright trên ba dịch vụ thật · Agent, Người rà

Viết KT-09 đến KT-17 (đỏ trước khi ghép đủ), chạy trên ba dịch vụ thật (Postgres, Redis, Kafka dev; dữ liệu thử có tiền tố riêng và được dọn sau mỗi lần chạy, báo trước khi ghi vào cơ sở dữ liệu dev).
Cần: tài khoản Quản trị có sẵn (feature 04) cho KT-11; công cụ xóa phiên BFF và cookie (Redis, Playwright) cho KT-12, 13, 16.

Xong khi: KT-09 đến KT-17 xanh; log của ba dịch vụ không có token, mật khẩu hay lỗi bất thường; không còn dữ liệu thử trong cơ sở dữ liệu dev và Redis.

### Bước 12. Chạy thật bằng trình duyệt và đóng feature · Người, Agent

Người dùng thử tay một lượt S1 và S4 bằng Chrome thật qua `shop.localhost:8000`. Sau đó cập nhật: `5-ui-analysis.md` mục 3.2 (chỉ dùng chung logic), ADR-0007 (cấu trúc `frontends/`), `tech-stack.md`, `deferred.md`, hàng 05 và điều học được ở `00-features.md`, các tài liệu nhắc `web/`,
`flow.md` đổi trạng thái sang Đã làm. Quyết định giữ hay xóa `web/`.

Xong khi: không còn chỗ nào trong tài liệu lệch với mã và test.
