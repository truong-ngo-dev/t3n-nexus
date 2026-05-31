# Event Catalog — t3n-nexus

Liệt kê toàn bộ domain event lưu chuyển qua Kafka giữa các services.
Schema quản lý bởi Confluent Schema Registry (Avro/Protobuf).

## Quy ước

- **Topic**: `{producer}.{entity}.{past-tense-verb}` — ví dụ `order.order.created`
- **Payload**: chỉ liệt kê các field có ý nghĩa inter-service — field nội bộ không đưa vào event
- **Pattern**: `Saga` = tham gia vào choreography saga; `Notify` = trigger Notification BC; `Projection` = cập nhật read model / search index; `CDC` = Debezium đọc binlog

---

## oauth2-service

| Event                | Topic                          | Consumers        | Pattern        | Key Payload                                                             |
|----------------------|--------------------------------|------------------|----------------|-------------------------------------------------------------------------|
| `UserRegistered`     | `oauth2.user.registered`       | identity-service | Projection     | `userId`, `email`, `fullName`, `role`, `registrationMethod`             |
| `DeviceLoginRecorded`| `oauth2.device.login-recorded` | identity-service | Projection     | `deviceId`, `userId`, `userAgent`, `ip`, `loginStatus`, `loginAt`       |

---

## identity-service

| Event                        | Topic                                   | Consumers            | Pattern    | Key Payload                                            |
|------------------------------|-----------------------------------------|----------------------|------------|--------------------------------------------------------|
| `CustomerAccountCreated`     | `identity.customer-account.created`     | customer-service     | Projection | `userId`, `email`, `fullName`                          |
| `VerificationEmailRequested` | `identity.email-verification.requested` | notification-service | Notify     | `userId`, `email`, `fullName`, `verificationToken`     |
| `VerificationEmailReissued`  | `identity.email-verification.reissued`  | notification-service | Notify     | `userId`, `email`, `fullName`, `verificationToken`     |
| `EmailVerified`              | `identity.email-verification.verified`  | notification-service | Notify     | `userId`, `email`, `fullName`                          |
| `UserActivated`              | `identity.user.activated`               | oauth2-service       | Projection | `userId`                                               |

---

## catalog-service

| Event                | Topic                         | Consumers      | Pattern    | Key Payload                                                        |
|----------------------|-------------------------------|----------------|------------|--------------------------------------------------------------------|
| `ProductPublished`   | `catalog.product.published`   | search-service | Projection | `productId`, `name`, `categoryId`, `sellerId`, `price`, `imageUrl` |
| `ProductUnpublished` | `catalog.product.unpublished` | search-service | Projection | `productId`                                                        |
| `ProductUpdated`     | `catalog.product.updated`     | search-service | Projection | `productId`, changed fields                                        |
| `CategoryUpdated`    | `catalog.category.updated`    | search-service | Projection | `categoryId`, `name`, `parentId`                                   |

---

## inventory-service

| Event                        | Topic                            | Consumers                            | Pattern                 | Key Payload                        |
|------------------------------|----------------------------------|--------------------------------------|-------------------------|------------------------------------|
| `InventoryReserved`          | `inventory.reservation.created`  | order-service                        | Saga reply              | `orderId`, `items[]{skuId, qty}`   |
| `InventoryReservationFailed` | `inventory.reservation.failed`   | order-service                        | Saga reply (compensate) | `orderId`, `reason`, `failedSkuId` |
| `InventoryReleased`          | `inventory.reservation.released` | search-service                       | Projection              | `orderId`, `items[]{skuId, qty}`   |
| `StockReplenished`           | `inventory.stock.replenished`    | search-service, notification-service | Projection + Notify     | `skuId`, `newQty`, `sellerId`      |
| `StockDepleted`              | `inventory.stock.depleted`       | search-service, notification-service | Projection + Notify     | `skuId`, `sellerId`                |

---

## order-service

| Event              | Topic                     | Consumers                                                   | Pattern                             | Key Payload                                                                   |
|--------------------|---------------------------|-------------------------------------------------------------|-------------------------------------|-------------------------------------------------------------------------------|
| `OrderCreated`     | `order.order.created`     | inventory-service                                           | Saga step 1 — trigger reservation   | `orderId`, `customerId`, `sellerId`, `items[]{skuId, qty}`, `paymentMethod`   |
| `PaymentRequested` | `order.payment.requested` | payment-service                                             | Saga step 2 — trigger payment async | `orderId`, `customerId`, `amount`, `paymentMethod`, `idempotencyKey`          |
| `OrderConfirmed`   | `order.order.confirmed`   | fulfillment-service, notification-service, customer-service | Saga step 3 — trigger fulfillment   | `orderId`, `customerId`, `sellerId`, `shippingAddress`                        |
| `OrderCancelled`   | `order.order.cancelled`   | inventory-service, payment-service, notification-service    | Saga compensate                     | `orderId`, `reason`, `cancelledBy`                                            |
| `OrderDelivered`   | `order.order.delivered`   | payment-service, notification-service                       | Saga step — COD reconcile trigger   | `orderId`, `deliveredAt`                                                      |
| `OrderCompleted`   | `order.order.completed`   | customer-service, review-service, notification-service      | Projection + unlock review          | `orderId`, `customerId`, `sellerId`, `shipperId`, `items[]{skuId, productId}` |

---

## payment-service

| Event                   | Topic                       | Consumers                           | Pattern                  | Key Payload                                |
|-------------------------|-----------------------------|-------------------------------------|--------------------------|--------------------------------------------|
| `PaymentSucceeded`      | `payment.payment.succeeded` | order-service                       | Saga reply step 2        | `orderId`, `paymentId`, `amount`, `method` |
| `PaymentFailed`         | `payment.payment.failed`    | order-service                       | Saga reply (compensate)  | `orderId`, `reason`                        |
| `RefundProcessed`       | `payment.refund.processed`  | order-service, notification-service | Saga reply (return flow) | `orderId`, `refundId`, `amount`            |
| `SellerPayoutCompleted` | `payment.payout.completed`  | notification-service                | Notify                   | `sellerId`, `amount`, `periodEnd`          |

---

## fulfillment-service

| Event               | Topic                             | Consumers                                            | Pattern            | Key Payload                                      |
|---------------------|-----------------------------------|------------------------------------------------------|--------------------|--------------------------------------------------|
| `ShipmentAssigned`  | `fulfillment.shipment.assigned`   | notification-service                                 | Notify             | `orderId`, `shipperId`, `estimatedPickup`        |
| `ShipmentPickedUp`  | `fulfillment.shipment.picked-up`  | order-service, notification-service                  | Saga step + Notify | `orderId`, `shipperId`, `pickedUpAt`             |
| `ShipmentInTransit` | `fulfillment.shipment.in-transit` | notification-service                                 | Notify             | `orderId`, `shipperId`                           |
| `ShipmentDelivered` | `fulfillment.shipment.delivered`  | order-service, payment-service, notification-service | Saga step + COD    | `orderId`, `shipperId`, `deliveredAt`, `podUrl`  |
| `ShipmentFailed`    | `fulfillment.shipment.failed`     | order-service, notification-service                  | Saga compensate    | `orderId`, `shipperId`, `reason`, `attemptCount` |

---

## return-service

| Event             | Topic                     | Consumers                                 | Pattern        | Key Payload                                               |
|-------------------|---------------------------|-------------------------------------------|----------------|-----------------------------------------------------------|
| `ReturnRequested` | `return.return.requested` | notification-service                      | Notify seller  | `returnId`, `orderId`, `customerId`, `sellerId`, `reason` |
| `ReturnApproved`  | `return.return.approved`  | fulfillment-service, notification-service | Trigger pickup | `returnId`, `orderId`, `shippingAddress`                  |
| `ReturnRejected`  | `return.return.rejected`  | notification-service                      | Notify         | `returnId`, `orderId`, `reason`                           |
| `ReturnCompleted` | `return.return.completed` | payment-service, notification-service     | Trigger refund | `returnId`, `orderId`, `amount`                           |

---

## seller-service

| Event                 | Topic                          | Consumers            | Pattern    | Key Payload                            |
|-----------------------|--------------------------------|----------------------|------------|----------------------------------------|
| `SellerApproved`      | `seller.seller.approved`       | notification-service | Notify     | `sellerId`, `userId`                   |
| `SellerRejected`      | `seller.seller.rejected`       | notification-service | Notify     | `sellerId`, `userId`, `reason`         |
| `SellerRatingUpdated` | `seller.seller.rating-updated` | search-service       | Projection | `sellerId`, `newRating`, `reviewCount` |

---

## shipper-service

| Event                  | Topic                            | Consumers            | Pattern | Key Payload                     |
|------------------------|----------------------------------|----------------------|---------|---------------------------------|
| `ShipperApproved`      | `shipper.shipper.approved`       | notification-service | Notify  | `shipperId`, `userId`, `zone`   |
| `ShipperRejected`      | `shipper.shipper.rejected`       | notification-service | Notify  | `shipperId`, `userId`, `reason` |
| `ShipperRatingUpdated` | `shipper.shipper.rating-updated` | *(internal)*         | —       | `shipperId`, `newRating`        |

---

## customer-service

| Event                  | Topic                      | Consumers            | Pattern | Key Payload                                   |
|------------------------|----------------------------|----------------------|---------|-----------------------------------------------|
| `LoyaltyPointsEarned`  | `customer.loyalty.earned`  | notification-service | Notify  | `customerId`, `points`, `orderId`, `expireAt` |
| `LoyaltyPointsExpired` | `customer.loyalty.expired` | notification-service | Notify  | `customerId`, `points`, `expiredEntryIds[]`   |

---

## review-service

| Event             | Topic                     | Consumers       | Pattern                       | Key Payload                                      |
|-------------------|---------------------------|-----------------|-------------------------------|--------------------------------------------------|
| `ProductReviewed` | `review.product.reviewed` | search-service  | Projection (rating aggregate) | `productId`, `orderId`, `rating`, `newAvgRating` |
| `SellerRated`     | `review.seller.rated`     | seller-service  | Projection                    | `sellerId`, `orderId`, `rating`, `newAvgRating`  |
| `ShipperRated`    | `review.shipper.rated`    | shipper-service | Projection                    | `shipperId`, `orderId`, `rating`, `newAvgRating` |

---

## chat-service

| Event                | Topic                   | Consumers            | Pattern                        | Key Payload                                                   |
|----------------------|-------------------------|----------------------|--------------------------------|---------------------------------------------------------------|
| `NewMessageReceived` | `chat.message.received` | notification-service | Notify (nếu recipient offline) | `conversationId`, `senderId`, `recipientId`, `messagePreview` |

---

## scheduler-service

> Scheduler không publish domain event — trigger trực tiếp qua internal API call hoặc Temporal scheduled workflow.

---

## Ghi chú Saga Flow

> **Yêu cầu bắt buộc với volume lớn:**
> - **Outbox Pattern** trên mọi event publish — ghi vào bảng `outbox` cùng transaction, Debezium push lên Kafka
> - **Idempotency** tại mọi consumer — dedup theo `eventId`, lưu `processed_event_id`
> - **Kafka partition key = `orderId`** — đảm bảo ordering cho mọi event của cùng một saga

### Tạo đơn hàng (Happy Path) — fully async, không có sync call
```
order-service         inventory-service      payment-service       fulfillment-service
      |                      |                     |                      |
      |-- OrderCreated -----> |                     |                      |
      | <-- InventoryReserved |                     |                      |
      |-- PaymentRequested -------------------- >   |                      |
      |                      |               <process async>               |
      | <-- PaymentSucceeded ------------------- -- |                      |
      |-- OrderConfirmed -------------------------------------------- > |  |
```

### Reservation thất bại (Compensating)
```
order-service         inventory-service
      |                      |
      |-- OrderCreated -----> |
      | <-- InventoryReservationFailed
      |-- OrderCancelled (payment không được trigger)
```

### Thanh toán thất bại (Compensating)
```
order-service         inventory-service      payment-service
      |                      |                     |
      |-- OrderCreated -----> |                     |
      | <-- InventoryReserved |                     |
      |-- PaymentRequested -------------------- >   |
      | <-- PaymentFailed ---------------------- -- |
      |-- OrderCancelled ----> |
      |                       | [release reservation]
```
