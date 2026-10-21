# Feature 08: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** quyết định hoặc rà điểm cần insight; **Agent** viết phần thường,
người rà), và cách biết bước đã xong. Phạm vi, quy tắc đo và đường lỗi ở `flow.md`.

**Nguyên tắc thứ tự:** chốt cách đo và máy đo trước, đo phép không cần mạng (băm) trước, rồi phép cần ứng dụng giả (điểm cuối rỗng), rồi phép cần
Postgres (ghi), sau cùng gom kết quả và mở khóa các khoản nợ đang chờ.

**Ngoài feature này:** xem `flow.md` mục 1.

## Điều kiện bắt đầu

Feature 06 xong (đã). Docker chạy được. JDK 21 tại `C:\Program Files\Java\jdk-21` (mặc định của máy là 1.8, phải đặt `JAVA_HOME`).

## Bước 1. Dựng khung và kiểm công cụ · Agent, Người rà

**Trạng thái:** Xong · `2026-10-20`. Kết luận từng điều ở `tech.md` T2; hệ quả cho bước sau ở T3.

Thư mục `bench/` ở gốc kho mã (đã chốt, `tech.md` T1): `bench/java` (băm và tải JDBC), `bench/empty-endpoint` (Spring Boot tối thiểu), `bench/k6` (kịch bản),
`bench/docker-compose.yml` (Postgres riêng của bộ đo), `bench/README.md`. Không chạm `infra/docker-compose.yml`.

Kiểm trước khi đo: `docker run grafana/k6 version` chạy được; k6 trong Docker gọi được một server trên máy chủ qua `host.docker.internal`; Docker ghim được CPU
(`--cpus`, `--cpuset-cpus`) và Docker Desktop có đủ nhân; Maven build được mô-đun Java của bộ đo.

Xong khi: mỗi điều trên có kết luận đúng hoặc sai kèm cách xử lý, ghi ở `tech.md`.

## Bước 2. Ghi cấu hình máy và quy ước đo · Người quyết định, Agent ghi

**Trạng thái:** Xong · `2026-10-20`. Ghi ở `tech.md` T4; khung chạy trong container 4 CPU, ngưỡng P99 50 ms (điểm cuối rỗng) và 100 ms (ghi) là số tạm, khóa sau lượt thử đầu.

Ghi vào `tech.md`: cấu hình máy (CPU, nhân/luồng, RAM, hệ điều hành, phiên bản Docker, JDK, Postgres, k6), cách chia nhân giữa bộ sinh tải, khung bị đo và Postgres,
thời lượng và số lần lặp của mỗi phép đo, ngưỡng P99 dùng để xác định trần cho từng phép (điểm cuối rỗng, ghi), mẫu bảng kết quả.

Quyết định cần người: ngưỡng P99 làm ranh giới trần (đề xuất: 50 ms cho điểm cuối rỗng, 100 ms cho ghi một dòng; chỉnh khi thấy đường cong thật).

Xong khi: `tech.md` có các mục trên; một người đọc lại biết đủ để lặp lại phép đo trên máy khác.

## Bước 3. Đo băm mật khẩu · Agent, Người rà

**Trạng thái:** Xong · `2026-10-20`. Kết quả ở `tech.md` T5: trần ~227 băm/giây ở hệ số 10 (16 luồng), một lần băm P50 45–65 ms (thấp hơn giả định 80 ms); đo ở gói điện Balanced, có cắm sạc.

Đoạn Java dùng cùng `BCryptPasswordEncoder` như `oauth2-service`. Đo băm/giây và thời gian một lần băm (P50, P99) với hệ số 10, 11, 12 và số luồng 1, 2, 4, 8, 16.
Có giai đoạn khởi động; lặp ba lần. Ghi đường cong theo số luồng: dưới số nhân thật thì gần tuyến tính, vượt thì bão hòa. Điểm bão hòa là trần.

Xong khi: bảng băm/giây theo (hệ số, số luồng) có cấu hình đo và biên độ giữa các lần; số liệu cho hệ số 10 so với giả định "~80 ms mỗi lần băm" ở `01-register/nfr.md` (đúng hay sai).

## Bước 4. Đo điểm cuối rỗng · Agent, Người rà

**Trạng thái:** Xong · `2026-10-20`. Kết quả ở `tech.md` T6: trần đọc khoảng 65.000 yêu cầu/giây (100 kết nối, P99 6,4 ms) trên khung 4 CPU, điểm gãy độ trễ từ khoảng 800 kết nối; k6 8 CPU gần bão hòa nên là cận dưới nhẹ. Chưa đo biến thể luồng ảo.

Spring Boot tối thiểu (JDK 21, cấu hình mặc định như dịch vụ thật, tức luồng nền tảng của Tomcat; luồng ảo là biến thể đo thêm; một `@GetMapping` trả hằng, không bảo mật, không log yêu cầu). Kịch bản k6 tăng số kết nối đồng thời theo bậc
(ví dụ 10, 50, 100, 200, 400), mỗi bậc đủ lâu sau khởi động. Ghi yêu cầu/giây và P50, P95, P99 mỗi bậc; trần là bậc cao nhất còn trong ngưỡng P99 đã chốt.

Khung chạy trên máy chủ với nhân ghim riêng; k6 trong Docker với nhân riêng.

Xong khi: bảng bậc tải và kết luận trần có điều kiện đo; đường cong có điểm gãy rõ hoặc ghi rằng bộ sinh tải đạt giới hạn trước khung (khi đó trần là cận dưới).

## Bước 5. Đo ghi một dòng vào Postgres · Agent, Người rà

**Trạng thái:** Xong · `2026-10-20`. Kết quả ở `tech.md` T7: trần ghi khoảng 11.000 giao dịch một dòng/giây (64 kết nối, P99 33 ms) trên Postgres 4 CPU; một kết nối 838/giây (khoảng 1,1 ms mỗi giao dịch); nghẽn ở CPU Postgres từ 16 kết nối trở lên.

Postgres 16 (cùng ảnh `postgres:16-alpine` như hạ tầng) trong container của bộ đo, giới hạn nhân và RAM, `fsync` bật. Bảng mô phỏng một bản ghi tài khoản
(khóa UUID, cột duy nhất cho email, vài cột nhỏ) để dòng ghi có kích thước thực tế. Tải JDBC thuần với 1, 4, 16, 64 kết nối, mỗi lần ghi là một giao dịch một dòng
(kiểu đăng ký). Bảng sạch mỗi lượt; ghi dòng/giây và P99.

Xong khi: bảng dòng/giây theo số kết nối có điều kiện đo; ghi rõ ghi mỗi dòng một giao dịch (đo trần giao dịch/giây chứ không phải trần chèn hàng loạt).

## Bước 6. Gom kết quả và mở khóa các khoản nợ · Agent, Người rà

**Trạng thái:** Xong · `2026-10-20`. Đã điền bảng NFR 4.1 (đọc, ghi, băm); ghi trần vào `01-register/nfr.md`, `02-login-session/nfr.md` (và chốt giữ hệ số băm 10 ở `02-login-session/tech.md` T5), `07-error-tracing/plan.md` bước 11; cập nhật trạng thái bước đo ở `plan.md`, `deferred.md` của 01 và 02. Các phép đo ứng dụng vẫn là việc của từng feature.

Điền cột "Trần máy" ở bảng NFR 4.1 (đọc/giây, ghi/giây, băm/giây) kèm nhãn **Đã đo** và điều kiện. Phần "Suy ra" (ví dụ số lõi cần cho 700 đăng nhập/giây từ trần băm)
ghi nhãn **Suy ra** và mô hình. Ghi trần vào chỗ các feature chờ: `01-register/nfr.md`, `02-login-session/nfr.md` (và quyết định giữ hệ số băm 10 hay đổi ở `02-login-session/tech.md`),
bước đo overhead của `07-error-tracing/plan.md`. Các phép đo ứng dụng của feature 01, 02, 07 vẫn là việc của từng feature; ghi rõ ở `deferred.md` của 08.

Xong khi: không còn ô "chưa đo" cho ba đại lượng; mỗi feature chờ có chỗ ghi trần.

## Bước 7. Đóng feature · Agent, Người rà

**Trạng thái:** Xong · `2026-10-20`. `00-features.md` (hàng 08, các hàng 01, 02, 07 và phần Điều học được), `flow.md`, `deferred.md`, `bench/README.md`; Docker đã dọn (mạng `bench-net`, Postgres của bộ đo).

Cập nhật `00-features.md` (trạng thái hàng 08 và phần "Điều học được"), `flow.md` (trạng thái), `deferred.md` (chỉ phần có chỗ trong phạm vi: gói điện, điểm bão hòa, luồng ảo, ghi quy mô lớn).
Dọn dữ liệu của bộ đo (container, bảng, tệp tạm); không để lại gì trên cơ sở dữ liệu dev.

Xong khi: checklist đóng feature của `docs/stage/` đạt; commit do người quyết định.
