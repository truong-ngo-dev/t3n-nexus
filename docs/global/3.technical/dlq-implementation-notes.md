# Implementation Notes — Dead Letter Queue

> Cách implement đúng DLQ cho Kafka consumer trong hệ thống. Quyết định "bao nhiêu DLQ, tách theo gì": `2.architecture/adr/011-dlq-per-service-strategy.md`. Danh sách DLQ topic hiện có: `2.architecture/5. event-catalog.md` §DLQ Topics.

---

## 1. Phân loại thất bại trước khi cấu hình retry

**Thất bại tạm thời** (sẽ tự khắc phục nếu thử lại) — DB timeout, Redis/SMTP tạm ngưng, rate limit hit. Retry có ý nghĩa.

**Thất bại vĩnh viễn** (thử lại bao nhiêu cũng không khác) — message sai định dạng, lỗi lập trình, dữ liệu nghiệp vụ không hợp lệ. Retry chỉ lãng phí thời gian — cấu hình bỏ qua retry cho các loại này (`NotRetryableException`, `DeserializationException`) để vào DLQ ngay.

## 2. Thứ tự message được bảo toàn tự động

Kafka dùng `orderId` làm partition key cho Saga event (đảm bảo mọi message cùng 1 order được xử lý đúng thứ tự). `DeadLetterPublishingRecoverer` (Spring Kafka) mặc định giữ nguyên partition key khi đẩy message vào DLQ — thứ tự Saga được bảo toàn ngay cả trong DLQ, không cần cấu hình thêm.

## 3. Thông tin debug đính kèm tự động

Mỗi message trong DLQ có sẵn các header sau — đây là lý do không cần tách nhiều DLQ topic chỉ để lưu metadata phân loại:

```
kafka_dlt-original-topic          → topic gốc message đến từ đâu
kafka_dlt-original-offset         → vị trí chính xác trong topic gốc
kafka_dlt-original-consumer-group → consumer group nào thất bại
kafka_dlt-exception-message       → thông báo lỗi
kafka_dlt-exception-stacktrace    → stack trace đầy đủ
```

## 4. Tránh vòng lặp vô hạn khi phát lại

Thất bại vĩnh viễn nếu được auto-retry sẽ tạo vòng lặp `topic gốc → fail → DLQ → retry → topic gốc → fail → ...`. Giai đoạn hiện tại: **phát lại thủ công** sau khi kỹ sư xác nhận và khắc phục nguyên nhân gốc. Nếu tự động hoá sau này, bắt buộc track số lần phát lại và giới hạn tối đa.

## 5. Cấu hình retry theo nhóm

| Nhóm | Retry | Lý do |
|---|---|---|
| Saga consumers (order/inventory/payment/fulfillment) | 3 lần, cách 2 giây | Phát hiện sớm quan trọng — Saga kẹt lâu nguy hiểm |
| customer-service | 3 lần, cách 2 giây | Cùng logic — phát hiện nhanh |
| search-service | 3 lần, cách 5 giây | Elasticsearch cần thêm thời gian ổn định |
| notification-service | 3 lần, cách 2 giây | DB fail thường phục hồi nhanh |
| email-worker Tier1 | 3 lần, cách 2 giây | Email xác minh cần phát hiện lỗi nhanh |
| email-worker Tier2 | 3 lần, cách 60 giây | SMTP bulk mail thường phục hồi chậm |

## 6. Pattern kết hợp DLQ + Saga idempotency

Khi consumer thuộc nhóm Saga, DLQ không tự giải quyết được duplicate-processing khi replay hay Saga bị kẹt vĩnh viễn nếu không ai replay — 2 vấn đề này cần thêm Redis idempotency + state machine check + Saga timeout. Chi tiết: `saga-dlq-integration.md`.
