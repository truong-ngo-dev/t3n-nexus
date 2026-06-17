# System Architecture Overview — t3n-nexus

Multi-vendor marketplace. Nghiệp vụ tối giản — độ phức tạp tập trung ở tầng kỹ thuật.
NFR và kịch bản kỹ thuật: `../requirement/requirement.md`

---

## C4 Level 1 — System Context

```
  [Storefront]  [Seller Portal]  [Admin Portal]      [Shipper*]
        \              |               /                   |
         \             |              /                    |
         [web-gateway (BFF + Gateway)]             [api-gateway]
                       │                                   │
                       └──────────────┬────────────────────┘
                                      │
                             [t3n-nexus Backend]
                             (23 microservices)
                                      │
               ┌──────────────────────┼──────────────────────┐
          [PostgreSQL]             [Kafka]                [Redis]
          [MongoDB]            [Schema Registry]     [Elasticsearch]
                                   [EMQX]
```

- Browser (3 Angular app) đi qua **web-gateway** — vừa là BFF vừa là gateway, không có hop api-gateway riêng
- Shipper đi qua **api-gateway** với Bearer token trực tiếp
- `*` Shipper chưa có mobile app — đang được giả lập bởi `simulator-service`

---

## C4 Level 2 — Service Groups

Chi tiết đầy đủ (runtime profile, dependencies, data ownership): `service-mapping.md`

| Group          | Services                                                                                                   | Count |
|----------------|------------------------------------------------------------------------------------------------------------|-------|
| Infrastructure | `api-gateway`, `web-gateway`, `oauth2-service`, `identity-service`                                         | 4     |
| Core Domain    | `catalog`, `inventory`, `warehouse`*, `cart`, `order`, `payment`, `fulfillment`, `return`                  | 8     |
| Supporting     | `seller`, `customer`, `shipper`, `pricing`, `promotion`, `review`, `search`                                | 7     |
| Generic        | `notification`, `websocket-gateway`, `chat`, `workflow`, `scheduler`, `reporting`, `simulator`†            | 7     |

`*` Phase 2 — chưa implement  
`†` Dev/staging only

---

## Bounded Context Map

Quan hệ upstream/downstream và integration patterns giữa các BC: `bounded-context-map.md`

---

## Key Architecture Decisions

Chi tiết từng quyết định: `adr/`

| #   | Quyết định                                             | ADR                                          |
|-----|--------------------------------------------------------|----------------------------------------------|
| 001 | Tách IAM thành oauth2-service + identity-service       | ` clss/adr/001-iam-services.md`              |
| 002 | ~~Kafka + RabbitMQ~~ — Superseded by ADR-008           | `adr/002-messaging-strategy.md`              |
| 003 | MQTT (EMQX) cho chat — không dùng STOMP over WebSocket | ` clss/adr/003-chat-mqtt.md`                 |
| 004 | Choreography Saga — không dùng Orchestration           | ` clss/adr/004-saga-choreography.md`         |
| 005 | Transactional Outbox cho mọi Kafka publish             | ` clss/adr/005-outbox-pattern.md`            |
| 006 | gRPC cho pricing-service — không dùng REST             | ` clss/adr/006-grpc-pricing.md`              |
| 007 | Frontend UI Design System                              | ` clss/adr/007-frontend-ui-design-system.md` |

---

## Cross-Cutting Concerns

| Concern              | Approach                                                                |
|----------------------|-------------------------------------------------------------------------|
| **Observability**    | OpenTelemetry (traces + metrics), structured logging với traceId/spanId |
| **Resilience**       | Resilience4j — Circuit Breaker, Retry, Bulkhead                         |
| **Security**         | ABAC in-process tại mỗi service — chi tiết: `security-architecture.md`  |
| **Idempotency**      | Redis-based idempotency key — bắt buộc cho payment + order              |
| **Schema Evolution** | Confluent Schema Registry + Avro — backward compatible                  |

---

## Tài liệu liên quan

| Tài liệu                        | Mô tả                                                         |
|---------------------------------|---------------------------------------------------------------|
| `service-mapping.md`            | Service inventory, inter-service dependencies, data ownership |
| `bounded-context-map.md`        | BC relationships, upstream/downstream, integration patterns   |
| `communication.md`              | Async/sync rules, Kafka standards, 4 sync pairs               |
| `security-architecture.md`      | AuthN/AuthZ model, trust boundaries, token model              |
| ` clss/deployment.md`           | Môi trường, Docker Compose profiles, AWS demo setup           |
| ` clss/tech-stack.md`           | Tech stack đầy đủ và lý do lựa chọn                           |
| `../requirement/requirement.md` | NFR, actors, bounded contexts, kịch bản kỹ thuật              |
