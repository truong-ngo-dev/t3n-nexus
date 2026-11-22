# ADR-0008: Hộp thư event và nhận event

> **Trạng thái:** Accepted · **Ngày:** `2026-10-24` (đề xuất `2026-10-23`) · **Phạm vi:** mọi BC phát hoặc nhận event qua bus (từ C1a: Người bán, Danh mục hàng, Nhật ký kiểm toán)
>
> **Đã bị thay một phần bởi ADR-0010** (`2026-10-26`): tên chủ đề hàng chờ lỗi ở quyết định 5 nay là `<spring.application.name>.dlq`, không còn `<chủ đề gốc>.dlq`. Phần còn lại của file giữ nguyên.
>
> Chuyển Accepted sau khi chạy thật ở feature 01 (kết quả ở mục "Đã kiểm khi chạy thật" cuối file; chi tiết ở `01-event-outbox/tech.md`, mục "Kết quả chạy thật") và người chốt các câu hỏi Q1 đến Q5.

## Bối cảnh

Từ C1a các BC nói chuyện bằng event (`stage.md` C1a mục 5). Hai nguy cơ chung: (1) thay đổi dữ liệu đã commit mà event mất, hoặc event đi mà thay đổi bị rollback (phá R12); (2) event tới bên nhận hai lần, sai thứ tự, hoặc một event hỏng chặn cả dòng. Mọi BC sẽ gặp, nên cần một quyết định chung chứ không để từng BC tự chọn.

## Quyết định

1. **Hộp thư event (transactional outbox).** BC phát ghi event vào bảng `outbox_events` trong cơ sở dữ liệu của mình, **cùng giao dịch** với thay đổi dữ liệu. Cổng ghi hộp thư từ chối khi không có giao dịch đang chạy. Không phát lên bus trực tiếp từ use case.
2. **Chuyển ra bus bằng CDC** (Debezium, `EventRouter`): đọc nhật ký giao dịch cơ sở dữ liệu, mỗi BC phát một connector. Giao ít nhất một lần.
3. **Khung thông điệp chung** (JSON): `eventId`, `eventType`, `occurredAt`, `aggregateId`, `aggregateType`, `schemaVersion`, `traceId`, `spanId` (hai mã truy vết tùy chọn), `payload`. Khóa thông điệp là `aggregateId`, ghi dưới dạng JSON (có dấu nháy, ví dụ `"K4"`) vì connector dùng `JsonConverter` cho khóa; bên nhận đọc khóa phải tính điều này. Chỉ thêm trường; đổi nghĩa thì tăng `schemaVersion`. Mã event là UUID v7 (ADR-0001), nằm ở cột `event_id` có ràng buộc duy nhất của hàng hộp thư. Mọi BC dùng cùng một hình bảng hộp thư (cùng tên và kiểu cột), nội dung là văn bản chứa JSON.
4. **Nhận ít nhất một lần, ack sau khi giao dịch của handler commit.** Khử trùng là việc của handler, bằng ràng buộc ở lưu trữ hoặc giá trị tuyệt đối kèm phiên bản (`idempotency-layering.md`); thư viện không giữ bảng "đã xử lý" chung. Handler không dựa vào thứ tự giữa các event.
5. **Lỗi nhận:** lỗi tạm thử lại giãn cách tăng dần, hữu hạn; hết lượt hoặc lỗi không thể thử lại thì chuyển sang chủ đề `<chủ đề gốc>.dlq` kèm lý do, ghi ERROR mã `EVENT_DEAD_LETTERED` và tăng bộ đếm; thông điệp kế vẫn chạy. Xử lý lại từ hàng chờ lỗi là thủ công.
6. **Thứ tự:** bảo đảm cho các event của một thực thể **trong cùng một chủ đề** (cùng khóa, cùng phân vùng). Không có thứ tự giữa hai chủ đề dù cùng thực thể, và không giữa các thực thể; event cần thứ tự nên chung chủ đề, còn lại bên nhận dựa vào phiên bản hoặc giá trị tuyệt đối.
7. **Cấu hình connector:** thông điệp là JSON trần (khai lớp converter và `schemas.enable=false` ở chính connector); mỗi connector có `topic.prefix` và khe sao chép riêng; `snapshot.mode=no_data`, đăng ký connector **trước** khi BC ghi hàng đầu tiên.
8. **Giữ hộp thư:** hàng không xóa khi chuyển; dọn chạy qua Lập lịch (C2b).

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Phát lên bus trực tiếp sau commit | Tiến trình chết giữa commit và gửi thì mất event; phát trước commit thì event đi mà thay đổi rollback |
| Poller trong dịch vụ quét bảng | Phải khóa hàng khi nhiều bản chạy, độ trễ theo chu kỳ quét, tải đọc lặp. Giữ làm phương án lui |
| Cổng ghi hộp thư tự mở giao dịch khi chưa có | Im lặng phá R12 khi use case quên giao dịch |
| Bảng `processed_events` chung ở thư viện | Thêm một lần ghi cho mọi event, trong khi các BC đã có cách khử trùng rẻ và đúng hơn từ ràng buộc nghiệp vụ |
| Thử lại vô hạn | Một thông điệp hỏng chặn cả phân vùng |
| Redis làm trọng tài khử trùng | Mất khóa thì mất tính đúng |

## Hệ quả

- Event đến muộn hơn lúc commit (CDC: đo được khoảng 0,5 đến 2 giây khi đã chạy ổn định, 11 đến 14 giây cho event đầu tiên sau khi connector hoặc nhóm tiêu thụ mới khởi động) và có thể trùng; mọi bên nhận phải khử trùng và chịu được độ trễ (bản sao trạng thái người bán có độ trễ ngắn sau lúc duyệt, đã chấp nhận ở `stage.md` 2.4).
- Mỗi BC phát cần một bảng hộp thư, một connector và một khe sao chép; connector dừng lâu làm cơ sở dữ liệu giữ nhật ký giao dịch.
- Hàng hộp thư chỉ tăng đến C2b.
- Cảnh báo ở C1a là log và bộ đếm; chưa có kênh báo tới người trực.
- Định dạng JSON ở C1a; nâng Avro kèm Schema Registry cần ADR mới.

## Đã kiểm khi chạy thật (`2026-10-24`)

Stack thử riêng (Postgres, Kafka, Kafka Connect), ba thư viện chạy nối nhau; 30 kiểm tra đạt trong một lượt sạch (`01-event-outbox/tech.md`, mục "Kết quả chạy thật").

- ~~`EventRouter` mở rộng cột nội dung dạng văn bản, đặt khóa và chủ đề đúng.~~ Đạt (bước 2 và bước 7).
- ~~Hàng ghi trước lúc connector được tạo không bị bỏ sót.~~ **Bị sót** với `no_data`; điều kiện đăng ký trước khi ghi hàng đầu tiên đã thêm vào quyết định 7.
- ~~Khởi động lại connector giữa chừng: không mất event, bên nhận khử trùng.~~ Đạt: restart khi mới nhận 7 trong 120 event, đủ 120, đúng thứ tự, không trùng. Dừng connector hoặc Kafka rồi chạy lại sau khi hàng đã tích cũng đạt.
- ~~Thông điệp hỏng đi vào hàng chờ lỗi và không chặn thông điệp sau.~~ Đạt: event hỏng chạy đúng 4 lần rồi vào `<chủ đề>.dlq`, event sau vẫn xử lý (`setCommitRecovered(true)` hoạt động). Giết bên nhận giữa chừng rồi chạy lại: không mất, không xử lý lại event nào.
- ~~Mã truy vết nối được log của hai bên.~~ Đạt.
