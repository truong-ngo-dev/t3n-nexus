# Tích Hợp Saga + DLQ + Redis Idempotency

> Ghi chú từ session phân tích. Chưa implement — cần thảo luận thêm trước khi code.

---

## 1. Vấn đề gốc rễ: Saga và DLQ giải quyết hai loại lỗi khác nhau

**Saga** là chuỗi các bước nghiệp vụ trải dài qua nhiều service, kết nối qua Kafka. Ví dụ flow tạo đơn hàng:

```
order-service         inventory-service      payment-service
     │                      │                      │
     │──OrderCreated────────►│                      │
     │                      │──InventoryReserved───►│
     │                      │                      │──PaymentProcessed──►...
```

Mỗi bước có **compensating transaction** để rollback nếu bước sau thất bại — ví dụ payment thất bại thì phát `OrderCancelled` để inventory release reservation.

**DLQ (Dead Letter Queue)** là nơi Kafka đẩy message vào khi consumer thất bại nhiều lần liên tiếp (DB crash, network blip, unhandled exception...).

Vấn đề cốt lõi: **Compensating transaction KHÔNG xử lý được lỗi DLQ.**

Compensating transaction chỉ được kích hoạt bởi một *failure event* từ business logic. Khi message nằm trong DLQ, không có failure event nào được phát ra — Saga bị kẹt hoàn toàn.

| Cơ chế                   | Xử lý loại lỗi nào                                                                |
|--------------------------|-----------------------------------------------------------------------------------|
| Compensating transaction | Lỗi nghiệp vụ đã được thiết kế trước (tồn kho không đủ, thanh toán thất bại)      |
| DLQ                      | Lỗi infrastructure không mong đợi (DB crash, network blip khi consumer đang chạy) |

---

## 2. Ba vấn đề cụ thể cần giải quyết

### Vấn đề A: Duplicate processing khi DLQ replay

Kafka đảm bảo at-least-once delivery. Khi kỹ sư replay message từ DLQ, consumer xử lý lại message đó. Nếu message đã được xử lý thành công trước khi vào DLQ, replay sẽ xử lý hai lần — tồn kho bị trừ hai lần, payment bị charge hai lần.

### Vấn đề B: Saga bị kẹt vĩnh viễn nếu không có timeout

```
T=0:   order-service publish OrderCreated
T=0:   inventory-service crash → message vào DLQ
T=???  Không ai replay DLQ → order ở PENDING_INVENTORY mãi mãi
```

Không có cơ chế nào tự động detect "Saga này đã stuck quá lâu" và tự cancel đơn hàng.

### Vấn đề C: Orphaned Reservation (phức tạp nhất)

Kịch bản xảy ra khi cả timeout lẫn DLQ replay đều xảy ra:

```
T=0m:  OrderCreated → inventory-service crash → vào DLQ
T=10m: Saga timeout → order-service tự cancel → order = CANCELLED
T=10m: inventory-service nhận OrderCancelled
       → KHÔNG có reservation nào để release → no-op ✓
T=15m: Kỹ sư replay DLQ
       → inventory-service xử lý lại OrderCreated
       → đặt giữ tồn kho thành công
       → publish InventoryReserved
T=15m: order-service nhận InventoryReserved
       → order đã là CANCELLED rồi
       → nếu bỏ qua: tồn kho bị giữ mãi (orphaned reservation) ✗
```

`OrderCancelled` lần đầu (T=10m) được gửi **trước khi** reservation tồn tại → inventory không có gì để release. Sau đó reservation mới tạo ra → không ai release nữa.

---

## 3. Ba lớp bảo vệ và tại sao chúng hoạt động

### Lớp 1: Redis Idempotency — chặn duplicate

```java
if (!idempotencyGuard.tryAcquire("saga:" + envelope.eventId(), SAGA_TTL)) {
    ack.acknowledge();  // đã xử lý rồi, bỏ qua
    return;
}
try {
    process();
    ack.acknowledge();
} catch (Exception e) {
    idempotencyGuard.release("saga:" + envelope.eventId()); // xóa key
    throw e; // message sẽ vào DLQ
}
```

**Cơ chế hoạt động:**

- `tryAcquire` ghi key vào Redis với TTL. Lần đầu thành công → trả về `true` → xử lý bình thường.
- Nếu cùng `eventId` đến lần hai (Kafka retry hoặc DLQ replay) → key đã tồn tại → `tryAcquire` trả về `false` → skip, acknowledge ngay.
- **Điểm then chốt — `release` khi exception:** Nếu xử lý thất bại, `release` xóa key trước khi throw. Khi DLQ replay, `tryAcquire` sẽ thành công lại — không bị chặn nhầm.

**Nếu không có `release`:**
```
Lần 1: tryAcquire → key ghi vào Redis → exception → key vẫn còn
DLQ replay: tryAcquire → key đã tồn tại → return false → bỏ qua
→ message không bao giờ được xử lý thành công dù đã vào DLQ
```

**Yêu cầu TTL:** Phải dài hơn Kafka retention window. Kafka retention hiện tại 7 ngày → TTL tối thiểu 7 ngày. Nếu TTL hết trước retention, một message cũ có thể bị Kafka re-deliver và Redis không còn key để chặn.

**Hiện trạng:** email-worker đã dùng đúng pattern này. Saga consumers (order, inventory, payment, fulfillment) chưa có.

---

### Lớp 2: State Machine Check — chặn late reply sau TTL expired

**Tại sao Redis không đủ:**

Redis key có TTL → hết hạn → mất. Sau 7+ ngày, Redis "quên" đã xử lý event này. Nếu có message cực kỳ trễ hoặc TTL cấu hình sai, Redis sẽ cho phép xử lý lại một lần nữa.

State machine trong DB không có TTL — trạng thái `CANCELLED` tồn tại mãi mãi.

```java
Order order = orderRepository.findById(orderId);
if (!order.canProcess(eventType)) {
    handleLateReply(order, envelope); // xem mục Orphaned Reservation bên dưới
    ack.acknowledge();
    return;
}
```

`canProcess` kiểm tra: "Với trạng thái hiện tại của order, event này có hợp lệ không?"
- Order `CANCELLED` nhận `InventoryReserved` → không hợp lệ → vào `handleLateReply`
- Order `PENDING_INVENTORY` nhận `InventoryReserved` → hợp lệ → tiếp tục

**Hai lớp bổ trợ nhau, không thay thế nhau:**

| Lớp | Ưu điểm | Nhược điểm |
|-----|---------|-----------|
| Redis | ~1ms, in-memory, chặn hầu hết duplicate | Có TTL, hết hạn thì mất |
| State machine | Vĩnh viễn, không TTL | ~10ms, DB query |

Redis chặn 99% cases. State machine là safety net cho edge case TTL expired. **Thứ tự Redis trước State machine** là tối ưu vì giảm tải DB đáng kể khi có burst retry.

---

### Lớp 3: Saga Timeout — chặn stuck forever

ADR-004 chưa đề cập cơ chế này — đây là gap hiện tại.

Nếu `inventory-service.dlq` có message `order.order.created` và không có Saga timeout: order-service kẹt ở `PENDING_INVENTORY` vĩnh viễn — không phải delay mà là stuck vĩnh viễn.

Cần scheduler-service định kỳ kiểm tra và tự cancel:
```
Mỗi 2 phút:
  - order ở PENDING_INVENTORY quá 10 phút → publish OrderCancelled
  - order ở PENDING_PAYMENT quá 15 phút   → publish OrderCancelled + trigger refund
```

**Tại sao timeout giải quyết được stuck:** Thay vì chờ DLQ được replay (có thể không bao giờ xảy ra), timeout chủ động "quyết định" Saga này đã thất bại và bắt đầu compensation chain bình thường — tương đương một business failure event được phát ra từ bên ngoài.

---

## 4. Giải pháp Orphaned Reservation — self-healing chain

Khi order-service nhận `InventoryReserved` cho order đã `CANCELLED`, state machine check phát hiện late reply và gọi `handleLateReply`. Thay vì chỉ bỏ qua, `handleLateReply` **publish lại `OrderCancelled` mới** (với `eventId` mới):

```
order-service nhận InventoryReserved (late reply)
  → order = CANCELLED → canProcess = false
  → publish OrderCancelled (eventId-MỚI, cùng orderId)
  → inventory-service nhận lần này
  → reservation đang tồn tại → release ✓
```

**Tại sao phải dùng `eventId` mới:** `OrderCancelled` lần đầu (T=10m) đã được Redis idempotency ghi nhận là đã xử lý tại inventory-service. Nếu dùng cùng `eventId`, inventory-service sẽ bỏ qua (Redis chặn duplicate). Dùng `eventId` mới thì inventory-service xử lý bình thường.

**Luồng đầy đủ với self-healing:**
```
T=0m:  OrderCreated (id=A)           → inventory-service crash → DLQ
T=10m: OrderCancelled (id=B)         → inventory: no reservation → no-op
T=15m: DLQ replay OrderCreated (id=A)→ inventory: reserve → InventoryReserved (id=C)
T=15m: order-service nhận id=C       → order=CANCELLED → late reply
                                     → publish OrderCancelled (id=D, MỚI)
T=15m: inventory-service nhận id=D   → reservation tồn tại → release ✓
```

Chuỗi tự heal, không cần can thiệp thủ công.

---

## 5. Pattern tổng hợp cho Saga consumer

```java
// Bước 1: Redis check (nhanh ~1ms, chặn hầu hết duplicate)
if (!idempotencyGuard.tryAcquire("saga:" + envelope.eventId(), SAGA_TTL)) {
    ack.acknowledge();
    return;
}
try {
    // Bước 2: State machine check (DB ~10ms, safety net sau TTL expired)
    Order order = orderRepository.findById(orderId).orElseThrow(...);
    if (!order.canProcess(eventType)) {
        handleLateReply(order, envelope); // re-publish OrderCancelled nếu cần
        ack.acknowledge();
        return;
    }
    // Bước 3: Xử lý nghiệp vụ trong @Transactional
    advanceSaga(order, payload);
    ack.acknowledge();
} catch (Exception e) {
    // Bước 4: Release key để DLQ replay có thể tryAcquire lại
    idempotencyGuard.release("saga:" + envelope.eventId());
    throw e;
}
```

---

## 6. Tổng kết — Defense-in-depth

| Lớp | Giải quyết | Cơ chế |
|-----|-----------|--------|
| Redis idempotency | Duplicate khi DLQ replay | `tryAcquire` ghi key; `release` khi fail để DLQ replay được |
| State machine | Late reply sau TTL expired | Business state vĩnh viễn trong DB, không TTL |
| Saga timeout | Stuck forever khi DLQ không được replay | Scheduler tự cancel sau ngưỡng thời gian |
| `handleLateReply` | Orphaned reservation | Re-publish `OrderCancelled` mới khi nhận late reply |

Ba lớp **độc lập nhau** — nếu một lớp bị bypass (TTL hết, timeout chưa implement), lớp kia vẫn bảo vệ. Đây là thiết kế defense-in-depth cho distributed system.

---

## 7. Việc cần làm

- [ ] Thêm `tryAcquire` / `release` vào tất cả Saga consumer (order, inventory, payment, fulfillment)
- [ ] Thiết kế `canProcess(eventType)` cho Order state machine
- [ ] Thiết kế `handleLateReply` — logic re-publish `OrderCancelled` với `eventId` mới khi nhận late reply
- [ ] Thiết kế Saga timeout trong scheduler-service → cần ADR bổ sung cho ADR-004
- [ ] Xác định TTL phù hợp cho Redis idempotency key của Saga events
  (phải dài hơn Kafka retention window — hiện tại retention 7 ngày → TTL tối thiểu 7 ngày)
