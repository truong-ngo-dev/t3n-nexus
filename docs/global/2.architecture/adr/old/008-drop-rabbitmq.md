# ADR-008 — Drop RabbitMQ: Kafka cho tất cả Messaging

**Status:** Accepted  
**Supersedes:** ADR-002

## Context

ADR-002 quyết định dùng RabbitMQ cho notification delivery (email/push) với lý do: fire-and-forget, không cần ordering hay replay, RabbitMQ đơn giản hơn cho pattern này.

Khi implement Transactional Outbox với CDC + Debezium (ADR-005), toàn bộ publish đều đi qua Kafka. Để route sang RabbitMQ cần thêm một Debezium sink connector hoặc một job trung gian bridge Kafka → RabbitMQ — thêm complexity không justify so với lợi ích.

Ngoài ra:
- RabbitMQ không scale tốt ở high throughput so với Kafka
- Không có service nào trong hệ thống cần đặc trưng riêng của RabbitMQ (AMQP exchange model, topic routing)
- Maintain hai broker tăng operational burden

## Decision

Bỏ RabbitMQ. **Kafka là message broker duy nhất** trong hệ thống.

`notification-service` consume trực tiếp từ Kafka, gọi SMTP/FCM trực tiếp cho email/push.
Retry dùng Kafka consumer retry pattern — không cần DLX của RabbitMQ.

## Consequences

**+** Một broker duy nhất — đơn giản hơn về operational, monitoring, config  
**+** Debezium Outbox chỉ cần route vào Kafka — không cần thêm connector hay job bridge  
**+** Kafka scale tốt hơn RabbitMQ ở notification volume (~1,000,000 events/ngày)  
**−** Retry/backoff phải implement ở consumer level thay vì dùng RabbitMQ retry policy sẵn có  
**−** Kafka overhead lớn hơn RabbitMQ cho simple fire-and-forget (acceptable trade-off)  
