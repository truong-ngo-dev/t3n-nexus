# ADR-011 — DLQ theo consumer service, không theo topic

**Status:** Accepted

## Context

Với `MANUAL_IMMEDIATE` ack, một message Kafka xử lý thất bại và không được ack sẽ bị Kafka giao lại liên tục — chặn toàn bộ luồng phía sau nó trên cùng partition. Cần Dead Letter Queue (DLQ): thử lại N lần, nếu vẫn fail thì đẩy sang topic riêng và ack message gốc để luồng chính tiếp tục.

Câu hỏi cần trả lời: cần bao nhiêu DLQ topic, và cấp độ tách nào (per topic × consumer / per consumer group / per service / toàn hệ thống)?

Khung quyết định dùng cho từng consumer — tách DLQ riêng khi **bất kỳ 1** yếu tố sau khác nhau giữa 2 nhóm message:
- Mức độ hậu quả nghiệp vụ khi message vào DLQ (nghiêm trọng / cao / trung bình / thấp)
- Cách phản ứng vận hành (ai xử lý, làm gì)
- Ngưỡng cảnh báo / SLA phát lại

Rà toàn bộ consumer trong hệ thống (dựa trên `5. event-catalog.md`) theo khung này:

- **Nhóm Saga** (order/inventory/payment/fulfillment-service): mọi topic trong cùng 1 service đều cùng mức độ nghiêm trọng (Saga kẹt vĩnh viễn nếu không phát lại), cùng người xử lý → không cần tách theo topic.
- **customer-service** (3 topic, mức độ hậu quả khác nhau: mất hồ sơ khách hàng / mất điểm thưởng / trạng thái trung gian): vẫn gom 1 DLQ vì volume nhỏ, quy trình xử lý giống nhau, không có trường hợp nào được trì hoãn.
- **search-service** (9 topic, cùng cơ chế ghi Elasticsearch, lỗi thường do ES down ảnh hưởng cả 9 topic cùng lúc): 1 DLQ.
- **notification-service** (26 topic, cùng 1 code path `NotificationDispatchService.dispatch()`, cùng hậu quả "user không nhận thông báo"): 1 DLQ, cảnh báo theo ngưỡng tích luỹ thay vì per-message.
- **email-worker** — ngoại lệ bắt buộc: Tier1 (email giao dịch — user không dùng được app nếu fail) và Tier2 (email marketing — chỉ mất khuyến mãi) có hậu quả và SLA phát lại khác hẳn nhau (1 giờ vs cuối ngày), đã tách consumer group riêng sẵn (`email-worker-t1`, `email-worker-t2`) → **bắt buộc** tách DLQ theo tier, nếu không 500 email marketing lỗi sẽ che khuất 1 email xác minh bị kẹt.

Trong mọi trường hợp, header `kafka_dlt-original-topic` (tự động đính kèm bởi Spring Kafka `DeadLetterPublishingRecoverer`) đã đủ để trace về topic gốc khi điều tra — không cần tách DLQ chỉ để giữ metadata.

## Decision

**1 DLQ topic cho mỗi consumer service, không tách theo topic gốc** — trừ **email-worker tách theo tier** (giao dịch vs marketing) vì SLA và hậu quả khác nhau rõ rệt.

| Consumer | DLQ topic | Mức độ | Phát lại trong |
|---|---|---|---|
| order-service | `order-service.dlq` | Nghiêm trọng | 15 phút |
| inventory-service | `inventory-service.dlq` | Nghiêm trọng | 15 phút |
| payment-service | `payment-service.dlq` | Nghiêm trọng | 15 phút |
| fulfillment-service | `fulfillment-service.dlq` | Nghiêm trọng | 30 phút |
| customer-service | `customer-service.dlq` | Nghiêm trọng / Cao | 30 phút |
| review-service | `review-service.dlq` | Trung bình | 4 giờ |
| seller-service | `seller-service.dlq` | Trung bình | 4 giờ |
| shipper-service | `shipper-service.dlq` | Trung bình | 4 giờ |
| search-service | `search-service.dlq` | Trung bình | 2 giờ |
| notification-service | `notification-service.dlq` | Thấp | Cuối ngày |
| email-worker Tier1 | `email-worker-t1.dlq` | Cao | 1 giờ |
| email-worker Tier2 | `email-worker-t2.dlq` | Thấp | Cuối ngày |

Bảng đầy đủ (kèm số topic nguồn/service) và retry config chi tiết: `5. event-catalog.md` §DLQ Topics. Cách implement (partition key giữ nguyên, debug header, tránh vòng lặp vô hạn): `3.technical/dlq-implementation-notes.md`.

## Consequences

**+** Số lượng DLQ topic quản lý được (12, không phải 1 per-topic × consumer — sẽ ra hàng chục)
**+** Alerting đơn giản — 1 ngưỡng cảnh báo / service, không phải theo từng topic
**−** Khi 1 service có nhiều topic nguồn cùng vào 1 DLQ, phải dựa vào header `kafka_dlt-original-topic` để phân loại lúc điều tra — chấp nhận được vì header có sẵn tự động, không cần thao tác thêm
**−** Nếu sau này 1 service có nhóm topic với mức độ hậu quả lệch xa nhau (như email-worker hiện tại), phải tách DLQ riêng cho nhóm đó — đánh giá lại theo đúng khung quyết định ở `## Context` mỗi khi thêm consumer group mới

## Cập nhật doc liên quan

Thay thế hoàn toàn `2.architecture/6. dlq-strategy.md` (đã xoá) — nội dung phân tích đầy đủ (bao gồm ví dụ minh hoạ rủi ro COD reconciliation, retry config theo nhóm) nằm trong lịch sử git nếu cần tra lại.
