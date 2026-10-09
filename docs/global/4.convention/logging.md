# Quy ước log

Quy ước cho mọi dịch vụ. Trả lời: log ở đâu, mức nào, ghi gì, không bao giờ ghi gì, và log thế nào khi cần chẩn đoán một thao tác
chậm. Phần cài đặt dùng chung (log JSON, che dữ liệu nhạy cảm, bộ đo bước) nằm ở `observability-starter` (feature 07 của C0).

## 1. Nguyên tắc

1. **Log ở ranh giới, không rải khắp nơi.** Mỗi sự việc ghi một lần, ở nơi biết đủ ngữ cảnh.
2. **Không vừa log vừa ném lại.** Nơi bắt lỗi rồi ném tiếp thì không log, để một lỗi không hiện nhiều lần với nhiều stack trace.
3. **Mức log theo "ai cần hành động".** Xem mục 2.
4. **Một yêu cầu, một mã tương quan.** `traceId` và `spanId` đã tự gắn vào mọi dòng log trong một yêu cầu; không tự in lại trong nội
   dung dòng log.

## 2. Mức log

| Mức | Dùng khi | Ví dụ |
|---|---|---|
| ERROR | Cần người xem ngay: lỗi hệ thống, mất dữ liệu, không tự xử lý được | Lỗi 5xx không lường trước, gửi event thất bại sau khi hết lần thử |
| WARN | Bất thường nhưng hệ thống tự xử lý, hoặc bị chặn vì lý do bảo mật | Đăng nhập thất bại, bị 429, Redis lỗi nên mở cửa, bù trừ một step |
| INFO | Sự kiện nghiệp vụ đã hoàn thành, và lỗi nghiệp vụ thường (4xx do người dùng) | Đã đăng ký tài khoản, email đã dùng (409), bỏ qua event trùng |
| DEBUG | Chẩn đoán, tắt mặc định | Thời gian từng bước, số dòng xử lý |

Lỗi nghiệp vụ thường ở INFO để WARN dành cho thứ thật sự bất thường; nếu không, một đợt người dùng nhập sai hàng loạt làm ngập WARN.

## 3. Điểm đặt log

| Điểm | Log gì | Mức |
|---|---|---|
| Miền (aggregate, value object, domain service) | Không log. Miền thuần; thông tin nằm trong exception | Không |
| Use case thay đổi trạng thái | Một dòng khi xong: tên thao tác, mã thực thể, kết quả, `durationMs`. Không log đầu vào | INFO |
| Use case truy vấn | Không log | Không |
| Xử lý lỗi tập trung (`GlobalExceptionHandler`) | **Nơi duy nhất** log lỗi lan lên qua HTTP: lỗi nghiệp vụ kèm mã lỗi; lỗi hệ thống kèm stack trace | INFO (4xx); ERROR (5xx) |
| Adapter ra ngoài (DB, Redis, Kafka, dịch vụ khác) | Chỉ khi lỗi hoặc suy giảm: đích, thời gian chờ, nguyên nhân | WARN hoặc ERROR |
| Nuốt lỗi có chủ ý (mở cửa, bù trừ, bỏ qua trùng) | Bắt buộc ghi lý do và hậu quả | WARN (bỏ qua event trùng: INFO) |
| Sự kiện bảo mật (đăng nhập thất bại, bị 429, khóa) | Một dòng: định danh đã che, lý do, địa chỉ mạng. Khác nhật ký kiểm toán (B25) là dữ liệu nghiệp vụ | WARN |
| Consumer event | Bỏ qua do trùng; xử lý lỗi kèm `eventId` | INFO; ERROR |
| Khởi động | Một dòng về cấu hình đã nạp (phiên bản, môi trường) | INFO |
| Mỗi yêu cầu HTTP | Một dòng hoàn tất do bộ lọc dùng chung ghi (mục 6); ứng dụng không tự ghi dòng này | INFO |

### 3.1 Use case nhiều step

Không log từng step ở mức INFO. Chia theo loại step:

| Loại step | Có log không | Mức |
|---|---|---|
| Phép tính, kiểm tra, chuyển đổi trong bộ nhớ | Không. Hỏng thì exception đã nói rõ ở đâu | Không |
| Step có tác dụng bền hoặc ra ngoài: ghi DB, gọi dịch vụ khác, gửi thư, phát event, giữ chỗ | Một dòng khi xong, kèm tên step và mã thực thể | INFO |
| Step bù trừ hoặc thử lại | Bắt buộc, kèm lý do | WARN |

Lý do: use case chỉ có một tác dụng bền thì một dòng cuối là đủ, và lỗi đã có stack trace chỉ ra step hỏng. Use case nhiều tác dụng
ngoài thì khi hỏng giữa chừng cần biết đã xong đến step nào để bù trừ; stack trace chỉ cho biết nơi hỏng, không cho biết các step trước.

Dạng dòng log của step: `[TênThaoTác] step=<tên> ...`, để lọc được bằng một truy vấn. Ví dụ `[PlaceOrder] step=reserve-stock ok orderId=…`.

## 4. Chẩn đoán một thao tác chậm

Hai việc khác nhau:

- **Đo thường xuyên** (P95, P99 của một use case) dùng số đo (metric), không dùng log. Số liệu NFR lấy từ đây.
- **Chẩn đoán một thao tác chậm cụ thể** (ví dụ xuất excel chậm) dùng log DEBUG theo từng bước kèm thời gian để tìm bước chậm.
  Ở C0 đây cũng là công cụ thực tế duy nhất vì chưa có nơi xem span của trace.

Quy ước cho log chẩn đoán:
- Mức DEBUG, tắt mặc định, không để lẫn vào dòng INFO thường ngày.
- Mỗi bước một dòng: `[TênThaoTác] step=<tên> took=<ms> total=<ms>`; thêm kích thước dữ liệu (số dòng, số byte) khi liên quan.
- Bật được mà không sửa mã hay triển khai lại: đổi mức log của một lớp lúc đang chạy qua endpoint `loggers` của actuator (chỉ cho
  vận hành nội bộ, không công khai).
- Dùng bộ đo bước dùng chung (kiểu đồng hồ bấm giờ): `timer.step("đọc dữ liệu")` ghi thời gian từ bước trước, chỉ tính khi DEBUG
  đang bật nên không tốn gì khi tắt.
- Tìm ra nguyên nhân thì giữ các dòng DEBUG còn hữu ích, bỏ những dòng chỉ phục vụ lần soi đó.
- Dòng INFO cuối của use case chỉ ghi tổng `durationMs`; dòng DEBUG chia nhỏ từng bước.

## 5. Nội dung log: được ghi, không được ghi

**Không bao giờ ghi:** mật khẩu, giá trị băm, token (kể cả trong môi trường phát triển), mã OTP, khóa bí mật. Các kiểu giá trị mang bí mật
tự che trong `toString` để lỡ in ra cũng không lộ.

**Được ghi (quyết định tạm thời, xét lại khi lên môi trường thật hoặc gom log tập trung):** định danh của người dùng như email, số
điện thoại, địa chỉ mạng. Dữ liệu cá nhân trong log là rủi ro được chấp nhận có ý thức ở giai đoạn này.

Quy tắc ghi:
- **Ưu tiên mã tài khoản khi đã biết.** Chỉ ghi email ở chỗ chưa có mã (đăng ký, đăng nhập thất bại, email lạ), để dòng log vừa tra
  được trong cơ sở dữ liệu vừa không lặp email ở mọi dòng.
- **Chuẩn hóa email** (chữ thường, cắt khoảng trắng) trước khi ghi, để tìm trong log là khớp.
- **Không ghi nội dung yêu cầu thô.** Ghi mô tả: lỗi gì, trường nào, vì sao (ví dụ `PASSWORD_INVALID field=password length=7`).
- **Trường nhị phân** (byte, ảnh, tệp, chuỗi base64 lớn): không ghi nội dung, chỉ ghi loại và kích thước.
- **Chuỗi dài thì cắt:** mỗi giá trị tối đa 200 ký tự, cả dòng tối đa 2000 ký tự, kèm dấu hiệu bị cắt, ví dụ `abc…(cắt, tổng 5300 ký tự)`. Ngưỡng
  cấu hình được.
- **Làm sạch giá trị người dùng nhập:** thay ký tự xuống dòng và ký tự điều khiển (CR, LF, tab) trước khi ghi, để tránh chèn dòng log giả.
- Cắt và làm sạch do bộ tiện ích dùng chung thực hiện (feature 07), không để từng chỗ tự làm.

### Ngoại lệ có kiểm soát: token ở môi trường dev

Nhu cầu có token để test được đáp ứng bằng cách **lấy token ở nguồn**, không phải đọc từ log: xin từ máy chủ xác thực bằng `client_credentials`
ở đầu file `.http`, hoặc dùng công cụ cấp token chỉ cho dev (feature 02). Với mọi dòng log về token, chỉ ghi định danh tra được: `jti`, `sub`,
`exp`, kèm vài ký tự đầu và cuối. Nếu thật sự cần ghi đầy đủ ở dev thì phải bật tường minh `app.dev.log-token=true` **và** profile là `dev`, ghi ở mức
DEBUG; ứng dụng từ chối khởi động nếu cờ này bật mà profile là production. Backfill hoặc việc chạy trên môi trường thật dùng tài khoản dịch vụ riêng với
quyền tối thiểu và token ngắn hạn, không dùng token copy từ log.

## 6. Log yêu cầu HTTP

**Một dòng hoàn tất cho mỗi yêu cầu** (kiểu "canonical log line"), ghi lúc kết thúc bởi một bộ lọc ngoài cùng, trong `try/finally`, nên
phủ cả yêu cầu bị từ chối ở bước đầu (401, 429, JSON hỏng). Mức INFO. Nó đứng sau các dòng bên trong của cùng yêu cầu; thời lượng
cho biết lúc bắt đầu, `trace.id` nối các dòng. Một dòng "vào" chỉ ở mức DEBUG (method, đường dẫn, kích thước, địa chỉ mạng), bật khi
chẩn đoán yêu cầu treo.

**Nội dung yêu cầu chỉ ghi khi lỗi.** Yêu cầu thành công không ghi nội dung. Khi trả 5xx, hoặc 4xx do kiểm đầu vào hay nghiệp vụ (trừ 401, 403,
404, 405, 429: danh sách `noBodyStatuses` cấu hình được), dòng hoàn tất kèm nội dung yêu cầu đã xử lý. Thao tác mà kết quả sai không phải lỗi (tính tiền, tồn kho, đặt hàng) ghi dấu vết
đầy đủ vào nhật ký kiểm toán riêng (B25), không vào log ứng dụng. Phản hồi chỉ cần `error.code` ở dòng hoàn tất. Header mặc định không ghi;
`Authorization`, `Cookie`, `Set-Cookie` không bao giờ.

**Danh sách trường được phép** khai báo bằng chú thích `@LogRequestFields("email")` trên method của controller. Nội dung không phân tích được (JSON
hỏng hoặc bị cắt) mà đường dẫn có danh sách trường được phép thì chỉ ghi kích thước, vì danh sách không áp dụng được; đường dẫn không có danh sách thì vẫn
che giá trị của cặp `tên: giá trị` có tên nhạy cảm trong chuỗi.

**Xử lý nội dung trước khi ghi**, theo thứ tự: chỉ JSON hoặc chữ (tệp, nhị phân chỉ ghi loại và kích thước); đệm tối đa khoảng 4 KB; che
trường có tên nhạy cảm ở mọi độ sâu (`password`, `token`, `accessToken`, `refreshToken`, `otp`, `secret`, `authorization`, `cardNumber`...);
đường dẫn nhạy cảm chỉ giữ các trường được phép; quét mẫu bí mật (`Bearer …`, JWT, mã băm) trên giá trị còn lại; cắt giá trị dài; đóng gói thành
**một chuỗi**, không trải khóa của nội dung thành trường (tránh bùng nổ số trường ở Elasticsearch). Lỗi khi xử lý không bao giờ làm hỏng yêu cầu.

Ghi log chạy bất đồng bộ qua hàng đợi giới hạn để không chặn luồng xử lý; đầy thì bỏ dòng mức thấp. Mỗi dịch vụ dùng cấu hình dùng chung bằng
`logback-spring.xml` chỉ gồm `<include resource="observability-logback.xml"/>`; console luôn ra chữ, file JSON cho Filebeat luôn được ghi (xem mục 7). Overhead
dự kiến dưới nửa mili-giây mỗi yêu cầu (Chỉ thiết kế), cần đo bằng điểm cuối rỗng của bộ đo nền (feature 08).

**Cổng quản trị riêng.** Các endpoint vận hành (`health`, `info`, `metrics`, `prometheus`, `loggers`) nằm ở cổng quản trị = cổng ứng dụng + 10000, chỉ nghe
`127.0.0.1`, không ở cổng công khai. Đổi mức log một lớp lúc chạy: `POST /actuator/loggers/<tên lớp>` với `{"configuredLevel":"DEBUG"}`, không khởi động lại.

## 7. Định dạng log

Console luôn là dạng chữ mặc định của Spring Boot để dev đọc bằng mắt (JSON khó đọc, nên không có công tắc đổi console sang JSON). Mục đích của JSON là đẩy lên ELK, nên **luôn có file JSON**. Ứng dụng không đẩy thẳng sang Logstash: Filebeat đọc file rồi gửi đi, nên Logstash chậm hay chết không ảnh hưởng ứng dụng.

| Đầu ra | Định dạng |
|---|---|
| Console | Luôn chữ |
| File `logs/<tên ứng dụng>.json` (đổi bằng `logging.file.name`) | Luôn JSON theo ECS, xoay vòng theo ngày và kích thước, giữ 7 ngày; Filebeat đọc file này rồi gửi vào ELK (roadmap C2a). Thư mục `logs/` đã có trong `.gitignore` |

Mỗi dòng JSON trong file là một đối tượng JSON, tên trường theo ECS (chữ thường, dấu chấm). Bảng dưới là cấu trúc của dòng JSON.

| Nhóm | Trường |
|---|---|
| Mọi dòng | `@timestamp` (giờ sự kiện), `log.level`, `log.logger`, `message`, `process.thread.name`, `service.name`, `service.environment` |
| Truy vết | `trace.id`, `span.id` (tự gắn từ ngữ cảnh trace) |
| Người dùng | `user.id` (mã tài khoản) do code đặt bằng `LogContext.user(...)` khi biết người dùng; chưa tự lấy từ danh tính đã xác thực |
| Lỗi | `error.type`, `error.message`, `error.stack_trace`; `error.code` là mã lỗi chữ ổn định |
| Khóa nghiệp vụ | `labels.<khóa>`, kiểu `keyword`, ví dụ `labels.orderId`, `labels.eventId`, `labels.eventType` |
| Dòng hoàn tất | `event.dataset=http.request`, `http.request.method`, `http.route` (khuôn đường dẫn), `url.path`, `http.response.status_code`, `event.duration` (nano-giây), `http.request.body.bytes`, `http.response.body.bytes` (chỉ khi phản hồi có `Content-Length`), `client.ip` (địa chỉ kết nối; địa chỉ thật của client do cổng cấp, xem feature cổng truy cập); khi lỗi thêm `error.code` và `http.request.body.content` |

Quy tắc:
- Dev dùng `log.info`, `log.warn`, `log.error(…, ex)` của SLF4J bình thường; bộ tiện ích không bọc `Logger`.
- `message` là câu đọc được cho người, có thể kèm `khóa=giá trị`; khóa cần tìm thì đã có trường riêng. Tiền tố `[TênThaoTác]` là tùy chọn: tên thao tác
  đã có ở `log.logger` (tên lớp) và ở `labels.operation` khi dev đặt.
- **Tên thao tác (`labels.operation`) không tự có**: dev gọi `LogContext.operation("tênMethod")` một lần ở đầu method khi cần, từ đó mọi dòng log
  trong khối mang trường này. Use case chỉ dựa vào `log.logger`. Không dùng thông tin người gọi của logback (`%M`, `%L`) ở production vì đắt.
- **Khóa nghiệp vụ do dev tự thêm bằng hàm dùng chung** (`LogContext.put("orderId", …)`) ở nơi đã biết giá trị; hàm đưa vào MDC, ra JSON có
  tiền tố `labels.`, và xuất hiện ở cả dòng ứng dụng lẫn dòng hoàn tất. Bộ lọc khôi phục MDC về trạng thái đầu yêu cầu ở `finally` (sau khi ghi dòng hoàn tất). Chỉ khóa tra cứu dạng chuỗi (khoảng 10 khóa, `camelCase`);
  số đo cần cộng hay so sánh thuộc về số đo (metric). Danh sách khóa dùng chung giữa các dịch vụ ghi ở đây để mọi nơi viết giống nhau.
- `traceId`, `spanId` ra JSON là `traceId`, `spanId`; cần đặt `logging.structured.json.rename.traceId=trace.id` và `…spanId=span.id` để thành tên ECS (đã kiểm).
- Khóa MDC có dấu chấm ra thành đối tượng lồng (`labels.orderId` thành `"labels":{"orderId":…}`); khóa không dấu chấm ra cấp cao. Bộ ghi console tự khai
  báo trong `logback-spring.xml` làm mất định dạng có cấu trúc, nên các file đó phải bỏ hoặc `include` bản có cấu trúc (đã kiểm).
- MDC gắn với luồng xử lý: khi chuyển việc sang luồng khác, khóa không tự theo sang.
- Không có trong dòng log: `event.original`, header, token, các trường nội bộ của bộ ghi (`endOfBatch`, `loggerFqcn`, `threadPriority`, `@version`).
- Phản hồi lỗi cho client trả thêm `traceId` (lấy từ MDC) để người dùng đọc lại cho bên rà log, và `code` chữ ổn định; `GlobalExceptionHandler` đặt mã lỗi vào thuộc tính
  `error.code` của yêu cầu để dòng hoàn tất mang mã. Lỗi nghiệp vụ ghi INFO, lỗi hệ thống ghi ERROR kèm stack trace; nội dung không đọc được là 400 `MALFORMED_REQUEST`;
  lỗi framework có sẵn mã trạng thái (405, 415...) là `HTTP_<mã>`, không phải 500.
- Bộ quét ở đầu ra che `Bearer …`, JWT, mã băm trong `message`, `error.message` và `error.stack_trace` (lưới an toàn cuối).

## 8. Áp dụng với mã có từ trước

Mã chưa thiết kế lại có chỗ vi phạm quy ước này: ghi nguyên token ở mức WARN trong phần cấp token, ghi email ở các bộ xử lý
đăng nhập thành công, thất bại và MFA mà không theo quy tắc chọn mã tài khoản trước, mức log không theo quy tắc. Không sửa riêng lẻ; sửa khi feature sở hữu phần đó được thiết kế lại
(đăng nhập và phiên: feature 02).

## 9. Ngữ cảnh khi chạy song song

MDC gắn với luồng. Khi giao việc sang luồng khác (`@Async`, executor, `CompletableFuture`, `parallelStream`, luồng ảo), ngữ cảnh phải được truyền
vào luồng thực thi để giữ toàn bộ ngữ cảnh của nhánh hiện tại:
- Chụp **bản sao** ngữ cảnh **lúc giao việc** (không phải lúc chạy), nạp vào MDC của luồng con, và **khôi phục trạng thái trước đó** khi xong để luồng
  trong pool không giữ lại gì. Nhánh con thêm hoặc đổi khóa riêng không ảnh hưởng luồng cha hay nhánh anh em.
- Dùng `LogContext.wrap(runnable/callable)` ở chỗ giao việc thủ công; executor do Spring quản lý dùng `TaskDecorator` của bộ tiện ích.
- `LogContext.scope(...)` khôi phục giá trị cũ khi đóng (không chỉ xóa), để lồng nhau trong cùng luồng không xóa nhầm khóa của lớp ngoài.
- `trace.id` và `span.id` giữ nguyên ở nhánh con (không tạo span riêng cho nhánh). Nhánh chạy cùng lúc phân biệt bằng `process.thread.name`; tên luồng trong pool
  được tái dùng nên khi cần phân biệt từng tác vụ thì nhánh tự đặt khóa riêng (ví dụ `LogContext.put("branch", "sku-A")`).
- `StepTimer` dùng trong một luồng; bước chạy song song thì mỗi tác vụ có timer riêng.
- Mã reactive (`api-gateway`) cần cơ chế truyền ngữ cảnh của Reactor, không phải `LogContext`; thuộc feature cổng truy cập.
