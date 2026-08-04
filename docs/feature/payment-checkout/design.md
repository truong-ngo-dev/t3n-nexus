# Feature: Payment Checkout

**UC gốc**: Buyer đặt hàng (`../../global/1.requirement/requirement.md`)
**Liên quan**: [`place-order/design.md`](../place-order/design.md) — bản đó là flow tổng 5 service ở mức demo/định hướng; file này là bản chi tiết hoá riêng đoạn "thanh toán" (payment-service participant + UX checkout ở FE), viết sau khi đã bàn kỹ COD vs Prepaid.
**Sequence**: chưa vẽ — khi implement, thêm block ```plantuml``` trực tiếp vào mục "Sequence Diagram" bên dưới (không tạo file `.puml` riêng).
**Status**: Draft

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

## Services tham gia

| Service                | Role                                                                                                                                                                       |
|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `order-service`        | Saga coordinator — nhận request, giữ `Order.paymentMethod`, điều phối theo COD/PREPAID                                                                                     |
| `inventory-service`    | Saga participant — reserve/release tồn kho (đã build, không đổi gì thêm cho feature này)                                                                                   |
| `payment-service`      | Saga participant — gọi API ví, nhận webhook, publish kết quả thanh toán *(chưa tồn tại — nằm trong scope feature này)*                                                     |
| `notification-service` | Router — nhận `OrderConfirmed`/`OrderCancelled`, ghi `notification_log` (channel=IN_APP, và EMAIL) *(handler cho 2 event này chưa tồn tại — nằm trong scope feature này)*  |
| `inapp-worker`         | Consume CDC topic `notification.inapp.dispatch`, `Redis PUBLISH user:{userId}:inapp` *(service chưa tồn tại — nằm trong scope feature này, xem `notification-service.md`)* |
| `websocket-gateway`    | Connection manager thuần — Redis subscribe `user:{userId}:inapp` → push xuống kênh WS `/inapp` (ticket-auth, đã build sẵn, không đổi gì thêm)                              |
| `api-gateway`          | Cần thêm route mới cho webhook — xem Business Rules                                                                                                                        |

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

### Nhánh Prepaid

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

---

## Failure Scenarios

| Điểm thất bại                                                                                                                                                                                                                                | Compensating action                                                                                                                                                                                                                                                            | Kết quả cuối                    | FE hiện gì                                                                                              |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|---------------------------------------------------------------------------------------------------------|
| Tồn kho hết ngay sau khi tạo Order (cả COD lẫn Prepaid)                                                                                                                                                                                      | `InventoryReservationFailed` → `Order CANCELLED` (reason=`OUT_OF_STOCK`)                                                                                                                                                                                                       | `CANCELLED`                     | "Sản phẩm đã hết hàng" — không redirect, gợi ý sản phẩm khác                                            |
| payment-service gọi API ví thất bại (timeout/ví sập/sai config) — **tồn kho đã reserve rồi**                                                                                                                                                 | `PaymentInitiationFailed` (event mới) → `order-service` nhận → `Order CANCELLED` (reason=`PAYMENT_INIT_FAILED`) → `OrderCancelled` → `inventory-service` release (dùng lại `OrderCancelledConsumer` đã có, không cần sửa gì bên inventory)                                     | `CANCELLED`                     | "Không kết nối được ví, thử lại"                                                                        |
| Ví từ chối giao dịch thật (buyer đã ở trang ví)                                                                                                                                                                                              | `PaymentFailed` → `Order CANCELLED` (reason=`PAYMENT_REJECTED`) → `OrderCancelled` → inventory release                                                                                                                                                                         | `CANCELLED`                     | "Thanh toán thất bại" — trang returnUrl hiện, không phải trang checkout                                 |
| Đơn ở `AWAITING_PAYMENT` quá `paymentDeadline` (15 phút) mà không có `PaymentSucceeded`/`PaymentFailed` nào tới — buyer bỏ ngang, không mở `payUrl`, hoặc ví không bao giờ gọi webhook (hành vi bình thường của phần lớn ví, không phải lỗi) | Fast-path (Redis) hoặc backstop (DB sweep) phát hiện quá hạn → CAS `Order CANCELLED` (reason=`PAYMENT_TIMEOUT`) → `OrderCancelled` → inventory release. Chi tiết cơ chế: xem [Payment Timeout Mechanism](#payment-timeout-mechanism--auto-cancel-sau-15-phút-không-thanh-toán) | `CANCELLED`                     | "Hết thời gian thanh toán, đơn đã bị huỷ" — không dựa vào return URL vì buyer có thể chưa từng redirect |
| Không có WebSocket message nào trong ~30s (mất kết nối, consumer chết...)                                                                                                                                                                    | FE tự timeout — không phải backend compensate                                                                                                                                                                                                                                  | Không xác định tại thời điểm đó | "Không xác định được kết quả, kiểm tra lịch sử đơn hàng" + poll dự phòng `GET /orders/{orderId}`        |

---

## Business Rules

- **`Order.paymentMethod`** (`COD` \| `PREPAID`) quyết định rẽ nhánh ngay khi `InventoryReserved` tới — COD bỏ qua hoàn toàn bước payment, đi thẳng `CONFIRMED`.
- **`OrderStatus` cần thêm state `AWAITING_PAYMENT`** — chỉ nhánh PREPAID đi qua state này, giữa `CREATED` và `CONFIRMED`.
- **`OrderCancelled` cần field `reason`** (`OUT_OF_STOCK` \| `PAYMENT_INIT_FAILED` \| `PAYMENT_REJECTED` \| `PAYMENT_TIMEOUT`) — FE cần biết lý do để hiện đúng message và hướng xử lý (gợi ý sản phẩm khác vs. gợi ý thử lại).
- **`Order` cần thêm field `paymentDeadline`** (timestamp) — ghi cùng transaction lúc `Order → AWAITING_PAYMENT`, giá trị = `now() + 15 phút`. Đây là nguồn sự thật cho việc auto-cancel, độc lập với Redis/queue nào đang chạy fast-path — xem [Payment Timeout Mechanism](#payment-timeout-mechanism--auto-cancel-sau-15-phút-không-thanh-toán).
- **Webhook (IPN) là nguồn sự thật duy nhất cho kết quả thanh toán — KHÔNG BAO GIỜ tin return URL redirect.** Return URL chỉ dùng cho UX ("đang xác minh..."), quyết định `PaymentSucceeded`/`PaymentFailed` chỉ dựa vào webhook đã verify signature.
- **`orderId` trong callback (return URL lẫn webhook) chỉ là khoá tương quan** ("callback này nói về đơn nào"), không phải tín hiệu xác nhận — tránh nhầm giữa "có orderId đúng" với "thanh toán thành công".
- **`idempotencyKey` cho `PaymentRequested` = `orderId`** — nhất quán với quy ước đã có ở `place-order/design.md`, không tạo thêm `paymentId` riêng; `orderId` được truyền thẳng làm merchant reference khi gọi API ví.
- **Webhook endpoint phải là route mới, public, không qua session/JWT** — `api-gateway` cần thêm nhánh (VD: `/webhooks/**` → thẳng `payment-service`), xác thực bằng chữ ký của ví thay vì cookie/Bearer token, vì ví gọi từ server của họ không có phiên đăng nhập nào của buyer. Nên cân nhắc thêm IP-allowlist theo dải IP ví công bố làm lớp phòng thủ bổ sung.
- **FE connect kênh `/inapp` (qua ws-ticket, xem `notification-service.md`) TỪ LÚC vào trang checkout**, không đợi có `orderId` mới connect — tránh race condition (backend xử lý xong saga nhanh hơn FE kịp mở kết nối, message bắn ra không ai nghe). Lọc theo `orderId` trong payload sau khi nhận, không có kênh riêng từng đơn.
- **FE bắt buộc có timeout (~30s) + poll fallback** (`GET /orders/{orderId}`) cho mọi bước chờ WebSocket — không riêng flash sale, áp dụng cho toàn bộ luồng checkout/payment này. Cùng 1 cơ chế nên tách thành 1 hook/service dùng chung (VD: `useOrderStatusListener(orderId)`).

---

## Payment Timeout Mechanism — Auto-cancel sau 15 phút không thanh toán

**Vấn đề**: Ví không đảm bảo gọi webhook khi buyer bỏ ngang (không mở `payUrl`, hoặc mở rồi không thao tác gì) — im lặng là hành vi bình thường của phần lớn ví (VNPay/MoMo/Stripe), không phải lỗi cần retry. Nếu chỉ dựa vào webhook, đơn có thể treo vĩnh viễn ở `AWAITING_PAYMENT`, tồn kho đã reserve không bao giờ được giải phóng — đây là SLA nội bộ của mình, không thể phó thác cho bên thứ ba.

**Quyết định**: Không dùng RabbitMQ (xem lý do cuối phần này), không dùng cron/schedule đơn lẻ. Dùng 2 lớp độc lập, hội tụ vào cùng một hành động idempotent — lớp 1 lo tốc độ, lớp 2 lo tính đúng đắn.

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

```sql
UPDATE orders
SET status = 'CANCELLED', cancel_reason = 'PAYMENT_TIMEOUT'
WHERE id = ? AND status = 'AWAITING_PAYMENT';
-- rows_affected == 1 → insert Outbox row (OrderCancelled), CDC publish theo pattern đã có (ADR-005)
-- rows_affected == 0 → lớp kia (hoặc PaymentSucceeded/PaymentFailed) đã xử lý trước → no-op
```

CAS bằng `WHERE status='AWAITING_PAYMENT'` đảm bảo an toàn khi cả 2 lớp cùng chạm một đơn cùng lúc — không double-cancel, không double `OrderCancelled`.

### Vì sao không dùng RabbitMQ

Đã cân nhắc TTL+DLX và plugin `delayed-message-exchange` — cả hai có giới hạn thật: TTL+DLX bị head-of-line blocking nếu dùng chung queue cho nhiều loại delay khác nhau (không phải vấn đề ở đây vì cùng 15 phút, nhưng chặn việc tái dùng sau này); plugin per-message TTL thiếu HA thật (Mnesia 1 bản sao, không replicate), delivery không đoán trước được khi tải cao. Quan trọng hơn: durability trong thiết kế này đến từ Lớp 2 (Postgres), không phải từ broker — nên "RabbitMQ có TTL đáng tin hơn Redis" không còn là lý do đủ mạnh để thêm một broker mới, khi ADR-008 đã chốt Kafka là messaging duy nhất trong hệ thống. Redis ở đây chỉ đóng vai trò tối ưu latency nội bộ một service, không phải messaging giữa services — không mở lại ADR-008.

---

## Events mới cần thêm vào `event-catalog.md` khi implement

| Event                     | Producer          | Consumers                                 | Payload sơ bộ                         |
|---------------------------|-------------------|-------------------------------------------|---------------------------------------|
| `PaymentRequested`        | `order-service`   | `payment-service`                         | `{ orderId, amount, walletProvider }` |
| `PaymentUrlIssued`        | `payment-service` | `order-service` (relay qua WebSocket), FE | `{ orderId, payUrl }`                 |
| `PaymentInitiationFailed` | `payment-service` | `order-service`                           | `{ orderId, reason }`                 |
| `PaymentSucceeded`        | `payment-service` | `order-service`                           | `{ orderId }`                         |
| `PaymentFailed`           | `payment-service` | `order-service`                           | `{ orderId, reason }`                 |

`OrderCreated`, `OrderConfirmed`, `OrderCancelled`, `InventoryReserved`, `InventoryReservationFailed` đã có sẵn trong `event-catalog.md`, kể cả field `reason` trên `OrderCancelled` — catalog đã đúng, chỉ **code** (`OrderCreatedEvent`, `OrderCancelledEvent` trong `order-service`) chưa khớp: thiếu `paymentMethod`, và `reason` hiện là String tự do thay vì enum chuẩn hoá (`OUT_OF_STOCK` \| `PAYMENT_INIT_FAILED` \| `PAYMENT_REJECTED` \| `PAYMENT_TIMEOUT`).

Catalog cũng đã liệt kê `notification-service` là consumer của `OrderConfirmed`/`OrderCancelled` — nhưng **handler cho 2 event này trong `notification-service` chưa tồn tại** (đang đánh dấu "later" trong `notification-service.md` > Events Consumed), và **`inapp-worker` — service consume CDC topic `notification.inapp.dispatch` để relay vào Redis — chưa được tạo**. Đây là phần việc thật của "FE nhận WebSocket" ở nhánh COD, không phải order-service relay trực tiếp qua websocket-gateway.

---

## Sequence Diagram

Chưa vẽ — khi implement, thêm block ```plantuml``` ngay tại đây, vẽ đủ 2 nhánh COD/Prepaid + 5 failure scenarios ở trên (bao gồm payment timeout, fast-path lẫn backstop).
