# Design: Place Order

**UC gốc**: Buyer đặt hàng (`../../global/1.requirement/requirement.md`)
**Implementation plan**: [`implementation.md`](implementation.md)
**Status**: Draft (nhánh COD đã implement — xem `implementation.md`; nhánh Prepaid vẫn Draft)

> File này trước đây tách thành 2 (`place-order/design.md` — flow tổng ở mức demo/định hướng, và `payment-checkout/design.md` — bản chi tiết hoá riêng đoạn thanh toán). Đã gộp lại thành 1 (2026-08-08) vì cả 2 mô tả cùng 1 UC — giữ tách gây lệch nội dung liên tục (COD/Prepaid, `AWAITING_PAYMENT`, timeout mechanism chỉ có ở bản chi tiết, khiến bản "flow tổng" trở nên sai so với hệ thống thật).

---

## Mục tiêu

Buyer chọn phương thức thanh toán (COD hoặc ví điện tử prepaid) lúc checkout, bấm xác nhận, và nhận được kết quả đúng — kể cả khi tồn kho hết giữa chừng, ví thanh toán thất bại, hoặc kết nối tới ví bị lỗi kỹ thuật. FE luôn biết chính xác trạng thái đơn, không đoán mò dựa trên redirect URL.

---

## Actors

| Actor                                   | Role                                                                                            |
|-----------------------------------------|-------------------------------------------------------------------------------------------------|
| Buyer                                   | Chọn phương thức thanh toán + địa chỉ, bấm xác nhận, (nếu prepaid) nhập thông tin trên trang ví |
| E-wallet (MoMo/ZaloPay/VNPay — sandbox) | Bên thứ 3 xử lý thanh toán, redirect buyer về + gọi webhook xác nhận kết quả                    |

---

## Pre-conditions

- Buyer đã login
- Cart không rỗng, tất cả items thuộc cùng 1 seller
- Địa chỉ giao hàng đã có

---

## Services tham gia

| Service                     | Role                                                                                                                               |
|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `order-service`             | Saga coordinator — nhận request, giữ `Order.paymentMethod`, điều phối theo COD/PREPAID                                             |
| `inventory-service`         | Saga participant — reserve/release tồn kho (đã build, không đổi gì thêm cho phần payment)                                          |
| `payment-service`           | Saga participant — gọi API ví, nhận webhook, publish kết quả thanh toán *(nhánh Prepaid — chưa build, xem Status)*                 |
| `fulfillment-service`       | Saga participant — assign shipper sau khi `OrderConfirmed` *(chưa build)*                                                          |
| `notification-service`      | Router — nhận `OrderConfirmed`/`OrderCancelled`, ghi `notification_log` (channel=IN_APP, và EMAIL)                                 |
| `inapp-worker`              | Consume CDC topic `notification.inapp.dispatch`, `Redis PUBLISH user:{userId}:inapp` — xem `notification-service.md`               |
| `websocket-gateway`         | Connection manager thuần — Redis subscribe `user:{userId}:inapp` → push xuống kênh WS `/inapp` (ticket-auth, đã build sẵn)         |
| `api-gateway`/`web-gateway` | Route `POST /orders`, `GET /orders/{id}` qua `web-gateway`; webhook (Prepaid) cần route riêng ở `api-gateway` — xem Business Rules |

---

## Happy Path

### Nhánh COD

```
1. Buyer chọn COD + địa chỉ → bấm "Xác nhận"
2. FE: đã lấy ws-ticket qua `web-gateway` và connect `wss://websocket-gateway/inapp?ticket=...` TỪ LÚC vào trang checkout (trước khi bấm) — xem cơ chế ticket ở `notification-service.md`
3. Buyer → POST /orders { items, paymentMethod: COD, address }
4. order-service → tạo Order(CREATED, paymentMethod=COD) → publish OrderCreated
   → trả 201 { orderId, status: CREATED } NGAY — FE hiện "Đang xử lý đơn hàng..."
5. inventory-service nhận OrderCreated → reserve tồn kho → publish InventoryReserved
6. order-service nhận InventoryReserved (paymentMethod=COD → bỏ qua bước payment)
   → Order → CONFIRMED → publish OrderConfirmed
7. FE nhận WebSocket "OrderConfirmed" (lọc theo orderId) → hiện màn "Đặt hàng thành công"
```

### Nhánh Prepaid *(Draft — chưa implement)*

```
1. Buyer chọn ví (VD: MoMo) + địa chỉ → bấm "Xác nhận"
2. FE: đã connect `/inapp` (qua ws-ticket) từ trước
3. Buyer → POST /orders { items, paymentMethod: PREPAID, walletProvider: MOMO, address }
4. order-service → tạo Order(CREATED, paymentMethod=PREPAID) → publish OrderCreated
   → trả 201 { orderId, status: CREATED } — FE hiện "Đang xử lý đơn hàng..."
5. inventory-service nhận OrderCreated → reserve tồn kho → publish InventoryReserved
6. order-service nhận InventoryReserved (paymentMethod=PREPAID)
   → Order → AWAITING_PAYMENT → publish PaymentRequested { orderId, amount, walletProvider }
7. payment-service nhận PaymentRequested
   → gọi "Create Payment" API của ví (sync, server-to-server), truyền orderId làm merchant reference
     + returnUrl + ipnUrl (2 URL riêng, do mình cấu hình)
   → ví trả về payUrl
   → publish PaymentUrlIssued { orderId, payUrl }
8. FE nhận WebSocket "PaymentUrlIssued" → redirect buyer sang payUrl (window.location.href)
9. Buyer nhập thông tin, bấm "Pay" trên trang ví
10. Ví làm 2 việc song song, độc lập nhau:
    a. Redirect browser về returnUrl (kèm orderId, resultCode do ví tự đặt) — CHỈ dùng hiện "đang xác minh...",
       KHÔNG dùng để quyết định thành công
    b. Gọi webhook (IPN) → payment-service, có ký signature — ĐÂY MỚI LÀ NGUỒN SỰ THẬT
11. payment-service verify signature → publish PaymentSucceeded { orderId } | PaymentFailed { orderId, reason }
12. order-service nhận:
    PaymentSucceeded → Order → CONFIRMED → publish OrderConfirmed
    PaymentFailed     → Order → CANCELLED (reason=PAYMENT_REJECTED) → publish OrderCancelled
13. FE (đang ở trang returnUrl, tiếp tục nghe cùng kênh WebSocket) nhận OrderConfirmed/OrderCancelled
    → hiện đúng kết quả thật, không dựa vào query param của bước 10a
```

Sau `OrderConfirmed` (cả 2 nhánh) — `fulfillment-service` nhận event, assign shipper (Rule Engine), publish `ShipmentAssigned`; `notification-service` gửi thông báo buyer + seller. Phần này chưa build, giữ nguyên mô tả tổng quát từ bản thiết kế gốc, chưa chi tiết hoá — không thuộc scope COD/Prepaid hiện tại.

---

## Failure Scenarios

| Điểm thất bại                                                                                                                                                                                                                                | Compensating action                                                                                                                                                                                                                                                            | Kết quả cuối                    | FE hiện gì                                                                                              |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|---------------------------------------------------------------------------------------------------------|
| Tồn kho hết ngay sau khi tạo Order (cả COD lẫn Prepaid)                                                                                                                                                                                      | `InventoryReservationFailed` → `Order CANCELLED` (reason=`OUT_OF_STOCK`)                                                                                                                                                                                                       | `CANCELLED`                     | "Sản phẩm đã hết hàng" — không redirect, gợi ý sản phẩm khác                                            |
| `inventory-service` không phản hồi trong thời hạn (mất message, service down dài hạn) — cả COD lẫn Prepaid                                                                                                                                   | `CREATED`-timeout (Redis ZSET + Postgres backstop, 3 phút) → `Order CANCELLED` (reason=`INVENTORY_TIMEOUT`)                                                                                                                                                                    | `CANCELLED`                     | "Hết thời gian xử lý"                                                                                   |
| payment-service gọi API ví thất bại (timeout/ví sập/sai config) — **tồn kho đã reserve rồi**                                                                                                                                                 | `PaymentInitiationFailed` (event mới) → `order-service` nhận → `Order CANCELLED` (reason=`PAYMENT_INIT_FAILED`) → `OrderCancelled` → `inventory-service` release (dùng lại `OrderCancelledConsumer` đã có, không cần sửa gì bên inventory)                                     | `CANCELLED`                     | "Không kết nối được ví, thử lại"                                                                        |
| Ví từ chối giao dịch thật (buyer đã ở trang ví)                                                                                                                                                                                              | `PaymentFailed` → `Order CANCELLED` (reason=`PAYMENT_REJECTED`) → `OrderCancelled` → inventory release                                                                                                                                                                         | `CANCELLED`                     | "Thanh toán thất bại" — trang returnUrl hiện, không phải trang checkout                                 |
| Đơn ở `AWAITING_PAYMENT` quá `paymentDeadline` (15 phút) mà không có `PaymentSucceeded`/`PaymentFailed` nào tới — buyer bỏ ngang, không mở `payUrl`, hoặc ví không bao giờ gọi webhook (hành vi bình thường của phần lớn ví, không phải lỗi) | Fast-path (Redis) hoặc backstop (DB sweep) phát hiện quá hạn → CAS `Order CANCELLED` (reason=`PAYMENT_TIMEOUT`) → `OrderCancelled` → inventory release. Chi tiết cơ chế: xem [Payment Timeout Mechanism](#payment-timeout-mechanism--auto-cancel-sau-15-phút-không-thanh-toán) | `CANCELLED`                     | "Hết thời gian thanh toán, đơn đã bị huỷ" — không dựa vào return URL vì buyer có thể chưa từng redirect |
| Không có WebSocket message nào trong ~30s (mất kết nối, consumer chết...)                                                                                                                                                                    | FE tự timeout — không phải backend compensate                                                                                                                                                                                                                                  | Không xác định tại thời điểm đó | "Không xác định được kết quả, kiểm tra lịch sử đơn hàng" + poll dự phòng `GET /orders/{orderId}`        |

---

## Business Rules

- **`Order.paymentMethod`** (`COD` \| `PREPAID`) quyết định rẽ nhánh ngay khi `InventoryReserved` tới — COD bỏ qua hoàn toàn bước payment, đi thẳng `CONFIRMED`.
- **`OrderStatus` cần state `AWAITING_PAYMENT`** — chỉ nhánh PREPAID đi qua state này, giữa `CREATED` và `CONFIRMED`.
- **`OrderCancelled` cần field `reason`** (enum `OrderCancelReason`: `OUT_OF_STOCK` \| `INVENTORY_TIMEOUT` \| `PAYMENT_INIT_FAILED` \| `PAYMENT_REJECTED` \| `PAYMENT_TIMEOUT`) — FE cần biết lý do để hiện đúng message và hướng xử lý (gợi ý sản phẩm khác vs. gợi ý thử lại).
- **`Order` cần field `paymentDeadline`** (timestamp) — ghi cùng transaction lúc `Order → AWAITING_PAYMENT`, giá trị = `now() + 15 phút`. Đây là nguồn sự thật cho việc auto-cancel, độc lập với Redis/queue nào đang chạy fast-path — xem [Payment Timeout Mechanism](#payment-timeout-mechanism--auto-cancel-sau-15-phút-không-thanh-toán).
- **Webhook (IPN) là nguồn sự thật duy nhất cho kết quả thanh toán — KHÔNG BAO GIỜ tin return URL redirect.** Return URL chỉ dùng cho UX ("đang xác minh..."), quyết định `PaymentSucceeded`/`PaymentFailed` chỉ dựa vào webhook đã verify signature.
- **`orderId` trong callback (return URL lẫn webhook) chỉ là khoá tương quan** ("callback này nói về đơn nào"), không phải tín hiệu xác nhận — tránh nhầm giữa "có orderId đúng" với "thanh toán thành công".
- **`idempotencyKey` cho `PaymentRequested` = `orderId`** — không tạo thêm `paymentId` riêng; `orderId` được truyền thẳng làm merchant reference khi gọi API ví.
- **Webhook endpoint phải là route mới, public, không qua session/JWT** — `api-gateway` cần thêm nhánh (VD: `/webhooks/**` → thẳng `payment-service`), xác thực bằng chữ ký của ví thay vì cookie/Bearer token, vì ví gọi từ server của họ không có phiên đăng nhập nào của buyer. Nên cân nhắc thêm IP-allowlist theo dải IP ví công bố làm lớp phòng thủ bổ sung.
- **FE connect kênh `/inapp` (qua ws-ticket, xem `notification-service.md`) TỪ LÚC vào trang checkout**, không đợi có `orderId` mới connect — tránh race condition (backend xử lý xong saga nhanh hơn FE kịp mở kết nối, message bắn ra không ai nghe). Lọc theo `orderId` trong payload sau khi nhận, không có kênh riêng từng đơn.
- **FE bắt buộc có timeout (~30s) + poll fallback** (`GET /orders/{orderId}`) cho mọi bước chờ WebSocket — không riêng flash sale, áp dụng cho toàn bộ luồng checkout/payment này. Cùng 1 cơ chế nên tách thành 1 hook/service dùng chung (VD: `useOrderStatusListener(orderId)`).
- **Kafka ordering**: partition key = `orderId` cho mọi event của saga này.
- **At-least-once delivery**: Outbox Pattern tại mỗi event publish (ADR-005).
- **Deduplication**: `Order.canProcess()` (state-machine guard, chặn late/duplicate reply tuần tự) + `ObjectOptimisticLockingFailureException` từ `@Version` (chặn race đồng thời thật) — không dùng Redis lock cho phần này (xem lý do ở `implementation.md` Phase 3, tránh lỗ hổng "acquire rồi crash trước khi release" làm mất event im lặng).

---

## Payment Timeout Mechanism — Auto-cancel sau 15 phút không thanh toán

**Vấn đề**: Ví không đảm bảo gọi webhook khi buyer bỏ ngang (không mở `payUrl`, hoặc mở rồi không thao tác gì) — im lặng là hành vi bình thường của phần lớn ví (VNPay/MoMo/Stripe), không phải lỗi cần retry. Nếu chỉ dựa vào webhook, đơn có thể treo vĩnh viễn ở `AWAITING_PAYMENT`, tồn kho đã reserve không bao giờ được giải phóng — đây là SLA nội bộ của mình, không thể phó thác cho bên thứ ba.

**Quyết định**: Không dùng RabbitMQ (xem lý do cuối phần này), không dùng cron/schedule đơn lẻ. Dùng 2 lớp độc lập, hội tụ vào cùng một hành động idempotent — lớp 1 lo tốc độ, lớp 2 lo tính đúng đắn. Cùng pattern này đã áp dụng cho `CREATED`-state timeout (3 phút, cả COD lẫn Prepaid) — xem `implementation.md` Phase 5.

### Lớp 1 — Fast path (Redis, tối ưu độ trễ, không cần durable)

- Khi `Order → AWAITING_PAYMENT`: `ZADD delayed:order-payment-timeout <deadlineEpoch> <orderId>`
- Khi `PaymentSucceeded` tới trước hạn (trường hợp phổ biến nhất — đa số đơn trả xong rất sớm): `ZREM delayed:order-payment-timeout <orderId>` — dọn sớm, tránh worker xử lý rác về sau
- Worker poll `ZRANGEBYSCORE ... 0 now` mỗi vài giây, atomic pop bằng Lua script → với mỗi `orderId` quá hạn: **re-check trạng thái hiện tại trước khi làm gì** (bắt buộc — entry có thể "cũ" nếu đã có race với path khác), nếu vẫn `AWAITING_PAYMENT` → thực hiện cancel ở Lớp 3

### Lớp 2 — Backstop (Postgres, đảm bảo đúng nghĩa — nguồn durability thật)

- Một `@Scheduled` job sống trong `order-service`, chạy mỗi 2–5 phút — không phải cơ chế chính, chỉ là lưới an toàn, nên tần suất thấp không sao
- Query: `SELECT * FROM orders WHERE status='AWAITING_PAYMENT' AND payment_deadline < now()`, dùng partial index `(payment_deadline) WHERE status='AWAITING_PAYMENT'` — tập này luôn nhỏ và tự rỗng dần, không phụ thuộc tổng volume đơn
- Với mỗi row quá hạn: cancel ở Lớp 3
- **Đây là nơi đảm bảo thật sự đến từ**: nếu Redis mất entry (crash trước khi fsync, hoặc bất kỳ lý do gì), backstop vẫn bắt được trong tối đa `deadline + chu kỳ quét`. Redis chỉ quyết định tốc độ (đa số đơn bị huỷ trong vài giây thay vì phải chờ tới lượt quét), không quyết định tính đúng đắn — nên Redis không cần cấu hình durable đặc biệt cho việc này.

### Lớp 3 — Hành động cancel (idempotent, dùng chung cho cả 2 lớp trên)

Gọi đúng `CancelOrder.handle(new CancelOrder.Command(orderId, PAYMENT_TIMEOUT))` — **không** raw SQL CAS. `Order` đi qua domain layer + outbox; raw SQL `UPDATE` thẳng vào bảng sẽ bỏ qua toàn bộ publish event, `inventory-service`/`notification-service` không biết gì để release/thông báo. `CancelOrder.handle()` tự có `canProcess()` guard (no-op nếu đã `CANCELLED`/`CONFIRMED`) + `ObjectOptimisticLockingFailureException` bắt race giữa 2 lớp — cùng cơ chế đã dùng cho `CREATED`-timeout ở Phase 5 `implementation.md`, không viết CAS riêng cho phần Prepaid.

> Ghi chú lịch sử: bản thiết kế gốc của mục này từng đề xuất raw SQL CAS (`UPDATE orders SET status=... WHERE status='AWAITING_PAYMENT'`) — sai, đã sửa lại ở đây theo đúng phát hiện khi implement `CREATED`-timeout (xem `implementation.md` Phase 5, mục "Quan trọng — quay lại sửa Phase 3" về late-reply re-publish).

### Vì sao không dùng RabbitMQ

Đã cân nhắc TTL+DLX và plugin `delayed-message-exchange` — cả hai có giới hạn thật: TTL+DLX bị head-of-line blocking nếu dùng chung queue cho nhiều loại delay khác nhau (không phải vấn đề ở đây vì cùng 15 phút, nhưng chặn việc tái dùng sau này); plugin per-message TTL thiếu HA thật (Mnesia 1 bản sao, không replicate), delivery không đoán trước được khi tải cao. Quan trọng hơn: durability trong thiết kế này đến từ Lớp 2 (Postgres), không phải từ broker — nên "RabbitMQ có TTL đáng tin hơn Redis" không còn là lý do đủ mạnh để thêm một broker mới, khi ADR-008 đã chốt Kafka là messaging duy nhất trong hệ thống. Redis ở đây chỉ đóng vai trò tối ưu latency nội bộ một service, không phải messaging giữa services — không mở lại ADR-008.

---

## Events

| Event                        | Producer              | Consumers                                     | Payload sơ bộ                                                  |
|------------------------------|-----------------------|-----------------------------------------------|----------------------------------------------------------------|
| `OrderCreated`               | `order-service`       | `inventory-service`                           | `{ orderId, items[], paymentMethod, shippingAddress }`         |
| `InventoryReserved`          | `inventory-service`   | `order-service`                               | `{ orderId, items[] }`                                         |
| `InventoryReservationFailed` | `inventory-service`   | `order-service`                               | `{ orderId, reason }`                                          |
| `PaymentRequested`           | `order-service`       | `payment-service`                             | `{ orderId, amount, walletProvider }` *(Prepaid — chưa build)* |
| `PaymentUrlIssued`           | `payment-service`     | `order-service` (relay qua WebSocket), FE     | `{ orderId, payUrl }` *(Prepaid — chưa build)*                 |
| `PaymentInitiationFailed`    | `payment-service`     | `order-service`                               | `{ orderId, reason }` *(Prepaid — chưa build)*                 |
| `PaymentSucceeded`           | `payment-service`     | `order-service`                               | `{ orderId }` *(Prepaid — chưa build)*                         |
| `PaymentFailed`              | `payment-service`     | `order-service`                               | `{ orderId, reason }` *(Prepaid — chưa build)*                 |
| `OrderConfirmed`             | `order-service`       | `fulfillment-service`, `notification-service` | `{ orderId, sellerId, shippingAddress }`                       |
| `OrderCancelled`             | `order-service`       | `inventory-service`, `notification-service`   | `{ orderId, reason, cancelledBy }`                             |
| `ShipmentAssigned`           | `fulfillment-service` | `notification-service`                        | `{ orderId, shipperId, estimatedPickup }` *(chưa build)*       |

`OrderCreated`/`OrderConfirmed`/`OrderCancelled`/`InventoryReserved`/`InventoryReservationFailed` đã có trong `event-catalog.md`. `PaymentRequested`/`PaymentUrlIssued`/`PaymentInitiationFailed`/`PaymentSucceeded`/`PaymentFailed` cần thêm vào `event-catalog.md` khi bắt đầu implement nhánh Prepaid.

Catalog đã liệt kê `notification-service` là consumer của `OrderConfirmed`/`OrderCancelled` — handler cho 2 event này + `inapp-worker` (consume CDC topic `notification.inapp.dispatch`) là phần việc thật của "FE nhận WebSocket" — xem `implementation.md` Phase 6-7.

---

## Sequence Diagram

Nhánh COD (đã implement — `implementation.md` Phase 0-5), 3 diagram theo 3 nhánh chính. Nhánh Prepaid chưa vẽ — thêm khi bắt đầu implement.

### Happy Path (COD)

```plantuml
@startuml sequence-place-order-cod-happy
title Place Order — COD Happy Path

skinparam sequenceArrowThickness 1.5
skinparam responseMessageBelowArrow true
skinparam ParticipantPadding 20

actor       "Buyer"                as U
participant "api-gateway"          as AG
participant "web-gateway\n(BFF)"     as BFF
participant "order-service"        as OS
participant "inventory-service"    as INV
queue       "Kafka"                 as K
participant "notification-service" as NOTIF
participant "websocket-gateway"    as WSG

U -> WSG : connect wss://.../inapp?ticket=...\n(ticket lấy qua web-gateway TRƯỚC khi bấm "Xác nhận")
activate WSG

U -> AG : POST /web/api/order/v1/orders\n{items, paymentMethod: COD, address}\n(session cookie)
activate AG
AG -> BFF : forward /api/order/v1/orders
activate BFF
BFF -> OS : POST /api/v1/orders\n(Bearer token)
activate OS
OS -> OS : create Order(CREATED, paymentMethod=COD)
OS -> K : OrderCreated\n{orderId, items[], paymentMethod, shippingAddress}
OS --> BFF : 201 { orderId, status: CREATED }
deactivate OS
BFF --> AG : 201
deactivate BFF
AG --> U : 201 — FE hiện "Đang xử lý đơn hàng..."
deactivate AG

K -> INV : OrderCreated
activate INV
INV -> INV : reserve stock
INV -> K : InventoryReserved\n{orderId, items[]}
deactivate INV

K -> OS : InventoryReserved
activate OS
OS -> OS : canProcess() == true\npaymentMethod == COD → bỏ qua payment
OS -> OS : Order → CONFIRMED
OS -> K : OrderConfirmed\n{orderId, sellerId, shippingAddress}
deactivate OS

K -> NOTIF : OrderConfirmed
activate NOTIF
NOTIF -> NOTIF : ghi notification_log\n(EMAIL + IN_APP) — Phase 6, chưa build
NOTIF -> WSG : relay qua CDC → Kafka → inapp-worker\n(chi tiết: notification-service.md) — Phase 7, chưa build
deactivate NOTIF
WSG -> U : push "OrderConfirmed" qua /inapp
deactivate WSG
U -> U : lọc theo orderId → hiện "Đặt hàng thành công"

note over OS, INV
  Mọi event publish đều qua Outbox Pattern (ADR-005)
  Kafka partition key = orderId
  Idempotency: Order.canProcess() (tuần tự) +
  ObjectOptimisticLockingFailureException (đồng thời) — không Redis lock
end note

@enduml
```

### Compensating: `OUT_OF_STOCK`

```plantuml
@startuml sequence-place-order-out-of-stock
title Place Order — Compensating: OUT_OF_STOCK

skinparam sequenceArrowThickness 1.5
skinparam responseMessageBelowArrow true
skinparam ParticipantPadding 20

participant "order-service"        as OS
participant "inventory-service"    as INV
participant "notification-service" as NOTIF
participant "websocket-gateway"    as WSG
actor       "Buyer"                as U
queue       "Kafka"                 as K

K -> INV : OrderCreated
activate INV
INV -> INV : reserve stock → hết hàng
INV -> K : InventoryReservationFailed\n{orderId, reason}
deactivate INV

K -> OS : InventoryReservationFailed
activate OS
OS -> OS : CancelOrder.handle(reason=OUT_OF_STOCK)
OS -> OS : Order → CANCELLED
OS -> K : OrderCancelled\n{orderId, reason=OUT_OF_STOCK}
deactivate OS

K -> NOTIF : OrderCancelled
activate NOTIF
NOTIF -> WSG : relay (chi tiết: notification-service.md) — chưa build
deactivate NOTIF
WSG -> U : push "OrderCancelled" qua /inapp
U -> U : hiện "Sản phẩm đã hết hàng" — không redirect,\ngợi ý sản phẩm khác

note over OS
  inventory-service không tạo Reservation nào —
  không cần release, khác nhánh PAYMENT_* (Prepaid, chưa build)
end note

@enduml
```

### Compensating: `INVENTORY_TIMEOUT` (3 phút, không phản hồi)

```plantuml
@startuml sequence-place-order-inventory-timeout
title Place Order — Compensating: INVENTORY_TIMEOUT

skinparam sequenceArrowThickness 1.5
skinparam responseMessageBelowArrow true
skinparam ParticipantPadding 20

participant "order-service"     as OS
participant "Redis\n(ZSET)"     as R
database    "orders\n(PostgreSQL)" as DB
participant "inventory-service" as INV
queue       "Kafka"             as K

== Tạo Order ==
OS -> DB : INSERT orders (status=CREATED,\ninventory_reply_deadline = now()+3p)
OS -> R  : ZADD delayed:order-inventory-timeout\n<deadlineEpoch> <orderId>

== Lớp 1 — Fast path (Redis, đa số case) ==
loop mỗi vài giây
  OS -> R : ZRANGEBYSCORE ... 0 now (atomic pop, Lua)
end
R --> OS : orderId quá hạn
OS -> DB : re-check status hiện tại
alt vẫn CREATED
  OS -> OS : CancelOrder.handle(reason=INVENTORY_TIMEOUT)
  OS -> DB : UPDATE orders SET status=CANCELLED\n(qua domain layer + outbox, KHÔNG raw SQL CAS)
  OS -> K  : OrderCancelled{orderId, reason=INVENTORY_TIMEOUT}
else đã CONFIRMED/CANCELLED (race với path khác)
  OS -> OS : no-op
end

== Lớp 2 — Backstop (Postgres, lưới an toàn) ==
loop mỗi 2-5 phút
  OS -> DB : SELECT id FROM orders\nWHERE status='CREATED' AND inventory_reply_deadline < now()
end
DB --> OS : rows quá hạn (nếu Lớp 1 miss)
OS -> OS : cancel như trên (idempotent, cùng CancelOrder.handle())

== Late reply — inventory-service phản hồi muộn sau khi đã cancel ==
K -> INV : OrderCreated (message gốc vẫn còn trong Kafka)
activate INV
INV -> INV : vẫn tạo Reservation thật\n(chưa biết Order đã bị huỷ)
INV -> K : InventoryReserved (muộn)
deactivate INV

K -> OS : InventoryReserved
activate OS
OS -> OS : canProcess() == false (Order đã CANCELLED)
OS -> K : re-publish OrderCancelled (eventId mới)
deactivate OS

K -> INV : OrderCancelled (lần 2, eventId mới)
activate INV
INV -> INV : ReleaseReservation — release đúng,\nkhông còn Reservation mồ côi
deactivate INV

note over OS
  Late-reply re-publish: chưa implement đầy đủ
  (xem implementation.md Phase 5 "Quan trọng — quay lại sửa Phase 3")
end note

@enduml
```

---

## ADR liên quan

- [`adr/004-saga-choreography.md`](../../global/2.architecture/adr/004-saga-choreography.md)
- [`adr/005-outbox-pattern.md`](../../global/2.architecture/adr/005-outbox-pattern.md)
- [`adr/010-order-crud-not-event-sourcing.md`](../../global/2.architecture/adr/010-order-crud-not-event-sourcing.md) — `Order` build ban đầu bằng Event Sourcing, revert sang CRUD khi làm `CREATED`-timeout (Phase 5), xem lịch sử đầy đủ ở `implementation.md`
