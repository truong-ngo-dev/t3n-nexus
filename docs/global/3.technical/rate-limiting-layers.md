# Giới hạn tần suất (rate limiting)

> Quy tắc của dự án: có hai nhóm giới hạn, mỗi loại là một cấu hình riêng; điểm đặt cố định, chỉ giá trị mới đổi. Tài liệu
> này trả lời: một giới hạn thuộc nhóm nào, đặt ở đâu, cấu hình thế nào, khi nào cần thêm.

## 1. Hai nhóm giới hạn

Phép thử: **quy tắc có nói về ý nghĩa của một hành động nghiệp vụ không?**

| | Nhóm 1: bị động, hạ tầng | Nhóm 2: nghiệp vụ |
|---|---|---|
| Bản chất | Bảo vệ hệ thống khỏi tải và lạm dụng, bất kể hành động đó là gì | Một luật của nghiệp vụ; vi phạm là sai luật chứ không chỉ quá tải |
| Ví dụ | Theo IP; theo người dùng; tối đa 10 lần đăng ký mỗi giờ mỗi IP | Không gửi lại OTP hay liên kết đặt mật khẩu trong 60 giây; tối đa 3 lần gửi lại xác minh mỗi giờ cho một email |
| Vị trí trong luồng | Cổng đầu của luồng, chạy trước dịch vụ; không đọc nội dung hay trạng thái nghiệp vụ | Một bước giữa luồng, cần trạng thái của thực thể (ví dụ lần gửi gần nhất của tài khoản) |
| Nằm ở | Gateway (biên) | Trong use case của BC sở hữu hành động |
| Khóa đếm | IP, người dùng, đường dẫn | Trường nghiệp vụ hẹp (email, mã tài khoản) |
| Lỗi trả về | 429 chung, `Retry-After`, `code` `TOO_MANY_REQUESTS` | Lỗi nghiệp vụ có mã riêng của BC, nói rõ phải chờ bao lâu |
| Chủ sở hữu | Vận hành, dev | BC (sản phẩm) |

## 2. Nhóm 1: giới hạn bị động ở biên

### 2.1 Các loại, mỗi loại một cấu hình riêng

| Loại | Chạy ở | Khóa | Phủ | Không phủ |
|---|---|---|---|---|
| Theo IP | `api-gateway` | IP của client | Mọi đường dẫn, kể cả `/auth/**` | |
| Theo người dùng | Mỗi BFF (hiện là `web-gateway`) | Mã tài khoản từ danh tính đã xác thực | `/api/**` đã đăng nhập | Yêu cầu chưa đăng nhập; `/auth/**` (đi thẳng từ `api-gateway`, không qua BFF) |
| Danh sách luật theo đường dẫn | `api-gateway` | IP, theo từng đường dẫn và phương thức | Các endpoint nhạy cảm (đăng ký) | |

- Mức toàn cục (IP, người dùng) là lưới an toàn thô để chặn client lỗi hoặc chạy vòng lặp, không phải để chống lạm dụng.
- **Giới hạn theo người dùng ở BFF, không ở `api-gateway`.** Danh tính chỉ có sau khi xác thực; `api-gateway` là lớp định tuyến
  thuần, không kiểm JWT, và trình duyệt đi qua BFF chỉ mang cookie phiên (token nằm phía máy chủ). Giải mã payload token mà
  không kiểm chữ ký thì không an toàn (kẻ xấu giả mã của nạn nhân để làm cạn hạn mức của họ); có kiểm chữ ký thì chỉ giúp được
  client mang token, không giúp trình duyệt. Nhiều BFF dùng chung **một thư viện, cùng khóa Redis và cùng mức**, để mỗi người dùng
  chỉ có một ngân sách trên mọi BFF.
- **Địa chỉ IP phải do gateway xác định.** Chỉ tin địa chỉ do cổng đặt vào tiêu đề; không tin phần tử đầu của
  `X-Forwarded-For` do client tự gửi, nếu không kẻ xấu đổi "IP" để né giới hạn.

### 2.2 Vì sao mức toàn cục không đủ cho hành động nhạy cảm

1. 300 yêu cầu/60 giây mỗi IP chặn DoS hạ tầng, quá lỏng để chống "một IP tạo hàng trăm tài khoản": mỗi lần đăng ký tốn một
   lần băm mật khẩu (~80 ms CPU).
2. `/auth/**` không qua giới hạn theo người dùng (chưa đăng nhập), chỉ còn giới hạn theo IP.
3. Một ngưỡng IP đủ chặt cho đăng ký sẽ chặn nhầm các yêu cầu bình thường cùng IP (NAT, văn phòng, mạng di động).

Vì vậy hành động nhạy cảm có luật riêng trong danh sách luật theo đường dẫn.

## 3. Nhóm 2: giới hạn là một phần của nghiệp vụ

Điểm đặt cố định trong luồng của BC; thư viện chỉ cung cấp cơ chế đếm, còn luật, mức, khóa đếm, thông điệp và mã lỗi do BC
sở hữu hành động khai báo. Ví dụ luồng gửi lại OTP: xác định tài khoản → kiểm lần gửi gần nhất → sinh mã mới → gửi → ghi thời
điểm gửi.

- **Chiếm chỗ nguyên tử.** Kiểm "đang trong thời gian chờ" và đặt khóa chờ phải là một thao tác (đặt nếu chưa có, kèm thời
  gian sống), làm trước khi gửi. Kiểm rồi mới đặt sẽ để hai yêu cầu cùng lúc cùng qua. Gửi thư lỗi thì nhả khóa.
- **Không lộ email có tồn tại hay không.** Gửi lại cho email lạ trả cùng kiểu kết quả như email thật; giới hạn theo email áp cả cho
  email không tồn tại.
- **Thông điệp nói rõ phải chờ bao lâu** và kèm `Retry-After` để giao diện đếm ngược.
- Trạng thái chờ có thể ở Redis (khóa có thời gian sống): mất khóa chỉ gửi thừa một thư.
- Mỗi luật nhóm 2 có đủ: được ghi trong `analysis.md` của BC như một luật nghiệp vụ, mức nằm ở cấu hình của BC, mã lỗi riêng, một test.

### Ba loại tham số của giới hạn nghiệp vụ

| Loại | Ví dụ | Ai quyết định | Quản trị |
|---|---|---|---|
| Chính sách cố định của sản phẩm | Thời gian chờ gửi lại OTP, hạn của mã | Sản phẩm | Cấu hình của BC, đổi theo bản phát hành |
| Chính sách do quản trị viên chỉnh | Ngưỡng khóa tài khoản khi nhập sai (Keycloak cho chỉnh ở Realm Settings > Brute Force Detection) | Quản trị bảo mật | Cần màn hình quản trị khi có nhu cầu đổi không qua triển khai (C1b trở đi) |
| Dữ liệu của miền | Giới hạn số lần dùng của từng mã giảm giá (Shopify đặt trên từng mã) | Người bán, ngành hàng | Thuộc tính của thực thể, quản lý qua chức năng của thực thể |

Không có thành phần quản trị rate limit chung cho nhóm 2: mỗi giới hạn được quản lý cùng nơi với tham số của BC sở hữu nó.

## 4. Quản trị cấu hình

- **Mỗi loại giới hạn là một cấu hình riêng**: IP, người dùng, danh sách luật theo đường dẫn, và từng chính sách nghiệp vụ trong BC của
  nó. Danh sách luật theo đường dẫn chỉ chứa luật nhóm 1; chính sách nhóm 2 không đưa vào danh sách của gateway.
- **Điểm đặt không đổi khi đổi giá trị.** Điểm đặt (đường dẫn nào, khóa nào) là một yêu cầu thiết kế/code, đổi bằng triển khai.
  Giá trị (mức, khoảng thời gian, `enabled`) là cấu hình.
- **Hiện tại dùng thuộc tính (`application.properties`)**, nạp một lần lúc khởi động thành đối tượng chính sách có kiểu, bất
  biến, kiểm tra hợp lệ (mức > 0, khoảng thời gian > 0, không trùng tên); cấu hình sai thì không cho khởi động. Mã nghiệp vụ chỉ thấy
  đối tượng chính sách, không thấy khóa cấu hình. Giá trị theo môi trường ghi đè bằng biến môi trường. Đổi giá trị bằng cách sửa
  file rồi triển khai lại; **chưa đổi lúc chạy** vì chưa có nhu cầu (một môi trường, ít người vận hành).
- Mỗi BFF phải cùng giá trị cho giới hạn theo người dùng; đặt mức mặc định trong thư viện dùng chung.

### Hướng mở rộng khi có nhu cầu (chưa làm)

Phần quản trị chính sách này (đổi lúc chạy, giao diện cho quản trị viên) thuộc nhóm "Dữ liệu tham chiếu và cấu hình" của quản trị chung
(`4-context-map.md` B24). Khi làm nhóm đó mới phân tích hạng mục nào cần quản trị.

Cần khi có nhiều thể hiện cần đổi mức không qua triển khai, hoặc cần ứng phó khi bị tấn công. Khi đó thêm một cơ chế lưu đệm cấu
hình dùng chung cho dự án, không đổi chỗ dùng:
- `ConfigSource` trả cấu hình thô (file hôm nay, dịch vụ cấu hình hay cơ sở dữ liệu về sau).
- `ConfigSnapshot` giữ đối tượng chính sách bất biến kèm phiên bản và thời điểm nạp; mỗi lần đọc chỉ là một đọc bộ nhớ.
- Nạp lúc khởi động; làm mới nền theo chu kỳ có xê dịch, kiểm tra hợp lệ rồi mới thay, lỗi thì giữ bản cũ; có giá trị mặc định
  đóng gói sẵn; cảnh báo khi bản chụp cũ quá ngưỡng. **Không bao giờ gọi API cấu hình trên đường xử lý yêu cầu.**
- Nhiều thể hiện nhất quán cuối cùng sau một chu kỳ làm mới.

## 5. Hiệu năng và khi lỗi

- Chi phí trên đường xử lý mỗi yêu cầu: đọc bản chụp cấu hình trong bộ nhớ, rồi một lần gọi Redis cho mỗi tầng (đoạn Lua nguyên tử,
  cửa sổ trượt). Khóa Redis có thời gian sống bằng khoảng thời gian của luật.
- Thời gian chờ ngắn cho lệnh đếm. Redis lỗi thì **mở cửa** (cho qua và ghi cảnh báo) để không sập hệ thống vì lớp bảo vệ; có thể thêm
  bộ ngắt mạch để mỗi yêu cầu không phải chờ hết thời gian.
- Bị chặn không để lại tác dụng phụ nào và không mở giao dịch.

## 6. Khi nào một endpoint cần luật riêng

Cần luật riêng khi **cả ba đều đúng**:
1. Endpoint thay đổi trạng thái hoặc kích hoạt tác dụng phụ tốn tài nguyên (tạo tài khoản, gửi thư/OTP), không phải đọc thuần.
2. Không được giới hạn theo người dùng bảo vệ (chưa đăng nhập), hoặc mức toàn cục vẫn quá lỏng so với chi phí thật.
3. Mức hợp lý thấp hơn nhiều so với mức toàn cục (300/60 giây).

Nếu luật không cần trạng thái nghiệp vụ và chỉ dựa trên IP hoặc đường dẫn thì là luật nhóm 1 trong danh sách luật theo đường dẫn;
nếu cần khóa hẹp hơn IP hoặc trạng thái của thực thể thì là luật nhóm 2 trong BC.

## 7. Cơ chế cài đặt

| Phần | Vị trí | Ghi chú |
|---|---|---|
| Bộ đếm cửa sổ trượt qua Redis | `rate-limiter-starter` (`RateLimiter`), `reactive-rate-limiter-starter` cho gateway | Cơ chế dùng chung |
| Bộ lọc theo IP / theo người dùng | `api-gateway`, `web-gateway` | Đọc `app.ratelimit.default.limit` và `window-seconds`, mặc định 300/60 giây |
| `@RateLimit` + `RateLimitAspect` | `rate-limiter-starter` | Đặt trên `handle()`; khóa là SpEL trên `Command`; ném `RateLimitExceededException` (429), cố ý không phải lỗi miền |

Hạn chế hiện có: `@RateLimit(limit, windowSeconds)` nhận hằng số nên **chưa đọc được từ cấu hình**. Khi luật nhóm 2 đầu tiên
cần mức cấu hình được, mở rộng để chú thích tham chiếu luật theo tên và đọc mức từ cấu hình của BC. Chưa làm.

Vì sao chú thích trên `handle()` mà không phải bộ lọc: bộ lọc chạy trước khi nội dung được đọc, muốn khóa theo trường trong nội dung
(email) thì phải tự đọc JSON và biết lược đồ nghiệp vụ. Chú thích trên `handle()` có `Command` đã sẵn và chạy trước giao dịch, nên
yêu cầu bị chặn không mở giao dịch.

## 8. Áp dụng hiện tại

| Hành động | Nhóm | Khóa | Mức | Nơi cài | Trạng thái |
|---|---|---|---|---|---|
| Mọi yêu cầu | 1, theo IP | IP | 300 / 60 giây | `api-gateway` | Đã có |
| `/api/**` đã đăng nhập | 1, theo người dùng | Mã tài khoản | 300 / 60 giây | `web-gateway` | Đã có |
| `POST /auth/register` | 1, luật theo đường dẫn | IP | 10 / giờ | Danh sách luật theo đường dẫn ở `api-gateway` (feature 06) | Chưa làm |
| Gửi lại liên kết đặt mật khẩu | 2 | Mã tài khoản | chờ 60 giây giữa hai lần | `oauth2-service` | Đã có (cần sửa chiếm chỗ nguyên tử) |
| Gửi lại xác minh | 2 | Email | 3 / giờ | BC Định danh | Chưa làm |

## 9. Tham khảo

- Stripe, Rate limits: giới hạn toàn cục cộng giới hạn riêng từng endpoint và giới hạn đồng thời: <https://docs.stripe.com/rate-limits>
- AWS, Throttle requests to your REST APIs: giới hạn theo tài khoản, stage, từng method và từng client: <https://docs.aws.amazon.com/en_en/apigateway/latest/developerguide/api-gateway-request-throttling.html>
- Red Hat build of Keycloak, Mitigating security threats (ngưỡng khóa tài khoản do quản trị chỉnh): <https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/26.0/html/server_administration_guide/mitigating_security_threats>
- Shopify, DiscountCodeBasic (giới hạn số lần dùng đặt trên từng mã): <https://shopify.dev/docs/api/admin-graphql/latest/objects/DiscountCodeBasic>
