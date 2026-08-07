# Idempotency — DB Constraint vs Redis Guard, Khi Nào Dùng Cái Nào

> Cookbook quyết định: 1 consumer/handler mới cần dedupe theo cơ chế nào — DB constraint thuần, Redis guard thuần, hay kết hợp cả hai.

---

## Quy tắc quyết định

Trục quyết định là **hệ quả khi dedup bị miss**, không phải "service đó có DB hay không".

| | Dedup sống còn | Dedup không sống còn |
|---|---|---|
| Hệ quả khi miss | Vi phạm business invariant, data corruption (VD: 2 `CustomerProfile` cho 1 user) | Chỉ dư thừa/phiền, không sai dữ liệu (VD: gửi trùng 1 email) |
| Guard bắt buộc | **DB constraint** (`UNIQUE` + `ON CONFLICT DO NOTHING`/upsert) — không thương lượng | Redis TTL guard là đủ — đánh đổi lấy hiệu năng/đơn giản hạ tầng |
| Redis có vai trò gì | Chỉ được đứng **trước** DB như cache tăng tốc (skip round-trip khi chắc chắn trùng), **không bao giờ** thay DB làm trọng tài | Có thể là guard duy nhất — chấp nhận Redis mất key thỉnh thoảng gây duplicate, vì hệ quả rẻ |

**Không dùng "service có DB hay không" làm tiêu chí** — một service có DB vẫn có thể chọn Redis guard thuần cho một dedup không sống còn nếu muốn tránh tải DB; một service không có DB (worker thuần Kafka consumer) mà cần dedup sống còn thì phải có DB, không có cách nào né được.

---

## Áp dụng thực tế trong hệ thống

| Consumer/Handler | Hệ quả khi miss | Guard đang dùng | Đúng/sai theo quy tắc |
|---|---|---|---|
| `customer-service` — `CreateCustomerProfile` (event `identity.customer-account.created`) | 2 `CustomerProfile` cho 1 `userId` — vi phạm invariant | DB `UNIQUE(user_id)` + `ON CONFLICT DO NOTHING`, không Redis | Đúng — sống còn |
| `notification-service` — Router ghi `notification_log` (mọi event consume) | 2 log entry cùng `(event_id, channel)` → CDC bắn trùng xuống dispatch topic → downstream gửi trùng | DB `UNIQUE(event_id, channel)` + `ON CONFLICT DO NOTHING`, không Redis | Đúng — sống còn (miss ở đây không chỉ dư thừa mà lan hệ quả xuống toàn bộ downstream) |
| `email-worker` — `EmailDispatchHandler` (gửi SMTP) | Gửi trùng 1 email verification/OTP | Redis `IdempotencyGuard.tryAcquire` (TTL 72h), release khi exception, **không có DB nào trong service này** | Đúng — không sống còn, và không có DB để đẩy xuống dù muốn |

`email-worker` không sống còn vì **không có DB nào để đẩy xuống** — chủ đích thiết kế `spring.main.web-application-type=none`, pure Kafka consumer. Thêm 1 DB riêng chỉ để ghi "đã gửi chưa" không giải quyết gì thêm về correctness (SMTP call vẫn là external side-effect không transactional được với bất kỳ storage nào, DB hay Redis) — chỉ đổi lấy thêm 1 hạ tầng để đạt cùng mức guarantee best-effort.

---

## Khi nào cần thêm Lớp 1 Redis cache trước DB (cho case sống còn)

Không cần theo mặc định — chỉ xét khi **volume duplicate-delivery thật sự lớn**, không phải duplicate lẻ tẻ do consumer restart bình thường. Ba nguồn thật gây burst lớn:

1. **Kafka consumer rebalance storm** — consumer bị coi "dead" do miss heartbeat (downstream chậm) → partition chuyển consumer khác → xử lý lại từ offset cũ. Nếu consumer bị đá cố rejoin, có thể kích thêm rebalance → chuỗi evict/rejoin/evict khuếch đại thành storm.
2. **Debezium connector restart/replay** — connector chỉ cam kết at-least-once; nếu Kafka Connect crash trước khi lưu offset, hoặc WAL bị purge do dừng quá lâu, connector phải replay/snapshot lại → bắn lại nguyên khối event đã publish trước đó khi restart. Áp dụng trực tiếp cho `connector-notification.json`, `connector-identity-outbox.json`, v.v. trong `infra/debezium/`.
3. **DLQ replay thủ công** sau khi fix bug — message trong DLQ thường đã xử lý một phần trước khi rơi vào đó, replay lại tạo duplicate hàng loạt như thao tác vận hành bình thường.

Ở volume đó, DB-only bắt đầu đau vì 2 điểm nghẽn cụ thể (không phải CPU của 1 INSERT): connection pool exhaustion (mỗi check vẫn chiếm 1 connection), và WAL overhead thật của `ON CONFLICT DO NOTHING` (Postgres dùng speculative insertion, vẫn ghi WAL record xác nhận/hủy dù kết quả là conflict — production case ghi nhận ~25,000 upsert/giây theo pattern này đã vượt khả năng sync WAL bền vững của 1 gp3 EBS volume phổ biến, buộc Postgres batch commit lại và tăng latency toàn bộ traffic).

**Hiện tại chưa có consumer nào trong hệ thống chạm ngưỡng này** — không thêm Lớp 1 Redis cho `customer-service`/`notification-service` cho tới khi có số liệu thật (consumer lag, DB connection pool saturation, hoặc rebalance frequency) cho thấy cần.

### Nếu/khi cần thêm — thứ tự bắt buộc để không tạo false-positive

```
1. GET key (Redis)      — hit → return ngay, không đụng DB
                           miss → đi tiếp bước 2, KHÔNG kết luận gì (chỉ là "chưa biết")
2. INSERT ... ON CONFLICT DO NOTHING (DB) — vẫn là nguồn sự thật duy nhất
3. SET key (Redis)      — CHỈ sau khi bước 2 đã có kết quả xác định
                           (dù insert mới hay conflict xác nhận trùng)
```

SET phải luôn đứng **sau** bước DB, không bao giờ trước hoặc song song — đây là điều kiện duy nhất cần giữ để loại trừ false-positive (Redis nói "đã thấy" nhưng DB chưa từng ghi → mất dữ liệu vĩnh viễn) bằng cấu trúc, không cần cơ chế detect-and-fix nào thêm.

### Redis chết/không kết nối được khi đã có Lớp 1

Không phải hành vi tự động an toàn — phải chủ động code fail-open, nếu không Redis chết sẽ làm cả consumer dừng theo (biến 1 optimization thành 1 single point of failure mới):

```java
boolean seen;
try {
    seen = redis.exists(key);
} catch (RedisConnectionException e) {
    log.warn("Redis unavailable, fallback DB-only idempotency check");
    seen = false;               // coi như cache-miss, KHÔNG phải "chưa xử lý" nguy hiểm
}                                // vì DB ở bước 2 vẫn phân xử đúng bất kể Redis nói gì
if (seen) return;

int rows = jdbcTemplate.update(insertOnConflictDoNothingSql, ...);

try {
    redis.set(key, "1", Duration.ofHours(ttl));
} catch (RedisConnectionException e) {
    log.warn("Redis unavailable, skip caching mark — DB vẫn là source of truth");
    // KHÔNG throw — không rollback việc DB vừa làm xong, không chặn commit offset
}
```

Kết quả khi Redis down hoàn toàn: hệ thống tự động degrade về đúng 100% DB-only (tức về đúng trạng thái hiện tại của `customer-service`/`notification-service`) — không có correctness impact, chỉ mất phần tối ưu hiệu năng đúng trong lúc Redis down.

**Lưu ý — không nhầm với pattern `email-worker`:** quy tắc reject vs degrade ở trên **không đối xứng**, và không phải "acquire xong, DB lỗi hay Redis lỗi thì xử lý như nhau". Trục quyết định là lớp nào đang đóng vai trò nguồn sự thật:
- `email-worker` (`EmailDispatchHandler` + `RedisIdempotencyGuard`) chỉ có **1 lớp guard** — Redis, không có DB backstop. Check code: `tryAcquire()` không bọc try/catch, lỗi Redis văng thẳng lên Kafka consumer → retry → DLQ nếu Redis down kéo dài. **Đúng** cho service này, vì Redis là nguồn sự thật duy nhất ở đây, không có gì để rơi về.
- Pattern Lớp 1 ở trên (nếu sau này build) có **2 lớp** — Redis chỉ là cache đứng trước DB. Redis lỗi (GET hoặc SET) phải **degrade**, không reject; chỉ DB lỗi mới reject. Áp nhầm rule "reject cả 2" của `email-worker` vào đây sẽ không sai correctness (DB vẫn idempotent khi retry) nhưng tự tạo thêm round-trip DB vô ích — đúng thứ Lớp 1 sinh ra để tránh.

---

## Tham khảo

Phân tích đầy đủ (case study, so sánh requestId vs event_id, pitfalls, nguồn đối chiếu thực tế) ở `distributed-systems/patterns/idempotency.md` trong Knowledge base — file này chỉ là bản áp dụng cụ thể cho hệ thống, không lặp lại lý thuyết.
