# Design: Place Order

**UC gốc**: Buyer đặt hàng (`../../global/1.requirement/requirement.md`)
**Implementation plan**: [`implementation.md`](implementation.md)
**Status**: Draft

---

## Services liên quan

| Service                | Vai trò                                         | Loại tham gia                     |
|------------------------|-------------------------------------------------|-----------------------------------|
| `order-service`        | Saga coordinator — nhận request, điều phối flow | Command handler + Event publisher |
| `inventory-service`    | Giữ tồn kho cho order                           | Saga participant                  |
| `payment-service`      | Thu tiền                                        | Saga participant                  |
| `fulfillment-service`  | Phân công shipper                               | Saga participant                  |
| `notification-service` | Thông báo buyer + seller                        | Event consumer (Notify)           |

---

## Pre-conditions

- Buyer đã login
- Cart không rỗng, tất cả items thuộc cùng 1 seller
- Địa chỉ giao hàng đã có

---

## Happy Path

```
Buyer
  → POST /orders  (order-service)
  → order-service tạo Order (PENDING), publish OrderCreated
        ↓ Kafka
  → inventory-service nhận OrderCreated, reserve stock
  → inventory-service publish InventoryReserved
        ↓ Kafka
  → order-service nhận InventoryReserved, publish PaymentRequested
        ↓ Kafka
  → payment-service nhận PaymentRequested, xử lý thanh toán async
  → payment-service publish PaymentSucceeded
        ↓ Kafka
  → order-service nhận PaymentSucceeded
    → cập nhật Order → CONFIRMED
    → publish OrderConfirmed
        ↓ Kafka
  → fulfillment-service nhận OrderConfirmed, assign shipper (Rule Engine)
  → fulfillment-service publish ShipmentAssigned
        ↓ Kafka
  → notification-service nhận OrderConfirmed + ShipmentAssigned → gửi thông báo
```

```plantuml
@startuml sequence-place-order-happy
title Place Order — Happy Path (Saga Choreography)

actor Buyer
participant "order-service" as OS
participant "inventory-service" as INV
participant "payment-service" as PAY
participant "fulfillment-service" as FUL
participant "notification-service" as NOTIF
queue "Kafka" as K

Buyer -> OS : POST /orders\n{cartId, shippingAddress, paymentMethod}
activate OS
OS -> OS : create Order (PENDING)
OS -> K : OrderCreated\n{orderId, items[], paymentMethod}
deactivate OS

K -> INV : OrderCreated
activate INV
INV -> INV : reserve stock
INV -> K : InventoryReserved\n{orderId, items[]}
deactivate INV

K -> OS : InventoryReserved
activate OS
OS -> K : PaymentRequested\n{orderId, amount, idempotencyKey}
deactivate OS

K -> PAY : PaymentRequested
activate PAY
PAY -> PAY : process payment (async)
PAY -> K : PaymentSucceeded\n{orderId, paymentId, amount}
deactivate PAY

K -> OS : PaymentSucceeded
activate OS
OS -> OS : Order → CONFIRMED
OS -> K : OrderConfirmed\n{orderId, sellerId, shippingAddress}
deactivate OS

K -> FUL : OrderConfirmed
activate FUL
FUL -> FUL : assign shipper\n(Rule Engine)
FUL -> K : ShipmentAssigned\n{orderId, shipperId, estimatedPickup}
deactivate FUL

K -> NOTIF : OrderConfirmed
K -> NOTIF : ShipmentAssigned
activate NOTIF
NOTIF -> Buyer : "Đơn hàng đã xác nhận"
NOTIF -> Buyer : "Shipper đã được phân công"
deactivate NOTIF

note over OS, PAY
  Mọi event publish đều qua Outbox Pattern
  Consumer dedup theo eventId
  Kafka partition key = orderId
end note

@enduml
```

---

## Compensating Paths

### Inventory thất bại

```
inventory-service publish InventoryReservationFailed
  → order-service nhận → Order → CANCELLED
  → publish OrderCancelled
  → payment-service KHÔNG được trigger
  → notification-service gửi thông báo huỷ
```

```plantuml
@startuml sequence-place-order-inventory-failed
title Place Order — Compensating: Inventory Failed

participant "order-service" as OS
participant "inventory-service" as INV
participant "notification-service" as NOTIF
actor Buyer
queue "Kafka" as K

K -> INV : OrderCreated
activate INV
INV -> K : InventoryReservationFailed\n{orderId, reason}
deactivate INV

K -> OS : InventoryReservationFailed
activate OS
OS -> OS : Order → CANCELLED
OS -> K : OrderCancelled\n{orderId, reason="INVENTORY_FAILED"}
deactivate OS

K -> NOTIF : OrderCancelled
activate NOTIF
NOTIF -> Buyer : "Đơn hàng đã bị huỷ\n(hết hàng)"
deactivate NOTIF

@enduml
```

### Payment thất bại

```
payment-service publish PaymentFailed
  → order-service nhận → Order → CANCELLED
  → publish OrderCancelled
        ↓ Kafka (fan-out)
  → inventory-service nhận OrderCancelled → release reservation
  → notification-service gửi thông báo huỷ
```

```plantuml
@startuml sequence-place-order-payment-failed
title Place Order — Compensating: Payment Failed

participant "order-service" as OS
participant "inventory-service" as INV
participant "payment-service" as PAY
participant "notification-service" as NOTIF
actor Buyer
queue "Kafka" as K

K -> PAY : PaymentRequested
activate PAY
PAY -> K : PaymentFailed\n{orderId, reason}
deactivate PAY

K -> OS : PaymentFailed
activate OS
OS -> OS : Order → CANCELLED
OS -> K : OrderCancelled\n{orderId, reason="PAYMENT_FAILED"}
deactivate OS

K -> INV : OrderCancelled
activate INV
INV -> INV : release reservation
deactivate INV

K -> NOTIF : OrderCancelled
activate NOTIF
NOTIF -> Buyer : "Đơn hàng đã bị huỷ\n(thanh toán thất bại)"
deactivate NOTIF

@enduml
```

---

## Yêu cầu kỹ thuật

| Concern                | Giải pháp                                                   |
|------------------------|-------------------------------------------------------------|
| Kafka ordering         | Partition key = `orderId`                                   |
| At-least-once delivery | Outbox Pattern tại mỗi event publish                        |
| Deduplication          | Consumer dedup theo `eventId`, lưu `processed_event_id`     |
| Payment idempotency    | `idempotencyKey` trong `PaymentRequested` = `orderId`       |
| Timeout                | Nếu không nhận reply sau X phút → Temporal scheduled cancel |

---

## ADR liên quan

- [`adr/004-saga-choreography.md`](../../global/2.architecture/adr/004-saga-choreography.md)
- [`adr/005-outbox-pattern.md`](../../global/2.architecture/adr/005-outbox-pattern.md)
