# Implementation Plan: Place Order

**Design**: [`design.md`](design.md)
**Sequence**: [`sequence.puml`](sequence.puml)

---

## Tài liệu cần tạo / cập nhật

| Tài liệu                                  | Hành động | Nội dung cần thêm                                                                                            |
|-------------------------------------------|-----------|--------------------------------------------------------------------------------------------------------------|
| `design/services/order-service.md`        | Tạo mới   | Commands: `CreateOrder` / Events: `OrderCreated`, `OrderConfirmed`, `OrderCancelled`                         |
| `design/services/inventory-service.md`    | Tạo mới   | Commands: `ReserveInventory`, `ReleaseInventory` / Events: `InventoryReserved`, `InventoryReservationFailed` |
| `design/services/payment-service.md`      | Tạo mới   | Commands: `ProcessPayment` / Events: `PaymentSucceeded`, `PaymentFailed`                                     |
| `design/services/fulfillment-service.md`  | Tạo mới   | Commands: `AssignShipper` / Events: `ShipmentAssigned`                                                       |
| `design/services/notification-service.md` | Tạo mới   | Consumers: `OrderConfirmed`, `OrderCancelled`, `ShipmentAssigned`                                            |
| `design/api/order-service.yaml`           | Tạo mới   | `POST /orders`                                                                                               |
| `design/data/order-service.md`            | Tạo mới   | Tables: `orders`, `order_items`, `outbox`                                                                    |
| `design/data/inventory-service.md`        | Tạo mới   | Tables: `reservations`, `outbox`                                                                             |
| `design/data/payment-service.md`          | Tạo mới   | Tables: `payments`, `outbox`                                                                                 |
| `design/data/fulfillment-service.md`      | Tạo mới   | Tables: `shipments`, `outbox`                                                                                |
| `design/events/event-catalog.md`          | Cập nhật  | Thêm events của UC này vào đúng section                                                                      |

---

## Thứ tự triển khai

Implement theo dependency chain của Saga — service nào không phụ thuộc reply của service khác thì làm trước.

### Bước 1 — Shared libs (prerequisite)

Các lib này phải có trước khi code bất kỳ service nào.

- [ ] `common-domain`: `AggregateRoot`, `DomainEvent`, `Money`
- [ ] `outbox-starter`: auto-config, polling strategy trước
- [ ] `observability-starter`: OTel + structured logging
- [ ] `common-web`: `ApiResponse`, `GlobalExceptionHandler`

### Bước 2 — order-service (Saga coordinator)

order-service là trung tâm của UC — làm trước để xác định contract cho các service khác.

- [ ] Tạo `design/services/order-service.md`
- [ ] Tạo `design/api/order-service.yaml` — `POST /orders`
- [ ] Tạo `design/data/order-service.md` — `orders`, `order_items`, `outbox`
- [ ] Implement `Order` aggregate, state machine (`PENDING → CONFIRMED / CANCELLED`)
- [ ] Implement `CreateOrder` command handler → publish `OrderCreated`
- [ ] Implement Kafka consumers: `InventoryReserved`, `InventoryReservationFailed`, `PaymentSucceeded`, `PaymentFailed`
- [ ] Implement Outbox relay

### Bước 3 — inventory-service (Saga participant)

Nhận `OrderCreated`, reply `InventoryReserved` hoặc `InventoryReservationFailed`.

- [ ] Tạo `design/services/inventory-service.md`
- [ ] Tạo `design/data/inventory-service.md` — `stock`, `reservations`, `outbox`
- [ ] Implement `ReserveInventory` handler → publish `InventoryReserved` / `InventoryReservationFailed`
- [ ] Implement `ReleaseInventory` handler (consumer `OrderCancelled`)
- [ ] Implement Outbox relay

### Bước 4 — payment-service (Saga participant)

Nhận `PaymentRequested`, reply `PaymentSucceeded` hoặc `PaymentFailed`.

- [ ] Tạo `design/services/payment-service.md`
- [ ] Tạo `design/data/payment-service.md` — `payments`, `outbox`
- [ ] Implement idempotency check (`idempotencyKey`)
- [ ] Implement `ProcessPayment` handler → publish `PaymentSucceeded` / `PaymentFailed`
- [ ] Implement Outbox relay

### Bước 5 — fulfillment-service (Saga participant)

Nhận `OrderConfirmed`, assign shipper, reply `ShipmentAssigned`.

- [ ] Tạo `design/services/fulfillment-service.md`
- [ ] Tạo `design/data/fulfillment-service.md` — `shipments`, `outbox`
- [ ] Implement `AssignShipper` handler (Rule Engine) → publish `ShipmentAssigned`
- [ ] Implement Outbox relay

### Bước 6 — notification-service (consumer)

Consume events, không publish lại — implement sau cùng.

- [ ] Tạo `design/services/notification-service.md`
- [ ] Implement consumers: `OrderConfirmed`, `OrderCancelled`, `ShipmentAssigned`
- [ ] Route đến đúng channel (email / push / in-app)

### Bước 7 — Integration & docs

- [ ] Cập nhật `design/events/event-catalog.md`
- [ ] Cập nhật sequence diagram nếu flow thực tế thay đổi
- [ ] Tạo ADR nếu có quyết định kỹ thuật mới trong quá trình implement
- [ ] Tick checklist bên dưới

---

## Checklist hoàn thành UC

- [ ] Happy path chạy end-to-end (order → inventory → payment → fulfillment → notification)
- [ ] Compensating path: inventory fail → order cancelled, payment không trigger
- [ ] Compensating path: payment fail → order cancelled, inventory released
- [ ] Outbox hoạt động — không mất event khi service restart giữa chừng
- [ ] Idempotency — gửi cùng request 2 lần không tạo 2 payment
- [ ] Tất cả service docs đã cập nhật đúng thực tế
- [ ] Event catalog đã cập nhật payload cuối cùng
