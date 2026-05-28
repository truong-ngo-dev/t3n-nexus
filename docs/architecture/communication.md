# Inter-Service Communication — t3n-nexus

## Async — Kafka (primary)

Toàn bộ domain event và Saga choreography đi qua Kafka.
Chi tiết topic, payload, consumers: `../design/events/event-catalog.md`

Chuẩn bắt buộc trên mọi service:

- **Transactional Outbox** — ghi `outbox_events` cùng DB transaction, Debezium push Kafka. Chi tiết: `adr/005-outbox-pattern.md`
- **Idempotency** tại consumer — dedup theo `eventId`, lưu `processed_event_id`
- **Partition key = `orderId`** cho Saga events — đảm bảo ordering trong cùng saga

## Sync — REST / gRPC (secondary)

Chỉ 4 cặp synchronous trong toàn hệ thống. Còn lại đều async qua Kafka.

| Caller                | Callee              | Protocol | Lý do                                                  |
|-----------------------|---------------------|----------|--------------------------------------------------------|
| `cart-service`        | `pricing-service`   | gRPC     | Computational, strongly typed contract, multi-consumer |
| `fulfillment-service` | `pricing-service`   | gRPC     | Cùng lý do                                             |
| `cart-service`        | `promotion-service` | REST     | Business logic, rich error states                      |
| `web-gateway`         | `oauth2-service`    | REST     | OAuth2 flow chuẩn HTTP                                 |

Quyết định chọn gRPC cho `pricing-service`: `adr/006-grpc-pricing.md`

## Realtime (ngoài API Gateway)

| Channel   | Technology                       | Dùng cho                                          |
|-----------|----------------------------------|---------------------------------------------------|
| MQTT      | EMQX                             | Chat (Customer ↔ Seller), shipper location update |
| WebSocket | Spring WebSocket + Redis Pub/Sub | In-app notification → browser                     |

EMQX xử lý connection management độc lập — không đi qua `api-gateway` hay `web-gateway`.

## Database Isolation

Mỗi service sở hữu **schema riêng** — không cross-schema query, không foreign key qua service boundary.
Cùng MySQL instance là operational convenience, không phải tight coupling.

| Store                    | Services                                                                                                       |
|--------------------------|----------------------------------------------------------------------------------------------------------------|
| MySQL (separate schemas) | catalog, inventory, order, payment, fulfillment, return, seller, customer, shipper, pricing, promotion, review |
| MongoDB                  | chat-service                                                                                                   |
| Elasticsearch            | search-service                                                                                                 |
| Redis                    | cart (guest session), notification pub/sub, idempotency, ABAC cache                                            |
