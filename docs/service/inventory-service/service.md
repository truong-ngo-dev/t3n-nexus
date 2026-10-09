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
- **Hard-delete (ngoại lệ duy nhất của Stock, không có method domain nào — xoá thẳng row):** `DeleteStock`
  (theo `VariantDeletedEvent`) — chỉ xảy ra khi catalog-service xoá cứng 1 Variant, mà catalog CHỈ cho xoá
  cứng khi Product của nó còn DRAFT (chưa từng publish) — nên Stock đó chắc chắn `reservedQty = 0`, chưa
  từng có Order/reservation nào chạm tới, xoá an toàn tuyệt đối. Idempotent — skuId không tồn tại thì no-op.

### Reservation

```
Reservation
├── ReservationId
├── orderId    string (ULID)   — UNIQUE, index
├── status     PENDING | COMMITTED | RELEASED | CANCELLED
├── expiresAt  timestamptz, nullable  — TTL self-guard (5 phút, hạ từ 10 — 2026-09-23), null cho CANCELLED (T2)
├── createdAt
└── items      List<ReservationItem>

ReservationItem  (Entity, owned by Reservation)
├── ReservationItemId
├── skuId  string (ULID)
└── qty    int
```

Domain methods:
- `Reservation.create(orderId, List<Item>)` — static factory, T1 (thành công) → `PENDING`, set `expiresAt = now+5min`
- `Reservation.createFailed(orderId, List<Item>, failedSkuId, reason)` — static factory, T2 (thất bại) → `CANCELLED` ngay, `expiresAt=null`
- `Reservation.release()` → `RELEASED`; guard: chỉ release khi `PENDING`
- `Reservation.commit()` → `COMMITTED`; guard: chỉ commit khi `PENDING` — nhận tín hiệu `OrderConfirmed`, dừng đồng hồ TTL vĩnh viễn (2026-09-22, xem "TTL self-guard" bên dưới)

**TTL self-guard (2026-09-22, đảo ngược lần 2 sau `V5`)**: `expiresAt` từng bị xoá hẳn (`V5__drop_reservation_expires_at.sql`) vì lúc đó chưa có state chung cuộc — sweep sẽ release nhầm cả đơn đã confirm (mọi `Reservation` thành công nằm mãi `PENDING`). Thêm lại (`V6`) sau khi có `COMMITTED`: Lớp 1 (Redis ZSET, `ReservationTimeoutIndex`) + Lớp 2 (Postgres backstop, partial index `WHERE status='PENDING'`) — độc lập hoàn toàn với việc `order-service` có publish đúng/đủ `OrderCancelled` hay không, chỉ là lớp phòng thủ cuối, không phải cơ chế chính (cơ chế chính vẫn là `OrderCancelledConsumer` phản ứng theo event, xử lý nhanh hơn hẳn TTL trong đa số trường hợp).

**Hạ TTL 10→5 phút (2026-09-23)**: phát hiện chi phí kinh doanh thật của cửa sổ TTL — trong lúc chờ TTL sweep, `reservedQuantity` vẫn tính SKU là đang giữ dù order đã chắc chắn huỷ, khiến khách khác thấy "hết hàng" oan (đặc biệt tệ với SKU hot/flash-sale). Lý do 10 phút ban đầu ("dài hơn 3 phút deadline của `Order`") không đúng bottleneck thật — chuỗi compensate bình thường (`OrderCancelled` → `ReleaseReservation`, kể cả `republishCancellation`) chỉ mất vài giây bất kể `Order` tự huỷ sau 3 giây hay 3 phút; TTL chỉ cần đủ dài hơn thời gian lan truyền compensate thật (giây), không cần so với deadline của `Order`. 5 phút vẫn đủ margin cho case compounding-failure hiếm gặp, giảm một nửa chi phí giữ tồn kho oan ở trường hợp xấu nhất.

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

| Trigger                       | Handler                      | Publishes                                                            |
|-------------------------------|------------------------------|----------------------------------------------------------------------|
| `order.order.created`         | `OrderCreatedConsumer`       | `InventoryReserved` \| `InventoryReservationFailed`                  |
| `order.order.cancelled`       | `OrderCancelledConsumer`     | `InventoryReleased`                                                  |
| `order.order.confirmed`       | `OrderConfirmedConsumer`     | — *(2026-09-22, mới — `CommitReservation`, chuyển `Reservation` sang `COMMITTED`, dừng TTL)* |
| `scheduler.job.fired`         | `ScheduledJobFiredConsumer`  | — *(TTL heartbeat cho `Reservation`, `@KafkaListener` đang comment — chưa đăng ký job, xem Phase R4)* |
| `catalog.variant.created`     | `VariantCreatedConsumer`     | —  *(init Stock, sellerActive/productPublished mirror theo payload)* |
| `catalog.variant.activated`   | `VariantActivatedConsumer`   | —                                                                    |
| `catalog.variant.deactivated` | `VariantDeactivatedConsumer` | —                                                                    |
| `catalog.variant.deleted`     | `VariantDeletedConsumer`     | — *(hard-delete Stock row — chỉ xảy ra khi Variant bị xoá cứng lúc Product còn DRAFT, an toàn tuyệt đối vì chưa từng có Order/reservation nào)* |
| `catalog.product.published`   | `ProductPublishedConsumer`   | — *(set `productPublished=true` cho toàn bộ SKU của product)*        |
| `catalog.product.unpublished` | `ProductUnpublishedConsumer` | — *(set `productPublished=false` cho toàn bộ SKU của product)*       |
| `catalog.product.blocked`     | `ProductBlockedConsumer`     | —                                                                    |
| `catalog.product.unblocked`   | `ProductUnblockedConsumer`   | —                                                                    |

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

| Feature                                               | Role                           | Handles                         | Publishes                                         |
|-------------------------------------------------------|--------------------------------|---------------------------------|---------------------------------------------------|
| [place-order](../../feature/07-place-order/design.md) | Participant — reservation step | `OrderCreated`                  | `InventoryReserved`, `InventoryReservationFailed` |
| [place-order](../../feature/07-place-order/design.md) | Participant — compensate step  | `OrderCancelled`                | `InventoryReleased`                               |
| [place-order](../../feature/07-place-order/design.md) | Participant — commit step (2026-09-22) | `OrderConfirmed`         | —                                                  |
| [flashsale.md](flashsale.md)                          | Enforcer — slot guard          | `OrderCreated` *(limited path)* | `InventoryReserved`, `InventoryReservationFailed` |

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
| `order.order.created`         | `OrderCreated`            | `OrderCreatedConsumer`        | DB `UNIQUE(order_id)` + `FOR UPDATE` trên stock (`ReserveInventory`) |
| `order.order.cancelled`       | `OrderCancelled`          | `OrderCancelledConsumer`      | DB `isPending()` + `FOR UPDATE` trên reservation/stock (`ReleaseReservation`) |
| `order.order.confirmed`       | `OrderConfirmed`          | `OrderConfirmedConsumer`      | DB `isPending()` + `FOR UPDATE` trên reservation (`CommitReservation`) — 2026-09-22, mới |
| `scheduler.job.fired`         | `ScheduledJobFiredEvent`  | `ScheduledJobFiredConsumer`   | Opaque heartbeat, không mang state — `@KafkaListener` đang comment, chưa đăng ký job (Phase R4) |
| `catalog.variant.created`     | `VariantCreatedEvent`     | `VariantCreatedConsumer`      | DB `existsBySkuId` + `UNIQUE(sku_id)` (`InitializeStock`) |
| `catalog.variant.activated`   | `VariantActivatedEvent`   | `VariantActivatedConsumer`    | DB fast-path + `@Version` (`ActivateStock`) |
| `catalog.variant.deactivated` | `VariantDeactivatedEvent` | `VariantDeactivatedConsumer`  | DB fast-path + `@Version` (`DeactivateStock`) |
| `catalog.variant.deleted`     | `VariantDeletedEvent`     | `VariantDeletedConsumer`      | DELETE tự thân idempotent (`DeleteStock`) |
| `catalog.product.published`   | `ProductPublishedEvent`   | `ProductPublishedConsumer`    | DB set tuyệt đối + `@Version` (`PublishProductStocks`) |
| `catalog.product.unpublished` | `ProductUnpublishedEvent` | `ProductUnpublishedConsumer`  | DB set tuyệt đối + `@Version` (`DeactivateProductStocks`) |
| `catalog.product.blocked`     | `ProductBlockedEvent`     | `ProductBlockedConsumer`      | DB set tuyệt đối + `@Version` (`BlockProductStocks`) |
| `catalog.product.unblocked`   | `ProductUnblockedEvent`   | `ProductUnblockedConsumer`    | DB set tuyệt đối + `@Version` (`ActivateStocksOnProductUnblocked`) |

**2026-09-20:** toàn bộ 9 consumer trên (trừ `OrderCreatedConsumer` vốn đã đúng từ đầu) đã bỏ hẳn Redis
`IdempotencyGuard` — trước đó guard-trước-DB kiểu `tryAcquire`/`release` có lỗ hổng thật (crash giữa 2
bước làm key rò rỉ, event bị nuốt mất khi Kafka redeliver, không tự phục hồi tới khi TTL 7 ngày hết hạn).
Chuyển hẳn sang DB-based, khớp quy tắc ở `global/3.technical/idempotency-layering.md`. 2 handler cần thêm
biện pháp mới (không chỉ bỏ Redis): `ReleaseReservation` thêm `findByOrderIdForUpdate`/`findBySkuIdForUpdate`
(trước đó đọc không khoá, có race double-release dưới concurrency thật); `InitializeStock` thêm catch
`DataIntegrityViolationException` quanh `save()` (đã đổi `StockPersistenceAdapter.save()` sang
`saveAndFlush` để race ở `UNIQUE(sku_id)` surface đồng bộ, thay vì lọt ra ngoài thành lỗi thật phải đợi 1
vòng retry mới tự chữa).

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
