# ADR-0010: Một hàng chờ lỗi cho mỗi dịch vụ nhận

> **Trạng thái:** Accepted · **Ngày:** `2026-10-26` · **Phạm vi:** mọi BC nhận event bằng `event-consumer-starter` · **Thay thế một phần:** ADR-0008 quyết định 5 (tên chủ đề hàng chờ lỗi)

## Bối cảnh

ADR-0008 (quyết định 5) ghi hàng chờ lỗi là `<chủ đề gốc>.dlq`, đúng mặc định của `DeadLetterPublishingRecoverer`, và `event-consumer-starter` cài như vậy. Cùng lúc đó `adr/old/011-dlq-per-service-strategy.md` (Accepted, có trước) và bảng "DLQ Topics" ở `5. event-catalog.md` chọn **một DLQ cho mỗi dịch vụ nhận**, kèm lý do. ADR-0008 không đối chiếu quyết định cũ này, nên hai quy ước tồn tại song song.

Mặc định theo chủ đề không gây hại khi dịch vụ nhận một chủ đề (như `audit-service`). Nhưng `notification-service` nhận 26 chủ đề, `search-service` 9, `order-service` 8; theo chủ đề thì mỗi dịch vụ đẻ ra bấy nhiêu hàng chờ lỗi, mỗi cái cần được tạo, giám sát và đặt ngưỡng cảnh báo riêng.

## Quyết định

1. **Mặc định: `<spring.application.name>.dlq`** cho mọi thông điệp hết lượt hoặc không thể thử lại của dịch vụ, bất kể chủ đề gốc. Ví dụ `audit-service.dlq`.
2. **Ghi đè bằng `app.event-consumer.dlq-topic`** khi dịch vụ phải tách. Khung quyết định giữ nguyên từ ADR-011: tách chỉ khi hai nhóm thông điệp khác nhau về hậu quả nghiệp vụ, cách xử lý, hoặc hạn phát lại. Ví dụ duy nhất đã biết: `email-worker` tách theo tier (`email-worker-t1.dlq`, `email-worker-t2.dlq`).
3. **Chủ đề gốc không mất:** tiêu đề `kafka_dlt-original-topic` (Spring tự thêm), cùng phân vùng và offset gốc. Khóa và giá trị giữ nguyên; tiêu đề `x-dlq-error-class` giữ nguyên như ADR-0008.
4. **Bộ đếm `events.dead_lettered` vẫn gắn nhãn theo chủ đề gốc**, nên cảnh báo theo chủ đề làm được mà không cần thêm hàng chờ lỗi.
5. **Thiếu cả tên dịch vụ lẫn cấu hình thì khởi động lỗi** (không tự đoán tên chủ đề).
6. Các phần còn lại của ADR-0008 quyết định 5 giữ nguyên: thử lại giãn cách hữu hạn, ERROR `EVENT_DEAD_LETTERED`, thông điệp kế vẫn chạy, xử lý lại thủ công.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| `<chủ đề gốc>.dlq` (ADR-0008 cũ) | Số hàng chờ lỗi nhân theo số chủ đề nguồn; mỗi cái cần tạo, giám sát, ngưỡng cảnh báo riêng, trong khi lỗi thường ảnh hưởng cả dịch vụ cùng lúc (ví dụ kho lưu trữ chết) |
| Một DLQ toàn hệ thống | Lẫn thông điệp của các dịch vụ có hậu quả và hạn phát lại khác hẳn nhau; không rõ ai sở hữu |
| Tách theo nhóm tiêu thụ ngay từ đầu | Chưa có dịch vụ C1a nào cần; áp dụng khi khung quyết định đòi (quyết định 2) |

## Hệ quả

- Khi điều tra, lọc thông điệp theo tiêu đề `kafka_dlt-original-topic`. Chấp nhận như ADR-011.
- Phân vùng đích: nhà sản xuất chọn theo khóa (`-1`), nên hàng chờ lỗi cần đủ phân vùng cho thông điệp của mọi chủ đề nguồn; chủ đề được tạo cùng hạ tầng của dịch vụ.
- Phát lại thủ công đọc `kafka_dlt-original-topic` để biết trả về đâu.
- Dịch vụ cũ còn cấu hình tay `app.kafka.topic.dlq=<dịch-vụ>.dlq` đã cùng quy ước này; khi chuyển sang starter không cần đổi tên chủ đề.
- Thay đổi so với ADR-0008 chỉ là tên chủ đề đích: test của starter và `audit-service` đổi theo, không cần dựng lại stack Kafka thật.

## Tham khảo

- `adr/old/011-dlq-per-service-strategy.md` (lý do và khung quyết định tách), `3.technical/dlq-implementation-notes.md`.
- ADR-0008 (hộp thư và nhận event).
