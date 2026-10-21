# Feature 09: giải pháp kỹ thuật và ghi nhận từng bước

## T1. Khảo sát hiện trạng (bước 1, `2026-10-20`)

### T1.1. Số đầu của dữ liệu dùng chung (dev)

Đọc bằng truy vấn chỉ-đọc ngày `2026-10-20`, không dịch vụ nào đang chạy. Bộ thật của 05 (`global-setup.ts`) tự ghi số đầu mỗi lần chạy và dọn về đúng số đó; bảng dưới là trạng thái nền hiện tại để đối chiếu.

| Nơi | Giá trị |
|---|---|
| `oauth2_db.user_accounts` | 1 (`ducthinh@dev.aplus.vn`, giữ nguyên) |
| `login_sessions`, `client_sessions`, `oauth2_authorization`, `oauth2_authorization_consent`, `spring_session` | 0 |
| `outbox_events` | 3 (từ tháng 9, không đụng) |
| `oauth2_registered_client`, `rsa_key_pairs`, `flyway_schema_history` | thống kê `n_live_tup` báo 0 (số ước lượng của Postgres, không tin cho phép so sánh; bộ thật đếm bằng `count(*)`) |
| Redis | 1 khóa: `{inventory:sold-out:bloom}:config` (có sẵn từ trước) |
| Khóa giới hạn tần suất của cổng | không có lúc này; tạo khi có yêu cầu: `ratelimit:rule:<tên luật>:<địa chỉ>` (luật riêng, ví dụ `ratelimit:rule:register:<ip>`) và `ratelimit:ip:<địa chỉ>` (mức chung) |

### T1.2. Bộ thật hiện có (`frontends/e2e-real`)

- 11 test liệt kê được (`playwright test -c playwright.real.config.ts --list`): KT-09 (S1), KT-10 (S2), KT-11 (S4), KT-12 (S5), KT-13, 14, 15, 16 (hai bản), 17, 20. **Không có test nào cho S3.** Chưa chạy lại ở bước này (cần cả chuỗi dịch vụ, làm ở bước 2 và 6).
- `global-setup.ts` đã có: kiểm cổng và Xác thực lên, từ chối chạy nếu còn tài khoản `e2e-*` của lần trước, ghi số dòng bảy bảng (`user_accounts`, `login_sessions`, `client_sessions`, `oauth2_authorization`, `oauth2_authorization_consent`, `spring_session`, `outbox_events`) và các khóa Redis đang có, seed Quản trị thử, rồi sau cùng dọn và **báo lỗi nếu số dòng lệch**. Redis được dọn bằng cách xóa mọi khóa mới so với lúc đầu, nên khóa giới hạn tần suất do test tạo ra cũng được dọn.
- Chạy một worker, tuần tự, Chrome thật (`channel: 'chrome'`), nền `http://shop.localhost:8000`.
- KT-15 mở cổng `8002` (customer-service giả); cổng phải trống.

### T1.3. Phát hiện ảnh hưởng tới thiết kế

| # | Phát hiện | Hệ quả |
|---|---|---|
| 1 | **Luật đăng ký 10 yêu cầu/giờ mâu thuẫn với bộ thật.** Bộ thật của 05 chạy cổng với luật nâng lên 100.000 (`frontends/README.md`, phải khai báo đủ bảy trường của luật). Kiểm 429 cần luật thật (10) | Hai pha: pha A cổng luật nâng cho S1 đến S5 và truy vết; pha B **khởi động lại cổng với luật mặc định** (10/giờ) cho kiểm 429, rồi dọn khóa. Thay cho "chạy 429 cuối cùng cùng cổng" ở `flow.md` mục 3 và 4 |
| 2 | **Đường dẫn log phụ thuộc thư mục chạy.** File log JSON nằm ở `logs/oauth2-service.json` tính từ thư mục làm việc của tiến trình: chạy ở `services/` ra `services/logs/`, chạy ở `services/oauth2-service/` ra `services/oauth2-service/logs/` (hai nơi cùng tồn tại, cũ và mới) | `up.sh` phải đặt thư mục làm việc cố định (hoặc `logging.file.name`) để kiểm truy vết biết đọc file nào. Chọn chạy ở `services/` (nơi có log mới nhất) |
| 3 | **Jar cũ hơn mã.** `oauth2-service` (`UserAccountDetails.java`) và `storefront-web-bff` (`application.properties`) có tệp nguồn mới hơn jar đóng gói lúc 09:18 hôm nay. `api-gateway` thì khớp | `up.sh` luôn đóng gói lại ba dịch vụ trước khi chạy (không dựa vào jar có sẵn), để kiểm đúng mã hiện tại |
| 4 | **Cổng cần trống:** 8000 (cổng), 8004 (Xác thực), 8090 (BFF), 4201 (giao diện), 8002 (KT-15). Lúc khảo sát cả năm đều trống | `up.sh` kiểm trước và dừng nếu bận, nêu cổng nào |
| 5 | **Có hai tiến trình `java` đang chạy** không thuộc chuỗi (không chiếm cổng nào trong danh sách trên) | `down.sh` không được dừng "mọi `java`": dừng đúng tiến trình theo mã đã ghi khi dựng |
| 6 | **Thời gian của S3 cần mốc tham chiếu.** Trần băm hệ số 10 đo ở feature 08 cho một lần băm P50 45 đến 65 ms (`08-baseline-bench/tech.md` T5) | Ngưỡng "cùng bậc" của S3 chọn rộng (chênh dưới khoảng 100 ms giữa hai lần), hợp với độ lệch khi chạy thật; chốt ở bước 3 sau khi thấy số thật |

### T1.4. Việc giao cho các bước sau

- Bước 2: `up.sh` (đóng gói lại, kiểm cổng, thư mục chạy cố định), `down.sh` (theo mã tiến trình), có hai chế độ cổng (luật nâng, luật mặc định).
- Bước 5: kiểm 429 chạy ở pha B với cổng luật mặc định; cần dọn khóa `ratelimit:rule:register:*` và `ratelimit:ip:*` sau pha.
- Bước 6: `run.sh` điều khiển hai pha và so số dòng cuối pha B với số đầu pha A.

## T2. Script dựng chuỗi (bước 2, `2026-10-20`)

`scenarios/up.sh` và `scenarios/down.sh` (Git Bash). Tiến trình ghi mã vào `scenarios/.run/<tên>.pid`, đầu ra vào `.run/<tên>.out` (thư mục không vào git).

| Lệnh | Việc |
|---|---|
| `up.sh` | Kiểm JDK 21, các cổng 8000, 8004, 8090, 4201, 8002 trống và ba container hạ tầng đang chạy; xóa khóa `ratelimit:*` còn sót trong Redis; đóng gói lại `oauth2-service`, `storefront-web-bff`, `api-gateway`; khởi động bốn tiến trình (Xác thực profile `dev`, BFF, cổng, `npm start`) từ `services/` và `frontends/`; chờ từng cái trả mã mong đợi rồi kiểm cổng chuyển tới BFF (`shop.localhost:8000/bff/auth/session` trả 401) |
| `up.sh --rules default` | Cổng chạy luật mặc định (đăng ký 10/giờ), cho kiểm 429 ở pha B. Mặc định `relaxed`: luật đăng ký nâng lên 100.000 (đủ bảy trường, như `frontends/README.md`) |
| `up.sh --only gateway` | Chỉ khởi động lại cổng (sau `down.sh --only gateway`) |
| `up.sh --no-build` | Không đóng gói lại |
| `down.sh [--only gateway]` | Dừng đúng tiến trình đã ghi mã (`taskkill /T`), chờ cổng trống; không dừng `java` hay `node` khác |

**Đã thử:** `up.sh` (có đóng gói) rồi `down.sh`; sau đó `up.sh --no-build`, `down.sh --only gateway`, `up.sh --only gateway --rules default`, `down.sh`. Mọi lần cả bốn dịch vụ lên và cổng 8000, 8004, 8090, 4201 trống sau `down.sh`. Lần khởi động cổng sau đó xóa đúng 1 khóa giới hạn tần suất do các lần kiểm sức khỏe tạo ra. Sau các lần thử, `oauth2_authorization` 0 dòng, `spring_session` 0, `user_accounts` 1, Redis chỉ còn khóa sẵn có.

**Một lỗi đã gặp và sửa:** `up.sh | tail` treo vì nhóm khởi động nền `( cd ... && nohup ... & )` vẫn giữ đầu ghi của ống dẫn của người gọi (chỉ `nohup` được chuyển hướng, nhóm bao ngoài thì không). Đã thêm `>/dev/null 2>&1` ngoài mỗi nhóm; `run.sh` (bước 6) gọi script qua ống dẫn nên cần điều này.

## T3. S3 qua cổng (bước 3, `2026-10-20`)

Hai test mới ở `frontends/e2e-real/s3.real.spec.ts` (và hàm `failLogin` ở `support/flows.ts`):

| Test | Kiểm | Cách |
|---|---|---|
| KT-21 (S3, nhìn thấy) | Cùng địa chỉ sau chuyển hướng (`/login?error`), cùng thông báo ("Email hoặc mật khẩu không đúng."), không có email hay mật khẩu trên địa chỉ | Trình duyệt thật qua cổng; sai mật khẩu của tài khoản có thật rồi email không tồn tại, mỗi bên 3 lần (dưới giới hạn 5 lần mỗi cặp email và địa chỉ mạng) |
| KT-22 (S3, thời gian) | Hai nhánh tốn thời gian cùng bậc (một lần băm mỗi bên); trung vị lệch dưới 30 ms | Gửi thẳng form `POST /login` qua cổng bằng API (kèm mã CSRF lấy từ trang đăng nhập), **xen kẽ** hai nhánh, 20 lần mỗi nhánh (5 tài khoản thật × 4 vòng, 20 email lạ), bỏ 2 lần đầu làm nóng; đăng ký tài khoản thử bằng API |

### Phép đo hiệu chỉnh ngưỡng thời gian

| Cách đo | Kết quả | Kết luận |
|---|---|---|
| Qua trình duyệt, 3 lần mỗi nhánh, nhánh này xong rồi mới tới nhánh kia | trung vị 401 và 349 ms (lệch 52) | Nhiễu của trình duyệt dựng trang lớn hơn chênh cần đo; n nhỏ |
| Qua trình duyệt, 12 lần mỗi nhánh | trung vị 380 và 389 (lệch −9), khoảng 266 đến 525 ms | Vẫn quá nhiễu để phát hiện chênh bằng một lần băm (45 đến 80 ms) |
| API, 4 lần mỗi nhánh, **không xen kẽ** (xong nhánh này mới tới nhánh kia) | khoảng 205 và 130 ms | **Sai lầm đo đã gặp:** đọc nhầm thành "nhánh có tài khoản chậm hơn một lần băm". Log phía dịch vụ cho số ngược lại ở lần đo sau (nhánh email lạ chậm hơn) |
| API, **xen kẽ**, 20 lần mỗi nhánh, hai lượt | lệch −4 và +6 ms | Không có chênh hệ thống. Chốt phương pháp này; ngưỡng 30 ms rộng hơn nhiễu quan sát (±6 ms) và hẹp hơn chênh khi mất băm giả |

### Phá thử (đã làm, mã đã hoàn tác bằng `git checkout`, jar đóng gói lại từ mã gốc)

| Phá | KT-21 | KT-22 |
|---|---|---|
| Bỏ băm giả ở nhánh email lạ (`AuthenticateWithPassword`, dòng `passwordHasher.verify(command.password(), dummyHash)`) | xanh (hành vi nhìn thấy không đổi) | **đỏ**: trung vị 136 ms (sai mật khẩu) và 38 ms (sai email), lệch 98 ms |
| Nhánh email lạ trả `accountNotActive` thay `invalidCredentials` (về `?locked`) | **đỏ**: địa chỉ `?locked` khác `?error` | **đỏ**: địa chỉ khác |

Bỏ băm giả chỉ KT-22 bắt được; KT-21 không thấy. Nên cả hai test đều cần: KT-21 giữ phần nhìn thấy, KT-22 giữ thời gian.

### Hai điều khác

- **Mức chung của cổng (300 yêu cầu mỗi phút mỗi địa chỉ) bị cộng dồn.** Bộ thật nạp cả tệp của giao diện qua cổng, nên vài lần chạy liền nhau trong một phút làm yêu cầu thứ n trả 429. Gặp khi `GET /login` của KT-22 trả 429 ở lần chạy thứ ba liên tiếp. Nguyên nhân: `global-setup` ghi số đầu của Redis **sau khi** các yêu cầu kiểm tra sẵn sàng tạo khóa `ratelimit:ip:`, rồi không bao giờ dọn khóa "đã có từ đầu" đó. Sửa: `global-setup` xóa khóa tiền tố `ratelimit:` trước khi ghi số đầu; bốn lần chạy liền nhau sau đó đều xanh. Cũng giảm số yêu cầu của S3: đăng ký tài khoản thử bằng API và dùng một ngữ cảnh trình duyệt (xóa cookie giữa hai trường hợp) cho KT-21.
- **Đo thời gian phải xen kẽ.** Nhiễu của máy (xung nhịp, tiến trình nền) có thể lớn hơn chênh cần đo và có hướng thay đổi theo thời điểm; đo nhánh này xong mới đo nhánh kia sẽ ra chênh giả.

### Kết quả toàn bộ bộ thật

13 test xanh (11 của feature 05 cộng KT-21 và KT-22), 1,1 phút. Sau chạy: `user_accounts` 1, `oauth2_authorization` 0, `spring_session` 0, Redis chỉ còn khóa sẵn có.

## T4. Truy vết lỗi qua cổng (bước 4, `2026-10-21`)

Hai test ở `frontends/e2e-real/trace.real.spec.ts`, cộng bộ đọc log `support/logs.ts` (đọc `services/logs/<dịch vụ>.json`, chờ có giới hạn vì log ghi bất đồng bộ).

| Test | Kiểm | Kết quả |
|---|---|---|
| KT-23 | Gửi `POST /register` với JSON hỏng thật (thiếu dấu đóng, có mật khẩu bên trong, gửi bằng `Buffer` để Playwright không mã hóa lại) qua cổng: 400 `MALFORMED_REQUEST` kèm `traceId` 32 ký tự hex; dòng `POST /register 400` của `oauth2-service` có đúng `trace.id` đó và `error.code` `MALFORMED_REQUEST`; log của `api-gateway` cũng có đúng `trace.id` đó với `POST /register`; mật khẩu không xuất hiện ở bất kỳ dòng nào của hai file log | Xanh |
| KT-24 | Cùng cách nhưng nội dung là một **chuỗi JSON hợp lệ** chứa JSON đăng ký (JSON bị mã hóa hai lần): mật khẩu không vào log | Đỏ trên mã cũ (đánh dấu `test.fail` để bộ không đỏ); **xanh sau khi sửa** (xem dưới), đã bỏ dấu `test.fail` |

### Phá thử KT-23

| Phá | Kết quả |
|---|---|
| Cổng tắt gửi vết (`management.tracing.enabled=false`), hoặc cổng gửi B3 (`propagation.produce` hoặc `type`) | Test vẫn xanh: các thuộc tính này không làm mất vết chung giữa cổng và Xác thực (Xác thực vẫn nhận được hoặc cổng vẫn gửi W3C). **Không coi là phá thử đạt** |
| `oauth2-service` chỉ nhận B3 (`management.tracing.propagation.consume=B3`), cổng gửi W3C | **Đỏ**: Xác thực tự sinh `trace.id` mới, nên dòng log của cổng không có `trace.id` của phản hồi. Chứng minh phép kiểm "cùng `trace.id` ở hai dịch vụ" thật sự bắt được đứt vết |

### Lỗi tìm ra ở chỗ nối: mật khẩu vào log khi nội dung là chuỗi JSON (thuộc feature 07)

Lần đầu viết KT-23 tôi gửi `data: "<chuỗi>"` kèm `content-type: application/json`; Playwright mã hóa chuỗi đó thành **chuỗi JSON hợp lệ** nên nội dung gửi đi là `"{\"email\":..., \"password\":\"...\"}"`. Test đỏ vì mật khẩu xuất hiện trong log. Điều này lộ ra một khe thật:

| Dạng nội dung | Mật khẩu có vào log? |
|---|---|
| JSON hỏng thật (cụt) | Không (đã có test đơn vị và KT-23) |
| Mảng chứa đối tượng đăng ký | Không |
| Đối tượng có trường thừa chứa giá trị nhạy cảm | Không |
| `password` sai kiểu (đối tượng lồng) | Không |
| **Chuỗi JSON hợp lệ chứa JSON đăng ký** | **Có**, ở hai dòng: dòng hoàn tất của yêu cầu (`http.request`, `http.request.body.content`) và dòng của `GlobalExceptionHandler` (thông báo của Jackson đưa nguyên giá trị chuỗi vào) |

Nguyên nhân: `BodySanitizer` che theo **tên trường** ở mọi độ sâu, mà chuỗi trơn không có tên trường nào để che; Jackson đưa giá trị chuỗi vào thông báo lỗi giải mã. Kịch bản thực tế: phía gọi mã hóa JSON hai lần (lỗi phổ biến). Chưa sửa vì thuộc `observability-starter` và `common-web` của feature 07, đợi người quyết định (các hướng sửa ở phần báo cáo bước 4).

### Đã sửa (cùng ngày, theo quyết định của người)

| Chỗ | Sửa | Test |
|---|---|---|
| `BodySanitizer` (`observability-starter`) | (1) Có danh sách trường được phép mà nội dung phân tích được nhưng không phải đối tượng (chuỗi, số, mảng) thì chỉ ghi kích thước; trước đây phần tử của mảng không qua danh sách. (2) Giá trị chuỗi cũng được che theo cặp `tên nhạy cảm: giá trị` nằm trong chuỗi (JSON mã hóa hai lần ở cả nội dung lẫn một trường lồng) | 3 test mới ở `BodySanitizerTest` (JSON mã hóa hai lần, JSON mã hóa trong chuỗi lồng, nội dung không phải đối tượng có danh sách); đỏ trước sửa, xanh sau sửa; 70 test của `observability-starter` xanh |
| `GlobalExceptionHandler` (`common-web`) | Dòng log `MALFORMED_REQUEST` chỉ ghi tên loại của nguyên nhân gốc, không ghi thông báo của bộ phân tích | 2 test mới ở `GlobalExceptionHandlerTest` (chuỗi JSON chứa mật khẩu; nội dung cụt chứa mật khẩu); trường hợp chuỗi JSON đỏ trước sửa, trường hợp cụt vốn đã an toàn |
| Tài liệu | `global/4.convention/logging.md` mục 6 (danh sách trường được phép với nội dung không phải đối tượng; che trong chuỗi) và mục về `GlobalExceptionHandler` | |

Đã đóng gói lại từ mã mới (`up.sh` dùng `-am`) và chạy thật: KT-23 và KT-24 xanh trên dịch vụ thật. Không chạy `RequestLoggingIntegrationTest` của `oauth2-service` (chạm hạ tầng dev như các test API cũ); kiểm bằng bộ thật thay thế.

## T5. 429 của đăng ký qua cổng (bước 5, `2026-10-21`)

Test KT-25 ở `frontends/e2e-real/ratelimit.real.spec.ts`. Chạy **pha B**: cổng chạy luật mặc định (`scenarios/up.sh --rules default`) và đặt `E2E_GATEWAY_RULES=default`; không đặt biến thì test bị bỏ qua (bộ thường ngày chạy cổng với luật đăng ký nâng lên 100.000).

| Kiểm | Kết quả |
|---|---|
| 10 yêu cầu `POST /register` đầu (nội dung sai cố ý để không tạo tài khoản: luật đếm mọi yêu cầu bất kể kết quả) | Mỗi yêu cầu 400, nằm trong mức |
| Yêu cầu thứ 11 | 429, `Retry-After` là số giây trong khoảng 1 đến 3600, thân `{success:false, code:"TOO_MANY_REQUESTS", message}` |
| Đường dẫn khác cùng host (`/.well-known/openid-configuration`) ngay sau đó | 200: luật chỉ gắn với đường dẫn đăng ký |

**Đã phá thử:** cổng chạy luật nâng (relaxed) mà test vẫn đòi 429: **đỏ** (nhận 400 ở yêu cầu thứ 11). Không đặt biến: test bỏ qua. Chạy hai lần liền nhau đều xanh, vì `global-setup` xóa khóa \`ratelimit:\` trước khi ghi số đầu (T3) và teardown xóa khóa do test tạo ra; sau chạy Redis chỉ còn khóa sẵn có.

**Bộ thật hiện có 16 test:** pha A (cổng luật nâng) 15 test xanh trong khoảng 36 giây và 1 bị bỏ qua (KT-25); KT-25 chạy ở pha B. Dữ liệu dev sau chạy: `user_accounts` 1, `oauth2_authorization` 0, Redis chỉ khóa sẵn có.

## T6. Chạy cả chuỗi từ trạng thái sạch (bước 6, `2026-10-21`)

`scenarios/run.sh [--no-build]`: ghi số đầu (bảy bảng, khóa Redis không kể `ratelimit:`, và tài khoản `ducthinh@dev.aplus.vn`), dừng mọi tiến trình do `up.sh` ghi mã, dựng chuỗi với cổng luật nâng, chạy pha A (cả bộ thật: 15 test, KT-25 bỏ qua), khởi động lại riêng cổng với luật mặc định, chạy pha B (KT-25), dừng chuỗi, ghi số cuối rồi so với số đầu. Thoát khác 0 nếu có test đỏ, chuỗi không lên hoặc số dòng lệch. Luôn dừng chuỗi khi thoát (bẫy `EXIT`). Nhật ký ở `scenarios/.run/` (`phase-a.log`, `phase-b.log`, `before.txt`, `after.txt`, `diff.txt`).

| Lần | Lệnh | Kết quả |
|---|---|---|
| 1 | `run.sh` (có đóng gói lại ba dịch vụ) | Pha A: 15 xanh, 1 bỏ qua, 36,6 giây. Pha B: 1 xanh, 3,6 giây. Số cuối bằng số đầu. **Đạt** |
| 2 | `run.sh --no-build`, bắt đầu lại từ trạng thái không dịch vụ nào chạy | Pha A: 15 xanh, 1 bỏ qua, 32,1 giây. Pha B: 1 xanh, 3,6 giây. Số cuối bằng số đầu. **Đạt** |

Sau hai lần: các cổng 8000, 8004, 8090, 4201 trống; `user_accounts` chỉ còn `ducthinh@dev.aplus.vn`; Redis chỉ còn khóa sẵn có. Mỗi lần chạy khoảng 3 phút (nhiều nhất ở khởi động bốn dịch vụ) cộng thời gian đóng gói ở lần đầu.

Ghi vào hạ tầng dev (Postgres `oauth2_db`, Redis, Kafka dùng chung với dịch vụ) như các bộ thật trước: dữ liệu thử có tiền tố `e2e-`, được dọn và kiểm số dòng. Kafka không dọn được (sự kiện đã đẩy ở lại).

## T7. Bảng truy vết (bước 7, `2026-10-21`)

Kịch bản `KT-xx` của feature khác viết kèm số feature (ví dụ 02/KT-03 là KT-03 ở `02-login-session/flow.md`). Test thật qua cổng là các `KT-xx` ở `frontends/e2e-real/` (bộ của feature 05 cộng KT-21 đến KT-25 của feature này), ký hiệu **E2E**. "Mức" ghi nơi chứng minh sâu nhất: đơn vị, API (gọi dịch vụ thật), thật qua cổng (E2E).

### Kịch bản

| Kịch bản | Test thật qua cổng | Chứng minh thêm ở mức thấp hơn |
|---|---|---|
| S1 đăng ký, đăng nhập, thấy mình đã đăng nhập, đăng xuất, không vào được trang cần đăng nhập | E2E KT-09 | 01/KT-01; 02/KT-01, KT-10; 03/KT-01 |
| S2 email trùng (kể cả khác chữ hoa), email miền nội bộ, bấm đăng ký hai lần | E2E KT-10; E2E KT-25 (429 của đăng ký) | 01/KT-03, KT-05, KT-13 (100 yêu cầu song song, lặp 100 lần) |
| S3 sai email và sai mật khẩu cho cùng thông báo | E2E KT-21 (nhìn thấy), KT-22 (thời gian) | 02/KT-03, KT-04 (API, đơn vị); phá thử ở T3 |
| S4 Quản trị có sẵn nhận ra vai; tự đăng ký (kể cả gửi kèm vai quản trị) ra vai khác | E2E KT-11 | 04/KT-08, KT-09; 01/KT-02 |
| S5 đăng xuất khi phiên đã hết hạn vẫn về trạng thái chưa đăng nhập | E2E KT-12 (phiên BFF mất trước khi đăng xuất), KT-13 | 03/KT-03, KT-22 (phiên Xác thực hết hạn hoặc đã kết thúc: API và use case) |

### Điều cần bảo đảm R1 đến R7 (`stage.md` mục 2.6)

| R | Điều cần bảo đảm | Chứng minh | Mức |
|---|---|---|---|
| R1 | Một email, một tài khoản | 01/KT-03, KT-04, KT-12, KT-13; E2E KT-10 | Đơn vị, lưu trữ, đồng thời, thật qua cổng |
| R2 | Mật khẩu không bao giờ ở dạng đọc được | 01/KT-11 (cột chỉ chứa chuỗi băm), 04/KT-05; log: E2E KT-23 và KT-24 (nội dung hỏng và chuỗi JSON, sau khi sửa lỗi ở T4); feature 07 (`BodySanitizer`, 70 test) | API, thật qua cổng cho đường đăng ký |
| R3 | Sai email và sai mật khẩu cùng một kết quả | Như S3 | Thật qua cổng |
| R4 | Gửi lặp không tạo hai tài khoản | 01/KT-13; E2E KT-10 (bấm đúp) | Đồng thời, thật qua cổng |
| R5 | Đăng xuất luôn thành công | 03/KT-03, KT-08, KT-14; E2E KT-12, KT-13 | Use case, API, thật qua cổng |
| R6 | Mỗi tài khoản đúng một vai thuộc tập quy định; đăng ký công khai không chọn được vai | 01/KT-02; 04/KT-09; E2E KT-11 | API, thật qua cổng |
| R7 | Email miền nội bộ chỉ dành cho tài khoản nội bộ | 01/KT-05; 04/KT-04, KT-09, KT-11; E2E KT-10 | Đơn vị, API, thật qua cổng |

### Bất biến INV-ATH-01 đến 11

| INV | Nội dung | Chứng minh | Mức |
|---|---|---|---|
| 01 | Một email, một tài khoản; không phân biệt chữ hoa thường | 01/KT-03, KT-04, KT-12; 02/KT-05; 04/KT-03; E2E KT-10 | Đơn vị, lưu trữ, thật qua cổng |
| 02 | Mật khẩu không tồn tại ở dạng đọc được, ở bất kỳ đâu | Như R2. **Chưa có** quét toàn bộ dữ liệu và log sau cả bộ (xem `deferred.md`) | API, thật qua cổng một phần |
| 03 | Sai email và sai mật khẩu cùng kết quả, kể cả thời gian | Như S3 (kể cả thời gian: E2E KT-22, đã phá thử bằng cách bỏ băm giả) | Thật qua cổng |
| 04 | Gửi đăng ký lặp không tạo hai tài khoản | Như R4 | Đồng thời, thật qua cổng |
| 05 | Đăng xuất luôn thành công | Như R5 | Use case, API, thật qua cổng |
| 06 | Một vai thuộc tập quy định; đăng ký công khai ra vai Người dùng | Như R6 | API, thật qua cổng |
| 07 | Vai nội bộ khi và chỉ khi email thuộc miền nội bộ | Như R7 | Đơn vị, API, thật qua cổng |
| 08 | Phiên hết hạn hoặc bị kết thúc thì không còn hiệu lực | 02/KT-12, KT-20, KT-23; 03/KT-01, KT-05, KT-22. **Chưa có** hết hạn thật qua cổng (xem `deferred.md`) | Use case, API |
| 09 | Vai của tài khoản không đổi sau khi tạo (ở C0) | 01/KT-11 (mô hình không có hành vi đổi vai: kiểm cấu trúc, không có hành vi để chạy); E2E KT-11 (vai ổn định qua đăng nhập) | Cấu trúc, thật qua cổng một phần |
| 10 | Mỗi (phiên người dùng, ứng dụng) tối đa một phiên ứng dụng đang sống | 02/KT-11, KT-22 | Đồng thời (lưu trữ) |
| 11 | Vai nội bộ chỉ tạo bởi dữ liệu khởi tạo hoặc người dùng nội bộ có quyền | 04/KT-09, KT-10 (không migration nào chèn tài khoản; không endpoint công khai tạo vai nội bộ); E2E KT-11 | API, thật qua cổng |

### Chỗ nối đã kiểm bởi feature này

| Chỗ nối | Test | Kết quả |
|---|---|---|
| Truy vết lỗi qua cổng (06 và 07) | E2E KT-23 | Xanh; phá thử bằng cách Xác thực chỉ nhận B3 thì đỏ |
| Mật khẩu vào log khi nội dung là chuỗi JSON (07) | E2E KT-24 | Lỗi tìm ra, đã sửa (T4) |
| 429 của đăng ký (01 và 06) | E2E KT-25 | Xanh; phá thử bằng cổng luật nâng thì đỏ |
| Mức giới hạn chung của cổng cộng dồn giữa hai lần chạy (05 và 06) | `global-setup` | Lỗi tìm ra, đã sửa (T3) |
| Dữ liệu thử dọn sạch | `run.sh` (so số dòng bảy bảng và khóa Redis) | Hai lần liên tiếp đạt |

Không còn kịch bản hay điều cần bảo đảm nào thiếu test chứng minh. Hai bất biến chứng minh chưa trọn qua cổng (INV-ATH-02, INV-ATH-08) được ghi ở `deferred.md`.
