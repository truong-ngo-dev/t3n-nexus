# Spring Security 7 — BFF Logout & Remote Revocation

**Status**: Implementation Reference  
**Applies to**: `oauth2-service` (Spring Authorization Server 7.0.5), `web-gateway` (Spring Cloud Gateway)  
**Spring Boot**: 4.0.x | **Spring Security**: 7.0.3 | **Spring Authorization Server**: 7.0.5

> Tài liệu này ở mức **framework** — không chứa business logic.  
> Domain-specific flow → `docs/design/features/user-login/logout-impl.md`.

---

## Components

```
oauth2-service   # Spring AS — end_session_endpoint, IDP session management
web-gateway      # Spring Cloud Gateway (WebFlux reactive) — OAuth2 Client, BFF session store
```

---

## OIDC Logout Endpoints

| Endpoint                               | Service        | Trigger                                                                                     |
|----------------------------------------|----------------|---------------------------------------------------------------------------------------------|
| `GET /connect/logout`                  | oauth2-service | web-gateway navigate sau khi local logout, với `id_token_hint` + `post_logout_redirect_uri` |
| `POST /webgw/internal/sessions/revoke` | web-gateway    | Custom back-channel từ oauth2-service sau khi IDP session bị xóa                            |

`/connect/logout` được expose tự động bởi `OidcLogoutEndpointFilter` khi OIDC provider config được bật.  
`/webgw/internal/sessions/revoke` là custom endpoint — không phải OIDC Back-Channel Logout spec.

---

## Core Framework Classes

### oauth2-service (Spring AS)

| Class                                       | Role                                                                              |
|---------------------------------------------|-----------------------------------------------------------------------------------|
| `OidcLogoutEndpointFilter`                  | Route và xử lý `GET /connect/logout` — validate, orchestrate logout handler chain |
| `OidcLogoutAuthenticationConverter`         | Extract `id_token_hint`, `post_logout_redirect_uri`, `state` từ request           |
| `OidcLogoutAuthenticationProvider`          | Validate `id_token_hint`: xác nhận token đúng với client và principal session     |
| `SecurityContextLogoutHandler`              | Xóa `SecurityContext` khỏi session + gọi `session.invalidate()`                   |
| `AuthorizationRevokingLogoutSuccessHandler` | Redirect về `post_logout_redirect_uri` sau khi logout handler chain hoàn thành    |

Spring Session JDBC không có cơ chế event đáng tin cậy tương đương Redis keyspace notification. Cleanup domain objects phải thực hiện **explicit** trong logout handler — không dựa vào `SessionDeletedEvent` / `SessionExpiredEvent`. Orphaned sessions khi [B] expire tự nhiên cần **scheduled job** xử lý riêng.

### web-gateway (Spring Cloud Gateway / WebFlux)

| Class                                       | Role                                                                             |
|---------------------------------------------|----------------------------------------------------------------------------------|
| `LogoutWebFilter`                           | Route `POST /webgw/auth/logout` vào logout handler chain                         |
| `SecurityContextServerLogoutHandler`        | Xóa `SecurityContext` + invalidate `WebSession` → xóa session khỏi Redis         |
| `ReactiveRedisMessageListenerContainer`     | Subscribe Redis pub/sub — lắng nghe keyspace "expired" events cho session keys   |
| `WebSessionServerSecurityContextRepository` | Đọc/ghi `SecurityContext` từ `WebSession` (backed by Spring Session Redis)       |

Reactive Redis (WebFlux stack) không có Spring Session event mechanism như Servlet stack — không có `HttpSessionEventPublisher`. web-gateway subscribe trực tiếp Redis keyspace "expired" events qua `ReactiveRedisMessageListenerContainer` (`notify-keyspace-events Kx`).

`OidcClientInitiatedServerLogoutSuccessHandler` **không được dùng** — web-gateway dùng custom handler trả **202 Accepted + Location header** để Angular SPA navigate thủ công sang AS logout endpoint.

---

## Customization Points

### oauth2-service — Custom logout handler (explicit cleanup)
**Wired vào**: logout handler chain sau `SecurityContextLogoutHandler`

Thực hiện explicit cleanup ngay trong request thread — không dựa vào session events:
- Xóa domain objects liên quan đến IDP session vừa bị invalidate ([F] + [C])
- Gọi back-channel để web-gateway xóa BFF session và mapping
- Sau cleanup: delegate sang `AuthorizationRevokingLogoutSuccessHandler` để redirect

### oauth2-service — Session cleanup job
**Type**: `@Scheduled` background job

Xử lý orphaned sessions — trường hợp [B] expire tự nhiên mà không có explicit logout:
- Tìm domain objects [F] không còn authorization [C] valid
- Xóa [F] + [C], gọi back-channel → web-gateway cleanup
- Cần config `enabled=false` mặc định — enable khi sẵn sàng và đã chốt interval

### web-gateway — `WebGatewayLogoutSuccessHandler`
**Implements**: `ServerLogoutSuccessHandler`

Thực hiện sau khi `LogoutWebFilter` xóa `WebSession`:
- Xóa session ↔ OAuthSession mapping trong Redis ([A1],[A2])
- Trả **202 Accepted + `Location: {end_session_url}`** — Angular đọc và navigate sang AS logout endpoint

Lấy `OidcIdToken` từ `OidcUser` để compose `id_token_hint` trong URL. Nếu session đã expire trước khi logout, `id_token_hint` không có sẵn → fallback redirect, không gọi AS.

### web-gateway — `SessionMappingCleanupListener`
**Trigger**: Redis keyspace "expired" event khi session [A] TTL expire

Cleanup session ↔ OAuthSession mapping ([A1],[A2]) khi web-gateway session expire tự nhiên.  
**Không được gọi** trong user-initiated logout — `WebSession.invalidate()` gửi Redis "del" event, không phải "expired", nên keyspace listener không fire.  
Dùng atomic operation (Lua script) để tránh race condition.

### web-gateway — `SessionRevokeController`
**Endpoint**: `POST /webgw/internal/sessions/revoke`

Nhận back-channel call từ oauth2-service. Xóa BFF session [A] và mapping [A1],[A2] theo OAuthSession ID.

### `WebGatewayRevocationClient` (oauth2-service)
**Calls**: `POST /webgw/internal/sessions/revoke`  
**Timing**: Sau khi cleanup [F],[C] hoàn thành

Custom back-channel — không dùng Kafka vì explicit revoke cần immediate invalidation và web-gateway không cần full Kafka setup.

---

## Storage Cleanup Sequence

### User-initiated logout

| Step | What                                                     | Who                                                        |
|------|----------------------------------------------------------|------------------------------------------------------------|
| 1    | `SecurityContext` + session [A]                          | `SecurityContextServerLogoutHandler` tại web-gateway       |
| 2    | Session ↔ OAuthSession mapping [A1],[A2]                 | `WebGatewayLogoutSuccessHandler` tại web-gateway           |
| 3    | IDP Session [B]                                          | `SecurityContextLogoutHandler.session.invalidate()` tại AS |
| 4    | `OAuthSession` [F] + `OAuth2Authorization` [C]           | Custom logout handler → explicit cleanup tại AS            |
| 5    | Session [A] + mapping [A1],[A2] (safety net, idempotent) | `WebGatewayRevocationClient.revoke()` → back-channel       |

Step 1–2 xảy ra tại web-gateway (202 response). Angular navigate → Step 3–5 xảy ra tại AS trong cùng request thread.

### web-gateway session [A] TTL expire

| Step | What              | Who                                      |
|------|-------------------|------------------------------------------|
| 1    | Session [A]       | Redis TTL → "expired" keyspace event     |
| 2    | Mapping [A1],[A2] | `SessionMappingCleanupListener` (atomic) |

IDP session [B] tồn tại độc lập — cleanup qua scheduled job khi [B] expire.

### IDP session [B] expire / orphaned [F]

| Step | What                                           | Who                                                  |
|------|------------------------------------------------|------------------------------------------------------|
| 1    | IDP Session [B]                                | Spring Session JDBC cleanup task                     |
| 2    | `OAuthSession` [F] + `OAuth2Authorization` [C] | Session cleanup job tại oauth2-service               |
| 3    | Session [A] + mapping [A1],[A2] (nếu còn)      | `WebGatewayRevocationClient.revoke()` → back-channel |

---

## Logout Paths

### Path A — User-initiated (RP-Initiated Logout)

```
Angular → POST /webgw/auth/logout

[web-gateway]
  LogoutWebFilter
    → SecurityContextServerLogoutHandler: WebSession.invalidate()  [A] deleted (Redis "del")
    → WebGatewayLogoutSuccessHandler:
        → clean up session ↔ OAuthSession mapping                  [A1][A2] deleted
        → 202 Accepted + Location: /connect/logout?id_token_hint=...&post_logout_redirect_uri=...

Angular: đọc Location header → navigate to end_session_url

[oauth2-service]
  GET /connect/logout?id_token_hint=...&post_logout_redirect_uri=...
    → OidcLogoutEndpointFilter
        → OidcLogoutAuthenticationConverter: extract params
        → OidcLogoutAuthenticationProvider: validate id_token_hint
    → SecurityContextLogoutHandler: session.invalidate()           [B] deleted
    → Custom logout handler (explicit — không dùng session events):
        → EndIdpSession.handle(sessionId, REVOKED):
            → DELETE OAuthSession                                   [F] deleted
            → remove OAuth2Authorization                            [C] deleted
            → SessionRevokedEvent → outbox → Kafka
        → WebGatewayRevocationClient.revoke(ossId):
            → POST /webgw/internal/sessions/revoke                 [A][A1][A2] cleanup (idempotent)
    → AuthorizationRevokingLogoutSuccessHandler
        → 302 post_logout_redirect_uri

Angular: nhận 302, navigate to post_logout_redirect_uri
```

### Path B — web-gateway session [A] TTL expire

```
[web-gateway — reactive background stream]
  ReactiveRedisSessionRepository: session hash TTL expires
    → Redis "expired" keyspace event for session key

  RedisConfiguration.sessionExpiryContainer():
    → filter message == "expired"
    → extract sessionId
    → SessionMappingCleanupListener.cleanupBySessionId(sessionId):
        → atomic: look up OAuthSession ID from mapping  →  clean up [A1][A2]

IDP session [B] còn hiệu lực độc lập — cleanup qua Path C.
```

### Path C — Orphaned sessions (IDP session [B] expire)

```
[oauth2-service — background thread]
  Spring Session JDBC cleanup task:
    → DELETE expired records from SPRING_SESSION              [B] deleted

[oauth2-service — scheduled job]
  Session cleanup job:
    → find [F] WHERE no valid [C] exists
    → for each orphaned [F]:
        → EndIdpSession.handle(sessionId, EXPIRED):
            → DELETE OAuthSession                             [F] deleted
            → remove OAuth2Authorization                      [C] deleted
            → SessionExpiredEvent → outbox → Kafka
        → WebGatewayRevocationClient.revoke(ossId):
            → POST /webgw/internal/sessions/revoke           [A][A1][A2] cleanup if still alive
```

---

## Lưu ý quan trọng

- Cleanup [F],[C] tại oauth2-service **không dựa vào session events** — phải explicit. Spring Session JDBC không có cơ chế event đáng tin cậy tương đương Redis keyspace notification cho cleanup use case. Orphaned sessions (khi [B] expire mà không có explicit logout) xử lý bởi scheduled job.
- `SecurityContextLogoutHandler.session.invalidate()` tại AS xảy ra trong cùng request thread **trước** custom cleanup handler — [B] đã bị xóa khi [F][C] được cleanup.
- `WebGatewayRevocationClient.revoke()` là HTTP call đồng bộ trên critical path logout tại AS. Nếu web-gateway chậm hoặc down, logout vẫn hoàn thành nhưng [A][A1][A2] không được xóa ngay. Redis TTL (24h) là fallback.
- Nếu web-gateway session [A] expire trước khi user click logout, `WebGatewayLogoutSuccessHandler` không có `OidcUser` → không gọi được AS. [B][F][C] không được cleanup ngay — chỉ scheduled job (Path C) xử lý sau.
- `SessionMappingCleanupListener` chỉ trigger khi session TTL expire (Redis "expired") — không trigger khi explicit `WebSession.invalidate()` (Redis "del"). Đây là lý do Path A và Path B dùng 2 cơ chế cleanup khác nhau.
- Back-channel `POST /webgw/internal/sessions/revoke` không phải OIDC Back-Channel Logout spec. Internal service-to-service call — security model dựa trên network isolation.
