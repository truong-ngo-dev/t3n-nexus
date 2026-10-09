# ADR-004 — Saga Choreography

**Status:** Accepted

## Context

Order flow cần coordinate across order-service, inventory-service, payment-service, fulfillment-service. Cần distributed transaction với compensating action khi bất kỳ bước nào fail.

Alternatives đã xem xét:
- **Orchestration Saga** (Temporal): central coordinator giữ toàn bộ state, dễ trace hơn nhưng tạo coupling vào orchestrator
- **2PC**: không phù hợp cho microservices — blocking, không scale

Temporal được dùng trong hệ thống nhưng cho long-running workflows (return, seller onboarding) — không phải cho Order Saga.

## Decision

**Choreography Saga** cho Order flow — mỗi service lắng nghe event và emit event tiếp theo, không có central orchestrator.

Happy path:
```
OrderCreated → [inventory] → InventoryReserved
             → [order]     → PaymentRequested
             → [payment]   → PaymentSucceeded
             → [order]     → OrderConfirmed
             → [fulfillment]
```

Compensating (payment fail):
```
PaymentFailed → [order] → OrderCancelled → [inventory] → InventoryReleased
```

`order-service` đóng vai **state machine** — consume reply events, drive state transitions, emit next commands.

## Consequences

**+** Loose coupling — services không phụ thuộc trực tiếp nhau  
**+** Kafka partition key = `orderId` đảm bảo ordering tự nhiên  
**+** Không cần Temporal cho Order Saga — giảm complexity  
**−** Harder to trace — distributed tracing (Jaeger) là bắt buộc  
**−** Compensating logic phân tán, khó visualize toàn bộ flow  
**−** Cần implement idempotency cẩn thận tại mọi consumer step
