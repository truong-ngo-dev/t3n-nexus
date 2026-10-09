# Feature 07: Truy vết lỗi

> **Trạng thái:** đã cài `2026-10-09`, còn nợ đo overhead (chờ feature 08) · **Chặng:** C0 · **Loại:** Kỹ thuật
>
> **Nguồn:** Roadmap C0 (quy ước phát triển: bắt lỗi, log; chuẩn xử lý lỗi và log), NFR mục 7 · **Kiểm chứng bằng:** Một yêu cầu lỗi lần ra nguyên nhân từ log

## Phạm vi

Truy vết có ba tầng; C0 làm hai tầng đầu.

| Tầng | Nội dung | Ở C0 |
|---|---|---|
| 1. Quy tắc log | Log ở đâu, mức nào, ghi gì, không bao giờ ghi gì (mật khẩu, mã băm, token, OTP). Là kỷ luật khi viết mọi feature, ghi vào `4.convention` | Xong: `docs/global/4.convention/logging.md` |
| 2. Bộ tiện ích log dùng chung | Log có cấu trúc (JSON) với `traceId`, `spanId`, tên service là trường riêng (mã tương quan đã có sẵn trong mẫu mặc định, cần đưa thành trường), che dữ liệu nhạy cảm, một chỗ ghi lỗi thống nhất không nuốt lỗi. Đặt ở `observability-starter` | Làm |
| 3. Hạ tầng tập trung | ELK: gom log về một chỗ, đánh chỉ mục các trường cần tìm, màn hình Kibana | Không làm. Cần từ chặng có luồng xuyên nhiều BC (roadmap C2a). Log JSON từ bây giờ để sau đưa vào Logstash không phải sửa mã |

C0 chỉ có một BC nên tìm nguyên nhân trong log của một dịch vụ bằng `traceId` là đủ.

## Hiện trạng (đã đọc mã)

- `observability-starter` chỉ bật lấy mẫu trace 100% và mở endpoint `health,info,metrics,prometheus`; chưa có định dạng log có cấu trúc hay che dữ liệu nhạy cảm.
- `oauth2-service` dùng mẫu log mặc định của Spring Boot. Mẫu này có ô mã tương quan nên mỗi dòng log trong một yêu cầu đã tự mang `traceId` và `spanId` khi trace đang hoạt động (đã thấy ở log chạy thật của `RegisterUser`). Đoạn Logstash trong `logback-spring.xml` đang comment.
- Một số use case còn tự đọc `traceId` từ MDC để ghi vào nội dung dòng log (ví dụ `RegisterUser`); việc này thừa với ô mã tương quan và nên bỏ khi có quy ước log.
- **Còn thiếu:** log chưa ở dạng cấu trúc (JSON) nên chưa sẵn sàng đưa vào Logstash; chưa có cơ chế che dữ liệu nhạy cảm; `GlobalExceptionHandler` đã ghi lỗi nghiệp vụ ở mức cảnh báo và lỗi chung ở mức lỗi kèm stack trace, nhưng chưa có quy ước chung cho nơi khác (consumer, bộ lọc).

## Xử lý dữ liệu nhạy cảm trong log

Hai lớp độc lập, mỗi lớp có phạm vi riêng. Quy ước ghi (code không đưa dữ liệu nhạy cảm vào log) vẫn là lớp phòng thủ thứ nhất; hai lớp dưới đây chỉ là lưới an toàn.

| | Lớp 1: `BodySanitizer` | Lớp 2: `SecretScrubbingCustomizer` |
|---|---|---|
| Áp dụng cho | Nội dung yêu cầu đính kèm dòng hoàn tất (`http.request.body.content`) | Mọi dòng log: `message`, `error.message`, `error.stack_trace` |
| Che theo tên trường (`password`, `token`, `secret`, `otp`...) | Có, mọi độ sâu; cả nội dung JSON hỏng hoặc bị cắt | **Không** |
| Danh sách trường được phép (`@LogRequestFields`) | Có | Không áp dụng |
| Che `Bearer …`, JWT, mã băm bcrypt trong giá trị | Có | Có (cùng bộ mẫu `SecretPatterns`) |
| Cắt giá trị dài, mảng dài; chuỗi giống base64 chỉ ghi kích thước | Có | **Không** |
| Bỏ ký tự điều khiển (chống giả dòng log) | Có, ở nhánh chữ | **Không** (JSON đã tự thoát ký tự điều khiển trong chuỗi) |

**Giới hạn đã chấp nhận:** `password=abc` hay `"password":"abc"` nằm trong `message` hoặc stack trace sẽ **không** bị che, vì lớp 2 chỉ nhận ra ba mẫu token. Điều này an toàn chừng nào code tuân quy ước (không nối nội dung yêu cầu, mật khẩu hay đối tượng chứa chúng vào `message`; không ghi cả đối tượng bằng `toString`). Cả hai giới hạn này, và việc lớp 2 chỉ chạy ở đầu ra JSON (console dạng chữ không được quét), ghi ở `deferred.md`.

## Việc đã giao cho feature này

Từ feature 01 (đăng ký):

- **Nội dung yêu cầu không phải JSON hợp lệ** (sai cú pháp, thiếu ngoặc) trả 500 `INTERNAL_ERROR`, đã xác nhận bằng yêu cầu thật,
  vì `GlobalExceptionHandler` của `common-web` chưa có nhánh cho `HttpMessageNotReadableException` nên rơi vào nhánh bắt mọi lỗi.
  Đây là lỗi do client, phải trả 400 kèm `code` ổn định (ví dụ `MALFORMED_REQUEST`) và không ghi log mức lỗi nghiêm trọng. Sửa ở
  thư viện dùng chung nên ảnh hưởng mọi dịch vụ; làm cùng quy ước lỗi chung của feature này.
- **Kiểm chứng:** gây một lỗi ở `POST /register`, rồi lần ra nguyên nhân từ log bằng `traceId`; log không chứa mật khẩu, mã băm, token.

Kế hoạch triển khai ở `plan.md`; quy ước log ở `docs/global/4.convention/logging.md`.
