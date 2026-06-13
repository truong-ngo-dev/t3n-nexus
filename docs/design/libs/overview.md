# Shared Libraries Overview — t3n-nexus

Các libs nằm tại `services/libs/`. Mỗi lib là một Maven module với `packaging=jar`.
Chi tiết thiết kế từng lib tại file riêng trong thư mục này.

---

## Danh sách libs

| Lib                     | Layer          | Scope                                            | Thay đổi              |
|-------------------------|----------------|--------------------------------------------------|-----------------------|
| `common-domain`         | Shared Kernel  | DDD primitives, shared Value Objects             | Cực kỳ hiếm           |
| `common-events`         | Integration    | Event envelope — metadata chỉ, không có payload  | Hiếm                  |
| `abac-engine`           | Security       | Policy evaluation engine, dynamic policy loading | Khi đổi policy engine |
| `outbox-starter`        | Infrastructure | Transactional Outbox auto-config                 | Hiếm                  |
| `observability-starter` | Infrastructure | OTel, structured logging, Micrometer             | Hiếm                  |
| `idempotency-support`   | Infrastructure | Redis-based idempotency key checking             | Hiếm                  |
| `common-web`            | Infrastructure | REST conventions, error format, filters          | Hiếm                  |
| `security-commons`      | Security       | JWT utils, Spring Security + ABAC bridge         | Khi đổi auth flow     |

---

## `common-domain`

**Scope — chỉ được chứa:**
- DDD base class: `AggregateRoot<ID>`, `Entity<ID>`, `ValueObject`, `DomainEvent`
- Shared Value Objects **không có business rule**: `Money`, `Address`, `PhoneNumber`, `Email`

**Không được chứa:** entity hoặc business logic của bất kỳ BC nào.

`Money` implement theo JSR-354 — immutable, có `Currency`, hỗ trợ arithmetic an toàn.

---

## `common-events`

**Scope — chỉ chứa event envelope:**

```java
public record EventEnvelope(
    String eventId,
    String eventType,
    String sourceService,
    String correlationId,
    String traceId,
    Instant occurredAt,
    int schemaVersion,
    Object payload
) {}
```

Event payload cụ thể (`OrderCreatedPayload`...) nằm **trong từng BC**, không ở lib này.
Schema quản lý bởi Confluent Schema Registry.

---

## `abac-engine`

Drools runtime embedded tại mỗi service — local PDP (Policy Decision Point).

**Dynamic policy loading**: policy definitions load từ `identity-service` hoặc config source,
cache local với TTL + invalidation event qua Kafka.
Không hardcode policy trong lib — thay đổi policy không cần redeploy service.

---

## `outbox-starter`

Spring Boot autoconfiguration cho Transactional Outbox pattern.

Hỗ trợ 2 strategy (chọn qua property):
- `POLLING` — đơn giản, scheduler poll bảng outbox
- `CDC` — Debezium đọc binlog, zero-latency, preferred cho production

---

## `observability-starter`

**Critical** — không có lib này, debug Saga qua nhiều service là bất khả thi.

Auto-config tự động inject vào mọi service:
- **OpenTelemetry** — distributed tracing (traces + metrics + logs)
- **Structured logging** — MDC inject `traceId`, `spanId`, `correlationId`, `serviceId` vào mọi log line
- **Micrometer** — Prometheus metrics export

---

## `idempotency-support`

Redis-based idempotency key checking. Bắt buộc cho:
- `payment-service` — mọi payment operation
- `order-service` — order creation, confirmation
- `return-service` — return/refund trigger

---

## `common-web`

- `ApiResponse<T>` — standard response wrapper
- `ApiError` — standard error format
- `GlobalExceptionHandler` — `@ControllerAdvice`
- Correlation ID / Request ID filter

---

## `security-commons`

- JWT validation utilities
- Spring Security filter chain integration
- `@PreAuthorize` ABAC bridge với `abac-engine`
- Security context helper — extract current user/roles

---

## Nguyên tắc versioning

Mỗi lib dùng **semantic versioning**. Breaking change → major version bump.
Services migrate dần — không force update đồng loạt.

`common-domain` và `common-events` phải có backward compatibility policy:
**chỉ thêm, không xóa hoặc rename field**.
