# Progress: Session Invalidation

> Design: [`session-invalidation.md`](../session-invalidation.md)
> Phụ thuộc: Phase 1–3 của [`checklist.md`](checklist.md) phải hoàn thành trước.

---

## Phase 4 — Prerequisites: Domain model & config

> Mục tiêu: chuẩn bị đủ nền tảng trước khi implement các flow invalidation.

### oauth2-service — Domain model

| # | Bước                                                                                        | Status |
|---|---------------------------------------------------------------------------------------------|--------|
| 1 | `OAuthSession.java` — thêm field `registeredClientId`                                       | ✅ DONE |
| 2 | `OAuthSession.expire()` — thêm `addDomainEvent(new SessionExpiredEvent(...))`               | ✅ DONE |
| 3 | Tạo `SessionExpiredEvent.java` (record, tương tự `SessionRevokedEvent`)                     | ✅ DONE |
| 4 | `OAuthSessionJpaEntity.java` — thêm field `registeredClientId` + `@Version version`         | ✅ DONE |
| 5 | `OAuthSessionMapper.java` — cập nhật mapping thêm `registeredClientId`                      | ✅ DONE |
| 6 | Migration — thêm `registered_client_id` column, thêm unique constraint, thêm version column | ✅ DONE |

```sql
-- Migration VX__oauth_session_invalidation.sql
ALTER TABLE oauth_sessions ADD COLUMN registered_client_id VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE oauth_sessions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE oauth_sessions DROP CONSTRAINT IF EXISTS uq_oauth_session_active;
ALTER TABLE oauth_sessions
    ADD CONSTRAINT uq_oauth_session_idp_client_active
    UNIQUE (idp_session_id, registered_client_id);
```

### identity-service — Schema

| # | Bước                                                             | Status |
|---|------------------------------------------------------------------|--------|
| 7 | Migration — thêm `session_id`, `ended_at` vào `login_activities` | ✅ DONE |

```sql
-- Migration VX__login_activity_session.sql
ALTER TABLE login_activities ADD COLUMN session_id  VARCHAR(26);
ALTER TABLE login_activities ADD COLUMN ended_at    TIMESTAMPTZ;
```

### Config TTL alignment

| # | Bước                                                                              | Status |
|---|-----------------------------------------------------------------------------------|--------|
| 8 | `web-gateway/application.properties` — `spring.session.timeout=86400s`            | ✅ DONE |
| 9 | `oauth2-service/application.properties` — `server.servlet.session.timeout=86400s` | ✅ DONE |

---

## Phase 5 — Fix F-002 (critical — trước go-live)

> Mục tiêu: ngăn `IssueSession` bị gọi lại mỗi lần refresh token rotation.

| #  | Bước                                                                                             | Status |
|----|--------------------------------------------------------------------------------------------------|--------|
| 10 | `AuditingOAuth2AuthorizationService` Phase 2 — thêm existence check trước khi gọi `IssueSession` | ✅ DONE |

```text
// Chỉ gọi IssueSession khi OAuthSession chưa tồn tại
String oauthSessionId = orEmpty(authorization.getAttribute(ATTR_OAUTH_SESSION_ID));
if (StringUtils.hasText(oauthSessionId)
        && !oAuthSessionRepository.existsById(new OAuthSessionId(oauthSessionId))) {
    issueSession.handle(new IssueSession.Command(...));
}
```

### Verify F-002

- [ ] Refresh token rotation xảy ra → `OAuthSession` không bị UPDATE lại
- [ ] `SessionIssuedEvent` chỉ có 1 bản ghi trong outbox sau nhiều lần refresh
- [ ] identity-service không nhận duplicate `SessionIssuedEvent`

---

## Phase 6 — SSO Invariant: 1 IDP session + 1 client → 1 OAuthSession active

> Mục tiêu: khi SSO tạo [F'] mới, revoke [F] cũ cùng `idpSessionId` + `registeredClientId`.

| #  | Bước                                                                                                                                 | Status |
|----|--------------------------------------------------------------------------------------------------------------------------------------|--------|
| 11 | `IssueSession.Command` — thêm `registeredClientId`                                                                                   | ✅ DONE |
| 12 | `IssueSession.handle()` — query `OAuthSession WHERE idpSessionId=? AND registeredClientId=? AND status=ACTIVE` → revoke nếu tìm thấy | ✅ DONE |
| 13 | `AuditingOAuth2AuthorizationService` — truyền `registeredClientId` vào `IssueSession.Command`                                        | ✅ DONE |
| 14 | identity-service `SessionIssuedConsumer` — cập nhật để lưu `session_id` vào `login_activities`                                       | ✅ DONE |
| 15 | identity-service `SessionRevokedConsumer` — set `login_activities.ended_at` khi nhận event                                           | ✅ DONE |

### Verify Phase 6

- [ ] Login lần 1 → `OAuthSession` [F] tạo thành công, status=ACTIVE
- [ ] [A] expire → user click Login → SSO → [F'] tạo mới, [F] cũ được REVOKED
- [ ] `SessionRevokedEvent` từ [F] cũ → identity-service set `ended_at`
- [ ] `SessionIssuedEvent` từ [F'] mới → identity-service tạo `login_activity` mới với `session_id`
- [ ] DB: tại mọi thời điểm chỉ có 1 `OAuthSession` ACTIVE với cùng `(idpSessionId, registeredClientId)`
- [ ] Concurrent SSO (2 tab): unique constraint đảm bảo chỉ 1 [F'] được persist

---

## Phase 7 — web-gateway: Redis keyspace notification cleanup

> Mục tiêu: khi [A] expire tự nhiên, xóa [A1],[A2] mapping keys.

| #  | Bước                                                                                            | Status |
|----|-------------------------------------------------------------------------------------------------|--------|
| 16 | Redis config — bật `notify-keyspace-events=Kx` (hoặc `KEx`)                                     | ✅ DONE |
| 17 | Implement `SessionMappingCleanupListener` — lắng nghe `SessionDestroyedEvent` từ Spring Session | ✅ DONE |
| 18 | Dùng Lua script để atomic xóa [A1],[A2] theo `sessionId`                                        | ✅ DONE |

```java
// Lua script atomic cleanup
private static final String CLEANUP_SCRIPT = """
    local ossId = redis.call('GET', KEYS[1])
    if ossId then
      redis.call('DEL', 'webgw:oauth:' .. ossId)
      redis.call('DEL', KEYS[1])
    end
    return ossId
    """;
// KEYS[1] = webgw:session:{sessionId}
```

### Verify Phase 7

- [ ] [A] expire tự nhiên (sau TTL) → `webgw:session:{id}` và `webgw:oauth:{ossId}` đều bị xóa
- [ ] Explicit logout → [A1],[A2] không bị xóa lại lần 2 (DEL idempotent)
- [ ] Keyspace notification miss (test bằng cách disable rồi re-enable) → stale keys tự hết sau 24h

---

## Phase 8 — oauth2-service: IDP session expire handler (Scenario B)

> Mục tiêu: khi [B] expire, expire [F] + xóa [F] + xóa [C] + notify web-gateway.

| #  | Bước                                                                                              | Status |
|----|---------------------------------------------------------------------------------------------------|--------|
| 19 | Implement `IdpSessionExpiredListener` + `IdpSessionDeletedListener` — tách 2 Spring Session event | ✅ DONE |
| 20 | Logic: query `OAuthSession WHERE idpSessionId=?` → expire/revoke → delete [F] + [C]              | ✅ DONE |
| 21 | Outbox: `SessionExpiredEvent`/`SessionRevokedEvent` persist trong cùng transaction với delete      | ✅ DONE |
| 22 | HTTP back-channel: `POST /webgw/internal/sessions/revoke {ossId}` sau khi commit                  | ✅ DONE |
| 23 | identity-service `SessionExpiredConsumer` — set `login_activities.ended_at`                       | ✅ DONE |

### Verify Phase 8

- [ ] [B] expire (simulate bằng cách xóa record `SPRING_SESSION`) → [F] bị EXPIRED + xóa + `SessionExpiredEvent` fire
- [ ] identity-service nhận `SessionExpiredEvent` → `login_activities.ended_at` được set
- [ ] web-gateway nhận back-channel call → [A][A1][A2] bị xóa
- [ ] Không tìm thấy [F] cho `idpSessionId` (đã xóa trước) → chỉ skip, không lỗi

---

## Phase 9 — oauth2-service: Scheduled job handler skeleton (Scenario C)

> Mục tiêu: tạo handler cho [E] expire khi user không quay lại.
> **Job chưa được enable** — chỉ tạo handler và logic, config `enabled=false` cho đến khi chốt interval.

| #  | Bước                                                                                              | Status |
|----|---------------------------------------------------------------------------------------------------|--------|
| 24 | Tạo `ExpiredAuthorizationCleanupJob.java` — `@Scheduled`, mặc định **disabled**                   | ⬜ TODO |
| 25 | Logic: query `oauth2_authorization WHERE refresh_token_expires_at < now()` → find [F] → expire → delete | ⬜ TODO |
| 26 | Outbox `SessionExpiredEvent` + HTTP back-channel (tái dùng logic từ Phase 8)                      | ⬜ TODO |
| 27 | Config `app.job.expired-authorization-cleanup.enabled=false` — enable khi sẵn sàng               | ⬜ TODO |

```text
@Scheduled(fixedDelayString = "${app.job.expired-authorization-cleanup.interval-ms:3600000}")
@ConditionalOnProperty(name = "app.job.expired-authorization-cleanup.enabled", havingValue = "true")
public void run() { ... }
```

### Verify Phase 9 (chạy khi enable job)

- [ ] [E] expire + user không refresh → job chạy → [F] EXPIRED + xóa + event fire
- [ ] `oauth2_authorization` record bị xóa sau khi job chạy
- [ ] Edge case: `oauth2_authorization` expired nhưng [F] không tồn tại → chỉ xóa record, không fire event

---

## Verify tổng thể

- [ ] Login → [A][B][C][D][E][F][A1][A2] đều được tạo đúng
- [ ] Logout tường minh → [A][A1][A2] xóa, [F] REVOKED, `ended_at` set
- [ ] [A] expire → SSO (nếu [B] còn) → [F] cũ REVOKED, [F'] mới ACTIVE
- [ ] [B] expire → [F] EXPIRED + xóa, web-gateway cleanup
- [ ] F-002: không còn duplicate `SessionIssuedEvent` khi refresh
- [ ] Concurrent SSO: unique constraint đảm bảo không vi phạm invariant
- [ ] DB: `login_activities.session_id` được set khi login, `ended_at` được set khi session kết thúc

---

Status: ⬜ TODO · 🔄 IN PROGRESS · ✅ DONE · ⏸ BLOCKED
