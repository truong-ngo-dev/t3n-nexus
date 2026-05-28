# notification-service

**Vai trò**: Notification hub — consume domain events, quyết định kênh và nội dung, ghi vào DB, để CDC tự động dispatch đến các workers. Không gửi trực tiếp.  
**Domain**: Supporting Domain  
**DB**: PostgreSQL — lý do chọn ở phần DB Schema  
**Libs**: `common-domain`, `web-commons`, `observability-starter`, `idempotency-support`, `rate-limiter-starter`

---

## Notification Channels

Hệ thống có 3 loại kênh thông báo. Mỗi kênh phục vụ một use case khác nhau — không thay thế mà bổ sung cho nhau.

### Email

**Đặc điểm**: Async, latency-tolerant, persistent. User không cần online, không cần cài app. Email nằm trong mailbox chờ — đây là kênh duy nhất đảm bảo eventual delivery cho mọi user segment.

**Use cases phù hợp**:
- Transactional critical: verification, order confirmation, refund, payout — thứ user cần lưu lại làm bằng chứng
- Bulk/marketing: loyalty points, stock alert — content dài, latency vài phút là acceptable

**Giới hạn thực tế**:
- SMTP/SES có rate limit cứng — SES production default 14 msg/s
- Domain mới cần setup SPF/DKIM/DMARC đúng để tránh spam filter
- Marketing email bắt buộc có unsubscribe link (CAN-SPAM/GDPR) — transactional thì không

---

### In-App Notification

**Đặc điểm**: Real-time khi user đang trong app. Đồng thời là notification center — lưu lịch sử, hỗ trợ read/unread, paginate.

**Infrastructure: Spring WebFlux + Netty + Redis pub/sub**

websocket-gateway là service riêng biệt, chạy trên Spring WebFlux (Netty embedded server). Không phải embedded WebSocket trong notification-service.

**Tại sao tách thành dedicated gateway:**

Nếu mỗi Spring Boot instance có embedded WebSocket server: khi scale lên N instances, browser A kết nối instance 1, nhưng notification cho user A đến instance 2 → không có connection → không deliver được. Giải quyết bằng sticky session hoặc fan-out — nhưng sticky session tạo uneven load và không resilient khi instance die.

Dedicated websocket-gateway giải quyết clean hơn: tất cả browser connect vào gateway, các workers chỉ cần publish vào Redis pub/sub, gateway nhận và push đúng connection. Scaling gateway không phụ thuộc scaling notification logic.

**Tại sao Spring WebFlux, không phải Spring MVC:**

websocket-gateway hoàn toàn I/O bound — nhận từ Redis pub/sub, push xuống WebSocket. Không có CPU work. Spring MVC dùng thread-per-connection (Tomcat) — 200,000 concurrent users theo NFR đòi 200,000 threads, không khả thi. Spring WebFlux dùng event loop model (Netty) — ít thread xử lý nhiều connection đồng thời bằng non-blocking I/O.

**Tại sao WebSocket + Redis, không phải MQTT:**

MQTT có ưu thế ở QoS built-in và persistent session — nhưng cả hai feature đó chỉ có giá trị cho native mobile client. Với web-only, browser không nói MQTT native. Dùng MQTT-over-WebSocket chỉ để thêm MQTT broker vào stack mà không gain được gì so với WebSocket thuần. Redis đã có sẵn trong stack, pub/sub là free ride. MQTT phù hợp hơn với chat và shipper tracking — hai bài toán cần bidirectional và QoS thực sự.

**Horizontal scale websocket-gateway:**

Spring Cloud Gateway (hoặc load balancer) route mỗi WebSocket connection đến một instance cụ thể. Instance đó own connection đó suốt lifetime của nó.

Mỗi connection là một reactive pipeline độc lập. Framework gọi `handle(session)` khi connection mở — `Mono<Void>` complete khi connection đóng:

```java
public Mono<Void> handle(WebSocketSession session) {
    String userId = extractUserId(session); // từ JWT trong handshake header

    Flux<WebSocketMessage> stream = redisTemplate
        .listenToChannel("user:" + userId + ":inapp")
        .map(msg -> session.textMessage(msg.getMessage()));

    return session.send(stream);
    // session đóng → Flux dispose → Redis unsubscribe tự động
}
```

Instance không cần explicit connection registry — lifecycle của reactive pipeline chính là lifecycle của connection. Connection mở → Redis subscribe. Tab đóng hoặc network drop → `Mono<Void>` complete → Flux dispose → Redis unsubscribe. Không cần cleanup thủ công.

Khi inapp-worker publish vào `user:A:inapp`, chỉ instance đang subscribe channel đó nhận được — tức là đúng instance đang giữ connection của user A. Các instance khác không subscribe channel này nên không nhận. Không cần sticky session tại load balancer.

**Multi-tab**: User mở 2 tab → 2 connections → 2 lần `handle()` → 2 Flux pipeline độc lập → cả 2 đều subscribe `user:A:inapp`. Khi inapp-worker publish → cả 2 tab nhận được. Đây là behavior đúng.

```
Instance 1:
  session-1 (tab 1, user A) → subscribe "user:A:inapp"
  session-2 (tab 2, user A) → subscribe "user:A:inapp"
  session-3 (user B)        → subscribe "user:B:inapp"

Instance 2:
  session-4 (user C)        → subscribe "user:C:inapp"

inapp-worker: PUBLISH "user:A:inapp" → Instance 1 nhận (2 sessions)
                                     → Instance 2 không nhận (không subscribe)
```

**Use cases phù hợp**:
- User đang active: order confirmed → toast/banner ngay lập tức (SLA < 1s theo NFR)
- Notification center: đọc lịch sử, badge count, mark as read

**Giới hạn**: Chỉ deliver khi user đang trong app. Không có giá trị khi tab đóng — email là fallback.

---

### Các kênh khác — không thuộc service này

| Kênh                       | Infrastructure | Service sở hữu               | Natural fit                                                            |
|----------------------------|----------------|------------------------------|------------------------------------------------------------------------|
| Push notification (mobile) | FCM/APNS       | notification-service (later) | Real-time khi app đóng, user có device token                           |
| Chat                       | EMQX (MQTT)    | chat-service                 | Bidirectional, QoS, persistent session — bài toán MQTT sinh ra để giải |
| Shipper location tracking  | EMQX (MQTT)    | fulfillment-service          | High-frequency IoT-like location update                                |

---

## Channel Routing — Khi nào dùng kênh nào

| Event                                      | Email   | In-App | Lý do                                                 |
|--------------------------------------------|---------|--------|-------------------------------------------------------|
| `CustomerRegistered`                       | ✅ T1    | ❌      | User chưa có session, cần persistent link             |
| `UserVerificationResent`                   | ✅ T1    | ❌      | Như trên                                              |
| `OrderConfirmed`                           | ✅ T1    | ✅      | Critical — audit trail (email) + realtime UX (in-app) |
| `OrderCancelled`                           | ✅ T1    | ✅      | Critical — user cần biết ngay                         |
| `RefundProcessed`                          | ✅ T1    | ✅      | Financial — audit trail quan trọng                    |
| `ShipmentPickedUp / InTransit / Delivered` | ✅ T1    | ✅      | Time-sensitive operational                            |
| `ShipmentFailed`                           | ✅ T1    | ✅      | Cần action từ user/seller                             |
| `ReturnRequested / Approved / Rejected`    | ✅ T1    | ✅      | Cần action, có deadline                               |
| `SellerApproved / Rejected`                | ✅ T1    | ✅      | Account lifecycle — critical                          |
| `SellerPayoutCompleted`                    | ✅ T1    | ✅      | Financial                                             |
| `LoyaltyPointsEarned`                      | ✅ T2    | ✅      | Engagement — delay vài phút ok                        |
| `LoyaltyPointsExpired`                     | ✅ T2    | ✅      | Warning — không urgent                                |
| `StockReplenished / StockDepleted`         | ✅ T2    | ❌      | Seller notification — email đủ                        |
| `OrderCompleted`                           | ✅ T2    | ✅      | Invite review — không urgent                          |
| `NewMessageReceived` (offline)             | ✅ T2    | ❌      | User offline — in-app vô nghĩa                        |

---

## Email Tier Model

### Bản chất phân tầng

Hai tier không phải phân loại kỹ thuật — mà là phân loại **business contract** với người nhận.

**Tier 1 — Transactional**: Email là hệ quả trực tiếp của một hành động user chủ động thực hiện. User đăng ký → đang chờ verification email. User checkout → muốn biết đơn hàng đã nhận. Đặc điểm cốt lõi: **user đang expect email này**. Không nhận được trong vài chục giây → họ nghĩ hệ thống lỗi. Volume bị chặn tự nhiên bởi action rate — không có fan-out.

**Tier 2 — Bulk/Marketing**: Email do hệ thống chủ động gửi, user không trigger. Nhận "bạn vừa tích 50 điểm" sau 2 giây hay 30 phút — trải nghiệm không khác nhau. Đặc điểm cốt lõi: **user không đang chờ email này**. Delay không phá vỡ flow. Volume có thể fan-out không kiểm soát — flash sale trigger 200,000 emails đồng thời.

---

### Tại sao phải tách — vấn đề Priority Inversion

Nếu Tier 1 và Tier 2 chung một consumer và một sender:

Flash sale bắt đầu → 200,000 loyalty emails đổ vào queue → sender drain với tốc độ 14 msg/s → lúc này user đăng ký tài khoản → verification email xếp hàng sau 200,000 email kia → user chờ **14,000 giây** để nhận email verify.

Đây là **priority inversion**: request có business contract thấp nhất đang chặn request có business contract cao nhất. Không phải scale problem — scale thêm instance không giải quyết được vì bottleneck ở SES rate limit, không phải CPU.

---

### Tại sao tách consumer group giải quyết được

Kafka consumer pull at own rate — mỗi consumer group giữ offset riêng, tiến độ hoàn toàn độc lập.

Tier 2 group có backlog 2 triệu message, lag 4 tiếng → Tier 1 group không biết điều đó tồn tại. Nó chỉ thấy topic của mình, offset của mình.

Để isolation hoàn toàn: CDC route notification_log rows vào **hai Kafka topic dispatch riêng** dựa trên column `tier`. email-worker có hai consumer group, mỗi group consume một topic. Không có shared state, không cần filter trong consumer.

---

### Tại sao concurrency khác nhau

**Tier 1 — concurrency 3–5 threads:**

Volume bị bounded bởi user action rate. Tăng concurrency không giải quyết gì — chỉ tạo thêm SMTP connections đồng thời → risk hit rate limit → retry storm. Tier 1 ưu tiên **reliability over throughput**.

**Tier 2 — concurrency 1–2 threads + rate-limited sender:**

Volume unbounded. Tăng concurrency chỉ làm consumer kéo message ra nhanh hơn, nhưng sender vẫn bị chặn bởi SES 14 msg/s. Rate limiter là knob đúng chỗ — đặt ceiling tại điểm bottleneck thực sự.

Khi scale Tier 2 lên nhiều instance: `rate-limiter-starter` (Redis sliding window) đảm bảo các instances share cùng quota 14 msg/s — không instance nào vượt giới hạn riêng lẻ.

---

### Tại sao failure handling phải khác nhau

|                    | Tier 1                                    | Tier 2                               |
|--------------------|-------------------------------------------|--------------------------------------|
| Retry policy       | Aggressive — 3 lần, backoff 1s / 5s / 30s | Gentle — backoff 1m / 5m / 30m       |
| Sau max retry      | DLQ → alert ngay                          | DLQ → alert khi lớn bất thường       |
| Consumer lag alert | > 30s → page                              | > 4h ngoài flash sale window → alert |

Nếu chung consumer group: ngưỡng alert đủ nhạy cho Tier 1 sẽ false alarm liên tục từ Tier 2; ngưỡng chấp nhận lag của Tier 2 sẽ bỏ sót incident thật của Tier 1.

---

### Tóm tắt specs

|                      | Tier 1 — Transactional             | Tier 2 — Bulk/Marketing                     |
|----------------------|------------------------------------|---------------------------------------------|
| Dispatch topic       | `notification.email.transactional` | `notification.email.bulk`                   |
| Consumer concurrency | 3–5                                | 1–2                                         |
| Sending strategy     | Gửi ngay, fail fast                | Rate-limited, Redis sliding window 14 msg/s |
| Max acceptable lag   | Seconds                            | Hours                                       |
| Retry                | Aggressive, backoff ngắn           | Gentle, backoff dài                         |

---

## Architecture

### Luồng tổng thể

```
Domain events (Kafka)
        │
        ▼
notification-service (Router)
        │
        │  BEGIN TRANSACTION
        │    INSERT notification_log
        │    INSERT notification_inbox  ← chỉ khi channel=IN_APP
        │  COMMIT
        │
        ▼
   notification_log (PostgreSQL)
        │
        ▼  Debezium CDC đọc WAL
   notification.log.changes (Kafka)
        │
        ▼  SMT route theo column channel + tier
        │
        ├──► notification.email.transactional ──► email-worker (Tier 1)
        │                                          Check Redis idempotency key
        │                                          Send email → Set Redis key → Commit offset
        │                                          Fail → retry → DLQ
        │
        ├──► notification.email.bulk          ──► email-worker (Tier 2)
        │                                          Check Redis idempotency key
        │                                          Rate-limited send → Set Redis key → Commit offset
        │                                          Fail → retry → DLQ
        │
        └──► notification.inapp.dispatch      ──► inapp-worker
                                                   Check Redis idempotency key
                                                   Redis PUBLISH user:{userId}:inapp
                                                   Set Redis key → Commit offset
                                                   Fail → retry → DLQ
                                                        │
                                                        ▼
                                               websocket-gateway
                                               (Spring WebFlux + Netty)
                                               Redis subscribe → WebSocket → browser
```

---

### notification-service — vai trò Router

Chỉ làm một việc: nhận domain event, map thành một hoặc nhiều notification, ghi vào DB. Không gửi email, không gọi SMTP, không publish Kafka dispatch trực tiếp — CDC tự làm điều đó.

**Tại sao ghi DB thay vì publish Kafka trực tiếp:**

Ghi vào `notification_log` trước khi CDC publish là Outbox Pattern áp dụng cho notification. Lợi ích:
- Delivery guarantee: nếu INSERT thành công thì CDC sẽ fire — không mất event
- `notification_log` là immutable audit record của mọi notification đã được quyết định gửi
- `notification_inbox` được tạo atomically cùng transaction — không cần worker tạo sau

**Tại sao không gây priority inversion ở tầng DB:**

SES tạo priority inversion vì là sequential processing với hard rate limit — message N phải chờ N-1 xong. DB write là concurrent processing — Tier 1 và Tier 2 INSERT song song, không xếp hàng sau nhau. CDC pick up per-row từ WAL ngay khi commit — Tier 1 row vào Kafka T1 topic gần như ngay lập tức, không bị backlog của Tier 2 chặn.

---

### inapp-worker — vai trò Delivery agent

```
Consume notification.inapp.dispatch (CDC event từ notification_log)
        │
        ├── Check Redis idempotency key: "inapp:{notification_log_id}"
        │   Nếu key tồn tại → skip (Kafka at-least-once, đã xử lý rồi)
        │
        ├── Redis PUBLISH user:{userId}:inapp {payload từ CDC event}
        │
        ├── SET Redis key "inapp:{notification_log_id}" TTL 72h
        │
        └── Commit Kafka offset
            Fail sau max retry → message vào DLQ → alert
```

`notification_inbox` đã được notification-service tạo atomically — inapp-worker không cần INSERT inbox, không cần write DB.

---

### websocket-gateway — vai trò Connection manager

```java
// Spring WebFlux WebSocketHandler
public Mono<Void> handle(WebSocketSession session) {
    String userId = extractUserId(session);

    Flux<WebSocketMessage> stream = redisTemplate
        .listenToChannel("user:" + userId + ":inapp")
        .map(msg -> session.textMessage(msg.getMessage()));

    return session.send(stream);
}
```

Redis channel → `Flux` → WebSocket send. Reactive pipeline hoàn toàn non-blocking — không có thread blocking ở bất kỳ bước nào.

---

## DB Schema

### notification_log — CDC source, audit trail

Immutable hoàn toàn sau INSERT — không có UPDATE nào. Workers không write vào bảng này.

| Column              | Type      | Note                                                              |
|---------------------|-----------|-------------------------------------------------------------------|
| `id`                | ULID      | PK                                                                |
| `event_id`          | VARCHAR   | Source event ID                                                   |
| `notification_type` | VARCHAR   | Ví dụ `VERIFICATION_EMAIL`, `ORDER_CONFIRMED`                     |
| `channel`           | VARCHAR   | `EMAIL` / `IN_APP`                                                |
| `tier`              | VARCHAR   | `TRANSACTIONAL` / `BULK`                                          |
| `user_id`           | VARCHAR   | Recipient userId                                                  |
| `recipient`         | VARCHAR   | Email address nếu channel=EMAIL                                   |
| `payload`           | JSONB     | Full content workers cần — title, body, action_url, template vars |
| `created_at`        | TIMESTAMP |                                                                   |

**UNIQUE (event_id, channel)** — idempotency tại DB level. INSERT trùng bị reject, không tạo 2 log entries cho cùng event + channel.

`payload` JSONB chứa toàn bộ thông tin workers cần xử lý. CDC event mang payload này → workers không cần query DB thêm.

```json
{
  "userId": "01HXYZ...",
  "title": "Đơn hàng đã xác nhận",
  "body": "Đơn #12345 đang được xử lý",
  "actionUrl": "/orders/12345",
  "templateVars": { "verificationToken": "abc...", "fullName": "Nguyen Van A" }
}
```

---

### notification_inbox — User-facing, mutable

Chỉ tồn tại cho `channel=IN_APP`. Được tạo bởi notification-service cùng transaction với notification_log.

| Column                | Type      | Note                      |
|-----------------------|-----------|---------------------------|
| `id`                  | ULID      | PK                        |
| `notification_log_id` | ULID      | FK → notification_log.id  |
| `user_id`             | VARCHAR   |                           |
| `title`               | VARCHAR   |                           |
| `body`                | VARCHAR   |                           |
| `action_url`          | VARCHAR   | Nullable — link khi click |
| `is_read`             | BOOLEAN   | Default false             |
| `created_at`          | TIMESTAMP |                           |

**INDEX (user_id, is_read)** — cho badge count: `SELECT COUNT(*) WHERE user_id=X AND is_read=false`  
**INDEX (user_id, created_at DESC)** — cho inbox list với pagination

**Tại sao tách khỏi notification_log:**

Hai bảng phục vụ hai consumer khác nhau với access pattern khác nhau:
- `notification_log`: system consumer — audit, CDC source. Immutable, query theo event_id hoặc date range.
- `notification_inbox`: user consumer — UI display, badge count, mark as read. Mutable, query theo user_id + is_read, paginate theo thời gian.

Nếu gộp: phải thêm `is_read`, `title`, `body`, `action_url` vào audit log — mixed concern. Schema audit log không nên biết về display content.

---

### Tại sao chọn PostgreSQL

Hai lý do kỹ thuật cụ thể, không phải convention:

**JSONB cho notification_log.payload**: Workers đọc `payload` từ CDC event để lấy title, body, template vars — không query DB thêm. PostgreSQL JSONB lưu binary, có thể index và query trực tiếp trên fields bên trong (`payload->>'title'`, GIN index). MySQL JSON type lưu text, kém hơn đáng kể cho use case này.

**Logical replication cho Debezium CDC**: PostgreSQL dùng logical replication slot — Debezium đọc WAL stream trực tiếp, low-latency, không cần polling. MySQL dùng binlog cũng được Debezium hỗ trợ, nhưng logical replication của PostgreSQL clean hơn cho CDC setup.

---

## Events Consumed

| Event                          | Topic                               | Tier | Channel        | Phase |
|--------------------------------|-------------------------------------|------|----------------|-------|
| `CustomerRegistered`           | `identity.customer.registered`      | T1   | Email          | 4     |
| `UserVerificationResent`       | `identity.user.verification-resent` | T1   | Email          | 4     |
| `OrderConfirmed`               | `order.order.confirmed`             | T1   | Email + In-App | later |
| `OrderCancelled`               | `order.order.cancelled`             | T1   | Email + In-App | later |
| `RefundProcessed`              | `payment.refund.processed`          | T1   | Email + In-App | later |
| `SellerPayoutCompleted`        | `payment.payout.completed`          | T1   | Email + In-App | later |
| `ShipmentAssigned`             | `fulfillment.shipment.assigned`     | T1   | Email + In-App | later |
| `ShipmentPickedUp`             | `fulfillment.shipment.picked-up`    | T1   | Email + In-App | later |
| `ShipmentInTransit`            | `fulfillment.shipment.in-transit`   | T1   | Email + In-App | later |
| `ShipmentDelivered`            | `fulfillment.shipment.delivered`    | T1   | Email + In-App | later |
| `ShipmentFailed`               | `fulfillment.shipment.failed`       | T1   | Email + In-App | later |
| `ReturnRequested`              | `return.return.requested`           | T1   | Email + In-App | later |
| `ReturnApproved`               | `return.return.approved`            | T1   | Email + In-App | later |
| `ReturnRejected`               | `return.return.rejected`            | T1   | Email + In-App | later |
| `ReturnCompleted`              | `return.return.completed`           | T1   | Email + In-App | later |
| `SellerApproved`               | `seller.seller.approved`            | T1   | Email + In-App | later |
| `SellerRejected`               | `seller.seller.rejected`            | T1   | Email + In-App | later |
| `ShipperApproved`              | `shipper.shipper.approved`          | T1   | Email + In-App | later |
| `OrderCompleted`               | `order.order.completed`             | T2   | Email + In-App | later |
| `LoyaltyPointsEarned`          | `customer.loyalty.earned`           | T2   | Email + In-App | later |
| `LoyaltyPointsExpired`         | `customer.loyalty.expired`          | T2   | Email + In-App | later |
| `StockReplenished`             | `inventory.stock.replenished`       | T2   | Email          | later |
| `StockDepleted`                | `inventory.stock.depleted`          | T2   | Email          | later |
| `NewMessageReceived` (offline) | `chat.message.received`             | T2   | Email          | later |

---

## Business Rules

### Idempotency

Hai lớp bảo vệ ở hai tầng khác nhau:

**Lớp 1 — DB UNIQUE constraint** (tại notification-service):  
`UNIQUE (event_id, channel)` trên `notification_log` — INSERT trùng bị DB reject. Không tạo duplicate log entry, không sinh duplicate CDC event.  
`idempotency-support` lib (Redis) check trước INSERT để tránh DB roundtrip không cần thiết khi event đến nhiều lần.

**Lớp 2 — Redis key tại worker** (tại email-worker / inapp-worker):  
Kafka at-least-once có thể deliver cùng CDC event nhiều lần. Worker check Redis key `"{channel}:{notification_log_id}"` trước khi deliver — nếu key tồn tại → skip. Set key sau khi deliver thành công, TTL 72h.  
Worker không write vào `notification_log` — hoàn toàn decoupled khỏi DB của notification-service.

### Failure Handling

Delivery fail sau max retry → message vào **Dead Letter Queue** → alert. Không track FAILED status trong DB — DLQ là signal duy nhất cho failure, đơn giản hơn và không cần DB write trong critical path.

### Delivery

- Dev: Spring Mail → **Mailhog** (SMTP mock, config trong `docker-compose.yml`)
- Production: AWS SES hoặc SMTP relay — configurable qua `application.yml`, không hardcode
- SMTP lỗi tạm thời → exception propagate lên Kafka consumer → Kafka retry theo backoff. Không swallow exception.

---

## Email Templates

Templates Thymeleaf tại `src/main/resources/templates/email/` trong email-worker. Mỗi loại email = 1 file `.html`.

| Template file       | `notificationType`   | Subject                  | Dùng cho event                                 |
|---------------------|----------------------|--------------------------|------------------------------------------------|
| `verification.html` | `VERIFICATION_EMAIL` | "Xác nhận email của bạn" | `CustomerRegistered`, `UserVerificationResent` |

**Template `verification.html` — biến inject từ `payload.templateVars`:**

| Biến               | Nguồn                                                     |
|--------------------|-----------------------------------------------------------|
| `fullName`         | `templateVars.fullName` (nullable — fallback: "bạn")      |
| `verificationLink` | `{baseUrl}/verify?token={templateVars.verificationToken}` |
| `expiryHours`      | hardcode 24                                               |

`baseUrl` đọc từ `app.base-url` trong `application.yml`.

---

## Services trong hệ thống notification

| Service                | Vai trò                                         | Tech                                |
|------------------------|-------------------------------------------------|-------------------------------------|
| `notification-service` | Router — consume domain events, ghi DB          | Spring Boot, Kafka consumer         |
| `email-worker`         | Deliver email Tier 1 + Tier 2                   | Spring Boot, Spring Mail, Thymeleaf |
| `inapp-worker`         | Deliver in-app qua Redis pub/sub                | Spring Boot, Redis                  |
| `websocket-gateway`    | Maintain WebSocket connections, push to browser | Spring WebFlux, Netty, Redis        |

---

## Dependencies

### notification-service

| Dependency              | Lý do                                   |
|-------------------------|-----------------------------------------|
| `common-domain`         | `DomainEvent`                           |
| `web-commons`           | `ApiResponse`, `GlobalExceptionHandler` |
| `observability-starter` | Tracing + structured logging            |
| `idempotency-support`   | Redis check trước INSERT                |
| Spring Kafka            | Consume domain events                   |

### email-worker

`spring.main.web-application-type=none` — pure Kafka consumer, không bind inbound HTTP port. Tracing (Micrometer) push spans outbound đến Zipkin/Jaeger, không cần web server. Nếu sau này cần Prometheus pull scraping thì đổi lại `servlet`.

| Dependency              | Lý do                                        |
|-------------------------|----------------------------------------------|
| `observability-starter` | Tracing + structured logging                 |
| `rate-limiter-starter`  | Redis sliding window rate limiter cho Tier 2 |
| `idempotency-support`   | Redis key check trước khi send               |
| Spring Kafka            | Consume dispatch topics                      |
| Spring Mail             | SMTP email delivery                          |
| Thymeleaf               | HTML email template rendering                |

### inapp-worker

| Dependency                 | Lý do                        |
|----------------------------|------------------------------|
| `observability-starter`    | Tracing + structured logging |
| Spring Kafka               | Consume inapp dispatch topic |
| Spring Data Redis Reactive | Redis PUBLISH                |

### websocket-gateway

| Dependency                 | Lý do                                             |
|----------------------------|---------------------------------------------------|
| `observability-starter`    | Tracing + structured logging                      |
| Spring WebFlux             | Reactive WebSocket server trên Netty              |
| Spring Data Redis Reactive | `listenToChannel()` → Flux pipeline vào WebSocket |
