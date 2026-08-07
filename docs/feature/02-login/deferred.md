# Deferred — login

Những việc được xác định trong feature này nhưng **chưa impl**, sẽ làm ở phase khác.

---

## 1. Concurrency — EstablishSession Phase 2 idempotency under retry

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

## 2. Index — `findActiveByIdpSessionAndClient`

**Làm khi nào:** Trước load test / staging promotion.

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

---

## 3. Outbox polling interval — OTP email latency

**Làm khi nào:** Trước load test / staging promotion.

**Vấn đề:**  
`LoginOtpRequestedEvent` đi qua Outbox → polling job → Kafka → notification-service → email provider. Nếu polling interval 5 giây + email provider 2-3 giây, user đợi OTP **7-10 giây** sau khi submit form.

**Fix (chọn 1):**
- Giảm polling interval xuống 1-2 giây cho Outbox processor của oauth2-service
- Hoặc dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` để trigger publish ngay sau transaction commit thay vì chờ polling cycle

---

## 4. BCrypt — horizontal scale note

**Làm khi nào:** Trước load test / staging promotion.

BCrypt cost 10 ≈ 100ms/thread. Không giảm cost factor. Tại high concurrent LOCAL login, scale horizontal oauth2-service là giải pháp duy nhất. Không có code change cần thiết — đây là infra decision.

**Rate-limiter**: phải là distributed (Redis-based), không dùng in-memory — sẽ không work khi có nhiều instance.

---

## 5. Relay DLQ (vấn đề chung, giải pháp trong tương lai)

**Trạng thái:** Chỉ là placeholder khi tách từ `user-auth/deferred.md` cũ — chưa có nội dung cụ thể lúc viết ban đầu. Đặt tạm ở đây vì liên quan gần nhất tới outbox relay của login OTP (mục 3), nhưng có thể là vấn đề chung hơn phạm vi login — xem `3.technical/dlq-implementation-notes.md` nếu cần bối cảnh DLQ chung của hệ thống trước khi viết cụ thể lại mục này.
