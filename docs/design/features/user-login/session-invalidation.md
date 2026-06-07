# Session Invalidation Strategy — User Login

> Phân tích và quyết định thiết kế. Tính đến 2026-06-06.

---

## 1. Các thành phần session/token — Notation [A]–[F]

| Label    | Tên                                   | Backend              | TTL hiện tại       | TTL cần set         |
|----------|---------------------------------------|----------------------|--------------------|---------------------|
| **[A]**  | Spring Session web-gateway            | Redis                | Sliding 30 phút    | **24h**             |
| **[A1]** | `webgw:oauth:{oss_id}` → session_id   | Redis                | Fixed 24h          | Giữ nguyên          |
| **[A2]** | `webgw:session:{session_id}` → oss_id | Redis                | Fixed 24h          | Giữ nguyên          |
| **[B]**  | IDP Session tại oauth2-service (JDBC) | PostgreSQL           | Sliding 30 phút    | **24h**             |
| **[C]**  | `OAuth2Authorization.id` (UUID)       | PostgreSQL           | Stable qua refresh | —                   |
| **[D]**  | Access token (JWT)                    | In [A] session store | Absolute 1h        | **Short TTL (TBD)** |
| **[E]**  | Refresh token                         | In [A] session store | Absolute 24h       | Giữ nguyên          |
| **[F]**  | `OAuthSession` domain aggregate       | PostgreSQL           | Không có TTL       | Revoke-only         |

### Quan hệ

```
[B] IDP Session ──── 1:1 per client ────► [F] OAuthSession   (invariant enforced)
                                               │
                                               ├── .authorizationId ──► [C] OAuth2Authorization
                                               └── .idpSessionId    ──► [B]

[D] Access token  ──── claim: oss_id = [F].id  ──────────────────────► [A1][A2] mapping
[A] Spring Session ──── contains (OAuth2AuthorizedClient in Redis) ──► [D] + [E]
```

**[F] được tạo khi:** [D] được issue lần đầu tiên (Phase 2 trong `AuditingOAuth2AuthorizationService`).  
[F].id (`oss_id`) được embed vào [D] claim → web-gateway đọc để tạo [A1],[A2] sau login.

---

## 2. Hành vi khi expire

| Component     | Hết hạn khi                   | Hành vi                                                                                                                                            | Side effect nếu không xử lý    |
|---------------|-------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| **[A]**       | Idle > TTL                    | `checkSession()` 401 → Angular login page → click Login → oauth2-service. Nếu [B] còn → silent SSO, tạo [C][D][E][F] mới. Nếu [B] hết → login form | [F] cũ orphaned                |
| **[A1],[A2]** | Fixed 24h                     | [A] vẫn sống, user OK. Remote revoke fail                                                                                                          | Stale mappings tồn tại đến 24h |
| **[B]**       | Idle > TTL                    | Không ảnh hưởng token refresh. Nếu [A] cũng hết → user phải nhập credentials, không được SSO                                                       | [F] orphaned                   |
| **[C]**       | Spring AS cleanup sau [E] hết | [F] orphaned w.r.t [C]                                                                                                                             | —                              |
| **[D]**       | Absolute short TTL            | web-gateway auto-refresh dùng [E]. Transparent với user                                                                                            | —                              |
| **[E]**       | Absolute 24h                  | web-gateway nhận `invalid_grant` → 401 → redirect login. Không SSO được                                                                            | [F] orphaned                   |
| **[F]**       | Không có TTL                  | Chỉ kết thúc qua explicit action                                                                                                                   | Orphan tích lũy                |

**Lý do align [A] = [B] = [E] = 24h:** tránh SSO trigger liên tục và đảm bảo cleanup job dựa trên [B] expire không xóa [C] khi [E] vẫn còn hiệu lực.

---

## 3. Invariant cốt lõi

> **1 IDP session [B] + 1 client → chỉ 1 `OAuthSession` [F] active tại mọi thời điểm.**

Enforce bằng 3 lớp:

| Layer  | Cơ chế                                                       |
|--------|--------------------------------------------------------------|
| Domain | `assertActive()` — chặn double-revoke/expire                 |
| JPA    | `@Version` optimistic locking — chặn race trên status update |
| DB     | Partial unique index — enforce invariant tại DB level        |

```sql
CREATE UNIQUE INDEX uq_oauth_session_active
ON oauth_sessions(idp_session_id, registered_client_id)
WHERE status = 'ACTIVE';
```

---

## 4. Invalidation strategy

### 4.1 web-gateway — Redis keyspace notification

**Trigger:** [A] expire tự nhiên hoặc bị xóa.

```
[A] key expire → Redis keyspace notification → SessionDestroyedEvent
  → Lua script (atomic):
      local ossId = redis.call('GET', 'webgw:session:' .. sessionId)
      if ossId then
        redis.call('DEL', 'webgw:oauth:' .. ossId)   -- [A1]
        redis.call('DEL', 'webgw:session:' .. sessionId)  -- [A2]
      end
```

**Cần bật Redis config:** `notify-keyspace-events = Kx` (hoặc `KEx`).

**Risk:** Keyspace notification có thể miss (Redis restart, high load) → stale mappings tồn tại đến 24h TTL. Acceptable — không gây security issue, chỉ ảnh hưởng remote revoke accuracy.

**Không notify sang oauth2-service** — web-gateway cleanup là việc nội bộ của gateway.

---

### 4.2 oauth2-service — 3 scenario

#### Scenario A: SSO tạo [F'] mới

Enforce invariant trong `IssueSession.handle()`:

```
IssueSession.handle(command):
  1. find OAuthSession WHERE idpSessionId = ? AND registeredClientId = ? AND status = ACTIVE
  2. nếu tìm thấy [F] cũ → [F].revoke() → SessionRevokedEvent (outbox)
  3. tạo [F'] mới → SessionIssuedEvent (outbox)
  4. persist [F] REVOKED + [F'] ACTIVE trong cùng transaction
```

Kafka partition theo `userId` đảm bảo ordering: `SessionRevokedEvent` đến identity-service trước `SessionIssuedEvent`.

---

#### Scenario B: IDP session [B] expire

Spring Session JDBC cleanup job fire `SessionDestroyedEvent`:

```
SessionDestroyedEvent(idpSessionId = B.id)
  → find OAuthSession WHERE idpSessionId = ? AND status = ACTIVE
    → guaranteed 1 record [F] (do invariant 4.2.A)
  → lấy [F].authorizationId → [C]
  → Transaction:
      - [F].expire() → SessionExpiredEvent (outbox)
      - DELETE [F]
      - DELETE oauth2_authorization [C]
  → Outbox relay → Kafka → identity-service: set login_activities.ended_at
  → HTTP POST /webgw/internal/sessions/revoke {ossId: [F].id}
    → web-gateway: xóa [A] + [A1] + [A2]
```

---

#### Scenario C: [E] expire, user không quay lại (không trigger refresh)

Scheduled job định kỳ trong oauth2-service:

```
Job:
  → query oauth2_authorization WHERE refresh_token_expires_at < now()
  → find OAuthSession WHERE authorizationId = ? AND status = ACTIVE
  → Transaction:
      - [F].expire() → SessionExpiredEvent (outbox)
      - DELETE [F]
      - DELETE oauth2_authorization [C]
  → Outbox relay → Kafka → identity-service: set login_activities.ended_at
  → HTTP POST /webgw/internal/sessions/revoke {ossId: [F].id}
    → web-gateway: xóa [A] + [A1] + [A2] (nếu còn tồn tại)
```

**Edge case:** `oauth2_authorization` expired nhưng không tìm thấy [F] tương ứng (đã bị xóa trước) → chỉ DELETE `oauth2_authorization`, không fire event.

---

### 4.3 Back-channel web-gateway

oauth2-service gọi trực tiếp HTTP, không qua Kafka:

```
POST /webgw/internal/sessions/revoke
Body: { "ossId": "[F].id" }
```

**Lý do dùng API call thay vì event:**
- Endpoint đã có sẵn (`SessionRevokeController`)
- web-gateway không cần Kafka (BFF layer, overkill)
- Explicit revoke cần immediate invalidation — không chấp nhận Kafka lag

Hai transport song song cho hai consumer:

```
oauth2-service → SessionExpiredEvent → Kafka   → identity-service (set ended_at)
oauth2-service → HTTP revoke         → web-gateway (xóa [A][A1][A2])
```

---

## 5. Domain model changes

### `OAuthSession` — thêm field

```java
private final String registeredClientId;  // cần cho 1-to-1 invariant per client
```

### `OAuthSession.expire()` — thêm event

```java
public void expire() {
    assertActive();
    this.status = SessionStatus.EXPIRED;
    addDomainEvent(new SessionExpiredEvent(
        authorizationId, getId().getValueAsString(),
        userId.getValueAsString(), idpSessionId));
}
```

Hiện tại `expire()` không dispatch event — cần bổ sung.

### `@Version` — optimistic locking

```java
@Version
private Long version;
```

---

## 6. Events

| Event                 | Trigger                                          | Consumer         | Hành động                       |
|-----------------------|--------------------------------------------------|------------------|---------------------------------|
| `SessionIssuedEvent`  | Fresh login / SSO tạo [F']                       | identity-service | Tạo `login_activity` mới        |
| `SessionRevokedEvent` | Explicit: logout, admin kick, SSO replace [F] cũ | identity-service | Set `login_activities.ended_at` |
| `SessionExpiredEvent` | Natural: [B] expire, [E] expire không refresh    | identity-service | Set `login_activities.ended_at` |

**Lý do giữ 2 event riêng (`SessionRevokedEvent` vs `SessionExpiredEvent`):**  
Đây là 2 domain fact khác nhau về bản chất — intentional action vs natural timeout. Consumer tương lai (security audit) có thể cần subscribe có chọn lọc. Dùng 1 event + `reason` field sẽ leak business logic ra consumer.

---

## 7. OAuthSession — design decisions

### Không giữ lịch sử

Lịch sử session (ended_at, device, IP) được carry bởi `login_activities` ở identity-service — đúng BC, đúng trách nhiệm. `OAuthSession` là operational aggregate, không phải audit store. Delete ngay khi expired/revoked.

### Không cần `status` column trong DB

Nếu delete trong cùng transaction với status change → `status = REVOKED` / `EXPIRED` không bao giờ được persist. DB chỉ thấy `ACTIVE` hoặc không có record.

**Existence = ACTIVE.** Phân biệt REVOKED vs EXPIRED được carry bởi event type, không bởi DB column.

Hệ quả:
- Partial unique index → regular unique constraint
- `assertActive()` → tương đương check record existence

### Không phải overhead

Sau khi fix F-002, `OAuthSession` **không nằm trên hot path** — token refresh không query bảng này. Chỉ được đọc khi login, revoke, cleanup job (tần suất thấp). Table luôn nhỏ vì chỉ chứa active sessions. Giữ lại vì domain boundary rõ ràng — tránh nhúng business logic vào Spring AS internal table (`oauth2_authorization`).

---

## 8. Config cần set

```properties
# web-gateway/application.properties
spring.session.timeout=86400s

# oauth2-service/application.properties
server.servlet.session.timeout=86400s
```

Mapping key TTL đã đúng (86400s) — không cần đổi.

---

## 9. Pending — chưa implement

| #  | Nội dung                                                      | Ưu tiên                 |
|----|---------------------------------------------------------------|-------------------------|
| 1  | Fix F-002 — IssueSession gọi lại mỗi refresh                  | **Cao — trước go-live** |
| 2  | Thêm `registeredClientId` vào `OAuthSession`                  | Cao                     |
| 3  | Thêm `SessionExpiredEvent` vào `expire()`                     | Cao                     |
| 4  | Thêm `@Version` optimistic locking                            | Cao                     |
| 5  | Thêm unique constraint thay partial index                     | Cao                     |
| 6  | Set TTL config [A] và [B] = 24h                               | Cao                     |
| 7  | Implement Redis keyspace notification cleanup tại web-gateway | Trung bình              |
| 8  | Implement IDP session expiry handler tại oauth2-service       | Trung bình              |
| 9  | Implement scheduled job cleanup [E] expire                    | Trung bình              |
| 10 | Chốt short TTL cho [D] (access token)                         | Trung bình              |
