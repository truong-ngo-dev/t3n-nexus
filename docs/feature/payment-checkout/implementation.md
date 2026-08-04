# Implementation Plan: Payment Checkout — Nhánh COD

**Design**: [`design.md`](design.md) | **Sequence**: nhúng vào `design.md` ở Phase 9 (xem cuối file), không tạo file `.puml` riêng

**Scope**: chỉ nhánh **COD** (Happy Path + failure `OUT_OF_STOCK` + `INVENTORY_TIMEOUT`). Nhánh Prepaid (`payment-service`, `AWAITING_PAYMENT`, payment timeout mechanism) **không** nằm trong plan này — làm ở implementation.md riêng khi bắt đầu.

**Progress log**: mỗi phase khi bắt đầu code tạo `progress/phase-N-<tên>.md` (status, checklist, session log) — không tạo trước, tạo khi vào phase đó.

---

## Điểm chưa chốt

- ~~Shape của `address`~~ — đã chốt, xem ghi chú ở Phase 1.
- Cơ chế lấy `customerId` từ authenticated principal ở `order-service` — xem caveat ở Phase 1, cần giải quyết trước/trong Phase 7-8.

---

## Quyết định kiến trúc — `Order` đổi từ Event Sourcing sang CRUD

**Đã ghi thành ADR chính thức**: [ADR-010](../../global/2.architecture/adr/010-order-crud-not-event-sourcing.md) — đây là bản tóm tắt tại chỗ, xem ADR để có context + consequences đầy đủ và danh sách doc liên quan cần đọc kèm.

**Bối cảnh**: `Order` được build ở Phase 1 dùng `event-sourcing-starter` (đã có sẵn lib trong hệ thống). Khi thiết kế `CREATED`-timeout (Phase 5) mới phát hiện: Event Sourcing thuần không query được kiểu "tìm mọi order status=X" — bắt buộc phải bolt-on thêm 1 bảng projection riêng chỉ để làm được 1 việc tầm thường.

**Đánh giá lại theo 4 tiêu chí đáng để trả chi phí ES** — không tiêu chí nào đúng cho `Order`:
1. Temporal query ("state tại thời điểm T quá khứ") — không có yêu cầu này.
2. Invariant cần replay để tính đúng — không: `canProcess()` chỉ check `status` hiện tại, logic giống hệt dù load qua replay hay qua 1 `SELECT` CRUD.
3. Optimistic concurrency — `@Version` JPA field (như `StockJpaEntity` đang dùng) cho đúng bảo vệ này, rẻ hơn nhiều so với `event_store` + `UNIQUE(aggregate_id, revision)`.
4. Audit/history — phục vụ được bằng Kafka topic retention (`order.order.*`) hoặc 1 bảng `order_status_history` đơn giản, không cần cả cơ chế ES.

Thêm 1 dữ kiện: `order-service` là **consumer duy nhất** của `event-sourcing-starter` trong toàn hệ thống (grep xác nhận) — chi phí không được amortize qua aggregate nào khác.

**Quyết định**: đổi `Order` sang CRUD thường (bảng `orders`, `status` column, `@Version`). Giữ lại `event-sourcing-starter` lib — dự định dùng cho **`Loyalty Points Ledger`** (`customer-service`, ngoài scope payment-checkout) sau này: bản chất ledger (đã thiết kế insert-only trong `customer-service/data.md`), phân tán theo `customerId` (không dồn vào hot SKU như flash sale), đúng use case kinh điển của Event Sourcing/ledger pattern — không đụng vào bất kỳ hot path nào của hệ thống.

**Hệ quả cho các phase liên quan**: Phase 1 (đã build ES) cần rework ở Phase 2 (mới). Phase 3 (Kafka consumer, cũ là Phase 2) đổi cơ chế idempotency từ `EventStoreConflictException` sang `ObjectOptimisticLockingFailureException`. Phase 5 (`CREATED` timeout, cũ là Phase 4) không cần bảng `order_summary` riêng nữa — bảng `orders` CRUD tự nó query được.

---

## Docs cần tạo / cập nhật

| Tài liệu                                              | Hành động                          | Nội dung                                                                                                          |
|-------------------------------------------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `infra/README.md`                                     | Cập nhật                           | Thêm connector `order-outbox-connector` vào bảng "Topics được tạo bởi các connectors"                             |
| `service/order-service/service.md`                    | Tạo mới (chưa tồn tại)             | Domain model `Order` (CRUD, không phải ES), Commands, Events, Integration Contract                                |
| `service/notification-service/service.md`             | Cập nhật nếu tồn tại, tạo nếu chưa | Thêm handler `OrderConfirmedHandler`/`OrderCancelledHandler`                                                      |
| `service/inapp-worker/service.md`                     | Tạo mới                            | Service hoàn toàn mới                                                                                             |
| `service/notification-service/service.md`             | Cập nhật                           | Events Consumed: đổi `OrderConfirmed`/`OrderCancelled` từ "later" → "current"                                     |
| `global/2.architecture/5. event-catalog.md`           | Kiểm tra + sửa nếu cần             | `OrderCancelled.reason` — catalog có `cancelledBy`, code hiện không có; thêm `OrderInventoryTimeoutCheck` nếu cần |
| `design.md`                                            | Cập nhật thêm                      | Sửa lại "Payment Timeout Mechanism — Lớp 3" cho đúng cơ chế reconciler (Phase 5 ghi chú chi tiết)                 |
| `design.md` §Sequence Diagram                          | Thêm block `plantuml`              | Vẽ đủ COD happy path + `OUT_OF_STOCK` + `INVENTORY_TIMEOUT`                                                       |

---

## Thứ tự triển khai

_Implement theo dependency chain — producer trước consumer. Blast radius mỗi phase = 1 service (trừ Phase 0 là infra thuần)._

**Ưu tiên theo yêu cầu người dùng (2026-08-03)**: tập trung xong "core order flow" trước khi sang notification/gateway/FE. Thứ tự core: **tạo đơn (Phase 1) → check kho + reserve stock (inventory-service, đã có sẵn) → confirm (Phase 3) → cuối cùng: cancel khi đơn chưa confirm sau khoảng thời gian quy định (Phase 5 — `CREATED` timeout)**. Phase 4 (inventory-service idempotency hardening) xen giữa vì đã code sẵn, chỉ cần verify. Phase 6-9 (notification-service, inapp-worker, gateway, FE) làm sau khi core flow xong và verify được, không làm song song.

**Ghi chú kèm theo — chỗ cancel prepaid/COD, đối chiếu lại sau khi Order đổi CRUD (session 2026-08-03)**:
- Loại exception concurrent-conflict đã thống nhất `OptimisticLockingFailureException` (không phải `EventStoreConflictException`) — áp dụng chung cho mọi `OrderCancelReason`, cả COD (`OUT_OF_STOCK`, `INVENTORY_TIMEOUT`) lẫn Prepaid sau này (`PAYMENT_INIT_FAILED`/`PAYMENT_REJECTED`/`PAYMENT_TIMEOUT`), vì dùng chung `CancelOrder.handle()`.
- Lý do "không dùng raw SQL CAS cho Lớp 3 cancel action" cần đính chính lúc sửa `design.md` (Phase 10): **không phải** vì "Order event-sourced, không có bảng để UPDATE" (giờ có bảng `orders` thật rồi) — mà vì raw SQL bypass tầng aggregate + Outbox (vi phạm ADR-005), đúng bất kể Order là ES hay CRUD. Kết luận (phải gọi `CancelOrder.handle()`) không đổi, chỉ lý do nêu ra cần sửa.
- Late-reply "re-publish `OrderCancelled`" (Phase 3/5 TODO) — **chưa có lời giải cụ thể**, không đổi gì so với lúc Order còn ES. `Order.cancel()` vẫn no-op nếu đã `CANCELLED` (business rule trong aggregate, không phụ thuộc persistence) — cần 1 method riêng kiểu `Order.republishCancellation()` (raise event không mutate state) hoặc dispatch trực tiếp từ consumer, chưa thiết kế chi tiết.
- Điểm mới phát sinh **chỉ vì CRUD** (không tồn tại lúc ES): `orders.status` có `CHECK` constraint DB thật — khi làm prepaid, thêm `AWAITING_PAYMENT` cần migration `ALTER` constraint này, việc này không tồn tại khi Order chưa lưu state trực tiếp.

### Phase 0 — Infra: Debezium connector cho `order-service` outbox

**Vì sao trước tiên**: `order-service` publish `OrderCreated`/`OrderConfirmed`/`OrderCancelled` vào bảng `outbox_events`, nhưng **chưa có connector nào đọc bảng này** — không giống `identity`/`oauth2`/`catalog`/`notification` đã có connector riêng trong `infra/debezium/`. Không có bước này thì không event nào của `order-service` tới được Kafka, toàn bộ saga đứng im dù code phía sau đúng 100%. Không đổi gì khi `Order` chuyển sang CRUD — outbox pattern giữ nguyên bất kể aggregate ES hay CRUD.

- [x] Tạo `infra/debezium/connector-order-outbox.json` — copy mẫu `connector-catalog-outbox.json`, đổi `database.hostname=postgres-order`, `database.dbname=order_db`, `topic.prefix=order`
- [x] Cập nhật bảng topics trong `infra/README.md`
- [ ] Đăng ký: `curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @debezium/connector-order-outbox.json` — cần Docker chạy

**Verify**: `curl http://localhost:8083/connectors/order-outbox-connector/status` → `RUNNING`. Gọi `POST /api/orders` → message xuất hiện ở topic `order.order.created` (kiểm bằng console consumer).

---

### Phase 1 — `order-service`: mở rộng domain cho `paymentMethod` + `address`, publish đúng payload

**Chặn bởi**: ~~quyết định shape `address`~~ — đã chốt: `ShippingAddress` VO (`recipientName, phone, addressLine, ward, province, note`) mirror schema `delivery_addresses` đã thiết kế sẵn ở `customer-service/data.md`, trừ `label`/`isDefault` (chỉ có ý nghĩa với address book, không phải snapshot). Không có `district` (theo mô hình hành chính 2 cấp sau sáp nhập 01/07/2025). Không sync-call sang `customer-service` để verify — FE gửi nguyên field xuống dù gõ tay hay chọn từ sổ, `order-service` chỉ validate shape cục bộ (Bean Validation ở request DTO). Inventory reservation không cần biết address (inventory chỉ theo SKU, không theo kho/vùng — xem `inventory-service/service.md`: "Không quản lý vị trí kho vật lý — Warehouse BC Phase 2").

**Lưu ý**: build lần đầu ở phase này dùng Event Sourcing — sẽ rework sang CRUD ở Phase 2 ngay sau. Danh sách dưới đây giữ nguyên để lịch sử phase rõ ràng, không xoá.

- [x] `PaymentMethod` enum (`COD`, `PREPAID`) — `domain/order/`
- [x] `OrderCancelReason` enum (`OUT_OF_STOCK` cho phase này; `PAYMENT_INIT_FAILED`/`PAYMENT_REJECTED`/`PAYMENT_TIMEOUT` để sẵn chỗ cho prepaid) thay cho `String reason` tự do trong `Order.cancel()`/`OrderCancelledEvent`
- [x] `ShippingAddress` VO — `domain/order/`
- [x] `Order`: thêm field `paymentMethod`, `shippingAddress`; cập nhật `create()` factory nhận thêm 2 tham số (validate `shippingAddress != null` → `ORDER_MISSING_SHIPPING_ADDRESS`); cập nhật `apply(OrderCreatedEvent)` để set field mới
- [x] `OrderCreatedEvent.Payload`: thêm `paymentMethod`, `shippingAddress` — khớp `OrderCreatedConsumer.Payload` bên `inventory-service` đã kỳ vọng field `paymentMethod` và khớp `event-catalog.md` dòng 69
- [x] `OrderCancelledEvent.Payload`: `reason` đổi type String → `OrderCancelReason`
- [x] `CreateOrder.Command`/`Result`: thêm `paymentMethod`, `address`; `Result` thêm `status`
- [x] `CancelOrder.Command`: `reason` đổi type sang `OrderCancelReason`
- [x] `GetOrder.Result`: thêm `paymentMethod`, `shippingAddress`, `cancelReason` đổi type
- [x] `OrderController` thật ở `presentation/order/` (+ `presentation/order/model/`: `CreateOrderRequest`, `ShippingAddressRequest`, `OrderItemRequest`, `OrderResponse`, `OrderDetailResponse`) — thay cho `DevOrderController`:
  - `POST /api/orders` → 201 `{ orderId, status }`
  - `GET /api/orders/{orderId}` → dùng cho FE poll fallback
- [x] Xoá `presentation/dev/DevOrderController`

**Đã verify**: `mvn compile` sạch.

**Ghi chú caveat (chưa giải quyết trong phase này)**: `customerId`/`sellerId` hiện nhận thẳng từ request body, chưa có cơ chế lấy từ authenticated principal (order-service chưa có resource-server/JWT parsing như `inventory-service`) — sẽ cần giải quyết ở Phase 7/8 khi nối FE thật qua `web-gateway` (tokenRelay), không phải lỗi riêng của order-service.

---

### Phase 2 — `order-service`: refactor `Order` từ Event Sourcing sang CRUD

**Chặn bởi**: Phase 1 (Order đã có đủ field). **Vì sao**: xem "Quyết định kiến trúc" ở đầu file.

- [x] Migration (`V1__init_schema.sql`, sửa trực tiếp vì chưa từng apply ở đâu): thay `event_store` bằng bảng `orders` — `id, customer_id, seller_id, items (JSONB), payment_method, shipping_address (JSONB), status, cancel_reason, version (BIGINT, @Version), created_at, updated_at`
- [x] `OrderJpaEntity` + `OrderJpaRepository` (`infrastructure/persistence/order/`) — `@JdbcTypeCode(SqlTypes.JSON)` cho `items`/`shipping_address` (theo đúng mẫu `NotificationLogJpaEntity`), `@Version` cho optimistic concurrency
- [x] `OrderMapper` (static utility, theo mẫu `StockMapper`) — dùng `JsonUtils`/`TypeReference` (đã có sẵn trong `common-utils`) để serialize/deserialize `items`/`shippingAddress`; version field round-trip qua `ReflectionUtils` (field `version` kế thừa từ `AbstractAggregateRoot`, không expose public getter — giữ domain sạch)
- [x] `Order` aggregate: bỏ `extends EventSourcedAggregateRoot<OrderId>`, đổi sang `extends AbstractAggregateRoot<OrderId>` — `create()`/`confirm()`/`cancel()`/`canProcess()` giữ nguyên logic, mutate field trực tiếp + `addDomainEvent()` thay vì `raise()`; thêm `reconstitute()` factory thay cho `rehydrate()`; thêm `createdAt`/`updatedAt` (trước đây suy ra từ `occurredOn` của event, giờ cần field riêng)
- [x] `OrderRepositoryAdapter`: bỏ `EventStore`, load/save qua `OrderJpaRepository` trực tiếp — `findById()` là 1 `SELECT`, không replay
- [x] `OrderCreatedEvent`/`OrderConfirmedEvent`/`OrderCancelledEvent`: bỏ dual-constructor (business + `@JsonCreator` reconstitution) — chỉ còn 1 constructor
- [x] **Tiện thể sửa 1 bug có sẵn từ trước**: `OrderCancelledEvent.Payload` thiếu `orderId` — `inventory-service/OrderCancelledConsumer` đã kỳ vọng `Payload(orderId, reason, cancelledBy)` nhưng order-service chưa từng gửi `orderId` trong payload (chỉ dựa vào `aggregate_id` ở tầng envelope). Thêm `orderId` vào `Payload`.
- [x] `GetOrder.Result`: bỏ field `revision` (chỉ có ý nghĩa với `EventSourcedAggregateRoot`, không còn tồn tại)
- [x] Xoá dependency `event-sourcing-starter` khỏi `order-service/pom.xml`; thêm `common-utils` (cho `ReflectionUtils`/`JsonUtils`)
- [x] `InventoryReservedConsumer`/`InventoryReservationFailedConsumer`: đổi catch `EventStoreConflictException` → `OptimisticLockingFailureException`

**Đã verify**: `mvn compile` sạch. **Chưa verify runtime** (cần Docker/Postgres chạy) — `POST /api/orders` → `GET /api/orders/{orderId}` trả đúng ngay từ 1 `SELECT`, không qua replay; test optimistic conflict (2 request `ConfirmOrder` đồng thời cùng `orderId` → 1 thành công, 1 catch `OptimisticLockingFailureException` → no-op sạch) vẫn cần môi trường thật để chạy.

---

### Phase 3 — `order-service`: Kafka consumer cho `InventoryReserved` / `InventoryReservationFailed`

**Chặn bởi**: Phase 0 + Phase 2 (cần `Order` đã là CRUD để đổi cơ chế idempotency).

**Quyết định idempotency — DB-based, không dùng Redis**: pattern gốc trong `saga-dlq-integration.md` (Redis `tryAcquire`/`release` làm lớp 1) có 1 lỗ hổng thật — nếu consumer crash **giữa lúc `tryAcquire` thành công và trước khi kịp `release()`/`ack()`**, key Redis vẫn còn (chưa release, TTL dài ~7 ngày). Khi Kafka redeliver, `tryAcquire` lần 2 thấy key đã tồn tại → code coi là duplicate → ack ngay mà không hề xử lý → **event bị nuốt mất, im lặng, mất dữ liệu thật**. Redis chỉ có 2 trạng thái (acquired / not-acquired), không phân biệt được "đã xong" với "đã lock nhưng crash giữa chừng".

Thay vào đó dùng đúng 2 lớp đều DB, không có "khoá" nào có thể rò rỉ:
- **`Order.canProcess()`** — check state machine trước khi mutate, chặn late/duplicate reply *tuần tự*. Đặt bên trong `ConfirmOrder.handle()`/`CancelOrder.handle()` (không phải ở consumer) — theo đúng chỗ inventory-service đặt check tương tự.
- **`ObjectOptimisticLockingFailureException`** (từ `@Version` sau khi đổi sang CRUD ở Phase 2) — bắt race *đồng thời* thật giữa 2 lần delivery cùng lúc. Consumer catch riêng exception này, coi là no-op, không cho lọt vào retry/DLQ của `DefaultErrorHandler`.

- [x] Thêm dependency `spring-boot-starter-kafka` vào `order-service/pom.xml` (không thêm `idempotency-support`/Redis)
- [x] `spring.kafka.*` (bootstrap, deserializer) + `app.kafka.topic.inventory-reserved=inventory.reservation.created`, `app.kafka.topic.inventory-reservation-failed=inventory.reservation.failed`, `app.kafka.consumer-group.inventory`, `app.kafka.topic.dlq` vào `application.properties`
- [x] `MessagingConfig` (`infrastructure/crosscutting/config/`) — `EventEnvelopeDecoder` bean + `ConcurrentKafkaListenerContainerFactory` với `DeadLetterPublishingRecoverer` (retry 3 lần, backoff 2s) — theo đúng mẫu `inventory-service/MessagingConfig`
- [x] `Order.canProcess()` — bỏ tham số không dùng
- [x] `ConfirmOrder.handle()`/`CancelOrder.handle()` — thêm `if (!order.canProcess()) return new Result();` trước khi mutate
- [x] `InventoryReservedConsumer` (`infrastructure/adapter/messaging/inventory/`) — load `Order`, rẽ nhánh `paymentMethod == COD` → `ConfirmOrder.handle()`; `PREPAID` → log skip (không xử lý ở phase này)
- [x] `InventoryReservationFailedConsumer` — gọi `CancelOrder.handle(reason=OUT_OF_STOCK)`
- [x] Catch `OptimisticLockingFailureException` (đổi từ `EventStoreConflictException` sau khi Phase 2 xong) ở cả 2 consumer — payload decode chuyển ra ngoài `try` để `orderId` vẫn log được trong catch block
- [ ] **Cập nhật sau Phase 5**: thêm logic re-publish `OrderCancelled` khi `canProcess()==false` và order đang `CANCELLED` (late-reply handling — xem ghi chú Phase 5)

**Đã verify**: `mvn compile` sạch (sau khi đổi sang `OptimisticLockingFailureException`).

---

### Phase 4 — `inventory-service`: idempotency hardening cho `ReserveInventory`/`RecordReservationFailure`

**Bối cảnh**: phát hiện khi audit lại flow reserve (không thuộc scope order-service, nhưng ảnh hưởng trực tiếp tới failure scenario `OUT_OF_STOCK` của COD) — `reservation.order_id` có `UNIQUE` constraint thật (`V1__init_schema.sql:45`), nhưng không ai catch `DataIntegrityViolationException` khi race xảy ra; và Redis limited-offer slot check chạy vô điều kiện cho mọi SKU dù có flash sale hay không.

- [x] `ReservationPersistenceAdapter.save()`: đổi `jpaRepository.save()` → `saveAndFlush()` — bắt buộc để constraint check nổ ngay tại đây thay vì lúc commit (ngoài method, không catch được)
- [x] `ReserveInventory.handle()`: catch `DataIntegrityViolationException` quanh `reservationRepository.save()` → `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` + return — **không** return suông (sẽ commit nhầm `stock.reserve()` của lần trùng, gây double-reserve thật)
- [x] `RecordReservationFailure.handle()`: cùng pattern — catch + `setRollbackOnly()` + return
- [x] Bỏ hoàn toàn Phase 1 (Redis limited-offer slot check) khỏi `ReserveInventory.handle()` — flow giờ chỉ còn: check `existsByOrderId` → sort theo `skuId` → lặp `findBySkuIdForUpdate` + `reserve()` → save `Reservation`. **Không xoá** `SlotService`/`RedisSlotAdapter`/`LimitedOffer` domain/`ActivateLimitedOffer`/`DeactivateLimitedOffer`/`LimitedOfferController` — giữ nguyên để dùng lại sau, chỉ tháo khỏi flow reserve
- [x] Xác nhận (không sửa, ghi chú lại): `findBySkuIdForUpdate` chỉ dùng ở `ReserveInventory` — pessimistic lock không bảo vệ reserve-vs-release/setQuantity; nguồn bảo vệ lost-update xuyên suốt thật là `@Version` trên `StockJpaEntity`. Việc `ReleaseReservation`/`SetStockQuantity` có nên dùng `findBySkuIdForUpdate` hay không — **để riêng, không thuộc scope payment-checkout**, cần phân tích flow `ReleaseReservation` độc lập nếu muốn làm.
- [ ] ~~(Sau này, khi nối lại flash-sale) Denormalize `hasActiveLimitedOffer` lên `Stock`~~ — **sai hướng, đã đính chính (xem phiên 2026-08-03 sau)**: flash sale không nên nối lại inline vào `ReserveInventory` bằng cách nào cả, kể cả denormalize flag. Theo đúng thiết kế đã có sẵn ở `docs/service/inventory-service/flashsale.md` (chưa build), flash sale phải đi qua **pipeline hoàn toàn riêng** — endpoint `/flash-sale/checkout` riêng, Redis Slot Gate (Bloom Filter + DECR) riêng, Kafka topic `inventory.flash-sale.requests` partition theo **`skuId`** (khác `orderId` như order thường) → consumer riêng dùng OCC (không `FOR UPDATE`, vì partition theo `skuId` đã tự loại bỏ conflict tại nguồn — xem lý do chi tiết trong `flashsale.md` phần "Tại sao Kafka partition by skuId"). Việc xoá Redis slot-check khỏi `ReserveInventory` ở dòng trên (Phase 4) **đúng hướng với thiết kế này** — flash sale vốn không nên chung logic với reserve thường. Xem "Components cần bổ sung" cuối `flashsale.md` cho danh sách việc cần làm khi build pipeline này — ngoài scope payment-checkout hoàn toàn, chỉ note lại để không quên.

**Đã verify**: `mvn compile` sạch. **Chưa verify runtime** race thật (cần môi trường Kafka+Postgres chạy đồng thời nhiều consumer).

---

### Phase 5 — `order-service`: `CREATED`-state timeout (Redis ZSET + Postgres backstop, theo đúng mẫu `AWAITING_PAYMENT`)

**Bối cảnh**: Order có thể kẹt vĩnh viễn ở `CREATED` nếu không bao giờ nhận được `InventoryReserved`/`InventoryReservationFailed` (inventory-service down dài hạn, message mất...). `design.md` hiện chỉ có timeout cho `AWAITING_PAYMENT` (prepaid, 15 phút, ngoài scope) — **không cover `CREATED`, áp dụng cho cả COD lẫn Prepaid**, nên thuộc scope COD.

**Lịch sử quyết định** (giữ lại để nhớ tại sao, không lặp lại sai lầm):
1. ~~Bảng `order_summary` riêng + partial index~~ — bị bác bỏ ban đầu vì lo ngại chi phí ghi index/scan, đề xuất Kafka delay-topic (partition pause) thay thế.
2. ~~Kafka delay-topic~~ — bị bác bỏ tiếp vì Kafka không có delay queue native, tự chế pause/resume dễ gây head-of-line blocking, không phải pattern proven — quay lại dùng Redis ZSET, đúng pattern `AWAITING_PAYMENT` đã có sẵn trong design.md.
3. **Chốt cuối**: sau khi `Order` đổi sang CRUD ở Phase 2, bảng `orders` chính nó **đã queryable** — không cần bảng `order_summary` riêng nữa. Dùng thẳng Redis ZSET (Lớp 1, tốc độ) + query trực tiếp bảng `orders` (Lớp 2, backstop) — y hệt cơ chế `AWAITING_PAYMENT`, chỉ khác thời lượng.

- [ ] `orders` table (đã có từ Phase 2): thêm cột `inventory_reply_deadline TIMESTAMPTZ`, set = `now() + 3 phút` lúc `Order.create()`
- [ ] Partial index `idx_orders_inventory_timeout ON orders (inventory_reply_deadline) WHERE status='CREATED'`
- [ ] `OrderCancelReason` thêm `INVENTORY_TIMEOUT`
- [ ] Lớp 1 (Redis ZSET): `ZADD delayed:order-inventory-timeout <deadlineEpoch> <orderId>` lúc tạo Order; `ZREM` trong `InventoryReservedConsumer`/`InventoryReservationFailedConsumer` khi nhận reply (dọn sớm)
- [ ] Worker poll `ZRANGEBYSCORE ... 0 now` mỗi vài giây, atomic pop (Lua) — với mỗi `orderId` quá hạn: re-check `canProcess()` trước khi cancel (an toàn nếu race với path khác)
- [ ] Lớp 2 (Postgres backstop): `@Scheduled` job (2-5 phút/lần) — `SELECT id FROM orders WHERE status='CREATED' AND inventory_reply_deadline < now()`
- [ ] Lớp 3 (cancel action, dùng chung cho cả 2 lớp): **KHÔNG raw SQL CAS**. Gọi đúng `CancelOrder.handle(new CancelOrder.Command(orderId, INVENTORY_TIMEOUT))` — tận dụng `canProcess()` + `ObjectOptimisticLockingFailureException` đã có ở Phase 3, không viết CAS riêng. Lý do: `Order` giờ vẫn qua domain layer + outbox — raw SQL UPDATE thẳng vào bảng sẽ bỏ qua toàn bộ publish event, consumer khác (inventory-service, notification-service) không biết gì.
- [ ] **Quan trọng — quay lại sửa Phase 3**: `InventoryReservedConsumer`/`InventoryReservationFailedConsumer` khi `canProcess()==false` hiện chỉ log skip. Nếu Order đã bị timeout-cancel ở đây, còn `InventoryReserved` tới muộn do inventory-service chỉ CHẬM (không phải mất hẳn) — inventory-service **sẽ vẫn tạo Reservation thật** cho order đã cancelled (vì `OrderCreated` gốc vẫn còn trong Kafka, chưa từng bị huỷ), và `OrderCancelledConsumer` bên inventory đã chạy trước đó thấy "chưa có reservation, no-op" — **không ai release lại**, tạo Reservation mồ côi giữ stock vĩnh viễn. Xử lý theo đúng pattern "late reply" trong `saga-dlq-integration.md`: khi `canProcess()==false` VÀ order đang `CANCELLED`, phải **re-publish `OrderCancelled` với `eventId` mới** để trigger `OrderCancelledConsumer` chạy lại, lần này release đúng.

**Verify**: tạo Order, giả lập inventory-service không phản hồi → sau ~3 phút thấy `Order` tự `CANCELLED` (`INVENTORY_TIMEOUT`) qua Lớp 1 (Redis, nhanh) hoặc Lớp 2 (Postgres, backstop nếu Redis miss). Giả lập inventory-service phản hồi **muộn** sau khi đã timeout-cancel → xác nhận `OrderCancelled` được re-publish, `ReleaseReservation` chạy đúng lần 2, không có Reservation mồ côi nào còn `PENDING`.

---

### Phase 6 — `notification-service`: handler cho `OrderConfirmed` / `OrderCancelled`

**Chặn bởi**: Phase 3 (cần event thật để test, có thể mock trong lúc chờ).

- [ ] `OrderConfirmedPayload` (record) — theo mẫu `LoginOtpRequestedPayload`
- [ ] `OrderConfirmedHandler implements NotificationEventHandler<OrderConfirmedPayload>` — `application/handler/`:
  - `supportedEventType()` = `"OrderConfirmedEvent"`
  - Trả `NotificationResult.of(logs, inboxes)` — **cả EMAIL lẫn IN_APP** (khác với `LoginOtpRequestedHandler.emailOnly()`), khớp bảng Channel Routing trong `notification-service.md` (`OrderConfirmed`: Email T1 + In-App)
- [ ] `OrderCancelledPayload` + `OrderCancelledHandler` — tương tự, Email T1 + In-App
- [ ] Thêm 2 topic (`order.order.confirmed`, `order.order.cancelled`) vào `@KafkaListener(topics = {...})` của `NotificationOutboxEventConsumer` + property `app.kafka.topic.order-confirmed`/`order-cancelled`
- [ ] Cập nhật `service/notification-service/service.md` — Events Consumed: `OrderConfirmed`/`OrderCancelled` từ "later" → "current"

**Verify**: publish `OrderConfirmed` thật (từ Phase 3) → row mới trong `notification_log` (channel=IN_APP và EMAIL) + `notification_inbox`. CDC route đúng: `notification.inapp.dispatch` có message tương ứng (connector đã sẵn từ trước, không cần đổi).

---

### Phase 7 — `inapp-worker`: service mới

**Chặn bởi**: Phase 6 (cần message thật trên `notification.inapp.dispatch` để test).

- [ ] Scaffold service mới `services/inapp-worker/` — theo mẫu `email-worker` (`spring.main.web-application-type=none`, pure Kafka consumer)
- [ ] `pom.xml`: `spring-boot-starter-kafka`, `spring-boot-starter-data-redis-reactive`, `observability-starter`, `common-events`
- [ ] `InappDispatchConsumer` — consume `notification.inapp.dispatch`:
  - Check Redis idempotency key `inapp:{notification_log_id}`, skip nếu tồn tại
  - `Redis PUBLISH user:{userId}:inapp {payload}`
  - `SET` idempotency key TTL 72h, commit offset
  - Fail → propagate exception → Kafka retry → DLQ sau max retry
- [ ] Thêm vào `services/pom.xml` (parent) làm module

**Verify**: publish message giả vào `notification.inapp.dispatch` → `redis-cli SUBSCRIBE user:{userId}:inapp` nhận đúng payload. Publish trùng `notification_log_id` → không publish lần 2 (check log "skip duplicate").

---

### Phase 8 — Gateway routes

**Chặn bởi**: Phase 1 (cần `OrderController` thật tồn tại để route tới).

- [ ] `web-gateway/RouteConfiguration.java`: thêm route `order-service` theo đúng pattern 3 route hiện có (`path("/api/order/**", "/web/api/order/**")`, `rewritePath`, `tokenRelay`, `saveSession`)
- [ ] `web-gateway/application.properties`: thêm `webgateway.routes.order-service.uri`
- [ ] **Không cần** thêm route cho `websocket-gateway` ở `api-gateway`/`web-gateway` — theo `4. communication.md:49`, WS data-plane connect thẳng, chỉ mint ticket qua `web-gateway` (endpoint `/webgw/auth/ws-ticket` đã có sẵn, không đổi)
- [ ] Verify path `ws-ticket` reachable qua `api-gateway` `/web/**` → `web-gateway` (đã có route `/web/**` generic, không cần thêm)

**Verify**: `curl -X POST http://api-gateway/web/api/order/api/orders ...` (qua cookie session thật) → tới được `order-service`. `GET .../webgw/auth/ws-ticket` → trả `{ticket}`.

---

### Phase 9 — FE (Angular)

**Chặn bởi**: Phase 1, 8 (cần API thật), Phase 7 (cần WS thật để test end-to-end).

- [ ] `feature-checkout`: trang chọn COD + form địa chỉ (theo shape đã chốt ở mục "Điểm chưa chốt"), nút "Xác nhận"
- [ ] Kết nối WS `/inapp` (lấy ticket qua `GET /webgw/auth/ws-ticket`) **ngay khi vào trang checkout**, trước khi bấm xác nhận — đúng rule trong `design.md` Business Rules
- [ ] `useOrderStatusListener(orderId)` (hoặc service Angular tương đương) — dùng chung, tách khỏi component:
  - Lọc message theo `orderId`
  - Timeout ~30s không nhận được gì → hiện "Không xác định được kết quả..." + trigger poll `GET /orders/{orderId}`
- [ ] `feature-order`: màn "Đặt hàng thành công" (`OrderConfirmed`) / "Sản phẩm đã hết hàng" (`OrderCancelled` reason=`OUT_OF_STOCK`) / "Hết thời gian xử lý" (reason=`INVENTORY_TIMEOUT`)
- [ ] State "Đang xử lý đơn hàng..." ngay sau `POST /orders` trả 201, trước khi có WS message

**Verify**: chạy thật trình duyệt — đặt COD với sản phẩm còn hàng → thấy "Đặt hàng thành công" real-time. Đặt COD với sản phẩm hết hàng (seed data 0 tồn kho) → thấy "Sản phẩm đã hết hàng". Tắt mạng giả lập mất WS → sau 30s thấy fallback message + poll vẫn ra đúng kết quả.

---

### Phase 10 — Docs sync cuối

- [ ] `service/order-service/service.md` — tạo mới, điền theo template, phản ánh đúng state CRUD (không phải ES) sau Phase 0-5
- [ ] `service/inventory-service/service.md`/`data.md` — cập nhật đúng thực tế: bỏ mô tả Redis limited-offer khỏi flow reserve chính (Phase 4), sửa lại claim "SELECT FOR UPDATE là cơ chế chính chống oversell" cho đúng vai trò `@Version`
- [ ] `service/inapp-worker/service.md` — tạo mới
- [ ] `global/2.architecture/event-catalog.md` — chốt field `cancelledBy` (thêm vào code hoặc bỏ khỏi catalog); xác nhận `paymentMethod` trong `OrderCreated` khớp code
- [ ] `design.md` — sửa lại đoạn "Payment Timeout Mechanism — Lớp 3" cho đúng cơ chế reconciler (gọi `CancelOrder.handle()`, không phải raw SQL CAS) — áp dụng khi làm prepaid, nhưng nên sửa doc ngay vì đã phát hiện sai
- [ ] `design.md` §Sequence Diagram — thêm block `plantuml` vẽ COD happy path + `OUT_OF_STOCK` + `INVENTORY_TIMEOUT`, theo đúng thứ tự Phase 0-9 vừa làm
- [ ] Đổi `design.md` **Status**: `Draft` → `Implemented (COD)` khi xong hết, ghi chú Prepaid vẫn Draft

---

## Ghi chú riêng — Event Sourcing cho `Loyalty Points Ledger` (ngoài scope, làm sau)

Khi bắt đầu feature loyalty (`customer-service`), cân nhắc dùng `event-sourcing-starter` cho `LoyaltyPointLedger` thay vì `Order`:
- Bản chất ledger (EARN/REDEEM/EXPIRE/ADJUST) đã insert-only, thiết kế sẵn trong `customer-service/data.md`
- Phân tán theo `customerId` — không dồn vào hot aggregate như `Stock` lúc flash sale
- Có giá trị audit thật (dispute "sao tôi có từng này điểm")

Không tạo implementation.md riêng cho việc này bây giờ — chỉ ghi lại để không quên hướng đi khi tới lúc.

---

## Checklist hoàn thành (nhánh COD)

- [ ] Happy path COD chạy end-to-end qua UI thật (không qua `Dev*Controller`)
- [ ] Failure `OUT_OF_STOCK` chạy end-to-end, FE hiện đúng message
- [ ] Failure `INVENTORY_TIMEOUT` chạy end-to-end (inventory-service không phản hồi kịp → order tự huỷ), kể cả case reply muộn sau khi đã timeout-cancel (late reply re-publish đúng)
- [ ] `DevOrderController` đã xoá
- [ ] `Order` là CRUD (không còn `event-sourcing-starter`), `event-sourcing-starter` chỉ còn dự định dùng cho Loyalty Ledger sau này
- [ ] Outbox → Kafka hoạt động qua connector thật (không mất event khi restart order-service)
- [ ] Idempotency `order-service`: replay `InventoryReserved`/`InventoryReservationFailed` không tạo duplicate state (`Order` vẫn đúng 1 lần confirm/cancel), dựa trên `canProcess()` + `ObjectOptimisticLockingFailureException`, không Redis cho phần này
- [ ] Idempotency `inventory-service`: race 2 delivery cùng `orderId` không double-reserve stock, không leak Reservation nào bị `DataIntegrityViolationException` chưa catch
- [ ] `notification_log` + `notification_inbox` có đúng record cho mỗi `OrderConfirmed`/`OrderCancelled`
- [ ] Tất cả service docs (`service/order-service`, `service/inventory-service`, `service/inapp-worker`, `notification-service.md`) khớp thực tế
- [ ] `event-catalog.md` khớp payload thật 100%
