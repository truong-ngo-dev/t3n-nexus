# Implementation Plan — Inventory Service

**Design:** [`service.md`](service.md) | **Schema:** [`data.md`](data.md)

> **Cập nhật 2026-07-08**: Model `Stock` đã tiến hoá qua V2/V3/V4 migration, khác với mô tả `StockStatus ACTIVE|INACTIVE` ở Phase 1.1 bên dưới (giữ nguyên phần lịch sử để tham khảo, không sửa lại). Trạng thái đúng hiện tại: `service.md` + `data.md`. Tóm tắt: 1 enum `status` ban đầu → tách thành `sellerActive` + `adminBlocked` (V3) → tách tiếp `productPublished` (V4), vì `sellerActive` một mình không đủ đại diện cho cả trạng thái publish của product lẫn trạng thái bật/tắt riêng của variant — bug thực tế: variant thêm vào product đã publish bị kẹt non-sellable. Phase 1.4 (catalog consumers) cũng đã có thêm 3 consumer không nằm trong plan gốc: `VariantCreatedConsumer` (init Stock, mirror `active`/`productPublished` từ event thay vì hardcode), `VariantActivatedConsumer`, `ProductUnblockedConsumer`. `ProductPublishedHandler` đã đổi tên/logic thành `PublishProductStocks` — set `productPublished=true` cho toàn bộ SKU theo `productId`, không còn cần snapshot `skuIds[]` từ event.

---

## Docs cần tạo / cập nhật

| Tài liệu                                    | Hành động            | Nội dung                                                               |
|---------------------------------------------|----------------------|------------------------------------------------------------------------|
| `service/inventory-service/service.md`      | Đã tạo               | Domain model, integration contract                                     |
| `service/inventory-service/data.md`         | Đã tạo               | DB schema, indexes                                                     |
| `service/inventory-service/api.yaml`        | Tạo khi Phase 1 xong | Seller stock endpoints                                                 |
| `global/2.architecture/5. event-catalog.md` | Cập nhật khi Phase 4 | Thêm `LimitedOfferActivated`/`LimitedOfferDeactivated` từ Promotion BC |

---

## Phase Overview

| Phase | Tên                       | Dependency |
|-------|---------------------------|------------|
| 0     | Bootstrap                 | —          |
| 1     | Stock Management          | Phase 0    |
| 2     | Reservation — Normal Path | Phase 1    |
| 3     | Depletion Events + Outbox | Phase 2    |
| 4     | Limited Offer             | Phase 2    |
| 5     | Verify & Polish           | Phase 3, 4 |

---

## Phase 0 — Bootstrap

### Module & Config

- [x] Tạo Maven module `inventory-service` trong root `pom.xml`
- [x] `pom.xml` — dependencies:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `spring-kafka`
  - `redisson-spring-boot-starter`
  - `flyway-core`
  - `common-domain` (AggregateRoot, Money, EventDispatcher)
  - `outbox-starter` (OutboxEventStore)
  - `common-events` (EventEnvelope)
  - `web-commons` (ApiResponse)
  - `observability-starter`
- [x] `InventoryServiceApplication.java` — `@SpringBootApplication`
- [x] `application.yml` — datasource, Kafka consumer groups, Redis, Flyway

### Flyway

- [x] `V1__init_schema.sql` — tạo đủ 6 tables theo `data.md`:
  - `stock` với CHECK `total_quantity >= 0`, `reserved_quantity >= 0`
  - `reservation`
  - `reservation_item` với CHECK `qty > 0`
  - `limited_offer` với CHECK `max_quantity > 0`, `window_seconds > 0`
  - `processed_event`
  - `outbox_events`
  - Toàn bộ indexes đã ghi trong `data.md`

### Cross-cutting Setup

- [x] `EventDispatcherConfig.java` trong `infrastructure/cross-cutting/config/` — wire `EventDispatcher` với tất cả `EventHandler<?>` beans
- [ ] `IdempotencyHelper.java` trong `infrastructure/cross-cutting/utils/` — method `markProcessed(eventId)` và `isProcessed(eventId)` dùng `processed_event` table; trả về `boolean`, dùng `INSERT ... ON CONFLICT DO NOTHING`
  > **Note:** Hiện dùng `IdempotencyGuard` (Redis) thay thế. Catalog consumers không cần (naturally idempotent); OrderCreated dedup qua `existsByOrderId`. Xem xét bỏ hoàn toàn.
- [x] `KafkaConsumerConfig.java` — configure 2 consumer groups:
  - `inventory-catalog-consumer` — consume events từ catalog-service
  - `inventory-order-consumer` — consume events từ order-service

---

## Phase 1 — Stock Management

### 1.1 Domain Layer

- [x] `domain/stock/StockId.java` — Value Object, wrap UUID, static `generate()` và `of(UUID)`
- [x] `domain/stock/StockStatus.java` — enum `ACTIVE | INACTIVE`
- [x] `domain/stock/StockErrorCode.java` — implement `ErrorCode` từ `libs/common`:
  - `STOCK_NOT_FOUND` (404)
  - `STOCK_ALREADY_EXISTS` (409)
  - `STOCK_INACTIVE` (422)
  - `INSUFFICIENT_STOCK` (422) — dùng ở Phase 2
- [x] `domain/stock/StockException.java` — static factory methods: `notFound()`, `alreadyExists()`, `inactive()`, `insufficientStock(String skuId, int requested, int available)`, `accessDenied()`, `invalidQuantity()`
- [x] `domain/stock/Stock.java` — Aggregate Root:
  - Fields: `StockId id`, `UUID skuId`, `UUID productId`, `UUID sellerId`, `int totalQuantity`, `int reservedQuantity`, `StockStatus status`, `int lowStockThreshold`, `long version`
  - `availableQuantity()` — computed: `totalQuantity - reservedQuantity`
  - `Stock.initialize(UUID skuId, UUID sellerId, UUID productId)` — static factory, qty=0, status=ACTIVE
  - `setQuantity(int newTotal)` — guard: `newTotal >= 0`; guard: `newTotal >= reservedQuantity` (không được set thấp hơn đang reserved); emit `StockReplenished` nếu trước đó `availableQuantity() == 0` và sau đó > 0
  - `deactivate()` — set INACTIVE; không xóa data, không rollback reservations
  - *(Phase 2)* `reserve(int qty)` và `release(int qty)` — thêm sau
- [x] `domain/stock/StockRepository.java` — interface:
  - `Optional<Stock> findById(StockId id)`
  - `Optional<Stock> findBySkuId(UUID skuId)`
  - `List<Stock> findByProductId(UUID productId)`
  - `List<Stock> findBySellerId(UUID sellerId, Pageable pageable)`
  - `void save(Stock stock)`

### 1.2 Infrastructure Layer

- [x] `infrastructure/persistence/stock/StockJpaEntity.java` — `@Entity @Table("stock")`, tất cả columns, `@Version long version`
- [x] `infrastructure/persistence/stock/StockJpaRepository.java` — Spring Data interface:
  - `Optional<StockJpaEntity> findBySkuId(UUID skuId)`
  - `List<StockJpaEntity> findByProductId(UUID productId)`
  - `Page<StockJpaEntity> findBySellerId(UUID sellerId, Pageable pageable)`
- [x] `infrastructure/persistence/stock/StockMapper.java` — `toDomain(StockJpaEntity)` và `toJpaEntity(Stock)`
- [x] `infrastructure/adapter/repository/stock/StockPersistenceAdapter.java` — implements `StockRepository`; delegate sang `StockJpaRepository` + `StockMapper`

### 1.3 Application Layer — Commands

**`application/stock/set_quantity/`**

- [x] `SetStockQuantity.java` — single file convention:
  - `Command(UUID skuId, UUID sellerId, int quantity)` — seller set absolute qty
  - `Result(UUID stockId, int totalQuantity, int availableQuantity)`
  - `Handler`:
    1. Load `Stock` by `skuId` — nếu không có thì throw `StockErrorCode.STOCK_NOT_FOUND` (seller phải init trước qua catalog event)
    2. Verify `stock.getSellerId().equals(command.sellerId())` — ownership check
    3. `stock.setQuantity(command.quantity())`
    4. `stockRepository.save(stock)`
    5. `eventDispatcher.dispatchAll(stock.pullEvents())`
    6. Return `Result`

**`application/stock/get_stock_by_sku/`**

- [x] `GetStockBySku.java` — implemented as `GetStock.java`:
  - `Query(UUID skuId, UUID sellerId)`
  - `Result(UUID stockId, UUID skuId, int totalQuantity, int reservedQuantity, int availableQuantity, StockStatus status)`
  - `Handler`: load by skuId → ownership check → map to Result

**`application/stock/get_stock_list/`**

- [x] `GetStockList.java`:
  - `Query(String sellerId, int page, int size)`
  - `Result(List<StockSummary> items, long total)` với `StockSummary(String skuId, int totalQuantity, int availableQuantity, String status)`
  - `Handler`: load by sellerId với pagination (sort createdAt DESC) → map to Result

### 1.4 Application Layer — Kafka Consumers (Catalog Events)

Tất cả nằm trong `application/stock/event/`:

- [x] `ProductPublishedHandler.java` — implements `EventHandler<ProductPublishedEvent>`:
  1. `idempotencyHelper.isProcessed(eventId)` → skip nếu true
  2. Với mỗi `skuId` trong `event.skuIds()`:
     - `stockRepository.findBySkuId(skuId)` — nếu đã có thì skip (idempotent)
     - Nếu chưa có: `Stock.initialize(skuId, event.sellerId(), event.productId())`
     - `stockRepository.save(stock)`
  3. `idempotencyHelper.markProcessed(eventId)`

- [x] `ProductUnpublishedHandler.java` — implements `EventHandler<ProductUnpublishedEvent>`:
  1. Idempotency check
  2. `stockRepository.findByProductId(event.productId())` → danh sách Stock
  3. Mỗi stock: `stock.deactivate()` → `stockRepository.save(stock)`
  4. Mark processed

- [x] `ProductBlockedHandler.java` — implements `EventHandler<ProductBlockedEvent>`:
  - Logic giống `ProductUnpublishedHandler`, deactivate theo `productId`

- [x] `VariantDeactivatedHandler.java` — implements `EventHandler<VariantDeactivatedEvent>`:
  1. Idempotency check
  2. `stockRepository.findBySkuId(event.skuId())` → nếu không có thì skip (có thể chưa published)
  3. `stock.deactivate()` → save
  4. Mark processed

### 1.5 Presentation Layer

- [x] `presentation/stock/model/SetStockQuantityRequest.java` — `record(int quantity)` với `@Min(0)`
- [x] `presentation/stock/model/StockResponse.java` — full response DTO
- [x] `presentation/stock/model/StockSummaryResponse.java` — dùng `GetStockList.StockSummary` trực tiếp, không tạo file riêng
- [x] `presentation/stock/StockController.java`:
  - `POST /api/seller/inventory/stocks` → `InitializeStock` *(thêm so với plan)*
  - `PUT /api/seller/inventory/stocks/{skuId}/quantity` → `SetStockQuantity`
  - `GET /api/seller/inventory/stocks/{skuId}` → `GetStock`
  - `GET /api/seller/inventory/stocks` → `GetStockList` (page, size query params)

### Verify Phase 1

```bash
# 1. Publish product từ catalog-service → ProductPublishedEvent
# → inventory-service tạo Stock với qty=0, status=ACTIVE

GET /api/seller/inventory/{skuId}
# → 200, totalQuantity: 0, availableQuantity: 0, status: ACTIVE

# 2. Seller set stock
PUT /api/seller/inventory/{skuId}
{ "quantity": 100 }
# → 200, totalQuantity: 100, availableQuantity: 100

# 3. Deactivate variant → VariantDeactivatedEvent
GET /api/seller/inventory/{skuId}
# → 200, status: INACTIVE

# 4. Duplicate ProductPublishedEvent
# → không tạo Stock mới, không lỗi (idempotent)
```

---

## Phase 2 — Reservation (Normal Path)

### 2.1 Domain Layer — Stock methods (bổ sung)

- [x] `Stock.reserve(int qty)`:
  - Guard: `status == ACTIVE` → throw `StockException.inactive()`
  - Guard: `availableQuantity() >= qty` → throw `StockException.insufficientStock(skuId, qty, availableQuantity())`
  - `this.reservedQuantity += qty`
  - Emit `StockDepletedEvent` nếu `availableQuantity() == 0` (chính xác sau khi trừ)

- [x] `Stock.release(int qty)`:
  - Guard: `qty > 0`
  - Guard: `reservedQuantity >= qty` (tránh âm)
  - `boolean wasDepletedBefore = availableQuantity() == 0`
  - `this.reservedQuantity -= qty`
  - Emit `StockReplenishedEvent` nếu `wasDepletedBefore && availableQuantity() > 0`

- [x] `StockRepository.findBySkuIdForUpdate(UUID skuId)` — thêm vào interface (Phase 2 cần)

### 2.2 Domain Layer — Reservation Aggregate

- [x] `domain/reservation/ReservationId.java` — Value Object
- [x] `domain/reservation/ReservationStatus.java` — enum `PENDING | RELEASED | CANCELLED`
- [x] `domain/reservation/ReservationItem.java` — Entity (owned by Reservation):
  - `ReservationItemId id`, `UUID skuId`, `int qty`
- [x] `domain/reservation/ReservationErrorCode.java`:
  - `RESERVATION_NOT_FOUND` (404)
  - `RESERVATION_ALREADY_EXISTS` (409) — duplicate orderId
  - `RESERVATION_NOT_PENDING` (422) — release khi không phải PENDING
- [x] `domain/reservation/ReservationException.java` *(chưa tạo — throw DomainException trực tiếp)*
- [x] `domain/reservation/Reservation.java` — Aggregate Root:
  - Fields: `ReservationId id`, `UUID orderId`, `ReservationStatus status`, `List<ReservationItem> items`, `Instant expiresAt`, `Instant createdAt`
  - `Reservation.create(UUID orderId, List<ReservationItem> items, Instant expiresAt)` — static factory; guard: `items` không rỗng
  - `release()` — guard: `status == PENDING`; set `RELEASED`
  - `cancel()` — guard: `status == PENDING`; set `CANCELLED`
- [x] `domain/reservation/ReservationRepository.java` — interface:
  - `Optional<Reservation> findByOrderId(UUID orderId)`
  - `void save(Reservation reservation)`

### 2.3 Infrastructure Layer — Pessimistic Lock

- [x] `StockJpaRepository` — bổ sung:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM StockJpaEntity s WHERE s.skuId = :skuId")
  Optional<StockJpaEntity> findBySkuIdForUpdate(@Param("skuId") UUID skuId);
  ```
- [x] `StockPersistenceAdapter` — implement `findBySkuIdForUpdate`
- [x] `infrastructure/persistence/reservation/ReservationJpaEntity.java` — `@Entity @Table("reservation")`
- [x] `infrastructure/persistence/reservation/ReservationItemJpaEntity.java` — `@Entity @Table("reservation_item")`; `@ManyToOne` sang `ReservationJpaEntity`
- [x] `infrastructure/persistence/reservation/ReservationJpaRepository.java`
- [x] `infrastructure/persistence/reservation/ReservationMapper.java`
- [x] `infrastructure/adapter/repository/reservation/ReservationPersistenceAdapter.java`

### 2.4 Domain Events — Saga Reply

Cả 2 Saga reply events đều đi qua **Outbox** — đây là yêu cầu bắt buộc từ ADR-005, không có ngoại lệ. Lý do: atomicity giữa nghiệp vụ (reserve/fail) và việc gửi event phải được đảm bảo; KafkaTemplate trực tiếp không có atomicity này.

- [x] `domain/reservation/InventoryReservedEvent.java` — fields: `UUID orderId`, `List<ReservedItem> items`
- [x] `domain/reservation/InventoryReservationFailedEvent.java` — fields: `UUID orderId`, `String reason`, `UUID failedSkuId`

Cả 2 events raise từ `Reservation` aggregate:
- `Reservation.create(...)` → raise `InventoryReservedEvent`
- `Reservation.markFailed(reason, failedSkuId)` → raise `InventoryReservationFailedEvent`; status = `CANCELLED`

### 2.5 Application Layer — Kafka Consumers

**`application/reservation/event/OrderCreatedHandler.java`** — implements `EventHandler<OrderCreated>`:

```
handle(OrderCreated event):
  try:
    executeInTransaction(event)   ← T1: reserve thành công → outbox ghi InventoryReservedEvent
  catch InsufficientStock | StockInactive:
    writeFailureOutbox(event)     ← T2 (REQUIRES_NEW): T1 đã rollback, cần transaction mới
                                     để ghi InventoryReservationFailedEvent vào outbox

@Transactional  ← T1
executeInTransaction(event):
  1. Idempotency check: isProcessed(event.eventId()) → skip nếu đã xử lý
  2. Dedup by orderId: reservationRepository.findByOrderId(event.orderId())
     → nếu đã có Reservation thì skip (idempotent)
  3. Sort items by skuId ASC → consistent lock order, tránh deadlock
  4. stocks = []
     foreach item in sortedItems:
       stock = stockRepository.findBySkuIdForUpdate(item.skuId())
                               .orElseThrow(StockException::notFound)
       stock.reserve(item.qty())   ← domain guard bên trong
       stocks.add(stock)
  5. foreach stock in stocks: stockRepository.save(stock)
  6. reservation = Reservation.create(event.orderId(), items, expiresAt)
     → raise InventoryReservedEvent bên trong
  7. reservationRepository.save(reservation)
  8. eventDispatcher.dispatchAll(stocks.events + reservation.pullEvents())
     → OutboxEventHandler ghi InventoryReservedEvent + StockDepletedEvent (nếu có) vào outbox_events
  9. markProcessed(event.eventId())
  → T1 commit: reservation saved + outbox records saved — atomic

@Transactional(REQUIRES_NEW)  ← T2, chạy sau khi T1 rollback
writeFailureOutbox(event, reason, failedSkuId):
  1. reservation = Reservation.markFailed(event.orderId(), reason, failedSkuId)
     → raise InventoryReservationFailedEvent
  2. reservationRepository.save(reservation)
  3. eventDispatcher.dispatch(reservation.pullEvents())
     → OutboxEventHandler ghi InventoryReservationFailedEvent vào outbox_events
  4. markProcessed(event.eventId())   ← mark ở đây, trong T2
  → T2 commit: failure outbox record saved — atomic
```

**Tại sao cần 2 transaction tách biệt:**
- Success path: T1 commit → outbox có `InventoryReservedEvent` → Debezium → Kafka
- Failure path: T1 rollback (không save gì) → cần T2 để ghi `InventoryReservationFailedEvent` vào outbox; nếu không có T2, order-service không bao giờ nhận được reply → Saga treo vĩnh viễn

**`application/reservation/event/OrderCancelledHandler.java`** — implements `EventHandler<OrderCancelled>`:

```
@Transactional
handle(OrderCancelled event):
  1. Idempotency check
  2. reservation = reservationRepository.findByOrderId(event.orderId())
     → nếu không có: log warn, markProcessed, return
       (order bị cancel trước khi inventory reserve — valid scenario)
  3. Nếu reservation.status != PENDING: markProcessed, return (đã release rồi)
  4. Sort items by skuId ASC (consistent lock order)
  5. foreach item in reservation.items():
       stock = stockRepository.findBySkuIdForUpdate(item.skuId())
               → nếu không có: log error, continue (stock đã deactivate)
       stock.release(item.qty())
       stockRepository.save(stock)
  6. reservation.release()   ← raise InventoryReleasedEvent bên trong
  7. reservationRepository.save(reservation)
  8. eventDispatcher.dispatchAll(all events)
     → outbox ghi InventoryReleasedEvent + StockReplenishedEvent (nếu có)
  9. markProcessed(event.eventId())
  → commit: atomic
```

### Verify Phase 2

```bash
# 1. Publish OrderCreated với items hợp lệ
# → inventory-service nhận event
# → InventoryReserved emitted → order-service nhận

GET /api/seller/inventory/{skuId}
# → reservedQuantity tăng đúng qty, availableQuantity giảm tương ứng

# 2. Publish OrderCreated với qty > stock
# → InventoryReservationFailed emitted

# 3. Duplicate OrderCreated (cùng orderId)
# → skip (idempotent), không reserve thêm

# 4. Publish OrderCancelled
# → InventoryReleased emitted
# → reservedQuantity giảm về 0 (nếu chỉ có 1 order)

# 5. Concurrent: 2 OrderCreated cho cùng SKU có qty = stock/2
# → cả 2 thành công, không oversell
# Concurrent: 2 OrderCreated, tổng qty > stock
# → 1 thành công, 1 fail → không oversell
```

---

## Phase 3 — Depletion Events + Outbox

### 3.1 Domain Events

- [x] `domain/stock/StockDepletedEvent.java` — fields: `UUID skuId`, `UUID sellerId`
- [x] `domain/stock/StockReplenishedEvent.java` — fields: `UUID skuId`, `UUID sellerId`, `int newAvailableQty`
- [x] `domain/reservation/InventoryReleasedEvent.java` — fields: `UUID orderId`, `List<ReleasedItem> items`
- [x] `InventoryReservedEvent` và `InventoryReservationFailedEvent` đã định nghĩa ở Phase 2.4

Raise points:
- `Stock.reserve()` → `StockDepletedEvent` (nếu availableQty về 0)
- `Stock.release()` → `StockReplenishedEvent` (nếu availableQty từ 0 lên dương)
- `Reservation.create()` → `InventoryReservedEvent`
- `Reservation.markFailed()` → `InventoryReservationFailedEvent`
- `Reservation.release()` → `InventoryReleasedEvent`

### 3.2 Outbox Wiring — tất cả 5 events

- [x] `OutboxEventHandlers.java` trong `infrastructure/cross-cutting/config/` — đăng ký 5 `EventHandler` beans:

| Event                             | routingKey                       | partitionKey | Payload                                |
|-----------------------------------|----------------------------------|--------------|----------------------------------------|
| `InventoryReservedEvent`          | `inventory.reservation.created`  | `orderId`    | `{ orderId, items[]{skuId, qty} }`     |
| `InventoryReservationFailedEvent` | `inventory.reservation.failed`   | `orderId`    | `{ orderId, reason, failedSkuId }`     |
| `InventoryReleasedEvent`          | `inventory.reservation.released` | `orderId`    | `{ orderId, items[]{skuId, qty} }`     |
| `StockDepletedEvent`              | `inventory.stock.depleted`       | `skuId`      | `{ skuId, sellerId }`                  |
| `StockReplenishedEvent`           | `inventory.stock.replenished`    | `skuId`      | `{ skuId, sellerId, newAvailableQty }` |

Mỗi bean: `outboxStore.store(EventEnvelope.wrap(event, routingKey, partitionKey))`

### Verify Phase 3

```bash
# 1. Reserve hết toàn bộ stock của 1 SKU
# → outbox_events có record với routing_key = "inventory.stock.depleted"
# → Debezium pick up → Kafka topic "inventory.stock.depleted"
# → search-service nhận, update stock filter

# 2. Cancel order → release reservation → available trở lại > 0
# → outbox_events có record "inventory.stock.replenished"

# 3. Restart service giữa chừng, outbox chưa publish
# → Debezium tự retry, event không bị mất
```

---

## Phase 4 — Limited Offer (Redis Lua + Bloom Filter)

### 4.1 Domain Layer

- [x] `domain/limited_offer/LimitedOfferId.java`
- [x] `domain/limited_offer/LimitedOfferStatus.java` — enum `INACTIVE | ACTIVE | ENDED`
- [x] `domain/limited_offer/LimitedOfferErrorCode.java`:
  - `LIMITED_OFFER_NOT_FOUND` (404)
  - `LIMITED_OFFER_ALREADY_ACTIVE` (409)
  - `LIMITED_OFFER_NOT_ACTIVE` (422)
- [x] `domain/limited_offer/LimitedOfferException.java` — `notFound()`, `alreadyActive()`, `notActive()`, `slotExhausted()`
- [x] `domain/limited_offer/LimitedOffer.java` — Aggregate Root:
  - `activate(int maxQty, int windowSeconds)` — guard: `status != ACTIVE`; set ACTIVE + `activatedAt`
  - `end()` — guard: `status == ACTIVE`; set ENDED + `endedAt`
- [x] `domain/limited_offer/LimitedOfferRepository.java`:
  - `Optional<LimitedOffer> findBySkuId(UUID skuId)`
  - `void save(LimitedOffer offer)`
- [x] `domain/limited_offer/SlotService.java` — Domain Service interface (port):
  - `void initSlots(UUID skuId, int maxQuantity)` — init Redis counter
  - `void clearSlots(UUID skuId)` — clear Redis counter + xóa khỏi Bloom Filter nếu có thể
  - `SlotCheckResult tryDecrementSlot(UUID skuId)` — Lua script; returns `SOLD_OUT | SLOT_GRANTED | NO_LIMIT`
  - `void returnSlot(UUID skuId)` — INCR back khi rollback

### 4.2 Infrastructure — Redis Adapter

- [x] `infrastructure/persistence/limited_offer/` — JPA entities, repository, mapper, adapter (giống các phases trước)
- [x] `infrastructure/adapter/service/slot/RedisSlotAdapter.java` — implements `SlotService`:

  **`initSlots(skuId, maxQty)`:**
  ```
  SET inventory:limited:{skuId}:slots {maxQty}
  ```

  **`clearSlots(skuId)`:**
  ```
  DEL inventory:limited:{skuId}:slots
  bloomFilter.remove(skuId)  // Redisson RBloomFilter — nếu dùng Counting Bloom Filter
  // Nếu dùng standard Bloom Filter: rebuild filter từ danh sách depleted SKUs trong DB
  ```

  **`tryDecrementSlot(skuId)`:**
  ```
  1. Kiểm tra Redis key có tồn tại không:
     - Không có key → NO_LIMIT (SKU này không có limited offer active)
  2. bloomFilter.contains(skuId) → true → return SOLD_OUT (fast-fail)
  3. Chạy Lua script:
     local slots = redis.call('GET', KEYS[1])
     if slots == false or tonumber(slots) <= 0 then return -1 end
     local newVal = redis.call('DECR', KEYS[1])
     return newVal
  4. result < 0:
     → INCR lại để rollback (Lua chỉ DECR rồi check, có thể < 0 nếu race)
     → return SOLD_OUT
  5. result == 0:
     → bloomFilter.add(skuId)  ← từ giờ Bloom Filter chặn trước
     → return SLOT_GRANTED
  6. result > 0 → return SLOT_GRANTED
  ```

  **Redis config:** `RBloomFilter` khởi tạo với `expectedInsertions = 5_000_000` (5M SKUs), `falseProbability = 0.01` (1% false positive).

### 4.3 Application Layer — Commands

**`application/limited_offer/activate/ActivateLimitedOffer.java`:**
- `Command(UUID skuId, UUID sellerId, int maxQuantity, int windowSeconds)`
- `Handler`:
  1. Load Stock by skuId → verify sellerId ownership
  2. `limitedOfferRepository.findBySkuId(skuId)` — nếu đang ACTIVE thì throw `LIMITED_OFFER_ALREADY_ACTIVE`
  3. Upsert: nếu chưa có thì tạo `LimitedOffer` mới; nếu có (ENDED) thì reuse
  4. `limitedOffer.activate(maxQuantity, windowSeconds)`
  5. `slotService.initSlots(skuId, maxQuantity)` — init Redis counter
  6. `limitedOfferRepository.save(limitedOffer)`

**`application/limited_offer/deactivate/DeactivateLimitedOffer.java`:**
- `Command(UUID skuId, UUID sellerId)`
- `Handler`:
  1. Load LimitedOffer by skuId → verify ownership qua Stock
  2. Guard: `status == ACTIVE`
  3. `limitedOffer.end()`
  4. `slotService.clearSlots(skuId)`
  5. `limitedOfferRepository.save(limitedOffer)`

### 4.4 Cập nhật OrderCreatedHandler

Thêm Limited Offer check **trước** bước SELECT FOR UPDATE:

```
foreach item in sortedItems:
  slotResult = slotService.tryDecrementSlot(item.skuId())
  match slotResult:
    SOLD_OUT  → collect failedSkuId, break
    NO_LIMIT  → continue bình thường (sẽ đến SELECT FOR UPDATE)
    SLOT_GRANTED → continue (đã giữ slot Redis, sẽ xác nhận ở DB)

Nếu có failedSkuId:
  → rollback tất cả Redis slots đã DECR (INCR lại)
  → emit InventoryReservationFailed
  → return

Proceed với SELECT FOR UPDATE như Phase 2...

Nếu DB fail sau khi Redis đã DECR:
  → exception handler: INCR lại tất cả skuId đã DECR (compensate Redis)
  → emit InventoryReservationFailed
```

### 4.5 Presentation — Admin Endpoints

- [x] `presentation/limited_offer/model/ActivateLimitedOfferRequest.java` — `record(int maxQuantity, int windowSeconds)`
- [x] `presentation/limited_offer/LimitedOfferController.java`:
  - `POST /api/seller/inventory/stocks/{skuId}/limited-offer` → `ActivateLimitedOffer`
  - `DELETE /api/seller/inventory/stocks/{skuId}/limited-offer` → `DeactivateLimitedOffer`

### Verify Phase 4

```bash
# Setup: SKU với stock = 5, activate limited offer maxQty = 3

# 1. Gửi 10 concurrent OrderCreated cho cùng SKU (mỗi order qty=1)
# → Đúng 3 order nhận InventoryReserved
# → 7 order còn lại nhận InventoryReservationFailed (reason: SOLD_OUT)
# → DB stock.reserved_quantity = 3, không phải 5

# 2. Bloom Filter hoạt động: slot về 0 → gửi thêm 100 request
# → Bị chặn tại Bloom Filter, không có request nào chạm Redis/DB

# 3. Deactivate limited offer → gửi thêm OrderCreated
# → Chạy normal path (SELECT FOR UPDATE), reserve từ stock còn lại (2 units)

# 4. False positive test: restock sau khi limited offer ended
# → Bloom Filter rebuild → request tiếp theo không bị false positive block
```

---

## Phase 5 — Verify & Polish

- [ ] End-to-end test: catalog publish → stock init → seller set qty → order reserve → order cancel → release
- [ ] Concurrent test: N=50 concurrent OrderCreated, verify không oversell
- [ ] Idempotency test: replay cùng eventId nhiều lần → state không thay đổi
- [ ] Debezium verify: kill service sau khi DB write nhưng trước Kafka publish → restart → event vẫn được deliver
- [ ] `api.yaml` — viết OpenAPI spec đầy đủ cho seller endpoints + admin endpoints
- [ ] Cập nhật `global/2.architecture/5. event-catalog.md` — thêm gap note về `LimitedOfferActivated`/`LimitedOfferDeactivated` khi Promotion BC được thiết kế

---

## Checklist hoàn thành

- [ ] Happy path end-to-end: ProductPublished → SetStock → OrderCreated → InventoryReserved → OrderCancelled → InventoryReleased
- [ ] Failure path: OrderCreated với qty > stock → InventoryReservationFailed → không thay đổi stock
- [ ] Concurrent reservation: không oversell dưới load
- [ ] Limited offer: đúng N slot được chấp nhận, phần còn lại rejected qua Redis/Bloom Filter
- [ ] Outbox hoạt động: không mất event khi restart
- [ ] Idempotency: duplicate event không tạo duplicate state
- [ ] Tất cả domain event có trong `event-catalog.md`
- [ ] `service.md` và `data.md` phản ánh đúng code đã implement
