# Architecture Decision Records

Mỗi ADR là một quyết định kiến trúc — **không bao giờ sửa**, chỉ tạo mới.
Format tên: `{number}-{slug}.md`

---

| #   | Title                                  | Status   |
|-----|----------------------------------------|----------|
| 001 | Thiết kế IAM Services                  | Accepted |
| 002 | Messaging strategy — Kafka + RabbitMQ  | Superseded by 008 |
| 003 | Chat dùng MQTT (EMQX)                  | Accepted          |
| 004 | Saga choreography                      | Accepted          |
| 005 | Transactional Outbox cho Kafka publish | Accepted          |
| 006 | gRPC cho pricing-service               | Accepted          |
| 007 | Frontend UI Design System              | Accepted          |
| 008 | Drop RabbitMQ — Kafka cho tất cả       | Accepted          |
