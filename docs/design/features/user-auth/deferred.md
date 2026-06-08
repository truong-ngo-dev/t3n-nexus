# Deferred — user-auth

Những việc được xác định trong feature này nhưng **chưa impl**, sẽ làm ở phase khác.

---

## 1. Scheduled job cleanup orphaned OAuthSession (Path C)

**Làm khi nào:** Sau khi logout flow (Path A) và web-gateway TTL cleanup (Path B) đã stable.

**Vấn đề:**  
Khi IDP session [B] hết TTL, Spring Session JDBC cleanup task xóa record khỏi `SPRING_SESSION`.  
Không có session event nào được fire (Spring Session JDBC 4.x removed event support).  
`OAuthSession` [F] và `OAuth2Authorization` [C] tương ứng trở thành orphaned — không được cleanup cho đến khi job chạy.

**Trạng thái hiện tại:**  
`JdbcOAuthSessionExpiryService.expireOrphaned()` đã implement — tìm orphaned [F] bằng LEFT JOIN SPRING_SESSION, batch delete [F] + [C], trả `SessionsBulkExpiredEvent`.  
**Chưa có `@Scheduled` caller.**

**Cần implement:**  
Tạo `ExpiredSessionCleanupJob` trong oauth2-service:
```
@Scheduled(...)
@ConditionalOnProperty("app.job.expired-session-cleanup.enabled")
cleanupJob():
  event = oAuthSessionExpiryService.expireOrphaned()
  if event.oauthSessionIds().isEmpty() → return
  eventDispatcher.dispatch(event)
  event.oauthSessionIds().forEach(revocationClient::revoke)
```

Job mặc định **disabled** — enable khi chốt cron interval.  
Chi tiết impl → [`logout-impl.md`](logout-impl.md) mục 8–9.

**Files liên quan:**
- `oauth2-service`: tạo `application/session/cleanup/ExpiredSessionCleanupJob.java`

## 2. Relay dlq (vấn đề chung, giải pháp trong tương lai)

---

## 3. Concurrency — EstablishSession Phase 2 idempotency under retry

**Làm khi nào:** Trước khi đưa lên production nếu BFF có retry middleware (exponential backoff on 5xx).

**Vấn đề:**  
Phase 2 của `SessionEstablishingAuthorizationService` không atomic:

```
findById(ossId)  →  NOT FOUND
IssueSession.handle()  →  INSERT oauth_sessions   ← nếu 2 request race, cả 2 thấy NOT FOUND
                                                     → second INSERT hit unique constraint
                                                     → DataIntegrityViolationException → 500
```

Trong flow bình thường, một authorization code chỉ được exchange 1 lần (Spring AS invalidate code ngay sau đó) — race này **không xảy ra trên happy path**. Rủi ro chỉ tồn tại nếu BFF retry request token exchange sau timeout trước khi nhận response.

**Các race scenarios đã safe (không cần fix):**

| Scenario | Tại sao safe |
|---|---|
| Multi-tab OTP generate | `hasActiveToken()` giảm duplicate; nếu race → 2 email, last write wins session — UX noise, không phải security issue |
| Phase 1.5 concurrent auth | 1 browser, 1 session — 2 concurrent `/oauth2/authorize` trên cùng session là edge case cực hiếm |
| Refresh token race | Guard `oldAuthId == newAuthId` → early return idempotent |

**Cần implement:**  
Convert INSERT trong `IssueSession` sang upsert idempotent:

```sql
INSERT INTO oauth_sessions (...) VALUES (...)
ON CONFLICT (oauth_session_id) DO NOTHING
```

Hoặc wrap trong try-catch `DataIntegrityViolationException` → verify row đã tồn tại với cùng `ossId` → treat as success.

**Files liên quan:**
- `oauth2-service`: `application/session/IssueSession.java` — phần INSERT `OAuthSession`

---

## 4. Performance — production readiness checklist

**Làm khi nào:** Trước load test / staging promotion.

### 4.1 Spring Session store — Redis (priority cao nhất)

**Vấn đề:**  
Nếu oauth2-service dùng `JdbcIndexedSessionRepository` (default khi có `spring-session-jdbc`), mỗi request trong login flow đều hit DB cho session read/write — đây là bottleneck #1 ở high concurrency.

Requests bị ảnh hưởng trong 1 login cycle:
- `GET /oauth2/authorize` — load session
- `GET /mfa` — load + write (`mfa_otp`, `mfa_otp_expiry`)
- `POST /login/ott` — load + write (invalidate OTP)
- `GET /oauth2/authorize` (lần 2) — load session

**Fix:** Switch sang `RedisIndexedSessionRepository` cho oauth2-service. Session TTL ngắn (~30 phút) — memory footprint nhỏ.

**Files liên quan:**
- `oauth2-service`: `application.yml` — `spring.session.store-type=redis`
- `oauth2-service`: dependency `spring-session-data-redis`

### 4.2 Index — `findActiveByIdpSessionAndClient`

**Vấn đề:**  
`findActiveByIdpSessionAndClient(idpSessionId, registeredClientId)` được gọi tại Phase 1.5 của mọi authorization code issuance. Nếu thiếu index → full scan `oauth_sessions`.

**Fix:** Composite index:
```sql
CREATE INDEX idx_oauth_sessions_idp_client_status
    ON oauth_sessions (idp_session_id, registered_client_id, status)
    WHERE status = 'ACTIVE';
```

**Files liên quan:**
- `oauth2-service`: migration SQL cho `oauth_sessions`

### 4.3 Outbox polling interval — OTP email latency

**Vấn đề:**  
`LoginOtpRequestedEvent` đi qua Outbox → polling job → Kafka → notification-service → email provider. Nếu polling interval 5 giây + email provider 2-3 giây, user đợi OTP **7-10 giây** sau khi submit form.

**Fix (chọn 1):**
- Giảm polling interval xuống 1-2 giây cho Outbox processor của oauth2-service
- Hoặc dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` để trigger publish ngay sau transaction commit thay vì chờ polling cycle

### 4.4 BCrypt — horizontal scale note

BCrypt cost 10 ≈ 100ms/thread. Không giảm cost factor. Tại high concurrent LOCAL login, scale horizontal oauth2-service là giải pháp duy nhất. Không có code change cần thiết — đây là infra decision.

**Rate-limiter**: phải là distributed (Redis-based), không dùng in-memory — sẽ không work khi có nhiều instance.