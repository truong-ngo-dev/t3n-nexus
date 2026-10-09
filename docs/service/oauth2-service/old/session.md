# Session — Domain Model & Component Notation

**Liên quan**: [`feature/02-login/login-impl.md`](../../feature/02-login/implementation) · [`feature/03-logout/logout-impl.md`](../../feature/03-logout/implementation)
**Cập nhật**: 2026-06-08

> Notation, quan hệ giữa các components, TTL, invariant, và domain model cho session lifecycle trong `oauth2-service` và `web-gateway`. Vai trò trong flow (ai tạo, ai destroy) mô tả ở 2 feature liên quan phía trên — file này chỉ giữ phần domain/component ổn định qua mọi flow.

---

## 1. Components — Notation [A]–[F]

| Label    | Tên                                   | Backend              | TTL hiện tại       | TTL cần set         |
|----------|----------------------------------------|-----------------------|---------------------|----------------------|
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

**[F] được tạo khi:** [D] được issue lần đầu tiên (Phase 2 trong `SessionEstablishingAuthorizationService`, xem `feature/02-login/login-impl.md`).
[F].id (`oss_id`) được embed vào [D] claim → web-gateway đọc để tạo [A1],[A2] sau login.

---

## 2. Hành vi khi expire

| Component     | Hết hạn khi                   | Hành vi                                                                                                                                                                            | Side effect nếu không xử lý            |
|---------------|--------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------|
| **[A]**       | Idle > TTL                    | `checkSession()` 401 → Angular login page → click Login → oauth2-service. Nếu [B] còn → silent SSO, tạo [C][D][E] mới, [F] reused via `onTokenRotated()`. Nếu [B] hết → login form | [F] cũ orphaned (chỉ khi [B] cũng hết) |
| **[A1],[A2]** | Fixed 24h                     | [A] vẫn sống, user OK. Remote revoke fail                                                                                                                                          | Stale mappings tồn tại đến 24h         |
| **[B]**       | Idle > TTL                    | Không ảnh hưởng token refresh. Nếu [A] cũng hết → user phải nhập credentials, không được SSO                                                                                       | [F] orphaned                            |
| **[C]**       | Spring AS cleanup sau [E] hết | [F] orphaned w.r.t [C]                                                                                                                                                             | —                                        |
| **[D]**       | Absolute short TTL            | web-gateway auto-refresh dùng [E]. Transparent với user                                                                                                                            | —                                        |
| **[E]**       | Absolute 24h                  | web-gateway nhận `invalid_grant` → 401 → redirect login. Không SSO được                                                                                                            | [F] orphaned                            |
| **[F]**       | Không có TTL                  | Chỉ kết thúc qua explicit action (logout — xem `feature/03-logout/`)                                                                                                              | Orphan tích lũy                         |

**Lý do align [A] = [B] = [E] = 24h:** tránh SSO trigger liên tục và đảm bảo cleanup job dựa trên [B] expire không xóa [C] khi [E] vẫn còn hiệu lực.

---

## 3. Invariant cốt lõi

> **1 IDP session [B] + 1 client → chỉ 1 `OAuthSession` [F] active tại mọi thời điểm.**

Enforce bằng 3 lớp:

| Layer  | Cơ chế                                                       |
|--------|----------------------------------------------------------------|
| Domain | `assertActive()` — chặn double-revoke/expire                 |
| JPA    | `@Version` optimistic locking — chặn race trên status update |
| DB     | Unique constraint — enforce invariant tại DB level            |

```sql
ALTER TABLE oauth_sessions
    ADD CONSTRAINT uq_oauth_session_idp_client_active
    UNIQUE (idp_session_id, registered_client_id);
```

---

## 4. Domain model

### `OAuthSession` — fields

| Field                | Type                    | Ghi chú                                                             |
|-----------------------|--------------------------|----------------------------------------------------------------------|
| `id`                  | `OAuthSessionId` (ULID) | = `oss_id` trong JWT claim                                          |
| `userId`              | `UserId`                |                                                                       |
| `idpSessionId`        | `String`                | Spring Session ID tại oauth2-service ([B])                          |
| `authorizationId`     | `String`                | `OAuth2Authorization.id` ([C]) — cập nhật mỗi lần token rotate      |
| `registeredClientId`  | `String`                | Cần cho invariant 1 session per (idpSession, client)                |
| `ipAddress`           | `String`                |                                                                       |
| `status`              | `SessionStatus`         | `ACTIVE` hoặc `REVOKED`/`EXPIRED` — chỉ tồn tại khi ACTIVE trong DB |
| `createdAt`           | `Instant`               |                                                                       |

### `OAuthSession.expire()`

```java
public void expire() {
    assertActive();
    this.status = SessionStatus.EXPIRED;
    addDomainEvent(new SessionExpiredEvent(
        authorizationId, getId().getValueAsString(),
        userId.getValueAsString(), idpSessionId));
}
```

---

## 5. OAuthSession — design decisions

### Không giữ lịch sử

Lịch sử session (ended_at, device, IP) được carry bởi `login_activities` ở identity-service — đúng BC, đúng trách nhiệm. `OAuthSession` là operational aggregate, không phải audit store. Delete ngay khi expired/revoked.

### Existence = ACTIVE

Nếu delete trong cùng transaction với status change → `status = REVOKED` / `EXPIRED` không bao giờ được persist. DB chỉ thấy `ACTIVE` hoặc không có record.

**Existence = ACTIVE.** Phân biệt REVOKED vs EXPIRED được carry bởi event type, không bởi DB column.

Hệ quả:
- Partial unique index → regular unique constraint
- `assertActive()` → tương đương check record existence

### Không phải overhead

Sau khi fix F-002, `OAuthSession` **không nằm trên hot path** — token refresh không query bảng này. Chỉ được đọc khi login, revoke, cleanup job (tần suất thấp). Table luôn nhỏ vì chỉ chứa active sessions. Giữ lại vì domain boundary rõ ràng — tránh nhúng business logic vào Spring AS internal table (`oauth2_authorization`).

---

## 6. Config

```properties
# web-gateway/application.properties
spring.session.timeout=86400s

# oauth2-service/application.properties
server.servlet.session.timeout=86400s
```

### Pending — Spring Session store nên là Redis, không phải JDBC

**Làm khi nào:** Trước load test / staging promotion.

**Vấn đề:** Nếu oauth2-service dùng `JdbcIndexedSessionRepository` (default khi có `spring-session-jdbc`), mỗi request trong login flow đều hit DB cho session read/write — bottleneck #1 ở high concurrency. Ảnh hưởng cả login (`GET /oauth2/authorize`, `GET /mfa`, `POST /login/ott`) lẫn logout (đọc IDP session để invalidate) — đây là lý do note này ở service-tier thay vì riêng feature login hay logout.

**Fix:** Switch sang `RedisIndexedSessionRepository` cho oauth2-service. Session TTL ngắn (~30 phút) — memory footprint nhỏ.

**Files liên quan:**
- `oauth2-service`: `application.yml` — `spring.session.store-type=redis`
- `oauth2-service`: dependency `spring-session-data-redis`

---

## Events — chi tiết đầy đủ ở feature docs

Danh sách event publish theo từng flow (topic, payload fields, consumer) đã ở đúng chỗ, không lặp lại ở đây:
- Login path (`SessionIssuedEvent`, v.v.) → [`feature/02-login/login-impl.md`](../../feature/02-login/implementation) mục "Domain Events published by oauth2-service (login path)"
- Logout path (`SessionRevokedEvent`, `SessionsBulkExpiredEvent`) → [`feature/03-logout/logout-impl.md`](../../feature/03-logout/implementation) mục "Domain Events"
