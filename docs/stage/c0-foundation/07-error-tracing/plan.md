# Feature 07: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định thiết kế;
**Agent** viết phần thường, người rà), điều cần trao đổi trong bước đó (nếu có), và cách biết bước đã xong.

Quy ước log đã chốt ở `docs/global/4.convention/logging.md` (điểm đặt, mức, nội dung được ghi, log yêu cầu HTTP, định dạng, ngữ cảnh khi chạy
song song). Phạm vi, hiện trạng và việc giao từ feature khác ở `flow.md`. Feature này xây bộ tiện ích dùng chung trong `observability-starter`
và `common-web`, rồi áp dụng vào `oauth2-service`.

**Nguyên tắc thứ tự:** kiểm chứng nền kỹ thuật trước (những điều còn chưa chắc về Spring Boot và logback), rồi viết các lớp thuần theo test
(không phụ thuộc Spring), rồi nối vào Spring, rồi áp dụng vào dịch vụ và chạy thật, đo sau cùng.

**Ngoài feature này:** bộ lọc cho `api-gateway` (WebFlux) và truyền ngữ cảnh kiểu Reactor thuộc feature 06; ELK thuộc roadmap C2a; aspect hoặc
chú thích tự gắn tên thao tác, lấy mẫu log thành công chưa làm.

## Điều kiện bắt đầu

Quy ước `logging.md` đã chốt. Không phụ thuộc feature khác. Bước đo overhead (bước 11) phụ thuộc feature 08.

## Bước 1. Xác minh nền kỹ thuật (thử nghiệm ngắn) · Agent, Người rà

**Trạng thái:** Xong một phần · `2026-10-09`. Đã kiểm: log `ecs` của Boot 4.0.6 ra JSON; khóa MDC có dấu chấm ra đối tượng lồng; khóa không dấu chấm ra cấp cao;
`traceId`, `spanId` ra nguyên tên và đổi được bằng `logging.structured.json.rename`; cặp khóa-giá trị của SLF4J được đưa vào; `logback-spring.xml` có bộ ghi console tự
khai báo làm mất định dạng có cấu trúc (kể cả bản đã sao vào `target/classes`).

Còn phải kiểm, ghi kết quả vào `tech.md`:
- `log.info("… {}", x)` kiểu cổ điển và `log.error("…", ex)` ra đúng JSON, `error.type`, `error.message`, `error.stack_trace`.
- Bọc bộ ghi có cấu trúc của Boot bằng `AsyncAppender` và hành vi khi hàng đợi đầy.
- Gắn `StructuredLoggingJsonMembersCustomizer` qua `logging.structured.json.customizer` để sửa `message` và `error.stack_trace`.
- Đọc chú thích trên handler từ trong bộ lọc sau khi chuỗi lọc đã chạy.
- `@Async` có tự truyền `traceId` và `spanId` hay không (nhánh con giữ nguyên cả hai, không cần span riêng).

Xong khi: mỗi điều trên có kết luận đúng hoặc sai kèm cách xử lý.

## Bước 2. Mặc định log JSON và bỏ cấu hình log trùng lặp · Agent, Người rà

**Trạng thái:** Chưa làm

Mở rộng `ObservabilityEnvironmentPostProcessor` đặt mặc định (mức ưu tiên thấp, dịch vụ ghi đè được): `logging.structured.format.console=ecs`,
`logging.structured.ecs.service.name` và `environment` lấy từ tên ứng dụng và profile, `logging.structured.json.rename` cho `traceId` thành `trace.id` và `spanId`
thành `span.id`. Ở `oauth2-service` bỏ `logback-spring.xml` tự khai báo (và dọn bản trong `target`), để Boot tự khởi tạo. Dev có thể đổi về dạng chữ bằng
thuộc tính.

Xong khi: khởi động `oauth2-service`, dòng log là JSON có `@timestamp`, `log.level`, `service.name`, `trace.id` khi có trace; bản chữ vẫn bật được ở dev.

## Bước 3. `LogContext` và truyền ngữ cảnh · Người rồi Agent

**Trạng thái:** Chưa làm

`LogContext`: `put(khóa, giá trị)` tự thêm tiền tố `labels.`, `user(id)`, `operation(tên)`, `remove`, `clear`, `scope(...)` khôi phục giá trị cũ khi đóng,
`snapshot()`, `wrap(Runnable/Callable)`; cộng `MdcTaskDecorator` cho executor do Spring quản lý. Giới hạn số khóa (khoảng 10), cắt giá trị dài, bỏ ký tự điều khiển.

Nội dung cần trao đổi: danh sách khóa dùng chung giữa các dịch vụ (ghi vào `logging.md`); hành vi khi vượt giới hạn khóa (bỏ khóa mới hay báo lỗi).

Test đơn vị: tiền tố đúng; vượt giới hạn; `scope` khôi phục kể cả khi lồng nhau và khi có lỗi; `wrap` truyền ngữ cảnh sang luồng khác và nhánh con thêm khóa không
ảnh hưởng luồng cha; luồng trong pool không giữ khóa sau khi xong.

Xong khi: test xanh; lớp không phụ thuộc Spring (trừ `MdcTaskDecorator`).

## Bước 4. `BodySanitizer` · Agent, Người rà

**Trạng thái:** Chưa làm

Dây chuyền xử lý nội dung (lớp thuần): kiểm loại (JSON, chữ; nhị phân chỉ ghi loại và kích thước), đệm tối đa 4 KB, che trường theo tên ở mọi độ sâu không phân biệt
hoa thường, danh sách trường được phép, quét mẫu bí mật, cắt giá trị và mảng, đóng gói thành một chuỗi. Ngưỡng và danh sách tên cấu hình qua thuộc tính. Lỗi bên trong không
văng ra: trả chuỗi "không ghi được nội dung".

Test bảng ca: lồng nhau, mảng, tên trường khác hoa thường, `Bearer …`, JWT, mã băm bcrypt, giá trị quá dài, mảng dài, không phải JSON, nhị phân, nội dung hỏng,
danh sách trường được phép loại hết trường lạ.

Xong khi: bảng ca xanh; không ca nào để lọt mật khẩu hay token.

## Bước 5. `SecretScrubber` · Agent, Người rà

**Trạng thái:** Chưa làm

Lưới an toàn cuối ở đầu ra: quét mẫu `Bearer …`, JWT, mã băm trong `message` và `error.stack_trace`, cài bằng `StructuredLoggingJsonMembersCustomizer`. Dùng cùng bộ mẫu
với `BodySanitizer` (một nơi định nghĩa).

Test: một dòng log chứa token trong message và trong stack trace được che trong đầu ra JSON thật.

Xong khi: test đọc đầu ra thật xanh.

## Bước 6. `RequestLoggingFilter` · Agent, Người rà

**Trạng thái:** Chưa làm

Bộ lọc servlet ngoài cùng (`OncePerRequestFilter`), `try/finally` dọn MDC. Bọc yêu cầu JSON ghi để đệm tối đa 4 KB; ghi một dòng hoàn tất ở INFO gồm
`event.dataset=http.request`, `http.request.method`, `http.route`, `url.path`, `http.response.status_code`, `event.duration`, kích thước vào và ra, `client.ip`,
`user.id`, `error.code` khi lỗi. Nội dung yêu cầu đã xử lý chỉ ở 5xx và 4xx do kiểm đầu vào hoặc nghiệp vụ (trừ 401, 404), theo danh sách trường được phép
đọc từ chú thích `@LogRequestFields` trên handler. Dòng "vào" ở DEBUG. Phản hồi chỉ ghi `error.code`. Header không ghi. Đặt các trường `http.*` vào MDC ngay trước
khi ghi rồi gỡ.

Test với MockMvc đọc đầu ra thật:
- 201: một dòng hoàn tất, không nội dung, đủ trường.
- 400: dòng hoàn tất kèm nội dung đã xử lý, `password` thành `***` hoặc bị bỏ.
- 500: dòng hoàn tất kèm nội dung và (từ bước 7) có dòng ERROR riêng.
- 401, 429 bị chặn trước controller vẫn có dòng hoàn tất.
- Dòng DEBUG "vào" xuất hiện trước các dòng bên trong khi bật DEBUG.
- Lỗi trong bộ xử lý nội dung không làm hỏng yêu cầu.
- MDC sạch sau mỗi yêu cầu.

Xong khi: test xanh.

## Bước 7. Quy ước lỗi chung và `traceId` trong phản hồi · Agent, Người rà

**Trạng thái:** Chưa làm

Sửa `GlobalExceptionHandler` ở `common-web`: nhánh 400 với `code` `MALFORMED_REQUEST` cho nội dung không đọc được (không ghi mức lỗi nghiêm trọng); lỗi nghiệp vụ ghi
INFO, lỗi hệ thống ghi ERROR kèm stack trace; thêm trường `traceId` vào `ApiResponse` lỗi lấy từ MDC (không phụ thuộc thư viện trace). Các dịch vụ khác không phải
sửa vì thay đổi mang tính cộng thêm.

Test: gửi JSON hỏng tới `POST /register` trả 400 `MALFORMED_REQUEST` (hiện trả 500); phản hồi lỗi có `traceId` khớp `trace.id` của dòng log.

Xong khi: test xanh và các dịch vụ khác vẫn biên dịch.

## Bước 8. `StepTimer` · Agent, Người rà

**Trạng thái:** Chưa làm

Hai dạng: đánh dấu sau mỗi bước (`timer.step("tên", kích_thước)`) và bọc khối (`timer.step("tên", () -> …)`), kèm `try-with-resources` ghi tổng. Ghi ở DEBUG, kiểm
DEBUG một lần lúc `start`; tắt thì không làm gì và không đọc đồng hồ. Một luồng.

Test: đo đúng thứ tự và cộng dồn; DEBUG tắt không ghi và không gọi đồng hồ; bọc khối vẫn ghi thời gian khi khối ném lỗi rồi ném tiếp.

Xong khi: test xanh.

## Bước 9. Ghi bất đồng bộ và đổi mức log lúc chạy · Người rồi Agent

**Trạng thái:** Chưa làm

Đoạn cấu hình logback dùng chung trong starter: bọc bộ ghi có cấu trúc bằng `AsyncAppender` có hàng đợi giới hạn (đầy thì bỏ dòng mức thấp), mỗi dịch vụ chỉ `include`.
Mở endpoint `loggers` của actuator ở cổng quản trị riêng (`management.server.port`), không công khai.

Nội dung cần trao đổi: kích thước hàng đợi và mức bị bỏ khi đầy; cổng quản trị nghe địa chỉ nội bộ.

Test: đổi mức log của một lớp lên DEBUG qua endpoint thì dòng DEBUG xuất hiện mà không khởi động lại; endpoint không truy cập được từ cổng công khai.

Xong khi: test xanh.

## Bước 10. Áp dụng vào `oauth2-service` và chạy thật · Người

**Trạng thái:** Chưa làm

Bỏ chỗ tự in `traceId` trong nội dung log (như `RegisterUser`); áp quy ước cho phần đã thiết kế lại (đăng ký). Khai báo `@LogRequestFields` cho `POST /register` (chỉ cho
`email`). Chạy `run.http` của feature 01 và đọc đầu ra thật.

Kiểm: dòng hoàn tất của 201, 409, 400 đúng; ca 400 có nội dung đã xử lý với `password` không xuất hiện; mật khẩu, mã băm và token không xuất hiện ở bất kỳ dòng nào; `trace.id` khớp
`traceId` trong phản hồi lỗi. Đây cũng là phần kiểm log còn nợ của bước "Chạy thật" ở feature 01.

Xong khi: đầu ra thật đúng với danh sách trên.

## Bước 11. Đo overhead · Người

**Trạng thái:** Chưa làm. Phụ thuộc feature 08.

Đo trần của điểm cuối rỗng khi tắt rồi bật bộ lọc, và khi bật ghi bất đồng bộ; ghi chênh lệch và điều kiện đo vào `nfr.md`. Mục tiêu thiết kế: dưới nửa mili-giây mỗi yêu cầu
(hiện là Chỉ thiết kế).

Xong khi: nhãn ở `nfr.md` đổi sang Đã đo kèm điều kiện đo.

## Bước 12. Đóng feature · Agent, Người rà

**Trạng thái:** Chưa làm

Cập nhật `logging.md` cho khớp mã (mục 6, 7, 9); danh sách khóa dùng chung; ghi điều học được vào `../00-features.md`; kiểm lại tham chiếu.

Xong khi: không còn chỗ nào trong tài liệu lệch với mã.
