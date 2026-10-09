# ADR-002 — Messaging Strategy: Kafka + RabbitMQ

**Status:** Superseded by ADR-008

## Context

Hệ thống cần hai loại async messaging với tính chất khác nhau:

1. **Domain events**: cần ordering, retention, replay, fan-out tới nhiều consumers — Saga choreography, Event Sourcing, Projection updates
2. **Notification delivery** (email, push): fire-and-forget, cần retry nhưng không cần ordering hay replay

Dùng Kafka cho cả hai có thể làm được nhưng không tối ưu — RabbitMQ đơn giản hơn đáng kể cho fire-and-forget pattern.

## Decision

- **Kafka**: domain events và Saga choreography. Retention 7 ngày, partition ordering, consumer group fan-out, Schema Registry (Avro). Mọi publish qua Transactional Outbox (ADR-005).
- **RabbitMQ**: `notification-service` → email/push. Dead letter queue, retry policy, simple routing. Không cần ordering hay replay.

## Consequences

**+** Kafka tối ưu cho event sourcing và Saga — retention, replay, partition ordering  
**+** RabbitMQ đơn giản hơn Kafka cho fire-and-forget — ít overhead hơn  
**−** Hai broker cần maintain (config, monitoring, upgrade)  
**−** Dev cần quen với cả hai API  
**−** Thêm operational complexity so với dùng Kafka cho tất cả
