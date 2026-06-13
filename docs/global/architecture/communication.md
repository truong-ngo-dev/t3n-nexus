# Inter-Service Communication — t3n-nexus

---

## Nguyên Tắc

**Async-first** — mọi inter-service communication mặc định đi qua Kafka.
Sync chỉ được phép khi thỏa đồng thời cả 3 điều kiện:

1. Response cần ngay tại cùng request — không thể defer
2. Caller không thể tiếp tục mà không có kết quả từ callee
3. Latency của async không đáp ứng được SLA của operation

Hiện có **4 cặp sync** được phê duyệt. Mọi cặp mới phải qua ADR.

---

## Async — Kafka

Chi tiết topic, payload, consumers: `../design/events/event-catalog.md`

### Bắt buộc trên mọi service

| Rule                         | Mô tả                                                                                       | Tài liệu                    |
|------------------------------|---------------------------------------------------------------------------------------------|-----------------------------|
| Transactional Outbox         | Ghi `outbox_events` cùng DB transaction, Debezium push Kafka — không publish trực tiếp      | `adr/005-outbox-pattern.md` |
| Idempotent consumer          | Dedup theo `eventId`, lưu `processed_event_id` trước khi xử lý                              | —                           |
| Partition key = aggregate ID | Saga events dùng aggregate ID của flow (ví dụ `orderId`) — đảm bảo ordering trong cùng Saga | —                           |

---

## Sync — REST / gRPC

Chỉ 4 cặp dưới đây được phép gọi synchronous. Thêm cặp mới phải qua ADR.

| Caller                | Callee              | Protocol | Lý do                                                  |
|-----------------------|---------------------|----------|--------------------------------------------------------|
| `web-gateway`         | `oauth2-service`    | REST     | OAuth2 flow chuẩn HTTP                                 |
| `cart-service`        | `pricing-service`   | gRPC     | Computational, strongly typed contract, multi-consumer |
| `fulfillment-service` | `pricing-service`   | gRPC     | Cùng lý do                                             |
| `cart-service`        | `promotion-service` | REST     | Business logic cần rich error states                   |

Quyết định chọn gRPC cho `pricing-service`: `adr/006-grpc-pricing.md`

---

## Realtime

Không đi qua `api-gateway` hay `web-gateway` — EMQX và WebSocket Gateway tự quản lý connection.

| Channel   | Technology                        | Dùng cho                                          |
|-----------|-----------------------------------|---------------------------------------------------|
| MQTT      | EMQX                              | Chat (Customer ↔ Seller), shipper location update |
| WebSocket | Spring WebSocket + Redis Pub/Sub  | In-app notification → browser                     |
