# Implementation Plan: order-service

> Tham khảo: [`design/features/place-order/design.md`](../../design/features/place-order/design.md) — chỉ mang tính demo/định hướng flow tổng (5 service), **không phải plan thực thi**. Plan thực thi của order-service nằm ở file này.

> **⚠️ 2026-08-03 — ADR-010: Event Sourcing cho `Order` đã bị REVERT.** Toàn bộ Phase 0 (ES infra) và phần "Event Sourcing" của Phase 1 dưới đây là **lịch sử**, không còn phản ánh code thật — giữ nguyên để biết tại sao từng làm và tại sao đảo ngược, không xoá. Xem lý do đầy đủ ở [ADR-010](../../global/2.architecture/adr/010-order-crud-not-event-sourcing.md). Plan thực thi hiện tại (CRUD, không ES) nằm ở [`design/features/payment-checkout/implementation.md`](../../design/features/payment-checkout/implementation.md) Phase 1-2 — đó mới là nguồn sự thật cho state hiện tại của `order-service`.

**Scope hiện tại**: chỉ order-service + inventory-service (đã build sẵn). payment-service / fulfillment-service / notification-service để phase sau, không nằm trong file này.

---

## Quyết định đã chốt cho order-service

- ~~**Event Sourcing** cho `Order` aggregate~~ — **REVERTED 2026-08-03, xem ADR-010**. `Order` giờ là CRUD (`orders` table, `@Version`). `event-sourcing-starter` không xoá, dành cho Loyalty Points Ledger (customer-service) sau này — xem ghi chú gốc ở dòng "Snapshot strategy" trong Phase 0 bên dưới, ý tưởng này đã được note từ lúc quyết định ES ban đầu.
- ~~Tách thành Phase 0 riêng (build hạ tầng ES trong `common-domain` trước), không gộp chung phase với order-service để giữ đúng "blast radius nhỏ" / "session-size fit" (`feature-implementation.md`).~~ — không còn áp dụng sau revert.
- **4 lớp bảo vệ idempotency/Saga-DLQ** — build ngay từ bản đầu tiên, không tách phase:
  1. **Redis** (`eventId`) — chặn Kafka redeliver cùng 1 message vật lý. Nhanh, nhưng có TTL và không biết business invariant.
  2. **DB unique constraint** — nguồn sự thật cuối cùng cho business invariant, không TTL, không thể bypass. Áp dụng ở 2 chỗ cụ thể của order-service (xem Phase 1 > Infrastructure): `UNIQUE(aggregate_id, revision)` trên `event_store`, và `UNIQUE` idempotency key trên bảng orders cho `POST /orders`.
  3. **`canProcess` state check** — chặn late reply hợp lệ nhưng sai thời điểm (event không hợp lệ với state hiện tại).
  4. **`handleLateReply`** — self-healing cho orphaned reservation khi late reply xảy ra sau khi đã compensate.

  Redis + DB constraint giải quyết 2 vấn đề khác nhau, không thay thế nhau: Redis chặn *redelivery vật lý*, DB constraint chặn *vi phạm invariant* (kể cả khi đến từ 2 `eventId` khác nhau, hoặc 2 HTTP request khác nhau do double-submit).

  > **Đính chính**: `reservation.order_id` bên inventory-service **đã có** `UNIQUE` constraint thật từ `V1__init_schema.sql` — không phải gap như ghi nhận trước đó (memory cũ sai, đã sửa — xem [[project_order_reservation_gap]]). Gap thật ở đó chỉ là exception-handling chưa graceful, không phải thiếu constraint. Nêu case này ở đây để làm mẫu: pattern "Redis + DB unique constraint" cho order-service nên copy đúng theo pattern **đã có sẵn** ở inventory-service (check-then-act + DB constraint làm lưới an toàn cuối), chứ không phải đang thiếu.
- **Saga timeout** (auto-cancel qua scheduler) — hoãn tới khi có `scheduler-service`, không nằm trong scope 2 phase dưới đây.

---

## Phase 0 — Event Sourcing infra (`common-domain` + `event-sourcing-starter`)

**Status:** `REVERTED` (2026-08-03, xem ADR-010 — infra vẫn còn nguyên trong `libs/event-sourcing-starter`, chỉ không dùng cho `Order` nữa, giữ cho Loyalty Points Ledger sau này)
**Started:** 2026-07-07
**Completed:** — (không hoàn thành theo hướng ban đầu, xem session log 2026-08-03)

### Checklist
- [x] `EventSourcedAggregateRoot<ID>` — `common-domain`, apply/raise/loadFromHistory pattern, field `revision` (đặt tên riêng, không đụng field `version` sẵn có trên `AbstractAggregateRoot` — tránh nhầm với optimistic-lock JPA)
- [x] `EventStore` interface (port trong `common-domain`, package `domain.service`, cạnh `Repository<T,ID>`) + `EventStoreConflictException`
- [x] Module mới `libs/event-sourcing-starter` (mirror `outbox-starter`) — `EventStoreEntity` (JPA, `UNIQUE(aggregate_id, revision)`, cột `correlation_id`), `EventStoreJpaRepository`, `JpaEventStore` (implements `EventStore`, tự sinh 1 `correlationId` mới mỗi lần `append()` — đánh dấu mọi event cùng 1 lần thay đổi, vì revision liền kề không đủ tin cậy để suy luận "cùng 1 use case" hay "2 use case riêng lẻ liên tiếp"; catch `DataIntegrityViolationException` → `EventStoreConflictException`), `EventSourcingAutoConfiguration`, reference DDL `event-store-schema.sql`. `correlationId` chỉ nằm ở `EventStoreEntity` (infra) — không đụng `DomainEvent`/`common-domain`, vì đó là metadata gán lúc persist, không phải thứ event tự mang lúc `raise()`.
- [ ] Bảng `event_store` thật trong DB order-service — chưa tạo, chờ order-service module tồn tại (Phase 1) để copy `event-store-schema.sql` vào Flyway migration riêng, đúng pattern outbox đang làm (mỗi service tự tạo bảng từ reference DDL, starter không tự migrate)
- [ ] Test round-trip: append N event (throwaway aggregate) → replay từ `event_store` → state khớp kỳ vọng — **chưa chạy được**, cần ít nhất 1 DB thật (Testcontainers) hoặc 1 aggregate test trong `event-sourcing-starter`
- [ ] Test conflict: 2 lần `append` cùng `expectedRevision` → lần 2 ném `EventStoreConflictException`
- [x] Snapshot strategy — **quyết định bỏ qua**, không cần cho Order (stream ngắn, ~4-6 event/instance, không tỉ lệ thuận với volume 250k event/ngày toàn hệ thống). Chỉ chuẩn bị sẵn chỗ hở ở `EventStore.loadEvents` để thêm overload theo revision sau này nếu có aggregate khác cần (candidate mạnh nhất nếu cần sau: Loyalty Points Ledger bên customer-service — xem giải thích trong session log)

### Lưu ý thiết kế quan trọng — chưa làm, cần nhớ ở Phase 1

Khi viết `OrderCreatedEvent`/`OrderConfirmedEvent`/`OrderCancelledEvent` thật, mỗi event cần **2 constructor**:
1. Constructor "business" (live) — tự sinh `eventId`/`occurredOn`, dùng khi `raise()`.
2. Constructor "reconstitution" — nhận thẳng `eventId`/`occurredOn`/`aggregateId` (giống hệt convention `create()`/`reconstitute()` đã dùng cho aggregate, áp xuống tầng event), đánh dấu `@JsonCreator`/`@JsonProperty` để `JpaEventStore.deserialize()` gọi đúng constructor này khi replay — nếu không, Jackson gọi nhầm constructor business, sinh `eventId`/`occurredOn` MỚI mỗi lần replay, làm sai lệch lịch sử.

`JpaEventStore` lưu `eventType` = fully-qualified class name (không phải simple name như outbox) — cần thiết để `Class.forName()` resolve đúng lúc load.

### Verify
- Aggregate test (throwaway, không phải Order) — append N event → replay từ event store → state khớp kỳ vọng
- Revision conflict (optimistic concurrency) → ném `EventStoreConflictException` đúng khi ghi đè revision cũ

### Session Log

#### 2026-07-07 — 2026-07-08
- Làm được: `EventSourcedAggregateRoot`, `EventStore`/`EventStoreConflictException` (common-domain), module `event-sourcing-starter` đầy đủ (entity/repository/adapter/auto-config/reference DDL, có `correlation_id`), build toàn workspace pass. Bàn xong quyết định bỏ snapshot cho Order, khảo sát candidate ES khác trong hệ thống.
- **2026-07-08 (tiếp)**: Round-trip verify **thật** — không dùng aggregate throwaway, dev thẳng domain layer + persistence của `Order` (module `order-service` mới, xem chi tiết Phase 1 bên dưới) rồi test qua HTTP + DB thật (không phải JUnit). Kết quả: create → GET (revision=1, CREATED) → confirm → GET (revision=2, CONFIRMED) → confirm lần 2 bị chặn đúng 422 (không phải throw ra 500) → order khác cancel → CANCELLED + cancelReason đúng. Query trực tiếp `event_store`/`outbox_events` xác nhận revision tăng đúng thứ tự, `correlation_id` khác nhau giữa 2 lần đổi riêng biệt, outbox có đủ routing_key cho cả 3 loại event.
- **Bug phát hiện khi chạy thật** (không thấy được nếu chỉ đọc code): (1) `EventStoreEntity` không được Hibernate nhận diện — thiếu `@EntityScan` cho package `vn.t3nexus.lib.eventsourcing` (mọi service khác đã có `JpaConfig` + `@EntityScan` cho `vn.t3nexus.lib.outbox`, order-service copy thiếu phần eventsourcing). (2) `DomainException` trả về `500` thay vì đúng `httpStatus` từ `ErrorCode` — vì `GlobalExceptionHandler` (`common-web`, package `vn.t3nexus.lib.web.*`) nằm ngoài phạm vi component-scan mặc định của `@SpringBootApplication`, phải thêm `scanBasePackages`. **Nghi ngờ đây là bug có sẵn ở catalog-service/inventory-service luôn** (không thấy `@ComponentScan`/`scanBasePackages` nào cho `vn.t3nexus.lib.web` ở 2 service đó) — chưa verify trực tiếp vì 2 service đó chưa từng được chạy thật trong session nào, ghi lại thành việc cần kiểm tra riêng.
- Còn lại: test conflict thật (2 concurrent writer, cùng `expectedRevision`) — chưa làm, khó test qua curl tuần tự, cần JUnit hoặc script riêng. `docs/service/order-service/service.md`/`data.md`/`api.yaml` chưa tạo.
- Blocker: —

#### 2026-08-03 — REVERTED

- Khi implement `CREATED`-state timeout cho `payment-checkout` (feature khác, xem `design/features/payment-checkout/implementation.md`), phát hiện Event Sourcing thuần không query được "mọi order status=X" — cần bolt-on bảng projection riêng chỉ để làm 1 việc tầm thường.
- Đánh giá lại theo 4 tiêu chí đáng để trả chi phí ES (temporal query / invariant cần replay / optimistic concurrency / audit) — không tiêu chí nào đúng cho `Order`. Thêm dữ kiện: `order-service` là consumer duy nhất của `event-sourcing-starter` trong toàn hệ thống, chi phí không amortize qua aggregate nào khác.
- Quyết định: revert `Order` về CRUD (`orders` table, `@Version`). Chi tiết đầy đủ + trade-off ở [ADR-010](../../global/2.architecture/adr/010-order-crud-not-event-sourcing.md).
- `event-sourcing-starter` không xoá — đúng như dự đoán ở dòng "Snapshot strategy" bên trên (2026-07-07/08, đã note "candidate mạnh nhất nếu cần sau: Loyalty Points Ledger"), giữ lại cho use case đó.
- Refactor thật (`Order` → CRUD, `OrderJpaEntity`/`OrderMapper`/`OrderRepositoryAdapter`, bỏ dual-constructor ở 3 event, sửa `InventoryReservedConsumer`/`InventoryReservationFailedConsumer` catch `OptimisticLockingFailureException` thay `EventStoreConflictException`) nằm trong `payment-checkout/implementation.md` Phase 2, không lặp lại chi tiết ở đây.
- Bug tiện thể phát hiện: `OrderCancelledEvent.Payload` thiếu `orderId` — `inventory-service/OrderCancelledConsumer` đã kỳ vọng field này từ trước nhưng order-service chưa từng gửi. Đã sửa cùng lúc.

---

## Phase 1 — order-service core (happy path + 2 compensating path với inventory-service thật)

**Status:** `IN_PROGRESS`
**Started:** 2026-07-08
**Completed:** —
**Phụ thuộc:** Phase 0 xong (ES infra)

### Checklist

**Docs**
- [ ] `docs/service/order-service/service.md` — theo template chuẩn (Aggregates, Commands, Domain Events, Business Rules, Integration Contract)
- [ ] `docs/service/order-service/data.md` — event_store, outbox_events, processed_event, read model table (nếu cần query nhanh ngoài replay)
- [ ] `docs/service/order-service/api.yaml` — `POST /orders`

**Domain** _(lịch sử tại thời điểm viết — `Order` lúc đó event-sourced, đã revert sang CRUD 2026-08-03, xem banner đầu file)_
- [x] `Order` aggregate ~~(event-sourced)~~ (CRUD sau revert), `OrderId`, `OrderLineItem`, `OrderStatus`, `OrderErrorCode`, `OrderException`
- [x] `OrderStatus` state machine: `CREATED → CONFIRMED / CANCELLED` (chưa có `FULFILLING/DELIVERED/COMPLETED` — chưa có fulfillment-service, đúng scope)
- [x] `Order.canProcess(Class<? extends DomainEvent>)` — hiện chỉ check `status == CREATED`, chưa dùng bởi consumer nào (chưa có Kafka consumer)
- [x] Domain events: `OrderCreatedEvent`, `OrderConfirmedEvent`, `OrderCancelledEvent` — đều theo đúng 2-constructor pattern (business + `@JsonCreator` reconstitution nhận `Payload` nested record khớp đúng shape lúc serialize)

**Application**
- [x] `CreateOrder`, `ConfirmOrder`, `CancelOrder` command handler, `GetOrder` query handler — **chưa có `Idempotency-Key`/`UNIQUE` constraint cho `POST /orders`** (mục còn thiếu thật, không phải giả định — xem Infrastructure bên dưới)
- [ ] Kafka consumer `InventoryReserved` — chưa làm, chưa có Kafka wiring trong order-service (pom chưa có `spring-kafka`)
- [ ] Kafka consumer `InventoryReservationFailed` — chưa làm
- [ ] `handleLateReply` — chưa làm, phụ thuộc 2 consumer trên

**Infrastructure**
- [x] `OrderRepositoryAdapter` (implements `OrderRepository`, dùng `EventStore` từ Phase 0) — **đã verify thật**: `expectedRevision = order.getRevision() - pending.size()`, append đúng, dispatch đúng
- [ ] Bảng `orders`/`idempotency_key` cho `POST /orders` — **chưa làm**, `CreateOrder` hiện tạo order mới mỗi lần gọi, không chặn double-submit
- [x] Outbox integration (reuse `outbox-starter`) — **đã verify thật**: `OrderCreatedHandler`/`OrderConfirmedHandler`/`OrderCancelledHandler` (mỗi event 1 handler, đúng convention `application/order/event/` của catalog-service) → `outbox_events` có đủ 3 loại, đúng `routing_key`
- [ ] Idempotency guard Redis (`idempotency-support`) — chưa cần vì chưa có Kafka consumer nào

**Presentation**
- [x] `DevOrderController` (`/dev/orders`) — endpoint tạm để test, **không phải** `OrderController` thật (chưa có `POST /orders` public, chưa có `X-Customer-Id`/auth)
- [ ] `OrderController` thật — chưa làm

### Bug phát hiện & sửa trong session này
- `EventStoreEntity` cần `@EntityScan("vn.t3nexus.lib.eventsourcing")` — dễ quên nếu copy `JpaConfig` từ service khác mà không thêm package mới
- `GlobalExceptionHandler` cần `scanBasePackages` bao gồm `vn.t3nexus.lib.web` cho order-service. **Không phải bug chung** — catalog-service/inventory-service chạy bình thường (đã xác nhận), nghi ngờ ban đầu sai, chỉ riêng order-service thiếu bước này lúc bootstrap module mới.

### Verify
- `POST /orders` → `201` + Order tồn tại, replay từ event_store ra đúng state `CREATED`
- `OrderCreated` xuất hiện trong `outbox_events` → lên topic `order.order.created` thật
- inventory-service (đã build) nhận event thật → tạo `Reservation` thật trong DB inventory-service
- `InventoryReserved` quay về → Order chuyển `CONFIRMED`, replay từ event_store ra đúng state
- Compensating: giả lập `InventoryReservationFailed` (dev trigger, chưa cần limited-offer sold-out thật) → Order chuyển `CANCELLED`
- Duplicate: gửi lại cùng `OrderCreated` (cùng eventId) → không tạo Order thứ 2
- Duplicate submit (khác idempotency key path): gọi `POST /orders` 2 lần liên tiếp cùng idempotency key trước khi request đầu commit xong (race thật, không phải sequential) → chỉ 1 Order được tạo, request thứ 2 nhận lại cùng `orderId` hoặc lỗi `409`, không phải nhờ Redis mà nhờ `UNIQUE` constraint chặn ở DB
- Late reply: gửi `InventoryReserved` sau khi Order đã `CANCELLED` (dev trigger) → `handleLateReply` chạy, publish `OrderCancelled` mới, không set Order về `CONFIRMED`

### Session Log

#### 2026-07-07
- Làm được: —
- Còn lại: toàn bộ
- Blocker: —

---

## Docs cần cập nhật khi 2 phase trên xong

| Tài liệu                                    | Hành động                                                                                                                                                         |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `global/2.architecture/5. event-catalog.md` | Thêm `OrderCreated`, `OrderCancelled` nếu payload khác với bản draft hiện có                                                                                      |
| `docs/service/order-service/service.md`     | Cập nhật đúng state cuối cùng sau khi implement                                                                                                                   |
| `docs/service/inventory-service/service.md` | Nếu phát hiện gap tương tự saga-dlq bên inventory (hiện chỉ có Redis guard, chưa có `canProcess`) — ghi nhận thành pending fix, không sửa trong scope 2 phase này |
