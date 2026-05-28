# Phase 4 — notification-service

**Status:** `IN_PROGRESS`
**Started:** 2026-05-26
**Completed:** —

## Checklist

### Design
- [x] Tạo `design/services/notification-service.md`
  - 3 notification channels: Email, In-App (WebSocket), MQTT (chat/shipper — not this service)
  - Email Tier Model: Tier 1 Transactional vs Tier 2 Bulk/Marketing — priority inversion analysis
  - CDC-driven delivery architecture (Outbox Pattern applied to notification)
  - DB schema: `notification_log` (CDC source, immutable) + `notification_inbox` (user-facing)
  - websocket-gateway: Spring WebFlux + Netty + Redis pub/sub, horizontal scale
  - Chọn PostgreSQL: JSONB cho payload + logical replication cho Debezium CDC

### Implementation — Phase 4 scope (verification email only)

**notification-service (Router)**
- [x] Setup project: Spring Boot, Kafka consumer, PostgreSQL
- [x] Tạo bảng `notification_log` (UNIQUE event_id + channel, payload JSONB)
- [x] Tạo bảng `notification_inbox`
- [x] Implement consumer `identity.customer.registered` → INSERT notification_log + notification_inbox (IN_APP) trong cùng transaction
- [x] Implement consumer `identity.user.verification-resent` → như trên
- [ ] Idempotency lớp 1: `idempotency-support` Redis check + DB UNIQUE constraint

**Debezium CDC**
- [ ] Config Debezium connector cho `notification_log`
- [ ] Config SMT route: `channel=EMAIL + tier=TRANSACTIONAL` → `notification.email.transactional`
- [ ] Thêm Debezium vào `docker-compose.yml`

**email-worker (Tier 1)**
- [ ] Setup project: Spring Boot, Kafka consumer, Spring Mail, Thymeleaf
- [ ] Implement consumer `notification.email.transactional`
- [ ] Idempotency lớp 2: Redis key `"email:{notification_log_id}"` TTL 72h
- [ ] Config email template `verification.html` — link `{baseUrl}/verify?token={verificationToken}`
- [ ] Failure: max retry → DLQ
- [ ] Config Spring Mail → Mailhog (dev)

**Infrastructure**
- [ ] Thêm Mailhog vào `docker-compose.yml`

## Verify

Dùng Mailhog:

- Đăng ký user mới → `notification_log` có row mới (channel=EMAIL, tier=TRANSACTIONAL) → trong vòng vài giây, verification email xuất hiện trong Mailhog.
- Email chứa link `{baseUrl}/verify?token=...`, subject và sender khớp config.
- Consume cùng `identity.customer.registered` event 2 lần → `UNIQUE (event_id, channel)` reject INSERT thứ 2 → chỉ 1 email được gửi.
- Gọi resend → `notification_log` có row mới với `event_id` khác → email mới với token khác.
- Consume cùng CDC event 2 lần tại email-worker → Redis idempotency key skip lần 2 → chỉ 1 email.

```sql
-- Sau khi đăng ký:
SELECT * FROM notification_log WHERE notification_type = 'VERIFICATION_EMAIL';
-- Expect: 1 row, channel=EMAIL, tier=TRANSACTIONAL

-- Idempotency — insert cùng event 2 lần:
SELECT COUNT(*) FROM notification_log WHERE event_id = '<eventId>' AND channel = 'EMAIL';
-- Expect: 1
```

## Session Log

### 2026-05-26
**Làm được:**
- Tạo và hoàn thiện `design/services/notification-service.md` với full architecture:
  - Phân tích 3 channel (Email, In-App/WebSocket, MQTT) — natural fit của từng cái
  - Email Tier Model: bản chất business contract, priority inversion problem, tại sao tách consumer group
  - CDC-driven delivery: notification-service ghi DB → Debezium → Kafka dispatch topics → workers
  - DB tách `notification_log` (CDC source, immutable, JSONB payload) + `notification_inbox` (user-facing)
  - notification-service INSERT cả 2 bảng atomically trong 1 transaction
  - Workers dùng Redis idempotency key, không UPDATE DB sau delivery
  - DLQ là signal duy nhất cho failure
  - websocket-gateway: Spring WebFlux + Netty, reactive pipeline per connection, Redis pub/sub fan-out, multi-tab handling
  - Chọn PostgreSQL: JSONB binary + logical replication

**Còn lại:** Toàn bộ implementation items
**Blocker:** Không có
