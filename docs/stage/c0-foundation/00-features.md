# Chặng C0: danh sách feature

Bước 4 của quy trình: từ phân tích của chặng ra danh sách feature. Mạch đi: **đầu vào** (`stage.md`, `analysis.md`, roadmap) →
**tính năng chặng cần** → **gom thành feature** → **danh sách feature** kèm kiểm tra phủ và thứ tự. Mỗi feature có thư mục riêng
`NN-<tên>/` ở cạnh file này, chứa luồng, kế hoạch, phần hoãn, chọn giải pháp kỹ thuật, đáp ứng NFR.

## 1. Đầu vào: chặng cần những tính năng nào

Ba nguồn cho ra ba nhóm tính năng.

**a. Nghiệp vụ** (từ `stage.md` mục 3 và `analysis.md`): sáu hành vi, mỗi hành vi là một năng lực.

| Tính năng | Hành vi (stage) | Năng lực (analysis) |
|---|---|---|
| Khách tự đăng ký | H1 | CAP-ATH-01 |
| Có sẵn tài khoản Quản trị | H2 | Dữ liệu khởi tạo |
| Đăng nhập | H3 | CAP-ATH-02 |
| Giữ phiên | H4 | CAP-ATH-03 |
| Đăng xuất | H5 | CAP-ATH-04 |
| Ghi nhận vai và đưa vào hợp đồng danh tính | H6 | CAP-ATH-05 |

**b. Giao diện** (từ `stage.md` mục 3 và `5-ui-analysis.md`): màn đăng ký, đăng nhập, đăng xuất, và một khung trang cho người
đã đăng nhập (để chứng minh đã vào được và để các ứng dụng sau dùng lại bố cục).

**c. Kỹ thuật** (từ roadmap C0, NFR, quy ước chung):
- một điểm vào duy nhất cho mọi yêu cầu từ ngoài, kèm giới hạn tần suất bị động, CSRF phía BFF và gửi `sid` khi làm mới token;
- lần ra nguyên nhân của một yêu cầu lỗi từ log;
- đo trần của máy (điểm cuối rỗng, ghi cơ sở dữ liệu, băm mật khẩu) trước khi đo ứng dụng;
- kiểm đầu-cuối cả chặng: chạy hết các kịch bản S1 đến S5 trên ứng dụng thật, sau khi mọi feature xong.

Điều kiện "xong" của phần nghiệp vụ là năm kịch bản S1 đến S5 và các bất biến INV-ATH-01 đến 11.

## 2. Gom thành feature

Nguyên tắc: mỗi feature là một lát dọc có quan sát được và kiểm chứng được riêng; cắt theo kịch bản chứ không theo BC (C0 chỉ
có một BC nên cắt theo BC chỉ ra một khối). Cách gom từng tính năng:

| Tính năng ở mục 1 | Thành feature | Vì sao gom hoặc tách như vậy |
|---|---|---|
| Khách tự đăng ký + gán vai lúc tạo | **01 Đăng ký** | Gán vai xảy ra ngay khi tạo tài khoản (INV-ATH-06). Chưa cần phiên nên làm được trước |
| Đăng nhập + Giữ phiên | **02 Đăng nhập và phiên** | Đăng nhập thành công luôn cấp phiên và hợp đồng danh tính mang mã phiên; tách ra thì "đăng nhập" không có gì để quan sát |
| Đăng xuất | **03 Đăng xuất** | Có bất biến riêng (INV-ATH-05: luôn thành công, kể cả phiên đã hết hạn), kịch bản riêng (S5), cắt cả hai tầng phiên |
| Tài khoản Quản trị có sẵn | **04 Quản trị có sẵn** | Là một lệnh SQL chứ không phải năng lực, nhưng có kiểm chứng riêng (email nội bộ, mật khẩu băm, vai Quản trị) |
| Màn đăng ký, đăng nhập, đăng xuất, khung trang | **05 Khung giao diện** | Phụ thuộc cả 01, 02, 03; là nơi kiểm S1 từ đầu đến cuối |
| Điểm vào duy nhất + giới hạn tần suất | **06 Cổng truy cập** | Cổng là nơi duy nhất biết địa chỉ mạng thật, nên giữ luôn giới hạn tần suất của đăng ký; cổng cũng là BFF giữ token nên giữ CSRF phía trình duyệt và việc gửi `sid` khi làm mới |
| Lần ra nguyên nhân lỗi | **07 Truy vết lỗi** | Roadmap C0 đòi; cần có trước khi các lát nghiệp vụ lớn lên |
| Đo trần của máy | **08 Bộ đo nền** | NFR 4.1: không đo trần máy thì số đo ứng dụng vô nghĩa; cần ứng dụng chạy được để so nên đứng cuối |
| Kiểm đầu-cuối cả chặng | **09 Kiểm đầu-cuối** | Mỗi feature đã tự build, chạy và gọi API thật (bước "Chạy thật" của feature). 09 chỉ chạy lại cả chuỗi S1 đến S5 qua cổng ở cuối chặng, để bắt lỗi nằm ở chỗ nối giữa các feature |

Gộp thêm sẽ làm một feature chứa hai nhóm kiểm chứng độc lập; tách thêm thì một feature không còn quan sát hoặc kiểm chứng được
riêng. → Chín feature.

## 3. Danh sách feature

Cột "Kiểm chứng bằng" là kịch bản hoặc bất biến mà feature phải qua; "Phụ thuộc" là khi chạy từ đầu đến cuối qua cổng (phần
miền, lưu trữ và API của feature nghiệp vụ làm được độc lập, như feature 01 đã làm).

| # | Feature | Loại | Nguồn | Kiểm chứng bằng | Phụ thuộc | Trạng thái |
|---|---|---|---|---|---|---|
| 01 | Đăng ký | Nghiệp vụ | CAP-ATH-01, 05 | S2, S4 · INV-ATH-01, 02, 04, 06, 07, 09 | 06 | Xong `2026-10-09`; nợ: đo NFR (chờ 08), bước "Chạy thật" mới kiểm API và DB, chưa kiểm log và chưa qua cổng |
| 02 | Đăng nhập và phiên | Nghiệp vụ | CAP-ATH-02, 03, 05 | S1, S3, S4 · INV-ATH-03, 06, 08, 10 | 01, 04 | Xong `2026-10-16` (bước 1 đến 13, 15, 16; 275 test); nợ: đo NFR (chờ 08), chưa chạy qua cổng (06). Gồm phiên hai tầng, token cắt hạn, làm mới token, cookie `DEVICE_KEY`, event ghi trong miền, CSRF, PKCE, giới hạn số lần thử, đăng nhập chung; ba ADR còn Proposed |
| 03 | Đăng xuất | Nghiệp vụ | CAP-ATH-04 | S1, S5 · INV-ATH-05, 08 | 02 | Chưa bắt đầu. Sau 9a, đăng xuất cũ (`EndIdpSession`, `RevokeSession`, `MeController`) còn dựa vào mô hình phiên cũ nên chưa kết thúc được `LoginSession`; feature này thay bằng `LoginSession.end` rồi gỡ mô hình cũ (`OAuthSession`, bảng `oauth_sessions`) |
| 04 | Quản trị có sẵn | Dữ liệu khởi tạo | Dữ liệu khởi tạo | S4 · INV-ATH-01, 02, 06, 07, 11 | không | Chưa bắt đầu |
| 05 | Khung giao diện | Giao diện | Roadmap C0, `5-ui-analysis.md` | S1, S4 (nhìn thấy qua giao diện); gửi header CSRF (`X-XSRF-TOKEN`) cho mọi yêu cầu thay đổi dữ liệu qua BFF (ADR-0005) | 01, 02, 03, 06 | Chưa bắt đầu |
| 06 | Cổng truy cập | Kỹ thuật | Roadmap C0, `rate-limiting-layers.md` | Mọi kịch bản đi qua cổng; giới hạn tần suất của đăng ký trả 429; CSRF phía BFF (cookie `XSRF-TOKEN`, thiếu hoặc sai mã thì 403); BFF gửi `sid` khi làm mới token (ADR-0003 mục 8); BFF đọc claim `sid` thay cho `oss_id` cũ để ánh xạ phiên (`SessionMappingAuthenticationSuccessHandler`, `WebGatewayLogoutSuccessHandler` còn đọc `oss_id`) | không | Chưa bắt đầu |
| 07 | Truy vết lỗi | Kỹ thuật | Roadmap C0, NFR | Một yêu cầu lỗi lần ra nguyên nhân từ log | 06 | Đã cài `2026-10-09`; nợ: đo overhead (chờ 08), `@Async` chưa kiểm, dịch vụ khác chưa chuyển cấu hình log (xem `07-error-tracing/plan.md`) |
| 08 | Bộ đo nền | Kỹ thuật | NFR 4.1, roadmap C0 | Số trần máy cho điểm cuối rỗng, ghi cơ sở dữ liệu, băm mật khẩu | 06 | Chưa bắt đầu |
| 09 | Kiểm đầu-cuối cả chặng | Kỹ thuật | Roadmap C0, `stage.md` mục 4 | Chạy hết S1 đến S5 qua cổng trên ứng dụng thật | 01 đến 08 | Chưa bắt đầu |

Việc đã giao giữa các feature: feature 06 giữ luật giới hạn tần suất của đăng ký (`POST /auth/register`, 10 yêu cầu mỗi giờ mỗi
địa chỉ mạng, xem `01-register/tech.md` T6); feature 01 không giữ phần này.

## 4. Phủ hai chiều

**Mọi năng lực có feature:** CAP-ATH-01 → 01; CAP-ATH-02, 03 → 02; CAP-ATH-04 → 03; CAP-ATH-05 → 01 (gán lúc đăng ký) và 02 (đưa
vào hợp đồng danh tính); dữ liệu khởi tạo Quản trị → 04.

**Mọi feature có nguồn:** xem cột "Nguồn" ở mục 3.

**Mọi kịch bản có feature chứng minh:** S1 (01, 02, 03, 05), S2 (01), S3 (02), S4 (01, 02, 04, 05), S5 (03).

**Mọi bất biến có feature giữ:** INV-ATH-01 (01, 04), 02 (01, 04, 02 khi kiểm), 03 (02), 04 (01), 05 (03), 06 (01, 04),
07 (01, 04), 08 (02, 03), 09 (01, 04), 10 (02), 11 (04).

## 5. Thứ tự làm

06 → 07 → 01 → 02 → 03 → 04 → 05 → 08 → 09. Cổng và quy ước trước, rồi đi theo luồng người dùng, giao diện sau khi API có, bộ đo
nền khi ứng dụng chạy được để so với trần máy, kiểm đầu-cuối cả chặng cuối cùng.

## 6. Kiểm chứng

Test viết từ kịch bản S1 đến S5 và bất biến INV-ATH-01 đến 11, không viết từ code. Số đo của feature 08 gắn nhãn Đã đo, Suy ra
hoặc Chỉ thiết kế theo NFR mục 5.

## 7. Điều học được

Ghi dần khi mỗi feature đóng; tổng kết khi đóng chặng.

### Feature 01 (Đăng ký), đóng `2026-10-09`

- **Mô hình hóa miền trước, hạ tầng sau** đúng như kế hoạch: miền nhỏ nên dựng khung và hành vi cùng lúc, test miền chạy mili-giây,
  còn cơ sở dữ liệu và API chỉ là chi tiết cài đặt đi sau. Mã, giá trị băm và thời điểm tính trước ở use case rồi truyền vào
  aggregate; chỉ hành động cần trạng thái của aggregate (kiểm mật khẩu) mới nhận cổng qua tham số.
- **Phân biệt ba tầng test** và dùng đúng chỗ: đơn vị (kho và bộ băm giả), tích hợp (chỉ ca không thay được: ánh xạ lưu rồi đọc
  lại, lỗi trùng do cơ sở dữ liệu sinh ra), gọi thật qua HTTP (hợp đồng API, tính đúng khi đồng thời). Test chạm cơ sở dữ liệu
  nên chạy trên một Postgres riêng cho test; test không để lại dữ liệu.
- **Quyết định ngoài phạm vi không biến thành phần hoãn.** Hộp thư event và Kafka chuyển về roadmap (C1a), giới hạn tần suất
  chuyển sang cổng truy cập (feature 06). `deferred.md` chỉ ghi phần đã có chỗ trong phạm vi nhưng làm đơn giản.
- **Khóa nghiệp vụ khác mã yêu cầu.** Đăng ký không dùng `Idempotency-Key`: email duy nhất đã đảm bảo một tài khoản, mã yêu cầu
  không chống lạm dụng. Chỉ bàn lại khi làm luồng tạo đơn (C2a).
- **Luật mật khẩu cần khớp giới hạn của thuật toán băm.** Chỉ nhận ASCII (`A-Za-z0-9@$`), 8 đến 64 ký tự, nên luôn dưới giới hạn
  72 byte của bcrypt và tránh khác biệt Unicode giữa thiết bị.
- **Khung phản hồi lỗi có mã chữ ổn định** (`code`), thêm vào khung chung theo hướng cộng thêm để giao diện rẽ nhánh và dịch lỗi
  mà các dịch vụ khác không phải sửa.
- **Giới hạn tần suất có hai nhóm** (bị động ở biên, nghiệp vụ trong BC); cấu hình bằng thuộc tính nạp lúc khởi động, chưa đổi
  lúc chạy. Mô hình ở `../../global/3.technical/rate-limiting-layers.md`.
- **Còn nợ:** phép đo tải của đăng ký (chờ feature 08); các cột và trạng thái tạm trong `user_accounts` (xem `data.md`).
  Giao diện đăng ký (gửi trường thừa, đọc mã lỗi `code`) thuộc feature 05. JSON hỏng trả 400 `MALFORMED_REQUEST` (feature 07).

### Feature 02 (Đăng nhập và phiên), đóng `2026-10-16`

- **Chạy thật vào ứng dụng đã bắt được lỗi mà test không thấy.** Hạn session Spring chỉ 30 phút dù cookie sống 30 ngày (`@EnableJdbcHttpSession` bỏ qua `server.servlet.session.timeout`); chuỗi `/api/**` nhận cả cookie phiên khi CSRF tắt; độ lệch đồng hồ 60 giây của bộ giải mã JWT. Cả ba đã có test đầu-cuối đo trực tiếp (bảng `spring_session`, mã trạng thái, thời điểm hết hạn).
- **Nhãn "xong" của tài liệu phải kiểm bằng số đo.** ADR-0003 và `tech.md` từng ghi hạn session Spring bằng hạn phiên lớn nhất nhưng không có dòng mã nào làm vậy; chỉ thấy khi đọc hàng `SPRING_SESSION`.
- **Token giả không thay được token thật.** Công cụ cấp token giả (`/dev/token`) đã làm rồi bỏ vì không có phiên thật (`sid` ngẫu nhiên, không làm mới được). Thay bằng móc `IssuedTokenListener` ghi token thật ra log, chỉ ở profile `dev` kèm cờ riêng.
- **Phân gói theo đối tượng được sửa đổi.** Lớp nào sửa hành vi của Spring (bộ lọc, bộ xử lý, bộ tùy biến) vào `extension`; phương thức xác thực (MFA, liên kết) là ngoại lệ ngữ nghĩa. Chuyển gói để lại tệp `.class` cũ trong `target` gây lỗi bean trùng, phải xóa `target/classes`.
- **Hai khóa giới hạn đăng nhập** (cặp email và địa chỉ mạng; riêng email) đều băm SHA-256; khóa theo cặp kiểm trước và chỉ khi không chặn mới đếm khóa email. Redis lỗi thì mở cửa kèm cảnh báo (đóng cửa sẽ khóa cả đăng nhập).
- **Còn nợ:** đo NFR đăng nhập (chờ feature 08); bản ghi ủy quyền mồ côi chờ job dọn (Lập lịch, C2b; chưa có use case dọn); BFF chưa gửi `sid` khi làm mới và CSRF tắt (feature 05, 06); đăng xuất, `MeController` và mô hình phiên cũ (feature 03); ba ADR 0003 đến 0005 còn Proposed, chuyển Accepted sau khi đo.

### Feature 07 (Truy vết lỗi), cài xong `2026-10-09`

- **Kiểm nền kỹ thuật trước đã tránh được hai lỗi im lặng.** `EnvironmentPostProcessor` ở Boot 4 phải đăng ký bằng `META-INF/spring.factories`
  (file `.imports` cũ bị bỏ qua, mặc định không bao giờ được đặt); `logback-spring.xml` tự khai báo bộ ghi chữ làm tắt log có cấu trúc.
- **Một dòng hoàn tất cho mỗi yêu cầu**, nội dung yêu cầu chỉ khi lỗi và chỉ qua danh sách trường được phép; tên trường nhạy cảm bị che ở mọi độ sâu,
  và nội dung hỏng không bao giờ ghi nguyên văn (từng để lọt mật khẩu qua đường lui dạng chữ, đã sửa và có test).
- **Mã lỗi đi bằng thuộc tính của yêu cầu**, không qua MDC, để không rò sang các dòng log khác.
- **Test đọc đầu ra JSON thật** (`RequestLoggingIntegrationTest`) thay cho kiểm bằng mắt; log bất đồng bộ nên test chờ có giới hạn.
- **Còn nợ:** đo overhead (chờ feature 08); `@Async` tự truyền ngữ cảnh chưa kiểm; các dịch vụ khác chưa chuyển sang `observability-logback.xml`;
  `client.ip` chờ cổng truy cập (06); `user.id` cho yêu cầu đã xác thực chờ đăng nhập (02), việc đã giao ở `02-login-session/flow.md`.
