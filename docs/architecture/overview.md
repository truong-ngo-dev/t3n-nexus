# System Architecture Overview — t3n-nexus

---

## C4 Level 1 — System Context

```
[Storefront]  [Seller Portal]  [Admin Portal]     [Shipper Mobile]
      \              |               /                    |
       \             |              /                     |
        +----- [Web Gateway BFF] --+              [Direct Token]
                     |                                    |
              [API Gateway]  <-----------------------------+
                     |
              [t3n-nexus Backend]
                     |
        +------------+------------+
        |            |            |
    [MySQL]      [Kafka]      [Redis]
    [MongoDB]  [Schema Reg]  [Elasticsearch]
```

---

## C4 Level 2 — Containers (Service Topology)

### Infrastructure Layer

| Service            | Role                                                       |
|--------------------|------------------------------------------------------------|
| `api-gateway`      | Spring Cloud Gateway — routing, rate limiting, auth filter |
| `web-gateway`      | BFF — session cookie, token exchange cho 3 Angular app     |
| `oauth2-service`   | Spring Authorization Server — authn, session, device       |
| `identity-service` | User profile, roles, ABAC policies                         |

### Communication Patterns

| Pattern              | Dùng cho                                              |
|----------------------|-------------------------------------------------------|
| **Synchronous REST** | Client → Gateway → Service, Service → Service (query) |
| **Async Kafka**      | Domain events, Saga choreography                      |
| **MQTT (EMQX)**      | Chat, Shipper location tracking                       |
| **WebSocket**        | In-app notification (push từ server)                  |
| **Temporal**         | Long-running workflows — dispute, scheduled jobs      |

### Data Isolation

- Mỗi service có **database riêng** — không share schema
- `MySQL`: catalog, inventory, order, payment, fulfillment, return, seller, customer, shipper, pricing, promotion, review
- `MongoDB`: chat-service (message store)
- `Elasticsearch`: search-service (read model)
- `Redis`: cart (guest session), notification pub/sub, idempotency keys, ABAC policy cache

---

## Key Architecture Decisions

> Chi tiết từng quyết định tại `adr/`

| # | Quyết định                                             | ADR                             |
|---|--------------------------------------------------------|---------------------------------|
| 1 | Tách IAM thành oauth2-service + identity-service       | `adr/001-iam-services.md`       |
| 2 | Kafka cho domain events, RabbitMQ cho email/push       | `adr/002-messaging-strategy.md` |
| 3 | MQTT (EMQX) cho chat — không dùng STOMP over WebSocket | `adr/003-chat-mqtt.md`          |
| 4 | Choreography Saga — không dùng Orchestration           | `adr/004-saga-choreography.md`  |
| 5 | Transactional Outbox cho mọi Kafka publish             | `adr/005-outbox-pattern.md`     |

---

## Cross-Cutting Concerns

| Concern              | Approach                                                                |
|----------------------|-------------------------------------------------------------------------|
| **Observability**    | OpenTelemetry (traces + metrics), structured logging với traceId/spanId |
| **Resilience**       | Resilience4j — Circuit Breaker, Retry, Bulkhead                         |
| **Security**         | ABAC — policy evaluation embedded tại mỗi service                       |
| **Idempotency**      | Redis-based idempotency key — bắt buộc cho payment + order              |
| **Schema Evolution** | Confluent Schema Registry + Avro — backward compatible                  |

---

## Tài liệu liên quan

- Tech stack đầy đủ: `tech-stack.md`
- Service mapping: `service-mapping.md`
- Inter-service communication + database isolation: `communication.md`
- Deployment strategy: `deployment.md`
- NFR (latency SLAs, data volume): `../requirement.md`
