# Flash Sale — Inventory Design

> **2026-08-03**: `ReserveInventory.handle()` (flow reserve đơn thường) đã bỏ hẳn Redis limited-offer slot check (`tryDecrementSlot`) khỏi flow của nó — xem `place-order/implementation.md` Phase 4 (lúc đó feature này còn tên `payment-checkout`, đã gộp vào `place-order` 2026-08-08). Đây là hành động đúng hướng với doc này: flash sale **không nên** nối lại inline vào `ReserveInventory` dưới bất kỳ hình thức nào (kể cả denormalize flag lên `Stock`) — phải đi qua pipeline riêng hoàn toàn như mô tả dưới đây. `SlotService`/`RedisSlotAdapter`/`LimitedOffer` domain vẫn còn nguyên trong code, chưa bị xoá, chỉ đang không được gọi tới.

## Tổng quan

Flash sale tách biệt hoàn toàn khỏi normal reservation path. Có ba tầng lọc trước khi request chạm DB:

```
200,000 users
    │
    ▼
[Gateway]  Rate limit (per-user + global)     ← lọc abuse, cap throughput
    │
    ▼
[Redis]    Bloom Filter + Slot DECR            ← fast-reject sold-out, đếm slot
    │ winners only (~vài nghìn total)
    ▼
[Kafka]    Durable queue (key = skuId)         ← buffer burst, consistency
    │
    ▼
[Consumer] TTL → Idempotency → DB reservation  ← xử lý có kiểm soát
    │
    ▼
[WebSocket] Notify user kết quả
```

---

## Tại sao phải có queue

10.000 slot hết trong ~3–5 giây đầu = ~2.000–3.000 winners/giây cần xuống DB.

DB reservation capacity (SELECT FOR UPDATE + INSERT + UPDATE):
```
10 connections × (1000ms / 30ms) ≈ 333 tx/s
Winners burst: ~3,000 req/s → ratio 9:1
```

Không có queue → DB connection pool cạn kiệt → timeout → slot đã DECR nhưng reservation không tạo được.
Queue là bắt buộc để hấp thụ burst và feed DB ở tốc độ nó chịu được.

---

## Tại sao Kafka, không phải Redis Queue

Redis LIST/STREAM có consistency gap:

| Vấn đề | Redis Queue | Kafka |
|---|---|---|
| Crash mất data | Có (in-memory) | Không — replicated, persisted |
| At-most-once (LPOP) | Có | Không — offset commit |
| DECR và enqueue không atomic | Có | Vẫn có nhưng Kafka side bền |
| Replay khi consumer lỗi | Không | Có — reset offset |

Requirement đã hint: *"Redis atomic + Kafka queue"* — Redis cho fast atomic ops, Kafka cho durable queue.

**Gap còn lại:** DECR slot và publish Kafka vẫn không atomic. Xử lý bằng compensation job định kỳ: so sánh slots DECR'd với messages trong Kafka, phát hiện leak và INCR slot về.

---

## Tầng 1 — Gateway

### Per-user rate limit
```
Key: rate:flash:{userId}:{skuId}
TTL: flash sale window
Logic: INCR → > 1 → 429
```
1 user chỉ được gửi 1 checkout request cho 1 SKU. Prevent retry spam.

### Global throughput cap
Token bucket cho flash sale checkout endpoint. Cap tại mức backend hấp thụ được.

### Servlet connection protection
Spring Boot 3.2+ với Virtual Threads (`spring.threads.virtual.enabled: true`):
- Mỗi request chạy trên virtual thread — block I/O không giữ OS thread
- OS carrier threads (10–20) multiplexed qua hàng nghìn virtual threads
- Tương đương reactive throughput, code vẫn blocking style

**Bulkhead — bắt buộc:**
```
ThreadPool A (size=50):  /flash-sale/checkout   ← isolated
ThreadPool B (size=150): /checkout, /orders, …  ← normal traffic
```
Flash sale không được phép kéo sập normal traffic.

**202 Accepted pattern:**
```
Request vào → Redis check (1ms) → Kafka publish (3ms) → 202 trả về
Thread giải phóng sau ~5ms, không phải sau 2s
```

---

## Tầng 2 — Redis (Slot Gate)

### Keys

| Key | Type | Mục đích |
|---|---|---|
| `inventory:limited:{skuId}:slots` | STRING (integer) | Slot counter, DECR atomic |
| `inventory:limited:{skuId}:queue` | — | Không dùng (replaced bởi Kafka) |
| `inventory:sold-out:bloom:{skuId}` | Bloom Filter | Fast-reject per-SKU |

> **Lưu ý:** Bloom Filter phải là per-SKU key (`bloom:{skuId}`), không phải 1 key global.
> Key global bị wipe toàn bộ mỗi khi bất kỳ SKU nào `initSlots` → mất sold-out state của các SKU khác đang chạy.

### tryDecrementSlot flow
```
Key exists? → NO  → NO_LIMIT (SKU không có flash sale)
            → YES → bloomFilter.contains(skuId)? → YES → SOLD_OUT (fast-reject)
                                                  → NO  → Lua DECR
                                                           result < 0 → SOLD_OUT
                                                           result = 0 → SLOT_GRANTED + add to bloom
                                                           result > 0 → SLOT_GRANTED
```

---

## Tầng 3 — Kafka (Durable Queue)

### Partition strategy: key = skuId

```
Producer: send(topic, key=skuId, value=requestPayload)
→ cùng skuId → cùng partition → cùng consumer
→ orders cùng SKU xử lý tuần tự, tự nhiên
→ OCC conflict gần như = 0
```

Đây là lý do OCC phù hợp trong consumer — partition by `skuId` loại bỏ nguyên nhân gây conflict, không phải vì conflict thấp chung chung.

### Topic
```
inventory.flash-sale.requests
inventory.flash-sale.dlq         ← Dead Letter Topic
```

### Request payload
```json
{
  "requestId":   "uuid",
  "orderId":     "uuid",
  "userId":      "uuid",
  "skuId":       "uuid",
  "qty":         1,
  "enqueuedAt":  "epoch-ms"
}
```

### Scaling
```
Số partition ≥ số SKU tham gia flash sale
Số consumer = số partition (1 consumer group, 1:1)
```
Scale throughput bằng tăng partition + consumer, không tăng thread trên 1 consumer.

---

## Tầng 4 — Consumer Processing

### Flow mỗi message
```
Consume message
    │
    ├── [1] TTL check
    │         enqueuedAt + queueTtlSeconds < now
    │         → skip, INCR slot, notify timeout, commit offset
    │
    ├── [2] Idempotency check
    │         requestId trong processed_event?
    │         → skip, commit offset
    │
    ├── [3] DB reservation (OCC)
    │         success → commit processed_event
    │                 → publish InventoryReserved
    │                 → commit Kafka offset
    │
    │         OCC conflict → retry tối đa K lần (default 3)
    │         exhausted   → DLQ
    │
    └── [4] DLQ handler
              INCR slot (trả slot về Redis)
              publish InventoryReservationFailed
              commit offset
```

### Offset commit timing

Commit offset **sau khi** toàn bộ xử lý hoàn tất:
```
DB commit ✓ → publish result event ✓ → commit Kafka offset ✓
```

Consumer crash trước offset commit → Kafka redeliver → idempotency check bước [2] bắt duplicate → skip an toàn. At-least-once với idempotency = effectively exactly-once.

---

## Tầng 5 — Async Notification (WebSocket)

User nhận 202 → thấy spinner → chờ WebSocket message:

```
inventory-service publish result event (Kafka)
    → notification-service consume
    → lookup userId từ requestId/orderId
    → websocket-gateway push tới /user/{userId}/notifications
    → browser nhận → navigate tới success/failure page
```

**Frontend timeout:** 30s sau khi nhận 202, nếu không có WebSocket message → hiển thị "Không xác định được kết quả, kiểm tra lịch sử đơn hàng."

**Backend TTL:** queueTtlSeconds (default 30s) — request quá hạn bị discard ở bước [1], INCR slot, WebSocket notify timeout.

| Tình huống | Kênh thông báo |
|---|---|
| Thua rate limit / slot | HTTP 429 đồng bộ |
| TTL expired trong queue | WebSocket async |
| Reservation thành công | WebSocket async |
| DLQ — exhausted retry | WebSocket async |

---

## Compensation Job

Chạy định kỳ (mỗi vài phút trong/sau flash sale):
- So sánh tổng DECR'd slots với số messages committed trong Kafka topic
- Phát hiện slot leak (DECR thành công nhưng publish Kafka thất bại)
- INCR slot về cho các leak được phát hiện

---

## Performance Analysis

Với 8.000 RPS checkout, 10.000 slots, flash sale 30 phút:

```
8,000 req/s × 1,800s = 14,400,000 requests
Kafka nhận: ~10,000 messages (slot-granted only)
Redis xử lý: ~14,390,000 fast-rejects (O(1), ~1ms/req)

Redis capacity: 100k ops/s → đủ, cần monitor
Kafka: 10,000 messages tổng → trivial
DB consumer: 10,000 reservations / ~30s burst = ~333 tx/s → trong capacity
```

Bottleneck thực tế theo thứ tự: Redis ops/s → DB connection pool → Kafka (không phải bottleneck).

---

## Components cần bổ sung (so với normal path)

| Component | Ghi chú |
|---|---|
| Per-user rate limiter tại Gateway | Redis, key = `rate:flash:{userId}:{skuId}` |
| Global throughput cap tại Gateway | Token bucket cho flash sale endpoint |
| Bulkhead thread pool | Tách flash sale khỏi normal traffic |
| Virtual Threads config | `spring.threads.virtual.enabled: true` |
| Kafka topic `flash-sale.requests` | Partition by skuId |
| Kafka DLQ `flash-sale.dlq` | Failed reservations |
| Flash sale Kafka consumer | TTL check → idempotency → OCC reservation |
| Compensation job | Detect và fix slot leak |
| Per-SKU Bloom Filter key | Fix bug global key bị wipe |
| WebSocket notification path | Trigger từ result event |
