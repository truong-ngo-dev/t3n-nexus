# Implementation Plan: Place Order

**Design**: [`design.md`](design.md) | **Deferred**: [`deferred.md`](deferred.md) | **Sequence**: chưa vẽ — khi implement, thêm block ```plantuml``` trực tiếp vào `design.md`, không tạo file `.puml` riêng

**Scope hiện tại**: nhánh **COD** đã xong (Happy Path + failure `OUT_OF_STOCK` + `INVENTORY_TIMEOUT`). Nhánh **Prepaid** (`payment-service`, `AWAITING_PAYMENT`, payment timeout mechanism — đã thiết kế đầy đủ ở `design.md`) tiếp tục ở **Phase 10+ ngay trong file này** khi bắt đầu — không tách file riêng (bài học từ chính file này: từng tách `place-order`/`payment-checkout` thành 2 file cho 1 UC, gây lệch nội dung liên tục, đã gộp lại 2026-08-08).

> File này trước đây là `docs/feature/payment-checkout/implementation.md` (tiêu đề cũ "Payment Checkout — Nhánh COD"). Đã đổi tên/gộp vào `place-order` — cùng 1 UC, tên cũ gây hiểu lầm phạm vi (không còn "chỉ thanh toán" từ Phase 2 trở đi: `Order` CRUD refactor, inventory idempotency hardening, timeout mechanism đều là lõi "đặt hàng"). `docs/feature/place-order/implementation.md` bản cũ (checklist template, 0 dòng tick) đã bị thay thế hoàn toàn bởi nội dung dưới đây — nội dung cũ không có giá trị lịch sử nào để giữ lại (chưa ai từng làm theo).
>
> **Lịch sử bổ sung (gộp từ `docs/service/order-service/implementation.md`, đã xoá)**: file đó ghi chi tiết session 2026-07-07/08 khi `Order` còn dùng Event Sourcing (trước khi revert ở Phase 2 dưới đây) — xem mục "Lịch sử — Event Sourcing (2026-07-07/08, trước khi revert)" cuối file này.

---

## Điểm chưa chốt

- ~~Shape của `address`~~ — đã chốt, xem ghi chú ở Phase 1.
- ~~Cơ chế lấy `customerId` từ authenticated principal~~ — đã giải quyết (2026-09-22, xem caveat Phase 1) — `order-service` giờ có `spring-boot-starter-oauth2-resource-server`, `OrderController.create()` lấy `customerId` (=`jwt.getSubject()`) và `customerEmail` (=`jwt.getClaimAsString("email")`, claim mới thêm ở `oauth2-service.JwtTokenCustomizer`) trực tiếp từ JWT, không nhận từ request body nữa. Route qua `web-gateway` (Phase 8, đăng ký route) vẫn chưa làm — đây chỉ là phần resource-server phía `order-service`.

---

## Quyết định kiến trúc — `Order` đổi từ Event Sourcing sang CRUD

**Đã ghi thành ADR chính thức**: [ADR-010](../../global/2.architecture/adr/old/010-order-crud-not-event-sourcing.md) — đây là bản tóm tắt tại chỗ, xem ADR để có context + consequences đầy đủ và danh sách doc liên quan cần đọc kèm.

**Bối cảnh**: `Order` được build ở Phase 1 dùng `event-sourcing-starter` (đã có sẵn lib trong hệ thống). Khi thiết kế `CREATED`-timeout (Phase 5) mới phát hiện: Event Sourcing thuần không query được kiểu "tìm mọi order status=X" — bắt buộc phải bolt-on thêm 1 bảng projection riêng chỉ để làm được 1 việc tầm thường.

**Đánh giá lại theo 4 tiêu chí đáng để trả chi phí ES** — không tiêu chí nào đúng cho `Order`:
1. Temporal query ("state tại thời điểm T quá khứ") — không có yêu cầu này.
2. Invariant cần replay để tính đúng — không: `canProcess()` chỉ check `status` hiện tại, logic giống hệt dù load qua replay hay qua 1 `SELECT` CRUD.
3. Optimistic concurrency — `@Version` JPA field (như `StockJpaEntity` đang dùng) cho đúng bảo vệ này, rẻ hơn nhiều so với `event_store` + `UNIQUE(aggregate_id, revision)`.
4. Audit/history — phục vụ được bằng Kafka topic retention (`order.order.*`) hoặc 1 bảng `order_status_history` đơn giản, không cần cả cơ chế ES.

Thêm 1 dữ kiện: `order-service` là **consumer duy nhất** của `event-sourcing-starter` trong toàn hệ thống (grep xác nhận) — chi phí không được amortize qua aggregate nào khác.

**Quyết định**: đổi `Order` sang CRUD thường (bảng `orders`, `status` column, `@Version`). Giữ lại `event-sourcing-starter` lib — dự định dùng cho **`Loyalty Points Ledger`** (`customer-service`, ngoài scope feature này) sau này: bản chất ledger (đã thiết kế insert-only trong `customer-service/data.md`), phân tán theo `customerId` (không dồn vào hot SKU như flash sale), đúng use case kinh điển của Event Sourcing/ledger pattern — không đụng vào bất kỳ hot path nào của hệ thống.

**Hệ quả cho các phase liên quan**: Phase 1 (đã build ES) cần rework ở Phase 2 (mới). Phase 3 (Kafka consumer, cũ là Phase 2) đổi cơ chế idempotency từ `EventStoreConflictException` sang `ObjectOptimisticLockingFailureException`. Phase 5 (`CREATED` timeout, cũ là Phase 4) không cần bảng `order_summary` riêng nữa — bảng `orders` CRUD tự nó query được.

---

## Docs cần tạo / cập nhật

| Tài liệu                                    | Hành động                          | Nội dung                                                                                                          |
|---------------------------------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `infra/README.md`                           | Cập nhật                           | Thêm connector `order-outbox-connector` vào bảng "Topics được tạo bởi các connectors"                             |
| `service/order-service/service.md`          | Tạo mới (chưa tồn tại)             | Domain model `Order` (CRUD, không phải ES), Commands, Events, Integration Contract                                |
| `service/notification-service/service.md`   | Cập nhật nếu tồn tại, tạo nếu chưa | Thêm handler `OrderConfirmedHandler`/`OrderCancelledHandler`                                                      |
| `service/inapp-worker/service.md`           | Tạo mới                            | Service hoàn toàn mới                                                                                             |
| `service/notification-service/service.md`   | Cập nhật                           | Events Consumed: đổi `OrderConfirmed`/`OrderCancelled` từ "later" → "current"                                     |
| `global/2.architecture/5. event-catalog.md` | Kiểm tra + sửa nếu cần             | `OrderCancelled.reason` — catalog có `cancelledBy`, code hiện không có; thêm `OrderInventoryTimeoutCheck` nếu cần |
| `service/scheduler-service/service.md`      | ✅ Đã cập nhật (2026-09-21)         | Đảo ngược §Không làm — `order-service` dùng scheduler-service làm heartbeat cho `CREATED`-timeout (2 taskType), xem Phase 5 |
| `feature/08-scheduler-service/design.md`    | ✅ Đã cập nhật (2026-09-21)         | Thêm `order-service` vào bảng Actors + bước 5 Happy Path |

---

## Thứ tự triển khai

_Implement theo dependency chain — producer trước consumer. Blast radius mỗi phase = 1 service (trừ Phase 0 là infra thuần)._

**Ưu tiên theo yêu cầu người dùng (2026-08-03)**: tập trung xong "core order flow" trước khi sang notification/gateway/FE. Thứ tự core: **tạo đơn (Phase 1) → check kho + reserve stock (inventory-service, đã có sẵn) → confirm (Phase 3) → cuối cùng: cancel khi đơn chưa confirm sau khoảng thời gian quy định (Phase 5 — `CREATED` timeout)**. Phase 4 (inventory-service idempotency hardening) xen giữa vì đã code sẵn, chỉ cần verify. Phase 6-9 (notification-service, inapp-worker, gateway, FE) làm sau khi core flow xong và verify được, không làm song song.

**Ghi chú kèm theo — chỗ cancel prepaid/COD, đối chiếu lại sau khi Order đổi CRUD (session 2026-08-03)**:
- Loại exception concurrent-conflict đã thống nhất `OptimisticLockingFailureException` (không phải `EventStoreConflictException`) — áp dụng chung cho mọi `OrderCancelReason`, cả COD (`OUT_OF_STOCK`, `INVENTORY_TIMEOUT`) lẫn Prepaid sau này (`PAYMENT_INIT_FAILED`/`PAYMENT_REJECTED`/`PAYMENT_TIMEOUT`), vì dùng chung `CancelOrder.handle()`.
- Lý do "không dùng raw SQL CAS cho Lớp 3 cancel action" cần đính chính lúc sửa `design.md` (Phase 10): **không phải** vì "Order event-sourced, không có bảng để UPDATE" (giờ có bảng `orders` thật rồi) — mà vì raw SQL bypass tầng aggregate + Outbox (vi phạm ADR-005), đúng bất kể Order là ES hay CRUD. Kết luận (phải gọi `CancelOrder.handle()`) không đổi, chỉ lý do nêu ra cần sửa. **Đã sửa trong `design.md`** (gộp 2026-08-08).
- Late-reply "re-publish `OrderCancelled`" (Phase 3/5 TODO) — **chưa có lời giải cụ thể**, không đổi gì so với lúc Order còn ES. `Order.cancel()` vẫn no-op nếu đã `CANCELLED` (business rule trong aggregate, không phụ thuộc persistence) — cần 1 method riêng kiểu `Order.republishCancellation()` (raise event không mutate state) hoặc dispatch trực tiếp từ consumer, chưa thiết kế chi tiết.
- Điểm mới phát sinh **chỉ vì CRUD** (không tồn tại lúc ES): `orders.status` có `CHECK` constraint DB thật — khi làm prepaid, thêm `AWAITING_PAYMENT` cần migration `ALTER` constraint này, việc này không tồn tại khi Order chưa lưu state trực tiếp.

### Phase 0 — Infra: Debezium connector cho `order-service` outbox

**Vì sao trước tiên**: `order-service` publish `OrderCreated`/`OrderConfirmed`/`OrderCancelled` vào bảng `outbox_events`, nhưng **chưa có connector nào đọc bảng này** — không giống `identity`/`oauth2`/`catalog`/`notification` đã có connector riêng trong `infra/debezium/`. Không có bước này thì không event nào của `order-service` tới được Kafka, toàn bộ saga đứng im dù code phía sau đúng 100%. Không đổi gì khi `Order` chuyển sang CRUD — outbox pattern giữ nguyên bất kể aggregate ES hay CRUD.

- [x] Tạo `infra/debezium/connector-order-outbox.json` — copy mẫu `connector-catalog-outbox.json`, đổi `database.hostname=postgres-order`, `database.dbname=order_db`, `topic.prefix=order`
- [x] Cập nhật bảng topics trong `infra/README.md`
- [ ] Đăng ký: `curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @debezium/connector-order-outbox.json` — cần Docker chạy

**Verify**: `curl http://localhost:8083/connectors/order-outbox-connector/status` → `RUNNING`. Gọi `POST /api/orders` → message xuất hiện ở topic `order.order.created` (kiểm bằng console consumer).

**2026-09-20 — Phát hiện GAP Y HỆT cho `inventory-service` khi rà soát lại luồng COD trước khi test happy
path:** `curl http://localhost:8083/connectors` chỉ liệt kê `notification-connector`,
`catalog-outbox-connector`, `scheduler-outbox-connector`, `order-outbox-connector`, `oauth2-outbox-connector`,
`identity-outbox-connector` — **không có connector nào cho `inventory_db`**, dù `inventory-service` đã ghi
đúng vào `outbox_events` mỗi lần `ReserveInventory`/`ReleaseReservation` chạy. Hệ quả: `InventoryReserved`/
`InventoryReservationFailed` không bao giờ tới được Kafka → `order-service.InventoryReservedConsumer`
không nhận được gì → `Order` đứng im ở `CREATED` vĩnh viễn — **chặn đứng happy path COD ở đúng bước giữa**,
dù code phía `ReserveInventory`/`ConfirmOrderOnCodCreated` đều đúng 100% (đã audit kỹ ở phiên trước, xem
`global/3.technical/idempotency-layering.md`). Đã tạo `infra/debezium/connector-inventory-outbox.json`
(copy mẫu `connector-order-outbox.json`, đổi `database.hostname=postgres-inventory`,
`database.dbname=inventory_db`, `topic.prefix=inventory`) và đăng ký thành công (`RUNNING`). Cập nhật
`infra/README.md`.

---

### Phase 1 — `order-service`: mở rộng domain cho `paymentMethod` + `address`, publish đúng payload

**Chặn bởi**: ~~quyết định shape `address`~~ — đã chốt: `ShippingAddress` VO (`recipientName, phone, addressLine, ward, province, note`) mirror schema `delivery_addresses` đã thiết kế sẵn ở `customer-service/data.md`, trừ `label`/`isDefault` (chỉ có ý nghĩa với address book, không phải snapshot). Không có `district` (theo mô hình hành chính 2 cấp sau sáp nhập 01/07/2025). Không sync-call sang `customer-service` để verify — FE gửi nguyên field xuống dù gõ tay hay chọn từ sổ, `order-service` chỉ validate shape cục bộ (Bean Validation ở request DTO). Inventory reservation không cần biết address (inventory chỉ theo SKU, không theo kho/vùng — xem `inventory-service/service.md`: "Không quản lý vị trí kho vật lý — Warehouse BC Phase 2").

**Lưu ý**: build lần đầu ở phase này dùng Event Sourcing — sẽ rework sang CRUD ở Phase 2 ngay sau. Danh sách dưới đây giữ nguyên để lịch sử phase rõ ràng, không xoá.

- [x] `PaymentMethod` enum (`COD`, `PREPAID`) — `domain/order/`
- [x] `OrderCancelReason` enum (`OUT_OF_STOCK` cho phase này; `PAYMENT_INIT_FAILED`/`PAYMENT_REJECTED`/`PAYMENT_TIMEOUT` để sẵn chỗ cho prepaid) thay cho `String reason` tự do trong `Order.cancel()`/`OrderCancelledEvent`
- [x] `ShippingAddress` VO — `domain/order/`
- [x] `Order`: thêm field `paymentMethod`, `shippingAddress`; cập nhật `create()` factory nhận thêm 2 tham số (validate `shippingAddress != null` → `ORDER_MISSING_SHIPPING_ADDRESS`); cập nhật `apply(OrderCreatedEvent)` để set field mới
- [x] `OrderCreatedEvent.Payload`: thêm `paymentMethod`, `shippingAddress` — khớp `OrderCreatedConsumer.Payload` bên `inventory-service` đã kỳ vọng field `paymentMethod` và khớp `event-catalog.md` dòng 69
- [x] **Bug nghiêm trọng phát hiện 2026-08-10 (đối chiếu diagram với code, đã sửa)**: `OrderCreatedEvent.getPayload()` không gửi `orderId` trong payload (chỉ có ở `envelope.aggregateId`, giống lỗi đã gặp + sửa ở `OrderCancelledEvent` Phase 2, nhưng bỏ sót event này) → `OrderCreatedConsumer.Payload.orderId()` (inventory-service) luôn nhận `null` → `Reservation.order_id` = NULL (mất tác dụng `UNIQUE(order_id)`, mọi race double-reserve không còn bị chặn) → `InventoryReservedEvent` publish lại với `orderId=null` → `order-service.InventoryReservedConsumer` tìm `Order` bằng `null` → `OrderException.notFound()` mỗi lần. **Không có order COD nào từng confirm được qua flow thật** dù Phase 1-3 đã tick DONE — `mvn compile` không bắt được vì đây là lỗi shape JSON runtime, không phải lỗi biên dịch. Đã sửa: `getPayload()` gọi `getAggregateId()`, thêm `orderId` vào `Payload` record.
- [x] `OrderCancelledEvent.Payload`: `reason` đổi type String → `OrderCancelReason`
- [x] `CreateOrder.Command`/`Result`: thêm `paymentMethod`, `address`; `Result` thêm `status`
- [x] `CancelOrder.Command`: `reason` đổi type sang `OrderCancelReason`
- [x] `GetOrder.Result`: thêm `paymentMethod`, `shippingAddress`, `cancelReason` đổi type
- [x] `OrderController` thật ở `presentation/order/` (+ `presentation/order/model/`: `CreateOrderRequest`, `ShippingAddressRequest`, `OrderItemRequest`, `OrderResponse`, `OrderDetailResponse`) — thay cho `DevOrderController`:
  - `POST /api/orders` → 201 `{ orderId, status }`
  - `GET /api/orders/{orderId}` → dùng cho FE poll fallback
- [x] Xoá `presentation/dev/DevOrderController`

**Đã verify**: `mvn compile` sạch.

**Ghi chú caveat — đã giải quyết (2026-09-22)**: `customerId` (`sub`) và `customerEmail` (claim `email`) giờ lấy từ JWT (`OrderController.create()`, `@AuthenticationPrincipal Jwt`). `sellerId` **vẫn** nhận từ request body — đúng, không phải gap: seller không phải người gọi API này (buyer đặt hàng), `sellerId` là dữ liệu của đơn hàng (ai fulfill), không phải danh tính người gọi — xem phân tích Phase 8 caveat bên dưới, đây là 2 vấn đề khác nhau.

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
- [x] `InventoryReservedConsumer` (`infrastructure/adapter/messaging/inventory/`) — chỉ decode payload + delegate, không tự `findById`/rẽ nhánh (fix 2026-08-10, xem ghi chú dưới)
- [x] `HandleInventoryReserved` (application layer, mới) — load `Order`, rẽ nhánh `paymentMethod == COD` (guard `canProcess()` + `confirm()` + save inline) hay `PREPAID` (log skip, không xử lý ở phase này)
- [x] **Refactor 2026-08-10**: logic rẽ nhánh COD/PREPAID trước đây nằm thẳng trong `InventoryReservedConsumer` (infra layer tự `orderRepository.findById()` + tự quyết định) — vi phạm convention "consumer chỉ decode + delegate, business rule thuộc application layer". Chuyển vào `HandleInventoryReserved` mới, consumer giờ chỉ giữ phần thuộc infra: decode + catch `OptimisticLockingFailureException`. Cơ chế concurrency (canProcess() + @Version) không đổi.
- [x] **Bỏ `ConfirmOrder` làm class riêng (cùng ngày)** — ban đầu tách `HandleInventoryReserved` gọi `ConfirmOrder.handle()` (load lần 2), nhưng phần logic "confirm" chỉ 3 dòng (guard + mutate + save) với đúng 1 caller — tách class cho từng đó logic là indirection không cần thiết + tốn 1 lần `findById` thừa. Gộp thẳng vào `HandleInventoryReserved`, 1 lần load duy nhất. Khi Prepaid implement (`PaymentSucceededConsumer` cũng cần confirm) mà logic trùng lặp thật, extract lại lúc đó — YAGNI cho tới khi có caller thứ 2 thật.
- [x] **`OrderConfirmedEvent`/`OrderCancelledEvent` thiếu `customerId`/`sellerId`/`shippingAddress` (phát hiện 2026-08-10, đối chiếu diagram với code)** — cả 2 event trước đây chỉ có `{orderId}`/`{orderId, reason}`. `fulfillment-service` (chưa build) cần `sellerId` + `shippingAddress` để assign shipper; `notification-service` (chưa build) cần báo cả buyer lẫn seller ("gửi thông báo buyer + seller" — `design.md`) nên cần `customerId` **và** `sellerId`, không chỉ 1 trong 2. `Order` aggregate đã có sẵn field này, chỉ cần truyền qua lúc raise event — đã sửa `Order.confirm()`/`Order.cancel()` + 2 event's `Payload` record. `design.md` Events table + diagram Happy Path cập nhật theo.
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
- [x] Xác nhận (không sửa, ghi chú lại): `findBySkuIdForUpdate` chỉ dùng ở `ReserveInventory` — pessimistic lock không bảo vệ reserve-vs-release/setQuantity; nguồn bảo vệ lost-update xuyên suốt thật là `@Version` trên `StockJpaEntity`. Việc `ReleaseReservation`/`SetStockQuantity` có nên dùng `findBySkuIdForUpdate` hay không — **để riêng, không thuộc scope**, cần phân tích flow `ReleaseReservation` độc lập nếu muốn làm.
- [ ] ~~(Sau này, khi nối lại flash-sale) Denormalize `hasActiveLimitedOffer` lên `Stock`~~ — **sai hướng, đã đính chính (xem phiên 2026-08-03 sau)**: flash sale không nên nối lại inline vào `ReserveInventory` bằng cách nào cả, kể cả denormalize flag. Theo đúng thiết kế đã có sẵn ở `docs/service/inventory-service/flashsale.md` (chưa build), flash sale phải đi qua **pipeline hoàn toàn riêng** — endpoint `/flash-sale/checkout` riêng, Redis Slot Gate (Bloom Filter + DECR) riêng, Kafka topic `inventory.flash-sale.requests` partition theo **`skuId`** (khác `orderId` như order thường) → consumer riêng dùng OCC (không `FOR UPDATE`, vì partition theo `skuId` đã tự loại bỏ conflict tại nguồn — xem lý do chi tiết trong `flashsale.md` phần "Tại sao Kafka partition by skuId"). Việc xoá Redis slot-check khỏi `ReserveInventory` ở dòng trên (Phase 4) **đúng hướng với thiết kế này** — flash sale vốn không nên chung logic với reserve thường. Xem "Components cần bổ sung" cuối `flashsale.md` cho danh sách việc cần làm khi build pipeline này — ngoài scope hoàn toàn, chỉ note lại để không quên.

**Đã verify**: `mvn compile` sạch. **Chưa verify runtime** race thật (cần môi trường Kafka+Postgres chạy đồng thời nhiều consumer).

---

### Phase 5 — `order-service`: `CREATED`-state timeout (Redis ZSET + Postgres backstop, heartbeat qua `scheduler-service`)

**Bối cảnh**: Order có thể kẹt vĩnh viễn ở `CREATED` nếu không bao giờ nhận được `InventoryReserved`/`InventoryReservationFailed` (inventory-service down dài hạn, message mất...). `design.md` có timeout cho `AWAITING_PAYMENT` (prepaid, 15 phút, ngoài scope hiện tại) — **không cover `CREATED`, áp dụng cho cả COD lẫn Prepaid**, nên thuộc scope COD.

**Lịch sử quyết định** (giữ lại để nhớ tại sao, không lặp lại sai lầm):
1. ~~Bảng `order_summary` riêng + partial index~~ — bị bác bỏ ban đầu vì lo ngại chi phí ghi index/scan, đề xuất Kafka delay-topic (partition pause) thay thế.
2. ~~Kafka delay-topic~~ — bị bác bỏ tiếp vì Kafka không có delay queue native, tự chế pause/resume dễ gây head-of-line blocking, không phải pattern proven — quay lại dùng Redis ZSET, đúng pattern `AWAITING_PAYMENT` đã có sẵn trong design.md.
3. Sau khi `Order` đổi sang CRUD ở Phase 2, bảng `orders` chính nó **đã queryable** — không cần bảng `order_summary` riêng nữa. Dùng thẳng Redis ZSET (Lớp 1, tốc độ) + query trực tiếp bảng `orders` (Lớp 2, backstop) — y hệt cơ chế `AWAITING_PAYMENT`, chỉ khác thời lượng.
4. **Đảo ngược 2026-09-21 — cơ chế "đánh thức" 2 lớp trên chuyển từ `@Scheduled` nội bộ sang heartbeat của `scheduler-service`**: trước đây `docs/service/scheduler-service/service.md` §Không làm ghi rõ "không quản lý auto-cancel đơn hàng ... không băng qua ranh giới service nên không cần scheduler-service" — quyết định lại: `order-service` **vẫn tự sở hữu 100% state** (cột `inventory_reply_deadline`, Redis ZSET riêng, logic cancel) — chỉ đổi **nguồn phát tín hiệu "tới giờ quét"** từ `@Scheduled` cục bộ sang 2 `ScheduledJob` recurring tĩnh bên `scheduler-service` (seed cùng đợt với 4 job tĩnh hiện có, không phải 1 job/order — tránh đúng lo ngại ban đầu về cardinality). Lý do đổi: bản chất "định kỳ đánh thức 1 worker" là đúng use case scheduler-service đã build sẵn (Index Poller + Redis DueItemFinder + Postgres reconciliation) — tận dụng lại thay vì mỗi service tự viết `@Scheduled` + tự vận hành lịch trình riêng. Đã cập nhật `service/scheduler-service/service.md`, `feature/08-scheduler-service/design.md`, `global/2.architecture/5. event-catalog.md` khớp quyết định này.

**Triển khai theo 2 lượt — lượt 1 dựng phần "plumbing" (schema + ghi Redis + đăng ký job, verify qua log), lượt 2 mới nối thành trigger thật (quyết định 2026-09-21, tránh viết consumer/scan logic trước khi phần nền đã chạy đúng):**

**Lượt 1 — đã xong:**
- [x] `orders` table: thêm cột `inventory_reply_deadline TIMESTAMPTZ` (migration `V2__order_inventory_timeout.sql`, không sửa `V1` vì đã apply thật — có test order từ phiên trước), set = `now() + 3 phút` lúc `Order.create()`
- [x] Partial index `idx_orders_inventory_timeout ON orders (inventory_reply_deadline) WHERE status='CREATED'`
- [x] `OrderCancelReason` thêm `INVENTORY_TIMEOUT`
- [x] Lớp 1 (Redis ZSET, **order-service tự ghi/đọc, không qua scheduler-service**) — port `OrderInventoryTimeoutIndex` (domain) + `OrderInventoryTimeoutIndexAdapter` (infra, `StringRedisTemplate`, best-effort, lỗi không chặn luồng chính): `add()` gọi trong `CreateOrder.handle()` sau khi save; `remove()` gọi trong `ConfirmOrderOnCodCreated.handle()`/`CancelOrder.handle()` sau khi save (dọn sớm). Thêm `spring-boot-starter-data-redis` vào `pom.xml` + `spring.data.redis.host/port` (dùng chung Redis container của toàn hệ thống, không cần Redisson — chỉ `ZADD`/`ZREM` thuần, chưa cần lock).
- [ ] Đăng ký 2 `ScheduledJob` recurring bên `scheduler-service` — **qua Admin REST API thật** (`POST /api/admin/scheduled-jobs` + `POST /{id}/start`), **không phải seed bootstrap** như ghi nhầm ở bản trước — grep xác nhận "4 job tĩnh" hiện mới là ý định thiết kế, chưa có code seed nào tồn tại, cơ chế đăng ký thật duy nhất hiện có là Admin API:
  - `taskType=ORDER_INVENTORY_TIMEOUT_REDIS_SCAN`, `RecurringSchedule` cron ~5s/lần
  - `taskType=ORDER_INVENTORY_TIMEOUT_DB_SCAN`, `RecurringSchedule` cron ~3 phút/lần
  - Verify lượt 1: job fire đúng lịch — xem `scheduled_job_instance` chuyển `DISPATCHED` hoặc log/console-consumer topic `scheduler.job.fired` — **chưa cần** ai xử lý event này, mục tiêu chỉ là xác nhận heartbeat tự chạy đúng trước khi viết consumer.

**Lượt 2 — logic đã viết (2026-09-21), Kafka wiring cố ý để rời cho tới khi lượt 1 verify xong:**
- [x] `ScanRedisInventoryTimeout` (application/order) — claim atomic qua `OrderInventoryTimeoutIndex.pollDue()` (Lua `ZRANGEBYSCORE`+`ZREM` gộp, cùng kỹ thuật `scheduler-service.RedisDueItemFinderAdapter`), mỗi orderId gọi `CancelOrder.handle(INVENTORY_TIMEOUT)`
- [x] `ScanDbInventoryTimeout` (application/order) — `OrderRepository.findCreatedWithExpiredDeadline()` (dùng partial index `idx_orders_inventory_timeout`), cùng gọi `CancelOrder.handle(INVENTORY_TIMEOUT)`
- [x] Lớp 3 (cancel action, dùng chung cho cả 2 nhánh): **KHÔNG raw SQL CAS** — cả 2 handler trên gọi đúng `CancelOrder.handle()`, tận dụng `canProcess()` + `ObjectOptimisticLockingFailureException` đã có ở Phase 3.
- [x] `ScheduledJobFiredConsumer` (infra/adapter/messaging/scheduler) — decode `{instanceId, taskType}`, rẽ nhánh theo `taskType` gọi 2 handler trên. **`@KafkaListener` + `app.kafka.topic.scheduler-job-fired` đang comment** (cả trong class lẫn `application.properties`) — bật lại khi 2 `ScheduledJob` đã đăng ký + verify fire đúng nhịp ở lượt 1, tránh consumer chạy trước khi có gì để consume.
- [x] **Quay lại sửa Phase 3 (2026-09-22)** — `Order.republishCancellation()` (mới, không mutate state, chỉ re-raise `OrderCancelledEvent` với `eventId` mới) + gọi trong `ConfirmOrderOnCodCreated.handle()` khi `canProcess()==false && status==CANCELLED`. **Cố tình chỉ sửa path này, không sửa `CancelOrder.handle()`** — phân tích: `InventoryReservationFailedEvent` (OUT_OF_STOCK) nghĩa là **chưa từng tạo Reservation**, nên dù `InventoryReservationFailedConsumer` gọi `CancelOrder` muộn, `canProcess()==false` no-op là đủ, không có gì để release; chỉ `InventoryReservedEvent` (Reservation **đã tạo thật**) đến muộn sau khi order đã timeout-cancel mới cần re-publish. Không guard chống republish lặp nếu `InventoryReserved` tự redeliver nhiều lần — chấp nhận được vì `ReleaseReservation` bên inventory-service đã idempotent (defense-in-depth, không đặt cược vào producer publish đúng 1 lần).
  - **Đánh giá batch cho auto-cancel job (theo yêu cầu, không đổi code)**: `ScanRedisInventoryTimeout`/`ScanDbInventoryTimeout` loop gọi `CancelOrder.handle()` **từng order, mỗi order 1 transaction riêng** — giữ nguyên, không chuyển sang bulk SQL. Lý do: (1) bulk `UPDATE ... WHERE id IN (...)` bỏ qua tầng aggregate + Outbox, vi phạm quyết định "KHÔNG raw SQL CAS" đã chốt ở trên; (2) đúng pattern đã có sẵn `scheduler-service.IndexPoller` (1 transaction/id, "1 job lỗi không kéo các id đã claim khác"); (3) perf: `BATCH_LIMIT=100` × (~vài ms/order) dưới 1s, thừa margin so với chu kỳ quét 5s/3 phút — không phải bottleneck ở quy mô này.
- [ ] (Tuỳ chọn, không block correctness) Publish callback outcome về `scheduler-service` sau mỗi lần xử lý xong 1 batch quét — theo đúng contract §Callback của `scheduler-service`; bỏ qua nếu không cần audit `ScheduledJobInstance` cho 2 job này ở giai đoạn đầu.
- [x] Bật `@KafkaListener` + property trong `ScheduledJobFiredConsumer`/`application.properties` sau khi verify lượt 1 — **2 job `order-service` đã đăng ký qua Admin API** (2026-09-22, `status=PENDING`, chờ `start()`), chưa bật listener.

### Phase R — `inventory-service`: TTL self-guard trên `Reservation` (2026-09-22, mirror Phase 5)

**Bối cảnh**: `republishCancellation` (trên) chỉ là compensate — phụ thuộc chuỗi 3 message phải cùng tới (`OrderCancelled(1)` → `InventoryReserved` quay lại → `OrderCancelled(2)`). Đối chiếu với nghiên cứu thực tế (Tiki/Arcturus 2-phase reserve/confirm-reverse, AppScale "Reservation-Then-Commit" — *"compensation is best-effort, not load-bearing"*, Stripe Authorize/Cancel Race — cùng khuyến nghị TTL tự guard là cơ chế chính) — bổ sung lớp tự guard độc lập ngay trên `Reservation`, không phụ thuộc `order-service` báo lại đúng/đủ/đúng lúc.

**Đảo ngược lần 2 của `V5__drop_reservation_expires_at.sql`**: cột `expires_at` từng bị xoá vì lúc đó chưa có state chung cuộc (`ReservationStatus` chỉ `PENDING/RELEASED/CANCELLED` — mọi reservation thành công nằm mãi `PENDING`, TTL sweep ngây thơ sẽ release nhầm đơn đã confirm). Đã bổ sung `COMMITTED` trước khi làm lại — điều kiện an toàn `V5` từng thiếu nay đã có.

- [x] `ReservationStatus` thêm `COMMITTED`
- [x] `Reservation`: thêm `expiresAt` (set `now+5 phút` — hạ từ 10 phút, 2026-09-23, xem lý do trong `service/inventory-service/service.md` §TTL self-guard), method `commit()` (`PENDING → COMMITTED`, guard chỉ từ `PENDING`)
- [x] Migration `V6__reservation_ttl_guard.sql` — thêm lại `expires_at` (nullable) + partial index `WHERE status='PENDING'`
- [x] Lớp 1 (Redis ZSET) — `ReservationTimeoutIndex` (domain) + `ReservationTimeoutIndexAdapter` (infra, key `delayed:reservation-timeout`, key theo `orderId`, atomic Lua pop giống `OrderInventoryTimeoutIndexAdapter`). `add()` trong `ReserveInventory.handle()`, `remove()` trong `ReleaseReservation.handle()` và `CommitReservation.handle()`
- [x] `OrderConfirmedConsumer` (mới) + `CommitReservation` (application, mới) — consume `order.order.confirmed` (topic đã tồn tại, chỉ thêm consumer group mới phía inventory), idempotent qua `isPending()`
- [x] Lớp 2 (DB backstop) — `ReservationRepository.findOrderIdsPendingWithExpiredDeadline()` (partial index) + `ScanRedisReservationTimeout`/`ScanDbReservationTimeout` (application, mới) — pop/query rồi gọi `ReleaseReservation.handle()` (tái dùng nguyên, không viết lại logic release)
- [x] `ScheduledJobFiredConsumer` (mới, `inventory-service`) — decode `{instanceId, taskType}`, rẽ `RESERVATION_TIMEOUT_REDIS_SCAN`/`RESERVATION_TIMEOUT_DB_SCAN`. **`@KafkaListener` đang comment** (cùng `application.properties`) — đúng convention đã áp cho order-service, chờ đăng ký 2 job qua Admin API + verify trước khi bật
- [ ] Đăng ký 2 `ScheduledJob` mới (`RESERVATION_TIMEOUT_REDIS_SCAN` cron `*/5 * * * * *`, `RESERVATION_TIMEOUT_DB_SCAN` cron `0 */3 * * * *`) qua Admin API + verify fire đúng nhịp (thao tác vận hành)
- [ ] Bật `@KafkaListener` (2 phía: `inventory-service.ScheduledJobFiredConsumer` + `scheduler-service.ScheduledJobFiredHandler` — outbox thật) sau khi verify

**Vai trò `republishCancellation` sau Phase R**: hạ xuống lớp tối ưu (dọn sớm hơn TTL khi phát hiện late-reply) — không còn là chỗ dựa duy nhất cho correctness.

**Verify**: tạo Order COD, mô phỏng `order-service` không bao giờ publish được `OrderConfirmed`/`OrderCancelled` (tắt hẳn outbox connector phía order) → sau ~5 phút `Reservation` tự `RELEASED` qua TTL, không phụ thuộc gì vào `order-service`. Xác nhận order confirm bình thường → `Reservation` chuyển `COMMITTED`, không bao giờ bị TTL sweep nhầm dù để quá 5 phút.

**Verify**: tạo Order, giả lập inventory-service không phản hồi → sau ~3 phút thấy `Order` tự `CANCELLED` (`INVENTORY_TIMEOUT`) qua Lớp 1 (Redis, nhanh, đánh thức bởi `ORDER_INVENTORY_TIMEOUT_REDIS_SCAN`) hoặc Lớp 2 (Postgres, backstop nếu Redis miss, đánh thức bởi `ORDER_INVENTORY_TIMEOUT_DB_SCAN`). Giả lập inventory-service phản hồi **muộn** sau khi đã timeout-cancel → xác nhận `OrderCancelled` được re-publish, `ReleaseReservation` chạy đúng lần 2, không có Reservation mồ côi nào còn `PENDING`. Kiểm thêm: tắt hẳn `scheduler-service` → 2 nhánh quét không chạy, `Order` kẹt ở `CREATED` (đúng kỳ vọng — heartbeat phụ thuộc `scheduler-service` chạy, khác với state chính vẫn nằm ở `order-service`).

---

### Phase 6 — `notification-service`: handler cho `OrderConfirmed` / `OrderCancelled`

**Chặn bởi**: Phase 3 (cần event thật để test, có thể mock trong lúc chờ). Phần notify **seller** trong phase này bị chặn thêm bởi `seller-service` chưa tồn tại — xem [`deferred.md`](deferred.md) #1 trước khi implement.

- [x] `OrderConfirmedPayload`/`OrderCancelledPayload` (record) — theo mẫu `LoginOtpRequestedPayload`, có thêm `customerEmail` (2026-09-22)
- [x] `OrderConfirmedHandler`/`OrderCancelledHandler implements NotificationEventHandler<...>` — `application/handler/`: **chỉ IN_APP** (không phải Email + In-App như dự tính ban đầu — xem lý do dưới)
- [x] Thêm 2 topic vào `@KafkaListener(topics = {...})` của `NotificationOutboxEventConsumer` (pool Tier1 chung — chỉ In-App, không có rủi ro SES nên dùng chung pool vẫn an toàn)
- [x] Cập nhật `service/notification-service/service.md` — Events Consumed: `OrderConfirmed`/`OrderCancelled` từ "later" → "current" (In-App); Email vẫn "later"

**Đảo ngược so với dự tính ban đầu (2026-09-22) — tách Email ra khỏi scope Phase 6 này**: `service/notification-service/service.md` §Channel Routing có sẵn "Open question — chưa quyết" (burst order-volume có thể vượt trần SES 14 msg/s + priority-inversion với Tier1 OTP nếu dùng chung pool). Làm thẳng Email + In-App chung 1 handler như dự tính ban đầu sẽ tái tạo đúng vấn đề đó. Xử lý:
- [x] `SendOrderConfirmedEmail`/`SendOrderCancelledEmail` (application/handler/, **không** implement `NotificationEventHandler` — abstraction đó chỉ hỗ trợ 1 handler/eventType, không khớp nhu cầu 2 consumer group độc lập cho cùng 1 event)
- [x] `OrderEmailOutboxEventConsumer` (mới) — consumer group riêng (`app.kafka.consumer-group.order-email`), tách khỏi Tier1. **`@KafkaListener` đang comment** — chờ quyết xong rate-limit/backpressure cho Email volume cao
- [x] `customerEmail` snapshot vào `Order` (order-service) — capture từ JWT claim `email` lúc đặt hàng (`oauth2-service.JwtTokenCustomizer` mới thêm claim này), truyền qua `OrderConfirmedEvent`/`OrderCancelledEvent` — notification-service không cần tra cứu gì thêm
- [ ] **TODO còn treo**: email cho seller (`OrderConfirmed`) — chưa có nguồn lấy email seller (không nằm trong request context lúc đặt hàng, khác customer). Ghi TODO thẳng trong `SendOrderConfirmedEmail`
- [ ] Quyết định rate-limit/backpressure cho Email volume cao (bảng Channel Routing "Open question") — vẫn treo, chưa làm
- [ ] Bật `@KafkaListener` ở `OrderEmailOutboxEventConsumer` sau khi 2 mục trên xong

**Verify**: publish `OrderConfirmed`/`OrderCancelled` thật → row mới trong `notification_log` (channel=IN_APP) + `notification_inbox` qua pool Tier1 chung. Email chưa test được (consumer chưa bật).

---

### Phase 7 — `inapp-worker`: service mới

**Chặn bởi**: Phase 6 (cần message thật trên `notification.inapp.dispatch` để test).

- [x] Scaffold service mới `services/inapp-worker/` — theo mẫu `email-worker` (`spring.main.web-application-type=none`, pure Kafka consumer)
- [x] `pom.xml`: **đổi so với dự tính ban đầu** — dùng `spring-boot-starter-data-redis` (blocking) thay vì `-reactive`, và `idempotency-support` (lib có sẵn, đúng lib email-worker đang dùng) thay vì tự viết guard riêng; bỏ `common-events` (email-worker cũng không dùng, tự parse JSON qua `ObjectMapper`/`JsonNode` thay vì shared envelope class)
- [x] `InappDispatchConsumer` — consume `notification.inapp.dispatch` (1 topic duy nhất, không tách tier1/tier2 như email vì in-app không có khái niệm TRANSACTIONAL/BULK riêng biệt ở tầng dispatch):
  - `InappDispatchHandler` — check Redis idempotency key `inapp:{notification_log_id}` (`IdempotencyGuard.tryAcquire`), skip nếu trùng
  - `InappPublisher.publish()` — `StringRedisTemplate.convertAndSend("user:{userId}:inapp", payload)`
  - Lỗi → `release()` guard rồi rethrow → Kafka retry (`FixedBackOff` 2s×3, cùng cấu hình Tier1 email) → DLQ sau max retry
- [x] Thêm vào `services/pom.xml` (parent) làm module
- [ ] `service/inapp-worker/service.md` — tạo mới (dời sang Phase 10 theo kế hoạch gốc)

**Chưa verify runtime** (cần Kafka/Redis/`notification-connector` chạy thật) — `mvn compile` sạch. Khi verify: publish message giả vào `notification.inapp.dispatch` → `redis-cli SUBSCRIBE user:{userId}:inapp` nhận đúng payload. Publish trùng `notification_log_id` → không publish lần 2 (check log "skip duplicate").

---

### Phase 8 — Gateway routes + resource-server phía `order-service`

**Chặn bởi**: Phase 1 (cần `OrderController` thật tồn tại để route tới).

- [x] **Resource-server phía `order-service` (2026-09-22)** — `spring-boot-starter-oauth2-resource-server` + `jwk-set-uri`, `OrderController.create()` lấy `customerId`/`customerEmail` từ JWT thay vì request body. Đây mới là phần "order-service tự parse token đúng" — chưa liên quan gì tới việc traffic có đi qua `web-gateway` hay chưa (2 việc độc lập, JWT verify được bất kể tới từ đâu miễn có Bearer token hợp lệ).
- [x] `web-gateway/RouteConfiguration.java`: thêm route `order-service` theo đúng pattern các route hiện có (`path("/api/order/**", "/web/api/order/**")`, `rewritePath("(/web)?/api/order/(?<segment>.*)", "/api/${segment}")`, `tokenRelay`, `saveSession`)
- [x] `web-gateway/application.properties`: thêm `webgateway.routes.order-service.uri=http://localhost:8007`
- [ ] **Không cần** thêm route cho `websocket-gateway` ở `api-gateway`/`web-gateway` — theo `4. communication.md:49`, WS data-plane connect thẳng, chỉ mint ticket qua `web-gateway` (endpoint `/webgw/auth/ws-ticket` đã có sẵn, không đổi)
- [ ] Verify path `ws-ticket` reachable qua `api-gateway` `/web/**` → `web-gateway` (đã có route `/web/**` generic, không cần thêm)
- [ ] **Caveat còn treo (không phải gap kỹ thuật)**: `sellerId` trong `CreateOrder.Command` vẫn nhận từ request body, client tự khai — chưa verify với catalog-service (ai thật sự sở hữu SKU trong đơn). Khác bản chất với `customerId` (đã giải quyết ở trên) — đây là dữ liệu của đơn hàng, không phải danh tính người gọi, nên không giải quyết bằng JWT được. Ghi nhận là gap riêng, chưa xử lý.

**Chưa verify runtime** (cần `web-gateway`/`api-gateway`/`order-service` chạy thật + session/token thật) — `mvn compile` sạch. Khi verify: `curl -X POST http://api-gateway/web/api/order/orders ...` (qua cookie session thật, `segment=orders` → rewrite thành `/api/orders`) → tới được `order-service`. `GET .../webgw/auth/ws-ticket` → trả `{ticket}`.

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

### Phase 10 — Docs sync cuối (nhánh COD)

- [ ] `service/order-service/service.md` — tạo mới, điền theo template, phản ánh đúng state CRUD (không phải ES) sau Phase 0-5
- [ ] `service/inventory-service/service.md`/`data.md` — cập nhật đúng thực tế: bỏ mô tả Redis limited-offer khỏi flow reserve chính (Phase 4), sửa lại claim "SELECT FOR UPDATE là cơ chế chính chống oversell" cho đúng vai trò `@Version`
- [ ] `service/inapp-worker/service.md` — tạo mới
- [ ] `global/2.architecture/event-catalog.md` — chốt field `cancelledBy` (thêm vào code hoặc bỏ khỏi catalog); xác nhận `paymentMethod` trong `OrderCreated` khớp code
- [x] `design.md` — đoạn "Payment Timeout Mechanism — Lớp 3" đã sửa đúng cơ chế reconciler (`CancelOrder.handle()`, không phải raw SQL CAS) trong lần gộp 2026-08-08
- [x] `design.md` §Sequence Diagram — thêm 3 block `plantuml` (COD happy path, `OUT_OF_STOCK`, `INVENTORY_TIMEOUT`) — đánh dấu rõ phần notification-service/inapp-worker (Phase 6-7) chưa build trong diagram
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

---

## Lịch sử — Event Sourcing (2026-07-07/08, trước khi revert)

_Gộp từ `docs/service/order-service/implementation.md` (đã xoá 2026-08-08) — giữ lại vì có bug thật + quyết định kỹ thuật không lặp lại ở đâu khác. Không phản ánh code hiện tại (đã revert sang CRUD ở Phase 2 trên) — chỉ giữ để biết đã thử gì và tại sao đảo ngược._

**4 lớp bảo vệ idempotency/Saga-DLQ đã bàn lúc đó** (2 lớp đầu không còn áp dụng sau revert, 2 lớp sau vẫn đúng và đã implement ở Phase 3):
1. ~~Redis (`eventId`)~~ — chặn Kafka redeliver cùng 1 message vật lý. Bị thay bằng cách tiếp cận DB-thuần ở Phase 3 (xem lý do "lỗ hổng acquire rồi crash trước khi release" tại đó).
2. ~~DB unique constraint trên `event_store` (`UNIQUE(aggregate_id, revision)`)~~ — không còn áp dụng sau khi bỏ `event_store`. `UNIQUE` idempotency key trên bảng `orders` cho `POST /orders` vẫn là ý tưởng đúng, chưa build (xem Phase 1 caveat).
3. **`canProcess` state check** — vẫn giữ, đã implement ở Phase 3.
4. **`handleLateReply`** — vẫn là hướng đúng, chưa build đầy đủ (xem Phase 5 mục "Quan trọng — quay lại sửa Phase 3").

### Session Log gốc

**2026-07-07 → 2026-07-08 (Phase 0 — ES infra)**
- Làm được: `EventSourcedAggregateRoot`, `EventStore`/`EventStoreConflictException` (common-domain), module `event-sourcing-starter` đầy đủ (entity/repository/adapter/auto-config/reference DDL, có `correlation_id`), build toàn workspace pass. Quyết định bỏ snapshot cho Order (stream ngắn, ~4-6 event/instance).
- Round-trip verify thật qua HTTP + DB thật: create → GET (revision=1, CREATED) → confirm → GET (revision=2, CONFIRMED) → confirm lần 2 bị chặn đúng 422 → order khác cancel → CANCELLED + cancelReason đúng. `correlation_id` khác nhau giữa 2 lần đổi riêng biệt.
- **Bug phát hiện khi chạy thật** (không thấy được nếu chỉ đọc code):
  1. `EventStoreEntity` không được Hibernate nhận diện — thiếu `@EntityScan` cho package `vn.t3nexus.lib.eventsourcing` (order-service copy `JpaConfig` từ service khác nhưng thiếu package mới).
  2. `DomainException` trả về `500` thay vì đúng `httpStatus` từ `ErrorCode` — `GlobalExceptionHandler` (`common-web`) nằm ngoài phạm vi component-scan mặc định của `@SpringBootApplication`, phải thêm `scanBasePackages`. Lúc đó nghi ngờ đây có thể là bug chung ở `catalog-service`/`inventory-service` (2 service chưa từng chạy thật trong session nào để verify trực tiếp) — **đã xác nhận (2026-08-08): không phải bug, `GlobalExceptionHandler` đã cấu hình đúng ở cả 2 service.** Chỉ riêng `order-service` thiếu bước này lúc bootstrap module mới (đã tự sửa cùng session 2026-07-07/08).

**2026-08-03 — REVERTED**
- Khi implement `CREATED`-state timeout (Phase 5), phát hiện Event Sourcing thuần không query được "mọi order status=X" — cần bolt-on bảng projection riêng chỉ để làm 1 việc tầm thường. Dẫn tới đánh giá lại theo 4 tiêu chí ES → quyết định revert (xem "Quyết định kiến trúc" đầu file + ADR-010).
- Refactor thật nằm trong Phase 2 ở trên — không lặp lại chi tiết ở đây.
