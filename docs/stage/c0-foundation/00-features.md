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
| 01 | Đăng ký | Nghiệp vụ | CAP-ATH-01, 05 | S2, S4 · INV-ATH-01, 02, 04, 06, 07, 09 | 06 | Xong `2026-10-09`; nợ: đo NFR của ứng dụng (trần máy đã có từ 08), bước "Chạy thật" mới kiểm API và DB, chưa kiểm log và chưa qua cổng |
| 02 | Đăng nhập và phiên | Nghiệp vụ | CAP-ATH-02, 03, 05 | S1, S3, S4 · INV-ATH-03, 06, 08, 10 | 01, 04 | Xong `2026-10-16` (bước 1 đến 13, 15, 16; 275 test); nợ: đo NFR của ứng dụng (trần máy đã có từ 08); nay đã chạy qua cổng (06). Gồm phiên hai tầng, token cắt hạn, làm mới token, cookie `DEVICE_KEY`, event ghi trong miền, CSRF, PKCE, giới hạn số lần thử, đăng nhập chung; ba ADR còn Proposed |
| 03 | Đăng xuất | Nghiệp vụ | CAP-ATH-04 | S1, S5 · INV-ATH-05, 08 | 02 | Xong `2026-10-17` (điểm cuối đăng xuất OIDC chạy trên miền phiên mới, khóa hàng chống hồi sinh phiên, báo BFF theo `sid`, gỡ mô hình phiên cũ; 323 test, đã chạy thật). Nợ: hàng `client_credentials` tích dần mỗi lần báo; xem `03-logout/deferred.md` |
| 04 | Quản trị có sẵn | Dữ liệu khởi tạo | Dữ liệu khởi tạo | S4 · INV-ATH-01, 02, 06, 07, 11 | không | Xong `2026-10-17` (script SQL chạy tay `infra/seed/admin.sql`, ngoài Flyway; 10 test script, 2 test đăng nhập Quản trị, đã chạy thật vào cơ sở dữ liệu dev rồi dọn). Nợ: không có riêng; nửa S4 qua giao diện ở 05; xem `04-seed-admin/deferred.md` |
| 05 | Khung giao diện | Giao diện | Roadmap C0, `5-ui-analysis.md` | S1, S4 (nhìn thấy qua giao diện); gửi header CSRF (`X-XSRF-TOKEN`) cho mọi yêu cầu thay đổi dữ liệu qua BFF (ADR-0005) | 01, 02, 03, 06 | Xong `2026-10-20` (`05-ui-shell/flow.md`, `plan.md`): viết lại thành dự án mới `frontends/storefront-web` (4 màn: Trang chủ, Đăng ký, Đăng nhập, Tài khoản; 166 test Jest, bộ Playwright thật 11/11 xanh hai lần liên tiếp); người thử tay S1, S4 bằng Chrome qua cổng; ba phần hoãn ở `05-ui-shell/deferred.md` |
| 06 | Cổng truy cập | Kỹ thuật | Roadmap C0, `rate-limiting-layers.md`, ADR-0006 | Mọi kịch bản đi qua cổng; giới hạn tần suất của đăng ký trả 429; CSRF phía BFF (cookie `XSRF-TOKEN`, thiếu hoặc sai mã thì 403); BFF gửi `sid` khi làm mới token (ADR-0003 mục 8); BFF đọc claim `sid` để ánh xạ phiên (đã thay `oss_id` cũ) | không | Xong `2026-10-18` (định tuyến theo `Host` ADR-0006 Accepted, giới hạn tần suất theo đường dẫn và mức chung, CSRF ở BFF, làm mới token kèm `sid` có tuần tự hóa, ánh xạ phiên theo `sid`, địa chỉ tin cậy và log hoàn tất mỗi yêu cầu, hành vi lỗi hai loại người gọi; `storefront-web-bff` 51, `api-gateway` 28, `observability-starter` 67, `reactive-rate-limiter-starter` 8 test; đã chạy thật qua ba dịch vụ và Chrome). Nợ: xem `06-access-gateway/deferred.md` và `global/technical-debt.md` mục 3 |
| 07 | Truy vết lỗi | Kỹ thuật | Roadmap C0, NFR | Một yêu cầu lỗi lần ra nguyên nhân từ log | 06 | Đã cài `2026-10-09`; nợ: đo overhead (trần nền đã có từ 08), `@Async` chưa kiểm, dịch vụ khác chưa chuyển cấu hình log (xem `07-error-tracing/plan.md`) |
| 08 | Bộ đo nền | Kỹ thuật | NFR 4.1, roadmap C0 | Số trần máy cho điểm cuối rỗng, ghi cơ sở dữ liệu, băm mật khẩu | 06 | Xong `2026-10-20` (`08-baseline-bench/tech.md` T5 đến T7, bộ chạy lại ở `bench/`): trần đọc khoảng 65.000 yêu cầu/giây (khung 4 CPU), trần ghi khoảng 11.000 giao dịch/giây (Postgres 4 CPU), trần băm khoảng 227 lần/giây (hệ số 10, cả máy); đã điền bảng NFR 4.1. Nợ: bốn phần hoãn ở `08-baseline-bench/deferred.md` (gói điện High performance, điểm bão hòa thật, luồng ảo, ghi ở quy mô lớn) |
| 09 | Kiểm đầu-cuối cả chặng | Kỹ thuật | Roadmap C0, `stage.md` mục 4 | Chạy hết S1 đến S5 qua cổng trên ứng dụng thật | 01 đến 08 | Xong `2026-10-21` (`09-e2e-check/tech.md`, bảng truy vết ở T7): `scenarios/run.sh` dựng cả chuỗi từ trạng thái sạch và chạy hai pha (16 test thật qua cổng: 15 ở pha A, KT-25 ở pha B), hai lần liên tiếp đạt và số dòng cuối bằng số đầu; thêm S3 qua cổng (kể cả thời gian), truy vết lỗi qua cổng, 429 của đăng ký; tìm ra và sửa hai lỗi ở chỗ nối (mật khẩu vào log khi nội dung là chuỗi JSON; mức giới hạn chung của cổng cộng dồn giữa hai lần chạy). Nợ: hai phần hoãn ở `09-e2e-check/deferred.md` (hết hạn phiên thật qua cổng, quét bí mật toàn bộ) |

Việc đã giao giữa các feature: feature 06 giữ luật giới hạn tần suất của đăng ký (`POST /register` ở host `auth`, 10 yêu cầu mỗi giờ mỗi
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
- **Còn nợ:** phép đo tải của đăng ký (trần máy đã có từ feature 08); các cột và trạng thái tạm trong `user_accounts` (xem `data.md`).
  Giao diện đăng ký (gửi trường thừa, đọc mã lỗi `code`) thuộc feature 05. JSON hỏng trả 400 `MALFORMED_REQUEST` (feature 07).

### Feature 02 (Đăng nhập và phiên), đóng `2026-10-16`

- **Chạy thật vào ứng dụng đã bắt được lỗi mà test không thấy.** Hạn session Spring chỉ 30 phút dù cookie sống 30 ngày (`@EnableJdbcHttpSession` bỏ qua `server.servlet.session.timeout`); chuỗi `/api/**` nhận cả cookie phiên khi CSRF tắt; độ lệch đồng hồ 60 giây của bộ giải mã JWT. Cả ba đã có test đầu-cuối đo trực tiếp (bảng `spring_session`, mã trạng thái, thời điểm hết hạn).
- **Nhãn "xong" của tài liệu phải kiểm bằng số đo.** ADR-0003 và `tech.md` từng ghi hạn session Spring bằng hạn phiên lớn nhất nhưng không có dòng mã nào làm vậy; chỉ thấy khi đọc hàng `SPRING_SESSION`.
- **Token giả không thay được token thật.** Công cụ cấp token giả (`/dev/token`) đã làm rồi bỏ vì không có phiên thật (`sid` ngẫu nhiên, không làm mới được). Thay bằng móc `IssuedTokenListener` ghi token thật ra log, chỉ ở profile `dev` kèm cờ riêng.
- **Phân gói theo đối tượng được sửa đổi.** Lớp nào sửa hành vi của Spring (bộ lọc, bộ xử lý, bộ tùy biến) vào `extension`; phương thức xác thực (MFA, liên kết) là ngoại lệ ngữ nghĩa. Chuyển gói để lại tệp `.class` cũ trong `target` gây lỗi bean trùng, phải xóa `target/classes`.
- **Hai khóa giới hạn đăng nhập** (cặp email và địa chỉ mạng; riêng email) đều băm SHA-256; khóa theo cặp kiểm trước và chỉ khi không chặn mới đếm khóa email. Redis lỗi thì mở cửa kèm cảnh báo (đóng cửa sẽ khóa cả đăng nhập).
- **Còn nợ:** đo NFR đăng nhập (trần máy đã có từ feature 08); bản ghi ủy quyền mồ côi chờ job dọn (Lập lịch, C2b; chưa có use case dọn); BFF gửi `sid` khi làm mới và CSRF đã trả ở feature 06; hạn refresh token ở dev không theo cấu hình, đã sửa ở bước điều tra sau feature 06 (`global/technical-debt.md` mục 3); đăng xuất, `MeController` và mô hình phiên cũ (feature 03); ba ADR 0003 đến 0005 còn Proposed, chuyển Accepted sau khi đo.

### Feature 03 (Đăng xuất), đóng `2026-10-17`

- **Thử cơ chế có sẵn trước khi thiết kế phần bổ sung.** Chạy thử điểm cuối đăng xuất của Spring trên miền phiên mới cho thấy nó **hỏng hoàn toàn** từ feature 02: lớp bọc ủy quyền cố ý không tra ID token (ADR-0003 mục 8) nên mọi hint đều `invalid_token`. Một quyết định đúng ở feature trước (không quét cột token) đã chặn một chức năng của feature sau; khi giới hạn tra cứu thì phải liệt kê mọi loại token Spring sẽ hỏi, kể cả `id_token`, `state` và loại `null`.
- **Đọc mã nguồn thư viện để xác minh, không suy luận.** Đọc `OidcLogoutAuthenticationProvider`, `JwtGenerator` và bộ cấu hình của Spring 7.0.5 cho biết chính xác Spring dùng `sid` ở năm chỗ, chọn session "đăng ký sau cùng" khi đổi mã (nhiều thiết bị cộng đăng nhập chung thì chọn nhầm, đã tái hiện bằng tay), và đã chỉ ra điểm mở rộng đúng (bean `SessionRegistry`). Giữ nguyên tắc **chỉ mở rộng, không thay lõi**: sổ phiên rỗng thay vì viết lại provider đăng xuất.
- **Viết test đỏ trước cho rủi ro đua.** Test đăng xuất đua với cấp phiên ứng dụng đỏ (`INSERT ... ON CONFLICT DO UPDATE` tạo lại phiên đã đăng xuất), sửa bằng khóa hàng thì xanh, bỏ khóa thì hai test cùng đỏ. Rủi ro đoán từ mã (R7) chỉ chắc khi có test chứng minh.
- **Chạy thật vẫn bắt được lỗi mà mọi test xanh bỏ sót.** Bộ báo BFF so chữ `storefront-web-bff` với cột `client_id` đang giữ mã nội bộ của client; test đơn vị giả định tên công khai, test đầu-cuối giả ở bộ báo, chỉ log thật mới thấy không có dòng báo. Khi giả một lớp để tránh phụ thuộc ngoài thì phải giả ở **biên ngoài cùng** (lời gọi HTTP), không giả lớp chứa logic cần kiểm.
- **Tìm tham chiếu phải tìm đủ các nơi.** Nhận định "không client nào đăng ký trang đích" sai vì chỉ tìm tên trong mã Java, bỏ sót cột SQL ở migration V4.
- **Test cũng có thể lúc đỏ lúc xanh vì chi tiết nhỏ:** so `Location` với chuỗi `localhost:4200` sai khi cổng ngẫu nhiên bắt đầu bằng 4200; "làm hỏng" JWT bằng cách đổi ký tự cuối chữ ký đôi khi không đổi byte nào (bit đệm của base64url). So sánh chính xác và đổi ký tự ở giữa.
- **Test cũ chạm cơ sở dữ liệu dev.** Ba test cũ dùng cơ sở dữ liệu cấu hình sẵn, nên chạy cả bộ test áp migration (kể cả `DROP TABLE` của V16) lên cơ sở dữ liệu dev. Migration phá hủy phải báo và xem dữ liệu trước.
- **Gỡ mô hình cũ sau khi đường mới chạy và có test** (bước 7 sau bước 5, 6), kèm test Flyway từ trống xác nhận bảng đã bỏ.
- **Khai báo bean:** use case dùng `@Service` (không `@Transactional`, giao dịch ở hạ tầng), lớp hạ tầng đơn giản dùng `@Component`, chỉ giữ một chỗ lắp ghép tay (bản ghi ủy quyền). Thành phần mà test có thể muốn thay phải là bean riêng, không phải `new` ẩn trong hàm config (`03-logout/tech.md` T11).
- **Còn nợ:** hàng `client_credentials` tích dần mỗi lần báo; Back-Channel Logout chuẩn, thử lại báo, `auth_time`, tên ứng dụng công khai (`03-logout/deferred.md`); tài liệu cũ còn mô tả mô hình `OAuthSession` (`spring-security-logout-bff.md` đã viết lại ở feature 06; còn vài tệp khác, xem `03-logout/flow.md`); `identity-service` còn consumer event phiên cũ.

### Feature 04 (Quản trị có sẵn), đóng `2026-10-17`

- **Dữ liệu khởi tạo thuộc về môi trường, không thuộc phiên bản mã.** Chốt script SQL chạy tay ngoài Flyway (không migration, không bộ nạp) để mật khẩu băm không vào repo và mô phỏng cách công ty vận hành. Đổi hướng so với đề xuất ban đầu (bộ nạp đọc cấu hình) do người quyết định; có test quét `db/migration` giữ quyết định này.
- **Test chạy chính tệp script, không chép nội dung.** Test chép tệp vào container Postgres rồi chạy bằng `psql -v` trên một cơ sở dữ liệu riêng đã migrate; test không có Spring vì script là thứ được kiểm. Phá thử từng kiểm đầu vào (bỏ kiểm bcrypt, bỏ kiểm miền, `DO UPDATE` thay `DO NOTHING`, vai `USER`) làm đúng test tương ứng đỏ.
- **Hai mặc định ở hai nơi cần một test giữ cho khớp.** Miền nội bộ mặc định nằm cả ở script và ở `application.properties`; KT-11 đọc mặc định của dịch vụ để so với script. Script không đọc được cấu hình của dịch vụ nên không có cách nào khác.
- **Chạy thật vẫn đáng làm cho cả việc nhỏ.** Dán nhầm đầu ra có lẫn tiến trình tải image vào tham số hash bị script từ chối đúng (bảo vệ của R2 chạy thật); README đã thêm cảnh báo không gộp stderr. Postgres 16 chưa có `uuidv7()` nên UUID v7 dựng bằng SQL, kiểm bằng bit phiên bản.
- **Còn nợ:** không có nợ riêng của feature; API tạo, sửa tài khoản nội bộ và bắt đổi mật khẩu lần đầu ở C1b; hai phần hoãn ở `04-seed-admin/deferred.md`; trong tài liệu cũ, `data.md` mục 2 từng lặp một đoạn về `oauth_sessions` (đã sửa).

### Feature 05 (Khung giao diện), đóng `2026-10-20`

- **Chạy thật vào ứng dụng bắt được lỗi mà Jest và Playwright giả lập không thấy.** `ng serve` mặc định chỉ nghe `::1` trong khi cổng nối tới `127.0.0.1` nên trả 500 (sửa bằng `--host 127.0.0.1`); một biến môi trường đơn lẻ cho luật tần suất làm cổng không khởi động vì danh sách luật không gộp giữa các nguồn cấu hình (phải khai báo đủ bảy trường); lần dọn đầu bỏ sót hai bảng không gắn tài khoản thử.
- **Test chạy đua có thể xanh vì lý do sai.** KT-13 viết lần đầu xanh dù bỏ bộ chặn phục hồi CSRF: thăm dò phiên đầu còn bay, phản hồi của nó cấp lại cookie `XSRF-TOKEN` sau khi test đã xóa. Chỉ lộ ra khi phá thử; sửa bằng cách đợi nhãn vai trước khi thao tác.
- **Kiểm số dòng từng bảng trước và sau là cách dọn dữ liệu thử đáng tin.** Dọn theo tiền tố bỏ sót bảng không gắn tài khoản (phiên ẩn danh, token nội bộ `client_credentials`); teardown so số dòng với lúc bắt đầu và báo lỗi nếu lệch. Dịch vụ tự sinh thêm token nội bộ mỗi lần khởi động, nên sau thử tay vẫn phải dọn lại.
- **Copy từ `web/` đến đâu quét đến đó** tránh mang hợp đồng cũ (vai `CUSTOMER`, route `/customer`, mọi 401 gọi đăng nhập); ESLint chặn từ vựng vai cũ làm quy tắc đó thành test.
- **Bộ chặn HTTP phải lọc `HttpResponse`.** `HttpClient` phát `Sent` rồi `Response`; không lọc thì bộ chặn thử lại chạy hai lần.
- **Quyết định ngoài phạm vi không biến thành phần hoãn** (xem feature 01): ba chỗ bộ thật khác `flow.md` (KT-12, 14, 16) người đã chấp nhận và ghi ở `deferred.md` kèm điểm nối.
- **Còn nợ:** `05-ui-shell/deferred.md` (bốn phần hoãn); ADR-0005 vẫn Proposed (đã Accepted `2026-10-21`), nay giao diện đã gửi `X-XSRF-TOKEN` và được kiểm bằng bộ thật nên có thể xem xét chuyển Accepted; mặc định `SHOP_FRONTEND_URL` của cổng vẫn là cổng 4200 của `web/` (xem README `frontends/`).

### Feature 06 (Cổng truy cập), đóng `2026-10-18`

- **Chạy thật tìm ra ba lỗi mà test tự động không thấy.** (1) Đăng xuất để lại phiên BFF cùng token trong Redis: chuỗi đăng xuất mặc định của WebFlux chỉ xóa ngữ cảnh bảo mật, không xóa phiên, và `LogoutTest` kiểm theo mã phiên cũ nên thấy "đã biến mất" trong khi phiên đã được đổi tên sang mã mới. (2) BFF còn mức log DEBUG làm cookie phiên và JWT ra console. (3) Yêu cầu đầu tiên sau khởi động bị mở cửa nhầm ở cổng vì kết nối Redis mở lần đầu chậm hơn thời gian chờ. Cả ba có test đỏ trước rồi mới sửa.
- **Test "đã xóa" phải kiểm theo nội dung, không theo định danh.** Khi framework có thể đổi tên khóa (đổi mã phiên), kiểm theo mã cũ luôn xanh. Test đếm phiên còn giữ token bất kể mã (`sessionsHoldingTokens`) mới đúng.
- **Giả định về mặc định của framework phải đo, không suy.** Tôi giải thích sai hai lần vì cho rằng đăng xuất đã `invalidate()` phiên; `MONITOR` của Redis cho thấy không có lệnh `DEL` nào và đọc bytecode `LogoutSpec` cho thấy chuỗi mặc định chỉ có bộ xóa ngữ cảnh. Công cụ đáng dùng: `redis-cli monitor` trong lúc chạy một ca, và `javap -v` (tham chiếu phương thức chỉ hiện ở phần bootstrap).
- **Phá thử bắt cả lỗi của chính test.** Token "hết hạn" tạo cho test có `iat` sau `exp` nên bị từ chối vì lý do khác; kiểm hạn ánh xạ quá lỏng. Hai phá thử đầu lần chạy đầu không đỏ, sửa test rồi phá thử lại mới đỏ đúng.
- **Hành vi lỗi quyết định theo bản chất endpoint, không theo đoán `Accept`.** Hai loại người gọi: fetch của SPA (luôn mã lỗi và khung lỗi chung, không bao giờ 302) và điều hướng trình duyệt (luồng đăng nhập của Spring, 302). `fetch()` mặc định gửi `*/*` nên coi `*/*` là HTML làm lời gọi API bị chuyển hướng sang host khác thành lỗi CORS. Đăng xuất là cầu nối duy nhất: fetch nhưng trả 202 kèm `Location` để SPA tự điều hướng và mang cookie của host `auth`.
- **Lấy mã truy vết từ ngữ cảnh quan sát của yêu cầu, không từ MDC của luồng**, vì ở WebFlux ghi log có thể xảy ra trên luồng khác. Dòng log truy cập không được phụ thuộc vào việc quan sát có bật hay không: thử `ObservationHandler` thì tắt quan sát là mất hết dòng log, nên giữ bộ lọc.
- **`timeout` không đủ cho Redis ở Reactor.** Lettuce có thể chặn luồng gọi lúc lấy kết nối, phải `subscribeOn(boundedElastic)` thì thời gian chờ mới có tác dụng; lỗi kết nối báo muộn sau khi đã hết giờ phải được xử lý để không thành ERROR "onErrorDropped".
- **Chạy thật dọn dữ liệu thử ở cả ba nơi** (Postgres dev, Redis dev, Kafka không dọn được): ghi lại sự kiện đã đẩy để lần sau biết.
- **Cấu hình có vẻ đúng nhưng bị bỏ qua âm thầm.** Hạn refresh token ở feature 02 có unit test đúng và cấu hình đúng, nhưng chạy thật mới thấy cột `refresh_token_expires_at` ghi 24 giờ: Spring Authorization Server tra bộ sinh token bằng `getIfUnique()`, có hai bean cùng kiểu thì nó dùng bộ sinh mặc định mà không báo gì. Hai bài học: test đơn vị của một thành phần không chứng minh thành phần đó được nối vào; và khi bean có thể bị bỏ qua thì test tích hợp phải kiểm kết quả cuối (giá trị lưu trong cơ sở dữ liệu), không kiểm thành phần. Đã sửa bằng `@Primary` kèm test.
- **Đặt điểm vào xác thực tường minh thì thay toàn bộ mặc định.** Trong `ExceptionHandlingConfigurer`, `authenticationEntryPoint(...)` thay hẳn các điểm vào mà `formLogin`, `oauth2Login` tự đăng ký, còn `defaultAuthenticationEntryPointFor` chỉ thêm một dòng vào bảng và điểm vào dự phòng là dòng đầu (chuyển hướng đăng nhập) — đó là lý do người gọi JSON hay `*/*` bị chuyển hướng ở `oauth2-service`. Bảng hành vi lỗi dạng (yêu cầu, `Accept`) → kết quả chạy trong một test duyệt mọi hàng và báo cùng lúc các hàng sai (`ErrorBehaviorTest`) cho thấy cả bảng, không dừng ở hàng sai đầu tiên.
- **Nợ đã giao từ feature 02 được trả đúng lúc:** PKCE của `storefront-web-bff` từng tắt "cho tới feature 06"; tới bước đóng mới nhớ ra và bật lại. Việc ghi "bật lại khi làm feature X" ở một tài liệu khác cần có chỗ ở danh sách việc của X, không chỉ nằm trong ghi chú của nơi sinh ra nó.
- **Còn nợ:** `06-access-gateway/deferred.md` (chín phần hoãn); `global/technical-debt.md` mục 3 (hạn refresh token, hành vi lỗi của `oauth2-service` và tài liệu đăng xuất BFF đã sửa); `api-gateway/service.md` TD-07 (giao diện chết thì cổng trả 500). ADR-0005 vẫn Proposed (đã Accepted `2026-10-21`) (chờ giao diện gửi đúng `X-XSRF-TOKEN`, feature 05).

### Feature 07 (Truy vết lỗi), cài xong `2026-10-09`

- **Kiểm nền kỹ thuật trước đã tránh được hai lỗi im lặng.** `EnvironmentPostProcessor` ở Boot 4 phải đăng ký bằng `META-INF/spring.factories`
  (file `.imports` cũ bị bỏ qua, mặc định không bao giờ được đặt); `logback-spring.xml` tự khai báo bộ ghi chữ làm tắt log có cấu trúc.
- **Một dòng hoàn tất cho mỗi yêu cầu**, nội dung yêu cầu chỉ khi lỗi và chỉ qua danh sách trường được phép; tên trường nhạy cảm bị che ở mọi độ sâu,
  và nội dung hỏng không bao giờ ghi nguyên văn (từng để lọt mật khẩu qua đường lui dạng chữ, đã sửa và có test).
- **Mã lỗi đi bằng thuộc tính của yêu cầu**, không qua MDC, để không rò sang các dòng log khác.
- **Test đọc đầu ra JSON thật** (`RequestLoggingIntegrationTest`) thay cho kiểm bằng mắt; log bất đồng bộ nên test chờ có giới hạn.
- **Còn nợ:** đo overhead (trần nền đã có từ feature 08); `@Async` tự truyền ngữ cảnh chưa kiểm; các dịch vụ khác chưa chuyển sang `observability-logback.xml`;
  `client.ip` đã đúng nhờ cổng truy cập (06, `X-Client-Ip`); `user.id` cho yêu cầu đã xác thực chờ đăng nhập (02), việc đã giao ở `02-login-session/flow.md`.

- **Cập nhật `2026-10-21` (từ feature 09): khe rò mật khẩu vào log đã đóng.** Nội dung là chuỗi JSON hợp lệ (JSON mã hóa hai lần) từng lọt mật khẩu vào log ở dòng hoàn tất của yêu cầu và ở thông báo của `GlobalExceptionHandler`. `BodySanitizer` giờ chỉ ghi kích thước khi có danh sách trường được phép mà nội dung không phải đối tượng, che cặp `tên nhạy cảm: giá trị` trong giá trị chuỗi; `GlobalExceptionHandler` chỉ ghi tên loại lỗi của bộ phân tích. Thông báo của bộ phân tích bên thứ ba có thể chứa dữ liệu của người dùng nên không bao giờ vào log.

### Feature 08 (Bộ đo nền), đóng `2026-10-20`

- **Số đo thay giả định, và giả định sai.** "Khoảng 80 ms mỗi lần băm" (feature 01) cao hơn thực đo 45 đến 65 ms; số lõi cần cho 700 đăng nhập/giây từ "khoảng 56" thành khoảng 25 nhân vật lý (Suy ra). Kết luận cũ (không cam kết flash sale trên một máy) vẫn đúng, nhưng lý do giờ có số.
- **Thử ngắn trước khi chạy dài tìm ra nút cổ chai của chính bộ đo.** Lần thử 24 giây cho thấy k6 4 CPU chạm 391/400% khi khung mới 320%, tức số đo là trần của bộ sinh tải. Tăng k6 lên 8 CPU và **giữ khung cố định 4 CPU** (cơ sở so sánh với dịch vụ thật) thay vì chia lại nhân. Cách nhận biết: lấy CPU của cả bộ sinh tải và hệ thống bị đo ở giữa lượt (`docker stats`).
- **Trần không phải thông lượng ở bậc cao nhất đạt ngưỡng.** Quá 100 kết nối thông lượng của điểm cuối rỗng giảm (65,4 nghìn xuống 51,3 nghìn ở 1.600) trong khi P99 tăng; chỉ "bậc cao nhất còn dưới ngưỡng" sẽ chọn nhầm mức đã quá tải. Quy tắc làm rõ: thông lượng cao nhất trong các bậc dưới ngưỡng, kèm điểm gãy độ trễ.
- **Một giao dịch ghi tuần tự tốn khoảng 1,1 ms, chủ yếu là `fsync`; nhiều kết nối gộp chung một lần ghi đĩa.** 1 lên 16 kết nối tăng 8,6 lần, sau đó nghẽn chuyển từ đĩa sang CPU của Postgres (chạm 400%). Muốn ghi nhanh hơn trên máy này thì thêm CPU cho Postgres.
- **Cô lập CPU bằng Docker là theo hạn mức, không phải gán cứng nhân vật lý** (máy ảo WSL2). Khung chạy trong container để ba thành phần cùng có hạn mức; hệ quả là số đo của khung trong container, và dịch vụ thật phải đo cùng điều kiện mới so được.
- **Làm nóng, lặp ba lần, khởi động lại giữa các lần** đủ để độ lệch ở 8 và 16 luồng, ở 100 và 400 kết nối, và ở mọi mức ghi nằm dưới 3 đến 5%. Chỉ cấu hình 1 và 4 luồng băm dao động tới 10 đến 15% (xung nhịp tăng tốc ở gói Balanced), nên chỉ dùng xem hình dạng đường cong.
- **Ngưỡng khóa trước khi thấy kết quả** (50 ms, 100 ms) để không chỉnh theo đường cong; chỉ làm rõ cách đọc, không đổi số.
- **Trần máy xách tay phụ thuộc gói điện.** Đo ở Balanced (có cắm sạc) vì đổi gói điện là cài đặt của người dùng; ghi thành phần hoãn thay vì đổi âm thầm.
- **Còn nợ:** `08-baseline-bench/deferred.md` (bốn phần hoãn); các phép đo ứng dụng của feature 01, 02, 07 giờ có trần để so và là việc của từng feature. `bench/` chạy lại được bằng ba script (xem `bench/README.md`).

### Feature 09 (Kiểm đầu-cuối cả chặng), đóng `2026-10-21`

- **Chạy ghép từ trạng thái sạch bắt được lỗi mà từng feature riêng không thấy.** Hai lỗi ở chỗ nối: (1) mật khẩu vào log khi nội dung yêu cầu là một chuỗi JSON hợp lệ (JSON mã hóa hai lần): `BodySanitizer` che theo tên trường mà chuỗi trơn không có tên, và Jackson đưa giá trị chuỗi vào thông báo lỗi mà `GlobalExceptionHandler` ghi nguyên văn; (2) mức giới hạn chung của cổng cộng dồn giữa hai lần chạy liền nhau, vì `global-setup` ghi "số đầu" của Redis sau khi các yêu cầu kiểm tra sẵn sàng đã tạo khóa giới hạn. Cả hai đều có test đỏ trước rồi mới sửa.
- **Lỗi thứ nhất lộ ra do một bất ngờ của công cụ test, không do kế hoạch.** Gửi `data: "<chuỗi>"` với `content-type: application/json` làm Playwright mã hóa chuỗi thành chuỗi JSON hợp lệ; JSON hỏng thật phải gửi bằng `Buffer`. Khi một test đỏ vì lý do không dự kiến thì điều tra trước khi sửa test: ở đây đó là một khe thật.
- **Đo thời gian phải xen kẽ hai nhánh và lấy nhiều lần.** Đo hết nhánh này rồi mới đo nhánh kia cho chênh giả khoảng 75 ms (gần một lần băm) và suýt bị đọc thành lỗ hổng dò email; xen kẽ 20 lần mỗi nhánh cho chênh trong ±6 ms. Đo qua trình duyệt quá nhiễu (khoảng 266 đến 525 ms mỗi lần), phải gửi thẳng form qua cổng. Ngưỡng 30 ms chọn sau phép đo hiệu chỉnh và kiểm bằng phá thử.
- **Phá thử bằng cấu hình có thể không đỏ vì phá sai, không phải vì test vô dụng.** Ba cách tắt hoặc đổi cách gửi vết ở cổng không làm KT-23 đỏ; chỉ khi Xác thực chỉ nhận B3 thì vết đứt và test đỏ. Ghi cả cách phá không hiệu lực, không tính nó là phá thử đạt. Bỏ băm giả chỉ làm test thời gian đỏ, test nhìn thấy vẫn xanh: cần cả hai.
- **Hai luật của cùng một cổng có thể loại trừ nhau trong một lần chạy** (đăng ký 10 mỗi giờ so với bộ thật đăng ký hàng chục tài khoản): chia hai pha, khởi động lại riêng cổng giữa hai pha, test của pha B tự bỏ qua ở pha A.
- **Script dựng chuỗi chạy nền phải tách khỏi ống dẫn của người gọi.** `( cd dir && nohup cmd > file & )` vẫn giữ đầu ghi của `| tail` vì chỉ `nohup` được chuyển hướng, nhóm bao ngoài thì không; treo hơn 9 phút. Thêm `>/dev/null 2>&1` ngoài mỗi nhóm.
- **Jar đóng gói có thể cũ hơn mã** (hai trong ba dịch vụ có tệp nguồn mới hơn jar): `up.sh` luôn đóng gói lại trước khi chạy, nên một lần chạy cuối chặng kiểm đúng mã hiện tại.
- **Bảng truy vết buộc nói thật.** Hai bất biến chứng minh chưa trọn qua cổng (INV-ATH-02: chưa quét bí mật toàn bộ; INV-ATH-08: chưa có hết hạn thật qua cổng) ghi thành phần hoãn có lý do thay vì ghi "đã chứng minh".
- **Còn nợ:** `09-e2e-check/deferred.md` (hai phần hoãn); ADR-0005 đã Accepted `2026-10-21`; ADR-0003 còn Proposed chờ phép đo làm mới token (`02-login-session/plan.md` bước 14); ADR-0004 còn Proposed chờ chặng cần mã một lần (C3c).

### Tổng kết chặng C0, đóng `2026-10-21`

Chín feature (01 đến 09) đều xong. Phần này gom điều học được xuyên suốt, không lặp từng feature ở trên.

**Điều kiện "Xong khi" của roadmap C0 (`7-roadmap.md`):**

| Điều kiện | Bằng chứng |
|---|---|
| Đăng ký, đăng nhập, đăng xuất qua giao diện | S1 qua cổng bằng Chrome thật (KT-09), người thử tay S1 và S4; `scenarios/run.sh` đạt hai lần liên tiếp |
| Một yêu cầu lỗi lần ra được nguyên nhân từ log | KT-23 (`09-e2e-check/tech.md` T4): `traceId` trong phản hồi tìm được ở log của cả cổng lẫn Xác thực; phá thử bằng đứt vết thì đỏ |
| Có số trần máy cho điểm cuối rỗng, ghi cơ sở dữ liệu, băm mật khẩu, ghi cùng điều kiện đo | Khoảng 65.000 yêu cầu/giây, 11.000 giao dịch/giây, 227 lần băm/giây; điều kiện ở `08-baseline-bench/tech.md` T4 đến T7 và bảng NFR 4.1 |

**Điều học được xuyên suốt:**

- **Mỗi lần chạy thật đều tìm ra ít nhất một lỗi mà toàn bộ test xanh không thấy**, ở các feature 02, 03, 05, 06 và 09: hạn session 30 phút so với 30 ngày (02), bộ báo BFF so nhầm cột (03), `ng serve` chỉ nghe `::1` (05), phiên BFF còn trong Redis sau đăng xuất (06), mật khẩu vào log (09). Chạy thật không phải thủ tục cuối: nó là phép kiểm duy nhất cho chỗ nối.
- **Test của một thành phần không chứng minh thành phần đó được nối vào.** Bộ sinh refresh token đúng mà bị bỏ qua vì hai bean cùng kiểu (06); bộ báo BFF đúng mà giả ở lớp chứa logic (03). Giả ở biên ngoài cùng; kiểm kết quả cuối (giá trị trong cơ sở dữ liệu, dòng trong log), không kiểm thành phần.
- **Phá thử là một phần của việc viết test, và cách phá cũng có thể sai.** Test không đỏ khi mã bị phá thì chưa chứng minh gì (05 KT-13 xanh vì lý do sai, 06 hai phá thử đầu không đỏ); ngược lại ba cách tắt vết ở cổng không làm KT-23 đỏ vì phá chưa đúng chỗ (09). Ghi cả cách phá không hiệu lực.
- **Giả định của framework phải đọc mã hoặc đo, không suy luận.** Chuỗi đăng xuất mặc định của WebFlux chỉ xóa ngữ cảnh chứ không xóa phiên (06, đo bằng `MONITOR` Redis và `javap`); điểm cuối đăng xuất của Spring hỏng hoàn toàn từ feature 02 (03, thử trước khi thiết kế); `getIfUnique()` bỏ qua bean (06). Số đo thay giả định cả với số của chính mình: "80 ms mỗi lần băm" thực đo 45 đến 65 ms (08).
- **Bảo mật dữ liệu trong log cần được kiểm từ ngoài vào.** Test đơn vị của bộ làm sạch nội dung không thấy dạng nội dung bất ngờ (chuỗi JSON mã hóa hai lần); chỉ yêu cầu thật qua cổng với một mật khẩu có dấu riêng, tìm lại trong file log, mới lộ khe (07 sửa ở 09). Thông báo lỗi của thư viện bên thứ ba có thể chứa dữ liệu người dùng nên không được ghi nguyên văn.
- **Đo lường: xác định ai nghẽn trước, rồi xen kẽ.** Bộ sinh tải 4 CPU nghẽn trước hệ thống bị đo (08); đo nhánh này xong mới đo nhánh kia cho chênh giả bằng cỡ một lần băm (09). Thông lượng cao nhất khác thông lượng ở bậc cuối còn trong ngưỡng, vì quá tải làm thông lượng giảm (08).
- **Dữ liệu thử phải đếm trước và sau, không dọn theo ước chừng.** Kiểm số dòng bảy bảng và khóa Redis (05, 09) bắt được chỗ dọn theo tiền tố bỏ sót; xóa không điều kiện bị chặn; tài khoản của người dùng giữ nguyên.
- **Quyết định ngoài phạm vi không biến thành phần hoãn** (01, 05, 08): `deferred.md` chỉ ghi phần đã có chỗ trong phạm vi nhưng làm đơn giản, kèm điểm nối để quay lại.
- **Tài liệu theo thứ tự `analysis.md` rồi `service.md` rồi mã**, và ADR chỉ đổi trạng thái khi tiêu chí ghi trong ADR đạt: đến cuối chặng ADR-0005 và 0007 Accepted, ADR-0003 chờ phép đo, ADR-0004 chờ chặng cần mã một lần. Rà ADR còn Proposed lúc đóng chặng tìm ra hai chỗ ADR nói khác mã (`fillfactor`, `revoke`/`introspect`) mà đọc riêng ADR hay riêng mã đều không thấy.

**Còn nợ khi sang chặng sau:**

- **Phép đo ứng dụng** của đăng ký (01 bước 11), đăng nhập (02 bước 14) và overhead của log (07 bước 11), đều đã có trần máy để so; mở khóa ADR-0003 sang Accepted và cho biết `fillfactor` 70 có ích không.
- **Phần hoãn của từng feature** (các `deferred.md`), `global/technical-debt.md` mục 3, hai phần hoãn của 09 (hết hạn phiên thật qua cổng, quét bí mật toàn bộ), bốn của 08, bốn của 05, chín của 06.
- **Hạ tầng dev dùng chung:** các bộ thật ghi vào Postgres, Redis, Kafka dev; Kafka không dọn được nên sự kiện đã đẩy ở lại.
- **Chưa ghi:** mục tiêu hiểu của frontend ("cơ chế phát hiện thay đổi, vòng đời, quản lý đăng ký sự kiện", `stage.md` mục 6 và roadmap C0) chưa có nội dung ở phần của feature 05. Cần người viết lại điều đã hiểu, không điền thay.
- **ADR:** 0003 (chờ phép đo) và 0004 (chờ C3c) còn Proposed.
