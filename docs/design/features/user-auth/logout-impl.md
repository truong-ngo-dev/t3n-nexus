# oauth2-service & web-gateway — Chi tiết implement Logout & Remote Revocation

**Framework reference**: [`docs/architecture/spring-security-logout-bff.md`](../../../global/technical/spring-security-logout-bff.md)  
**Domain design**: [`design.md`](design.md)  
**Session components**: [`session-management.md`](session-management.md)

> File này mô tả **project-specific implementation** của từng customization point trong logout flow.  
> Framework-level → file reference trên. Session lifecycle chung → `session-management.md`.

---

## 1. `oidcLogoutHandler` (oauth2-service)

**Bean**: `AuthenticationSuccessHandler` wired vào `logoutEndpoint.logoutResponseHandler()`  
**Class**: `OAuth2AuthorizationServerConfig.oidcLogoutHandler()`

Được Spring AS gọi sau khi `OidcLogoutAuthenticationProvider` validate xong `id_token_hint`.  
Guard: chỉ chạy cleanup nếu principal đã authenticated VÀ có `sessionId` (tức là có IDP session hợp lệ).

```
oidcLogoutHandler(request, response, authentication):
  oidcLogoutAuth = (OidcLogoutAuthenticationToken) authentication

  if isPrincipalAuthenticated() && hasText(sessionId):
    SecurityContextLogoutHandler().logout(request, response, principal)
    // Spring Session JDBC 4.x removed SessionDeletedEvent support → explicit call
    result = endIdpSession.handle(Command(idpSessionId))
    result.ossIds().forEach(revocationClient::revoke)

  if isAuthenticated() && hasText(postLogoutRedirectUri):
    redirect(postLogoutRedirectUri + ?state=...) // URL-encoded
  else:
    redirect("/")
```

**Lưu ý**: `SecurityContextLogoutHandler().logout()` xóa `SecurityContext` khỏi IDP session và gọi `session.invalidate()`. Đây là lý do `idpSessionId` được lấy từ `OidcLogoutAuthenticationToken.getSessionId()` trước khi gọi logout — sau khi `invalidate()`, session không còn truy cập được.

---

## 2. `EndIdpSession` (oauth2-service)

**Class**: `application/session/end_idp_session/EndIdpSession`  
**Command**: `idpSessionId` (String)  
**Result**: `ossIds` (List\<String>)

```
EndIdpSession.handle(Command):
  sessions = oAuthSessionRepository.findAllByIdpSessionId(idpSessionId)
  if sessions.isEmpty() → log.debug + return Result([])

  for each session:
    oAuthSessionRepository.delete(session.getId())
    auth = oauth2AuthorizationService.findById(session.getAuthorizationId())
    if auth != null → oauth2AuthorizationService.remove(auth)
    ossIds.add(session.getId().getValueAsString())

  eventDispatcher.dispatchAll([
    SessionRevokedEvent(idpSessionId, ossIds, userId)  // 1 event cho toàn bộ sessions của IDP session
  ])
  return Result(ossIds)
```

**Invariant**: 1 IDP session + 1 client → 1 OAuthSession active (xem `session-management.md` mục 3).  
Trong thực tế, `sessions` thường có đúng 1 phần tử. Vòng lặp là defensive cho tính đúng đắn.

**Event**: `SessionRevokedEvent` mang `oauthSessionIds` là **list** — 1 event cover toàn bộ sessions của IDP session đó, không phát riêng per-session.

---

## 3. `WebGatewayRevocationClient` (oauth2-service)

**Class**: `infrastructure/adapter/http/WebGatewayRevocationClient`  
**Config**: `app.webgateway.base-url`

```text
restClient.post()
    .uri("/webgw/internal/sessions/revoke")
    .contentType(MediaType.APPLICATION_JSON)
    .body(Map.of("ossId", ossId))
    .retrieve()
    .toBodilessEntity();
```

Dùng `RestClient` (blocking) — gọi đồng bộ trong request thread của logout.  
Exception bị swallow với `log.warn` — failure không làm abort logout flow. Redis TTL 24h là fallback nếu back-channel fail.

---

## 4. `WebGatewayLogoutSuccessHandler` (web-gateway)

**Class**: `infrastructure/configuration/security/WebGatewayLogoutSuccessHandler`  
**Implements**: `ServerLogoutSuccessHandler`

```
onLogoutSuccess(exchange, authentication):
  ossId = ((OidcUser) authentication.getPrincipal()).getAttribute("oss_id")

  // Lookup springSessionId từ webgw:oauth:{ossId}
  springSessionId = redisTemplate.opsForValue().get("webgw:oauth:" + ossId)
  DEL "webgw:oauth:" + ossId
  DEL "webgw:session:" + springSessionId

  // Build end_session URL
  clientRegistration = clientRegistrationRepository.findByRegistrationId(registrationId)
  endSessionEndpoint  = clientRegistration.providerDetails.configurationMetadata["end_session_endpoint"]
                     // hoặc fallback sang config app.logout.end-session-uri
  idToken            = ((OidcUser) principal).getIdToken().getTokenValue()
  postLogoutUri      = expand(app.logout.post-redirect-uri, requestContext)

  url = endSessionEndpoint + "?id_token_hint={idToken}&post_logout_redirect_uri={postLogoutUri}"

  // 202 Accepted — Angular đọc Location header để navigate
  sendRedirect(exchange, url, HttpStatus.ACCEPTED)
```

`WebGatewayOAuth2RedirectStrategy` được dùng thay cho default redirect — trả 202 + `Location` header thay vì 302, để Angular SPA intercept và navigate thủ công.

**Fallback**: nếu principal không phải `OidcUser` hoặc không phải `OAuth2AuthenticationToken` → `RedirectServerLogoutSuccessHandler` redirect về logout success URL.

---

## 5. `SessionMappingCleanupListener` (web-gateway)

**Class**: `infrastructure/configuration/security/SessionMappingCleanupListener`  
**Trigger**: `RedisConfiguration.sessionExpiryContainer()` — keyspace "expired" event

Lua script thực thi atomic để tránh race condition giữa GET và DEL:

```lua
-- KEYS[1] = "webgw:session:{sessionId}"
local ossId = redis.call('GET', KEYS[1])
if ossId then
  redis.call('DEL', 'webgw:oauth:' .. ossId)
  redis.call('DEL', KEYS[1])
end
return ossId  -- trả về để caller log
```

**Không được gọi** trong user-initiated logout — `WebSession.invalidate()` gửi Redis "del" event, keyspace listener chỉ filter "expired".

---

## 6. `RedisConfiguration.sessionExpiryContainer()` (web-gateway)

**Bean**: `ReactiveRedisMessageListenerContainer`  
**Subscribe pattern**: `__keyspace@*__:spring:session:sessions:*`  
**Filter**: `message == "expired"` — bỏ qua "del", "set", v.v.

```text
container.receive(PatternTopic.of("__keyspace@*__:" + SESSION_KEY_PREFIX + "*"))
    .filter(msg -> "expired".equals(msg.getMessage()))
    .map(msg -> extractSessionId(msg.getChannel()))
    .flatMap(sessionId -> cleanupListener.cleanupBySessionId(sessionId)
        .onErrorResume(e -> Mono.empty()))
    .subscribe();
```

Session key prefix: `spring:session:sessions:` (default của `ReactiveRedisSessionRepository`).

**Yêu cầu Redis server**: `notify-keyspace-events Kx`

---

## 7. `SessionRevokeController` (web-gateway)

**Class**: `presentation/SessionRevokeController`  
**Endpoint**: `POST /webgw/internal/sessions/revoke`  
**Body**: `{ "ossId": "..." }`

```
RevokeRequest(ossId):
  oauthKey    = "webgw:oauth:" + ossId
  sessionId   = redisTemplate.opsForValue().get(oauthKey)

  sessionRepository.deleteById(sessionId)   // xóa Spring Session khỏi Redis ("del" event — không trigger keyspace listener)
  DEL "webgw:oauth:" + ossId
  DEL "webgw:session:" + sessionId

  return 200 OK
```

**Idempotent**: nếu `sessionId` null (mapping đã bị xóa trước) → `deleteById(null)` là no-op, DEL nonexistent key là no-op.

---

## 8. `JdbcOAuthSessionExpiryService` — orphaned session cleanup (oauth2-service)

**Class**: `infrastructure/adapter/service/JdbcOAuthSessionExpiryService`  
**Implements**: `OAuthSessionExpiryService`

```sql
-- Tìm oauth_sessions mà IDP session không còn trong SPRING_SESSION
SELECT os.id, os.authorization_id
FROM oauth_sessions os
LEFT JOIN SPRING_SESSION ss ON ss.SESSION_ID = os.idp_session_id
WHERE ss.SESSION_ID IS NULL
  AND os.idp_session_id IS NOT NULL
```

Batch DELETE `oauth2_authorization` + `oauth_sessions`, trả `SessionsBulkExpiredEvent(ossIds)`.

Không đi qua aggregate lifecycle (`OAuthSession.expire()`) vì đây là bulk cleanup — per-session event không phù hợp cho path này.

> **⚠ Chưa có caller**: `expireOrphaned()` được implement nhưng chưa có `@Scheduled` job gọi nó. Cần implement caller hoàn chỉnh — xem mục 9.

---

## 9. Session cleanup job — chưa implement

**Deferred**: xem [`deferred.md`](deferred.md) mục 6.

Job cần implement đầy đủ:

```
@Scheduled(...)
@ConditionalOnProperty("app.job.expired-authorization-cleanup.enabled")
cleanupJob():
  event = oAuthSessionExpiryService.expireOrphaned()
  if event.oauthSessionIds().isEmpty() → return

  eventDispatcher.dispatch(event)  // SessionsBulkExpiredEvent → outbox → Kafka

  event.oauthSessionIds().forEach(ossId ->
    revocationClient.revoke(ossId)  // back-channel → web-gateway cleanup
  )
```

Lưu ý: `revoke(ossId)` per-session sau bulk delete là sequential HTTP calls. Acceptable vì job chạy ngoài request thread.

---

## Domain Events

| Event                      | Kafka topic                   | Published khi                                      | Fields                                             |
|----------------------------|-------------------------------|----------------------------------------------------|----------------------------------------------------|
| `SessionRevokedEvent`      | `oauth2.session.revoked`      | `EndIdpSession` — explicit logout                  | `idpSessionId`, `oauthSessionIds` (list), `userId` |
| `SessionsBulkExpiredEvent` | `oauth2.session.expired.bulk` | Session cleanup job — orphaned sessions (deferred) | `oauthSessionIds` (list)                           |

Consumer: identity-service — set `login_activities.ended_at` cho các `session_id` trong list.

---

## Session attributes — lifecycle trong logout

| Attribute              | Tạo bởi                          | Đọc bởi                          | Xóa khi                                       |
|------------------------|----------------------------------|----------------------------------|-----------------------------------------------|
| `oss_id`               | `JwtTokenCustomizer` → JWT claim | `WebGatewayLogoutSuccessHandler` | Không store trong session — đọc từ `OidcUser` |
| `WEBGW_SESSION` cookie | Spring Session tạo sau login     | Browser gửi theo mọi request     | `WebSession.invalidate()` hoặc cookie expire  |
