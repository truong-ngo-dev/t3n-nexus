# Business Hook Lifecycle — OAuth2 Session

**Diagram**: [`business-hook-sequence.puml`](business-hook-sequence.puml)  
**Liên quan**: [`session-invalidation.md`](session-invalidation.md) · [`findings.md`](findings.md)  
**Cập nhật**: 2026-06-07

> Tài liệu này mô tả cách business logic được enforce tại 3 hook points trong Spring AS/Security flow,
> gồm 5 kịch bản: Fresh Login, Refresh Token, Silent SSO, Logout, Session Expire.

---

## Hook Points

| Hook       | Class                                                     | Fire khi                                                                                                          |
|------------|-----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| **Hook 1** | `DeviceAwareAuthenticationSuccessHandler`                 | Sau khi `UsernamePasswordAuthenticationFilter` (LOCAL) hoặc `OAuth2LoginAuthenticationFilter` (GOOGLE) thành công |
| **Hook 2** | `AuditingOAuth2AuthorizationService.save()`               | Mỗi lần Spring AS gọi `OAuth2AuthorizationService.save()` — bọc ngoài `JdbcOAuth2AuthorizationService`            |
| **Hook 3** | `IdpSessionDeletedListener` / `IdpSessionExpiredListener` | Khi IDP session bị xóa (logout, revoke) hoặc hết TTL                                                              |

Hook 2 có 2 phase bên trong, được guard bằng condition riêng:

| Phase         | Condition                                        | Mục đích                                                                                                              |
|---------------|--------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| **Phase 1.5** | `hasCode && !hasToken && oauthSessionId == null` | Bridge device signals từ browser context (HTTP session) vào `OAuth2Authorization.attributes` trước khi token exchange |
| **Phase 2**   | `hasToken && email != null`                      | Gọi `EstablishSession` sau khi token được issue                                                                       |

---

## Kịch bản 1 — Fresh Login

```
[Hook 1] DeviceAwareAuthenticationSuccessHandler.onAuthenticationSuccess()
  ↓  Sau khi password verified (LOCAL) / OIDC callback (GOOGLE)
  → ghi vào IDP HTTP session:
      auth_email, auth_device_hash, auth_user_agent,
      auth_accept_language, auth_ip_address, auth_provider

[Session fixation] as-sid-1 → as-sid-2  (sau password auth)

  → MFA: OTT flow qua MfaBridgeController (nếu mfaEnabled = true)
    → OneTimeTokenService.generate(email) → Redis ott:{token} TTL 5 phút
    → user submit OTT → GETDEL atomic → SecurityContext { PASSWORD_AUTHORITY, OTT_AUTHORITY }

[Session fixation] as-sid-2 → as-sid-3  (sau OTT auth)

[Hook 2 — Phase 1.5]  Authorization code issued
  ↓  hasCode && !hasToken && oauthSessionId == null
  → findActiveByIdpSessionAndClient(as-sid-3, "web-gateway")
      → EMPTY (lần đầu login)
  → generate ULID mới → ossId-NEW
  → copy device signals từ HTTP session as-sid-3 → OAuth2Authorization.attributes
  → delegate.save(authorizationWithCode + ossId-NEW + device_attrs)

web-gateway exchanges code → POST /oauth2/token

[Hook 2 — Phase 2]  Access token issued
  ↓  hasToken && email != null
  → delegate.save(authorizationWithToken)
  → EstablishSession.handle(ossId-NEW, as-sid-3, newAuthorizationId, ...)
      → findById(ossId-NEW) → NOT FOUND
      → IssueSession.handle(ossId-NEW, ...)
          → findActiveByIdpSessionAndClient(as-sid-3, "web-gateway") → EMPTY
          → OAuthSession.issue(ossId-NEW) → SessionIssuedEvent
          → INSERT oauth_sessions

JwtTokenCustomizer: oss_id = ossId-NEW, roles, kid → JWT signed

SessionIssuedEvent → outbox → Kafka
  → identity-service: RecordLoginSession
      → Device.register() / recordActivity()
      → LoginActivity.recordSuccess(sessionId = ossId-NEW)
```

---

## Kịch bản 2 — Refresh Token

> Spring AS **không tạo** `OAuth2Authorization` mới khi refresh — cập nhật in-place với cùng `id`.
> Xem [F-001](findings.md).

```
web-gateway → POST /oauth2/token {grant_type=refresh_token}
Spring AS: UPDATE OAuth2Authorization (same id, new token values)

[Hook 2 — Phase 1.5]  SKIP
  ↓  ATTR_OAUTH_SESSION_ID != null → condition false

[Hook 2 — Phase 2]  hasToken && email != null
  → delegate.save(authorization)
  → EstablishSession.handle(ossId-EXISTING, ..., sameAuthorizationId)
      → findById(ossId-EXISTING) → FOUND
      → oldAuthorizationId == sameAuthorizationId → EARLY RETURN (no-op)

Kết quả: không có DB write, không có event, không có Kafka message.
LoginActivity không thay đổi.
```

---

## Kịch bản 3 — Silent SSO

> Xảy ra khi web-gateway session [A] hết hạn nhưng IDP session [B] còn hiệu lực
> với `{ PASSWORD_AUTHORITY, OTT_AUTHORITY }`.
> `OAuth2Authorization` mới được tạo (new code flow), `OAuth2Authorization.id` là UUID mới.

```
web-gateway session hết hạn → 302 /oauth2/authorize
Spring AS: load IDP session as-sid-3 → fully authenticated → issue code ngay

[Hook 2 — Phase 1.5]  Authorization code issued
  ↓  hasCode && !hasToken && oauthSessionId == null
  → findActiveByIdpSessionAndClient(as-sid-3, "web-gateway")
      → FOUND: OAuthSession(ossId-EXISTING)
      → [log: silent SSO detected]
  → reuse ossId-EXISTING (không generate ULID mới)
  → copy device signals từ HTTP session as-sid-3 (attrs vẫn còn từ lần login gốc)
  → delegate.save(newAuthorizationWithCode + ossId-EXISTING)

web-gateway exchanges code → POST /oauth2/token

[Hook 2 — Phase 2]  Access token issued
  → EstablishSession.handle(ossId-EXISTING, ..., NEW_authorizationId)
      → findById(ossId-EXISTING) → FOUND
      → oldAuthorizationId != NEW_authorizationId
          → oauth2AuthorizationService.remove(oldAuth)  ← xóa token cũ
          → session.onTokenRotated(NEW_authorizationId)
          → UPSERT oauth_sessions SET authorization_id = NEW_authorizationId

JwtTokenCustomizer: oss_id = ossId-EXISTING  ← stable, không đổi

Kết quả: không có SessionIssuedEvent, không có Kafka message.
LoginActivity không thay đổi — silent SSO không tạo bản ghi mới.
oss_id trong JWT giữ nguyên → web-gateway mapping [A1][A2] vẫn valid.
```

---

## Kịch bản 4 — Logout

> Trigger: web-gateway gọi OIDC logout endpoint `GET /connect/logout`.

```
GET /connect/logout?id_token_hint=...&post_logout_redirect_uri=...

Spring AS: OidcLogoutAuthenticationProvider validate request
  → oidcLogoutHandler:
      SecurityContextLogoutHandler.logout()
        → session.invalidate() — IDP session as-sid-3

[Hook 3]  SessionDeletedEvent(sessionId = as-sid-3)
  → IdpSessionDeletedListener → EndIdpSession.handle(as-sid-3, Reason.REVOKED)
      → findAllByIdpSessionId(as-sid-3) → [OAuthSession(ossId-EXISTING)]
      → DELETE oauth_sessions WHERE id = ossId-EXISTING
      → oauth2AuthorizationService.remove(auth)  ← xóa OAuth2Authorization
      → SessionRevokedEvent([ossId-EXISTING], as-sid-3, userId)
      → outbox → Kafka
  → WebGatewayRevocationClient.revoke(ossId-EXISTING)
      → POST /webgw/internal/sessions/revoke
        → web-gateway: DEL spring:session:{wg-sid} + DEL webgw:oauth:{ossId-EXISTING}

AuthorizationRevokingLogoutSuccessHandler → 302 post_logout_redirect_uri

SessionRevokedEvent → identity-service: CloseLoginSession
  → UPDATE login_activities SET ended_at = now()
     WHERE session_id IN [ossId-EXISTING]
```

**Lưu ý**: `WebGatewayRevocationClient.revoke()` là HTTP call đồng bộ, nằm trên critical path của logout.
Nếu web-gateway chậm/down, logout vẫn hoàn thành ở oauth2-service nhưng web-gateway session không được xóa ngay.

---

## Kịch bản 5 — Session Expire

> Trigger: Spring Session JDBC cleanup job phát hiện IDP session as-sid-3 vượt TTL.

```
Spring Session JDBC background task → DELETE SPRING_SESSION WHERE expiry_time <= now()

[Hook 3]  SessionExpiredEvent(sessionId = as-sid-3)
  → IdpSessionExpiredListener → EndIdpSession.handle(as-sid-3, Reason.EXPIRED)
      → findAllByIdpSessionId(as-sid-3) → [OAuthSession(ossId-EXISTING)]
      → DELETE oauth_sessions WHERE id = ossId-EXISTING
      → oauth2AuthorizationService.remove(auth)
      → SessionExpiredEvent([ossId-EXISTING], as-sid-3, userId)
      → outbox → Kafka
  → WebGatewayRevocationClient.revoke(ossId-EXISTING)
      → POST /webgw/internal/sessions/revoke
        → web-gateway: DEL spring:session:{wg-sid} (nếu còn) + DEL webgw:oauth:{ossId-EXISTING}

SessionExpiredEvent → identity-service: CloseLoginSession
  → UPDATE login_activities SET ended_at = now()
     WHERE session_id IN [ossId-EXISTING]
```

---

## Hook fire matrix

| Hook / Phase | Fresh Login | Refresh | Silent SSO | Logout | Session Expire |
|---|:---:|:---:|:---:|:---:|:---:|
| Hook 1 — `AuthSuccessHandler` | ✅ | — | — | — | — |
| Hook 2 — Phase 1.5 | ✅ | — | ✅ | — | — |
| Hook 2 — Phase 2 | ✅ | ✅ (no-op) | ✅ | — | — |
| Hook 3 — `SessionDeletedListener` | — | — | — | ✅ | — |
| Hook 3 — `SessionExpiredListener` | — | — | — | — | ✅ |

---

## EstablishSession — 3 nhánh

`EstablishSession.handle()` là điểm phân kỳ trong Phase 2, quyết định action dựa trên state:

| Trạng thái | Điều kiện | Kết quả |
|------------|-----------|---------|
| **Refresh** | `findById(ossId)` → found + `oldAuthId == newAuthId` | Early return — no-op hoàn toàn |
| **Silent SSO** | `findById(ossId)` → found + `oldAuthId != newAuthId` | Xóa old auth + `onTokenRotated()` + upsert |
| **Fresh login** | `findById(ossId)` → not found | Gọi `IssueSession` → tạo mới + `SessionIssuedEvent` |

---

## Session fixation và Hook 3

Session fixation protection rotate IDP session ID 2 lần trong fresh login:
- `as-sid-1 → as-sid-2` (sau password auth)
- `as-sid-2 → as-sid-3` (sau OTT auth)

Khi session cũ bị xóa, `SessionDeletedEvent` fire → `IdpSessionDeletedListener` chạy.
Tuy nhiên, `OAuthSession` chỉ được tạo sau khi token exchange hoàn thành (Phase 2) với `idpSessionId = as-sid-3`.
Nên `findAllByIdpSessionId(as-sid-1)` và `findAllByIdpSessionId(as-sid-2)` đều trả về empty
→ Hook 3 là no-op trong session fixation, không ảnh hưởng gì.
