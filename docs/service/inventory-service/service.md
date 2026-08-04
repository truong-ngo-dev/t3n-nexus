# Inventory Service

## Trách nhiệm

Quản lý tồn kho theo SKU: theo dõi số lượng thực tế (`totalQty`), số lượng đang giữ chỗ (`reservedQty`), và tham gia Saga choreography với vai trò participant — nhận `OrderCreated`, thực hiện reservation, phát `InventoryReserved` hoặc `InventoryReservationFailed`. Ngoài ra enforce giới hạn slot cho limited offer qua Redis atomic + Bloom Filter.

**Không làm:** Không quản lý vị trí kho vật lý (Warehouse BC — Phase 2). Không tính giá. Không quyết định trạng thái đơn hàng.

---

## Domain Model

### Aggregates

| Aggregate      | Root Entity    | Invariants                                                                 |
|----------------|----------------|----------------------------------------------------------------------------|
| `Stock`        | `Stock`        | `reservedQty` ≤ `totalQty` luôn luôn; `availableQty` ≥ 0 trước khi reserve |
| `Reservation`  | `Reservation`  | Một `orderId` tối đa 1 Reservation đang PENDING; items không được rỗng     |
| `LimitedOffer` | `LimitedOffer` | Một `skuId` tối đa 1 LimitedOffer ACTIVE tại cùng thời điểm                |

### Stock

```
Stock
├── StockId
├── skuId              UUID    — reference sang Catalog BC (raw UUID, across BC)
├── productId          UUID    — để publish/unpublish/block theo nhóm
├── sellerId           UUID
├── totalQuantity      int     — physical stock, seller cập nhật
├── reservedQuantity   int     — tổng qty của các Reservation đang PENDING
├── sellerActive       boolean — seller bật/tắt riêng từng SKU (mirror Variant.status ACTIVE/INACTIVE)
├── productPublished   boolean — mirror Product.status PUBLISHED/UNPUBLISHED, áp cho toàn bộ SKU của product
├── adminBlocked       boolean — platform gỡ toàn bộ product (độc lập 2 cờ trên)
└── lowStockThreshold  int     — ngưỡng cảnh báo, emit StockDepleted khi availableQty < threshold

availableQuantity = totalQuantity - reservedQuantity  (computed, không lưu DB)
isSellable = sellerActive AND productPublished AND NOT adminBlocked
```

`sellerActive` và `productPublished` là 2 trục độc lập (mirror đúng nghiệp vụ Shopee/Tiki): trục sản phẩm (product publish/unpublish) là cổng tổng áp cho toàn bộ SKU — kể cả SKU được thêm sau khi product đã publish; trục variant (activate/deactivate) là cổng riêng seller bật/tắt từng SKU, không phụ thuộc thứ tự xảy ra so với publish.

Domain methods:
- `Stock.initialize(skuId, productId, sellerId, sellerActive, productPublished)` — static factory, qty=0; 2 tham số cuối lấy từ trạng thái thật của Variant/Product tại thời điểm variant được tạo (qua `VariantCreatedEvent`)
- `Stock.setQuantity(newTotalQty)` — seller set tuyệt đối; emit `StockReplenished` nếu trước đó depleted
- `Stock.reserve(qty)` — tăng `reservedQty`; guard: `availableQty >= qty` và `isSellable()`; emit `StockDepleted` nếu `availableQty` về 0
- `Stock.release(qty)` — giảm `reservedQty`; emit `StockReplenished` nếu availableQty từ 0 lên dương
- `Stock.activate()` / `Stock.deactivate()` — toggle `sellerActive` (theo `VariantActivatedEvent`/`VariantDeactivatedEvent`)
- `Stock.publishProduct()` / `Stock.unpublishProduct()` — toggle `productPublished` (theo `ProductPublishedEvent`/`ProductUnpublishedEvent`, áp cho toàn bộ SKU của product)
- `Stock.block()` / `Stock.unblock()` — toggle `adminBlocked` (theo `ProductBlockedEvent`/`ProductUnblockedEvent`)

### Reservation

```
Reservation
├── ReservationId
├── orderId    UUID   — UNIQUE, index
├── status     PENDING | RELEASED | CANCELLED
├── expiresAt  timestamp  — TTL khớp với order auto-cancel window
├── createdAt
└── items      List<ReservationItem>

ReservationItem  (Entity, owned by Reservation)
├── ReservationItemId
├── skuId  UUID
└── qty    int
```

Domain methods:
- `Reservation.create(orderId, List<Item>)` — static factory
- `Reservation.release()` → RELEASED; guard: chỉ release khi PENDING
- `Reservation.cancel()` → CANCELLED; dùng khi TTL expire nội bộ

### LimitedOffer

Shadow của config từ Promotion BC. Inventory lưu local để enforce qua Redis.

```
LimitedOffer
├── LimitedOfferId
├── skuId        UUID  — UNIQUE
├── maxQuantity  int
├── windowSeconds int
├── status       INACTIVE | ACTIVE | ENDED
└── activatedAt  timestamp
```

Domain methods:
- `LimitedOffer.activate(maxQty, windowSeconds)` — set ACTIVE, gọi Redis init
- `LimitedOffer.end()` — set ENDED, gọi Redis clear

### Commands

| Command                  | Handler                         | Publishes                                |
|--------------------------|---------------------------------|------------------------------------------|
| `SetStock`               | `SetStockHandler`               | `StockReplenished` *(nếu qty tăng từ 0)* |
| `RestockSku`             | `RestockSkuHandler`             | `StockReplenished` *(nếu qty tăng từ 0)* |
| `ActivateLimitedOffer`   | `ActivateLimitedOfferHandler`   | —                                        |
| `DeactivateLimitedOffer` | `DeactivateLimitedOfferHandler` | —                                        |

Event-driven (Kafka consumers):

| Trigger                       | Handler                       | Publishes                                           |
|-------------------------------|-------------------------------|-----------------------------------------------------|
| `order.order.created`         | `OrderCreatedConsumer`        | `InventoryReserved` \| `InventoryReservationFailed` |
| `order.order.cancelled`       | `OrderCancelledConsumer`      | `InventoryReleased`                                 |
| `catalog.variant.created`     | `VariantCreatedConsumer`      | —  *(init Stock, sellerActive/productPublished mirror theo payload)* |
| `catalog.variant.activated`   | `VariantActivatedConsumer`    | —                                                    |
| `catalog.variant.deactivated` | `VariantDeactivatedConsumer`  | —                                                    |
| `catalog.product.published`   | `ProductPublishedConsumer`    | — *(set `productPublished=true` cho toàn bộ SKU của product)* |
| `catalog.product.unpublished` | `ProductUnpublishedConsumer`  | — *(set `productPublished=false` cho toàn bộ SKU của product)* |
| `catalog.product.blocked`     | `ProductBlockedConsumer`      | —                                                    |
| `catalog.product.unblocked`   | `ProductUnblockedConsumer`    | —                                                    |

### Domain Events

| Event                        | Trigger                                         | Consumers                                |
|------------------------------|-------------------------------------------------|------------------------------------------|
| `InventoryReserved`          | Reserve thành công toàn bộ items của order      | `order-service`                          |
| `InventoryReservationFailed` | Không đủ stock ít nhất 1 item                   | `order-service`                          |
| `InventoryReleased`          | Reservation released sau OrderCancelled         | `search-service`                         |
| `StockDepleted`              | `availableQty` xuống 0 sau reserve/set-quantity | `search-service`, `notification-service` |
| `StockReplenished`           | `availableQty` tăng từ 0 lên dương              | `search-service`, `notification-service` |

### Business Rules

- `availableQty = totalQty - reservedQty` — không bao giờ âm; reservation phải fail nếu không đủ qty
- Một orderId chỉ có tối đa 1 Reservation PENDING — duplicate `OrderCreated` phải idempotent
- Khi Stock INACTIVE: từ chối mọi lệnh reserve mới; các Reservation đang PENDING vẫn giữ nguyên
- Depletion threshold: emit `StockDepleted` khi `availableQty < lowStockThreshold` (default: 0 — chỉ emit khi hết sạch)
- **Limited Offer path**: khi SKU có LimitedOffer ACTIVE → kiểm tra Bloom Filter → chạy Redis Lua DECR trước khi chạm DB; nếu Redis DECR trả về < 0 → fail fast, không cần SELECT FOR UPDATE
- **Normal path**: dùng `SELECT FOR UPDATE` trên Stock để tránh oversell dưới concurrent order
- Reservation phải atomic: tất cả items của order thành công hoặc rollback toàn bộ

---

## Key Technical Components

### Reservation — Pessimistic Lock (Normal Path)

```
OrderCreatedHandler (@Transactional):
  foreach item in order.items:
    stock = stockRepository.findBySkuIdForUpdate(item.skuId)   ← SELECT FOR UPDATE
    stock.reserve(item.qty)                                      ← guard trong aggregate
    stockRepository.save(stock)
  reservation = Reservation.create(orderId, items)
  reservationRepository.save(reservation)
  → emit InventoryReserved

  Exception → rollback → emit InventoryReservationFailed
```

### Limited Offer — Redis Lua Script

Redis key: `inventory:limited:{skuId}:slots`

```lua
-- Atomic check-and-decrement, tránh race giữa READ và WRITE
local slots = redis.call('GET', KEYS[1])
if slots == false or tonumber(slots) <= 0 then
  return -1
end
return redis.call('DECR', KEYS[1])
```

Flow:
1. `bloomFilter.mightBeSoldOut(skuId)` → `true` → reject ngay (false positive acceptable)
2. Chạy Lua script → result < 0 → emit `InventoryReservationFailed`
3. result ≥ 0 → proceed DB reservation (normal path)
4. Nếu `slots DECR` đến 0 → thêm `skuId` vào Bloom Filter

Restock khi LimitedOffer ACTIVE → xóa skuId khỏi Bloom Filter (rebuild), reset Redis counter.

### Idempotent Consumer

```sql
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);
```

Trước mỗi event: `INSERT INTO processed_event(event_id) VALUES(?) ON CONFLICT DO NOTHING`
→ 0 rows affected = duplicate → skip.

---

## Use Cases — tham gia

| Feature                                                | Role                           | Handles                         | Publishes                                         |
|--------------------------------------------------------|--------------------------------|---------------------------------|---------------------------------------------------|
| [place-order](../../feature/place-order/design.md) | Participant — reservation step | `OrderCreated`                  | `InventoryReserved`, `InventoryReservationFailed` |
| [place-order](../../feature/place-order/design.md) | Participant — compensate step  | `OrderCancelled`                | `InventoryReleased`                               |
| [flashsale.md](flashsale.md) | Enforcer — slot guard          | `OrderCreated` *(limited path)* | `InventoryReserved`, `InventoryReservationFailed` |

---

## Integration Contract

### Publishes (Kafka)

| Topic                            | Event                        | Partition Key |
|----------------------------------|------------------------------|---------------|
| `inventory.reservation.created`  | `InventoryReserved`          | `orderId`     |
| `inventory.reservation.failed`   | `InventoryReservationFailed` | `orderId`     |
| `inventory.reservation.released` | `InventoryReleased`          | `orderId`     |
| `inventory.stock.replenished`    | `StockReplenished`           | `skuId`       |
| `inventory.stock.depleted`       | `StockDepleted`              | `skuId`       |

### Consumes (Kafka)

| Topic                         | Event                     | Handler                       | Idempotency |
|-------------------------------|---------------------------|-------------------------------|-------------|
| `order.order.created`         | `OrderCreated`            | `OrderCreatedConsumer`        | `eventId`   |
| `order.order.cancelled`       | `OrderCancelled`          | `OrderCancelledConsumer`      | `eventId`   |
| `catalog.variant.created`     | `VariantCreatedEvent`     | `VariantCreatedConsumer`      | `eventId`   |
| `catalog.variant.activated`   | `VariantActivatedEvent`   | `VariantActivatedConsumer`    | `eventId`   |
| `catalog.variant.deactivated` | `VariantDeactivatedEvent` | `VariantDeactivatedConsumer`  | `eventId`   |
| `catalog.product.published`   | `ProductPublishedEvent`   | `ProductPublishedConsumer`    | `eventId`   |
| `catalog.product.unpublished` | `ProductUnpublishedEvent` | `ProductUnpublishedConsumer`  | `eventId`   |
| `catalog.product.blocked`     | `ProductBlockedEvent`     | `ProductBlockedConsumer`      | `eventId`   |
| `catalog.product.unblocked`   | `ProductUnblockedEvent`   | `ProductUnblockedConsumer`    | `eventId`   |

> **Gap:** Khi Promotion BC được implement, cần bổ sung consumer cho `LimitedOfferActivated` / `LimitedOfferDeactivated`. Hai events này chưa có trong event-catalog.

### Sync Calls

Không có. Inventory service không thực hiện sync call ra ngoài và không nhận sync call từ service khác trong approved list.

> Internal read endpoint (nếu cần): `GET /internal/inventory/{skuId}/available` — chỉ dùng cho dev tooling / admin, không nằm trong Saga flow.

---

## Dependencies

- **Services cần chạy cùng:** `catalog-service` *(upstream — ProductPublished)*,  `order-service` *(Saga partner)*
- **Infrastructure:**
  - PostgreSQL — primary store (Stock, Reservation, LimitedOffer, processed_event, outbox_events)
  - Redis — limited offer slot counters + Bloom Filter (Redisson)
  - Kafka — consumer groups: `inventory-catalog-consumer`, `inventory-order-consumer`
  - Debezium — CDC từ `outbox_events` table sang Kafka (shared infra)

## Tài liệu liên quan

- [`data.md`](data.md) — DB schema, index strategy
- [`flashsale.md`](flashsale.md) — thiết kế pipeline flash sale riêng biệt khỏi normal reservation path (rate limit → Bloom Filter → Redis Lua slot decrement)
