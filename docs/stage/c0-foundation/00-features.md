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
| 02 | Đăng nhập và phiên | Nghiệp vụ | CAP-ATH-02, 03, 05 | S1, S3, S4 · INV-ATH-03, 06, 08, 10 | 01, 04 | Xong `2026-10-16` (bước 1 đến 13, 15, 16; 275 test); nợ: đo NFR (chờ 08); nay đã chạy qua cổng (06). Gồm phiên hai tầng, token cắt hạn, làm mới token, cookie `DEVICE_KEY`, event ghi trong miền, CSRF, PKCE, giới hạn số lần thử, đăng nhập chung; ba ADR còn Proposed |
| 03 | Đăng xuất | Nghiệp vụ | CAP-ATH-04 | S1, S5 · INV-ATH-05, 08 | 02 | Xong `2026-10-17` (điểm cuối đăng xuất OIDC chạy trên miền phiên mới, khóa hàng chống hồi sinh phiên, báo BFF theo `sid`, gỡ mô hình phiên cũ; 323 test, đã chạy thật). Nợ: hàng `client_credentials` tích dần mỗi lần báo; xem `03-logout/deferred.md` |
| 04 | Quản trị có sẵn | Dữ liệu khởi tạo | Dữ liệu khởi tạo | S4 · INV-ATH-01, 02, 06, 07, 11 | không | Xong `2026-10-17` (script SQL chạy tay `infra/seed/admin.sql`, ngoài Flyway; 10 test script, 2 test đăng nhập Quản trị, đã chạy thật vào cơ sở dữ liệu dev rồi dọn). Nợ: không có riêng; nửa S4 qua giao diện ở 05; xem `04-seed-admin/deferred.md` |
| 05 | Khung giao diện | Giao diện | Roadmap C0, `5-ui-analysis.md` | S1, S4 (nhìn thấy qua giao diện); gửi header CSRF (`X-XSRF-TOKEN`) cho mọi yêu cầu thay đổi dữ liệu qua BFF (ADR-0005) | 01, 02, 03, 06 | Xong `2026-10-20` (`05-ui-shell/flow.md`, `plan.md`): viết lại thành dự án mới `frontends/storefront-web` (4 màn: Trang chủ, Đăng ký, Đăng nhập, Tài khoản; 166 test Jest, bộ Playwright thật 11/11 xanh hai lần liên tiếp); người thử tay S1, S4 bằng Chrome qua cổng; ba phần hoãn ở `05-ui-shell/deferred.md` |
| 06 | Cổng truy cập | Kỹ thuật | Roadmap C0, `rate-limiting-layers.md`, ADR-0006 | Mọi kịch bản đi qua cổng; giới hạn tần suất của đăng ký trả 429; CSRF phía BFF (cookie `XSRF-TOKEN`, thiếu hoặc sai mã thì 403); BFF gửi `sid` khi làm mới token (ADR-0003 mục 8); BFF đọc claim `sid` để ánh xạ phiên (đã thay `oss_id` cũ) | không | Xong `2026-10-18` (định tuyến theo `Host` ADR-0006 Accepted, giới hạn tần suất theo đường dẫn và mức chung, CSRF ở BFF, làm mới token kèm `sid` có tuần tự hóa, ánh xạ phiên theo `sid`, địa chỉ tin cậy và log hoàn tất mỗi yêu cầu, hành vi lỗi hai loại người gọi; `storefront-web-bff` 51, `api-gateway` 28, `observability-starter` 67, `reactive-rate-limiter-starter` 8 test; đã chạy thật qua ba dịch vụ và Chrome). Nợ: xem `06-access-gateway/deferred.md` và `global/technical-debt.md` mục 3 |
| 07 | Truy vết lỗi | Kỹ thuật | Roadmap C0, NFR | Một yêu cầu lỗi lần ra nguyên nhân từ log | 06 | Đã cài `2026-10-09`; nợ: đo overhead (chờ 08), `@Async` chưa kiểm, dịch vụ khác chưa chuyển cấu hình log (xem `07-error-tracing/plan.md`) |
| 08 | Bộ đo nền | Kỹ thuật | NFR 4.1, roadmap C0 | Số trần máy cho điểm cuối rỗng, ghi cơ sở dữ liệu, băm mật khẩu | 06 | Chưa bắt đầu |
| 09 | Kiểm đầu-cuối cả chặng | Kỹ thuật | Roadmap C0, `stage.md` mục 4 | Chạy hết S1 đến S5 qua cổng trên ứng dụng thật | 01 đến 08 | Chưa bắt đầu |

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
- **Còn nợ:** phép đo tải của đăng ký (chờ feature 08); các cột và trạng thái tạm trong `user_accounts` (xem `data.md`).
  Giao diện đăng ký (gửi trường thừa, đọc mã lỗi `code`) thuộc feature 05. JSON hỏng trả 400 `MALFORMED_REQUEST` (feature 07).

### Feature 02 (Đăng nhập và phiên), đóng `2026-10-16`

- **Chạy thật vào ứng dụng đã bắt được lỗi mà test không thấy.** Hạn session Spring chỉ 30 phút dù cookie sống 30 ngày (`@EnableJdbcHttpSession` bỏ qua `server.servlet.session.timeout`); chuỗi `/api/**` nhận cả cookie phiên khi CSRF tắt; độ lệch đồng hồ 60 giây của bộ giải mã JWT. Cả ba đã có test đầu-cuối đo trực tiếp (bảng `spring_session`, mã trạng thái, thời điểm hết hạn).
- **Nhãn "xong" của tài liệu phải kiểm bằng số đo.** ADR-0003 và `tech.md` từng ghi hạn session Spring bằng hạn phiên lớn nhất nhưng không có dòng mã nào làm vậy; chỉ thấy khi đọc hàng `SPRING_SESSION`.
- **Token giả không thay được token thật.** Công cụ cấp token giả (`/dev/token`) đã làm rồi bỏ vì không có phiên thật (`sid` ngẫu nhiên, không làm mới được). Thay bằng móc `IssuedTokenListener` ghi token thật ra log, chỉ ở profile `dev` kèm cờ riêng.
- **Phân gói theo đối tượng được sửa đổi.** Lớp nào sửa hành vi của Spring (bộ lọc, bộ xử lý, bộ tùy biến) vào `extension`; phương thức xác thực (MFA, liên kết) là ngoại lệ ngữ nghĩa. Chuyển gói để lại tệp `.class` cũ trong `target` gây lỗi bean trùng, phải xóa `target/classes`.
- **Hai khóa giới hạn đăng nhập** (cặp email và địa chỉ mạng; riêng email) đều băm SHA-256; khóa theo cặp kiểm trước và chỉ khi không chặn mới đếm khóa email. Redis lỗi thì mở cửa kèm cảnh báo (đóng cửa sẽ khóa cả đăng nhập).
- **Còn nợ:** đo NFR đăng nhập (chờ feature 08); bản ghi ủy quyền mồ côi chờ job dọn (Lập lịch, C2b; chưa có use case dọn); BFF gửi `sid` khi làm mới và CSRF đã trả ở feature 06; hạn refresh token ở dev không theo cấu hình, đã sửa ở bước điều tra sau feature 06 (`global/technical-debt.md` mục 3); đăng xuất, `MeController` và mô hình phiên cũ (feature 03); ba ADR 0003 đến 0005 còn Proposed, chuyển Accepted sau khi đo.

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
- **Còn nợ:** `05-ui-shell/deferred.md` (bốn phần hoãn); ADR-0005 vẫn Proposed, nay giao diện đã gửi `X-XSRF-TOKEN` và được kiểm bằng bộ thật nên có thể xem xét chuyển Accepted; mặc định `SHOP_FRONTEND_URL` của cổng vẫn là cổng 4200 của `web/` (xem README `frontends/`).

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
- **Còn nợ:** `06-access-gateway/deferred.md` (chín phần hoãn); `global/technical-debt.md` mục 3 (hạn refresh token, hành vi lỗi của `oauth2-service` và tài liệu đăng xuất BFF đã sửa); `api-gateway/service.md` TD-07 (giao diện chết thì cổng trả 500). ADR-0005 vẫn Proposed (chờ giao diện gửi đúng `X-XSRF-TOKEN`, feature 05).

### Feature 07 (Truy vết lỗi), cài xong `2026-10-09`

- **Kiểm nền kỹ thuật trước đã tránh được hai lỗi im lặng.** `EnvironmentPostProcessor` ở Boot 4 phải đăng ký bằng `META-INF/spring.factories`
  (file `.imports` cũ bị bỏ qua, mặc định không bao giờ được đặt); `logback-spring.xml` tự khai báo bộ ghi chữ làm tắt log có cấu trúc.
- **Một dòng hoàn tất cho mỗi yêu cầu**, nội dung yêu cầu chỉ khi lỗi và chỉ qua danh sách trường được phép; tên trường nhạy cảm bị che ở mọi độ sâu,
  và nội dung hỏng không bao giờ ghi nguyên văn (từng để lọt mật khẩu qua đường lui dạng chữ, đã sửa và có test).
- **Mã lỗi đi bằng thuộc tính của yêu cầu**, không qua MDC, để không rò sang các dòng log khác.
- **Test đọc đầu ra JSON thật** (`RequestLoggingIntegrationTest`) thay cho kiểm bằng mắt; log bất đồng bộ nên test chờ có giới hạn.
- **Còn nợ:** đo overhead (chờ feature 08); `@Async` tự truyền ngữ cảnh chưa kiểm; các dịch vụ khác chưa chuyển sang `observability-logback.xml`;
  `client.ip` đã đúng nhờ cổng truy cập (06, `X-Client-Ip`); `user.id` cho yêu cầu đã xác thực chờ đăng nhập (02), việc đã giao ở `02-login-session/flow.md`.
