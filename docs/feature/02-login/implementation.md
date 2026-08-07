# oauth2-service — Login Flow Implementation

**Framework reference**: [`spring-security-login-mfa-bff.md`](../../global/3.technical/spring-security-login-mfa-bff.md)  
**Domain design**: [`design.md`](design.md)  
**Session components**: [`service/oauth2-service/session.md`](../../service/oauth2-service/session.md)

> Chi tiết implement login flow, MFA, và session establishment trong `oauth2-service`.  
> Framework-level → file reference trên. Logout implementation → [`03-logout/logout-impl.md`](../03-logout/implementation).

---

## Hook Points

| Hook       | Class                                                | Fire khi                                                                                                            |
|------------|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| **Hook 0** | `LoginRateLimitFilter`                               | Trước `UsernamePasswordAuthenticationFilter` — chỉ scope đúng `POST /login`, chặn brute-force password             |
| **Hook 1** | `DeviceAwareAuthenticationSuccessHandler`            | Sau khi `UsernamePasswordAuthenticationFilter` (LOCAL) hoặc `OAuth2LoginAuthenticationFilter` (GOOGLE) thành công   |
| **Hook 2** | `SessionEstablishingAuthorizationService.save()`     | Mỗi lần Spring AS gọi `OAuth2AuthorizationService.save()` — wraps `JdbcOAuth2AuthorizationService`                  |
| **Hook 3** | `oidcLogoutHandler` (explicit) / session cleanup job | Logout: gọi explicit sau `session.invalidate()`. Expire: scheduled job (deferred). Không có session event listener. |

Hook 2 có 2 phase bên trong, được guard bằng condition riêng:

| Phase         | Condition                                        | Mục đích                                                                                                              |
|---------------|----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| **Phase 1.5** | `hasCode && !hasToken && oauthSessionId == null` | Bridge device signals từ browser context (HTTP session) vào `OAuth2Authorization.attributes` trước khi token exchange |
| **Phase 2**   | `hasToken && email != null`                      | Gọi `EstablishSession` sau khi token được issue                                                                       |

---

## 0. `LoginRateLimitFilter`

**Class**: `infrastructure/security/LoginRateLimitFilter`

`OncePerRequestFilter` đặt trước `UsernamePasswordAuthenticationFilter` trong `defaultSecurityFilterChain` (`SecurityConfiguration`). Chỉ can thiệp đúng `POST /login`:

```
doFilterInternal(request, response, chain):
  if method == POST && servletPath == "/login":
    username = request.getParameter("username")
    if !rateLimiter.tryAcquire("login_attempt:" + username, 10, Duration.ofMinutes(15)):
      redirect "/login?locked"
      return
  chain.doFilter(request, response)
```

Chặn **trước** khi request chạm `UsernamePasswordAuthenticationFilter` — vượt limit thì không tốn cả BCrypt compare lẫn DB lookup. Tính cả lần login đúng lẫn sai vào cùng quota (filter không biết kết quả auth, chỉ biết có request POST /login) — chấp nhận được vì 10 lần/15 phút hiếm khi chạm bởi user hợp lệ.

**Vì sao không đặt trong `OAuth2UserDetailsService.loadUserByUsername()`** (đã cân nhắc, bỏ): method đó bị OTT authentication provider gọi lại sau khi verify OTP đúng (xem mục 2) — đặt rate-limit ở đó sẽ tính trùng quota cho 1 lần login MFA, và áp nhầm lên bất kỳ caller nào khác gọi method này vì lý do không phải login. Filter riêng scope chính xác vào `POST /login`, tách biệt hoàn toàn khỏi lookup logic dùng chung.

**Vì sao không dùng `@RateLimit` annotation** (như `RegisterUser`): annotation đó chỉ pointcut vào `CommandHandler.handle()` — không chạm được filter/SPI method của Spring Security. Ném `RateLimitExceededException` map sẵn 429 qua REST exception handler cũng không khớp semantics ở đây (login flow kỳ vọng redirect, không phải JSON response).

---

## 1. `SocialLoginOidcUserService`

**Class**: `infrastructure/security/service/SocialLoginOidcUserService`

Sau khi Google xác nhận identity, class này bridge từ Google identity sang internal user model:

```
loadUser(userRequest)
  → OidcUserService.loadUser()          # delegate — gọi Google UserInfo endpoint
  → ResolveSocialUser.handle(email, fullName)
      ├─ findByEmail() → found:
      │     return { userId, locked, mfaEnabled }
      └─ not found:
            UserCredential.registerWithOAuth(newUserId, email, CUSTOMER, fullName)
            → UserRegisteredEvent → EventDispatcher → Outbox → Kafka
            return { userId, newAccount=true, mfaEnabled=false }
  → claims["sub"]           = systemUserId     # override Google sub
  → claims["app_mfa_enabled"] = true           # chỉ khi user đã bật MFA
  → authorities += FACTOR_AUTHORIZATION_CODE   # issuedAt = idToken.issuedAt
  → return DefaultOidcUser(enrichedAuthorities, enrichedToken, providerUserInfo)
```

**Role rule**: Google account mới luôn là `CUSTOMER`. Các role khác (seller, shipper, admin) có approval workflow riêng — không được tự register qua OAuth.

**Account linking**: Email đã tồn tại (user đăng ký credential trước) → `ResolveSocialUser` tìm thấy, trả về `existing.userId`. Google login được link vào cùng account.

**Account locked**: Nếu `result.locked() = true` → ném `OAuth2AuthenticationException("account_locked")`.

---

## 2. `UserCredentialDetails`

**Class**: `infrastructure/security/service/UserCredentialDetails`

| Field           | Giá trị                                    | Mục đích                               |
|------------------|----------------------------------------------|-------------------------------------------|
| `getUsername()` | `userId` (ULID)                            | JWT `sub` claim — principal identifier |
| `email`         | `UserCredential.email`                     | OTP delivery target, session attribute |
| `mfaEnabled`    | `UserCredential.mfaEnabled` (denormalized) | `MfaEnforcementFilter` gate            |

**Construct bởi**: `OAuth2UserDetailsService.loadUserByUsername(email)` — tìm `UserCredential` theo email, map sang `UserCredentialDetails`. Password = `""` cho OAuth user — không được verify trong OTT flow (OTT auth không check password).

**Context sau OTT auth**: Sau khi OTT verify thành công, principal chuyển từ `OidcUser`/`UserCredentialDetails` sang `UserCredentialDetails` mới do `OAuth2UserDetailsService.loadUserByUsername()` được gọi lại với email từ OTT token.

**Không đặt rate-limit trong `loadUserByUsername()`** dù có vẻ tiện (đã cân nhắc rồi bỏ) — method này bị OTT authentication provider gọi lại sau khi verify OTP đúng (dòng trên), nên rate-limit ở đây sẽ tính trùng quota cho 1 lần login MFA (2 lần gọi = 2 quota cho đúng 1 lần login), và áp nhầm lên bất kỳ caller nào khác gọi `loadUserByUsername()` trong tương lai vì lý do không phải login. Rate-limit brute-force password đặt riêng ở `LoginRateLimitFilter` (mục 0 trong Hook Points) — chỉ scope đúng `POST /login`, không đụng gì tới lookup logic ở đây.

---

## 3. `DeviceAwareAuthorizationRequestResolver`

**Class**: `infrastructure/security/oauth2/DeviceAwareAuthorizationRequestResolver`

Hook tại `GET /oauth2/authorization/google` — trước khi redirect sang Google.

**Logic**: Đọc cookie `dh` (device hash từ Angular browser fingerprint) → lưu vào `session["pre_auth_device_hash"]`.  
Sau khi Google callback về, `DeviceAwareAuthenticationSuccessHandler` đọc lại từ session.  
Xóa attribute này ngay trong success handler để tránh stale data.

---

## 4. `DeviceAwareAuthenticationSuccessHandler`

**Class**: `infrastructure/security/handler/DeviceAwareAuthenticationSuccessHandler`

Phase 1 trong device tracking — capture device signals, lưu vào HTTP session để `SessionEstablishingAuthorizationService` dùng ở Phase 1.5.

| Session attribute      | LOCAL path                                                | GOOGLE path                            |
|--------------------------|--------------------------------------------------------------|-------------------------------------------|
| `auth_email`           | `request.getParameter("username")`                        | `OidcUser.getEmail()`                  |
| `auth_device_hash`     | `DeviceAwareWebAuthenticationDetails.getDeviceHash()`     | `session["pre_auth_device_hash"]`      |
| `auth_user_agent`      | `DeviceAwareWebAuthenticationDetails.getUserAgent()`      | `request.getHeader("User-Agent")`      |
| `auth_accept_language` | `DeviceAwareWebAuthenticationDetails.getAcceptLanguage()` | `request.getHeader("Accept-Language")` |
| `auth_ip_address`      | `DeviceAwareWebAuthenticationDetails.getIpAddress()`      | `IpAddressExtractor.extract(request)`  |
| `auth_provider`        | `"LOCAL"`                                                 | `"GOOGLE"`                             |

`pre_auth_device_hash` bị `removeAttribute()` ngay sau khi copy xong.

Sau khi set session attributes → `super.onAuthenticationSuccess()` → redirect về saved request (tức là `/oauth2/authorize`).

---

## 5. `MfaEnforcementFilter`

**Class**: `infrastructure/security/mfa/MfaEnforcementFilter`

Per-user MFA gate đặt trước `AuthorizationFilter` trong AS filter chain (Order 1).

| Principal type          | Check                                  | Redirect nếu cần MFA                             |
|----------------------------|-------------------------------------------|--------------------------------------------------------|
| `UserCredentialDetails` | `userDetails.isMfaEnabled()`           | `true` và không có `FACTOR_OTT` → `/mfa`         |
| `OidcUser`              | `oidcUser.getClaim("app_mfa_enabled")` | `Boolean.TRUE` và không có `FACTOR_OTT` → `/mfa` |
| Khác                    | —                                      | pass through                                     |

Trước khi redirect: `requestCache.saveRequest()` → lưu `/oauth2/authorize?...` để restore sau OTT auth.

> **Cơ chế grant `FACTOR_OTT`**: đây là built-in behavior của Spring Security 7, không phải code tự viết. `OneTimeTokenAuthenticationProvider` tự động cộng `FactorGrantedAuthority` (`FACTOR_OTT`) vào `Authentication` ngay khi `consume()` (mục 6) thành công — tích luỹ thêm vào authorities hiện có (không replace `Authentication` cũ), đúng cơ chế "progressive factor" của Spring Security 7 MFA support. `@EnableMultiFactorAuthentication` (còn TODO trong `SecurityConfiguration.java`) chỉ cần nếu muốn Spring tự enforce qua authorization rule — không cần cho việc grant, vì `MfaEnforcementFilter` đã tự enforce thủ công rồi. TODO comment đó nên dọn vì gây hiểu nhầm (đã verify qua docs.spring.io/spring-security/reference/servlet/authentication/mfa.html).

---

## 6. `EmailOtpOneTimeTokenService`

**Class**: `infrastructure/security/mfa/EmailOtpOneTimeTokenService`

Session-based OTP storage. Ba session keys:

| Key                | Giá trị                                                    |
|-----------------------|---------------------------------------------------------------|
| `mfa_otp`          | 6 chữ số, `SecureRandom.nextInt(1_000_000)`, format `%06d` |
| `mfa_otp_username` | email của user (OTT username = email)                      |
| `mfa_otp_expiry`   | `Instant.now().plusSeconds(300).getEpochSecond()`          |

**`consume()`**: Không tự xóa OTP khi nhập sai, nhưng bị chặn bởi rate limit: `rateLimiter.tryAcquire("mfa_otp_verify:" + username, 5, Duration.ofMinutes(5))` — kiểm tra **sau** bước check expiry, **trước** bước so khớp giá trị OTP. Vượt 5 lần thử/5 phút (đúng bằng TTL của 1 OTP) → `invalidate(session)` (buộc phải request OTP mới qua link "Gửi lại OTP") + ném `BadCredentialsException`. Giới hạn 5 lần thay vì để mở tự do — trước đó OTP 6 số (1,000,000 khả năng) không có giới hạn số lần thử nào ngoài filter gateway chung (300/60s), cho phép tới ~1,500 lần đoán/1 cửa sổ OTP.

**`hasActiveToken(email)`**: Guard cho `MfaBridgeController` — trả về `true` nếu session đã có OTP còn hạn và đúng username. Dùng để tránh gửi email OTP trùng khi user mở nhiều tab.

---

## 7. `EmailOtpGenerationSuccessHandler`

**Class**: `infrastructure/security/mfa/EmailOtpGenerationSuccessHandler`

Delivery hook sau khi OTP được generate. Resolve identity từ principal (xử lý cả 2 type sau khi auth):

```
principal instanceof UserCredentialDetails → userId = getUserId(), email = getEmail()
principal instanceof OidcUser              → userId = getSubject(), email = getEmail()
```

Gọi `SendLoginOtp.handle(userId, email, token)`:

```
SendLoginOtp
  → LoginOtpService.requestOtp(userId, email, token)
      → new LoginOtpRequestedEvent(userId, email, token)
  → EventDispatcher.dispatch(event)
      → Outbox → Kafka (topic: oauth2.login.otp.requested)
      → notification-service consume → gửi OTP email
```

Redirect `/mfa/verify` sau khi dispatch.

---

## 8. `SessionEstablishingAuthorizationService`

**Class**: `infrastructure/security/oauth2/SessionEstablishingAuthorizationService`

Wraps `JdbcOAuth2AuthorizationService`. Hai phase hook trong lifecycle của `OAuth2Authorization`:

### Phase 1.5 — Authorization Code issued (có HTTP session)

**Trigger**: `save()` khi authorization có `OAuth2AuthorizationCode` nhưng chưa có `OAuth2AccessToken`.  
Đây là browser request — có HTTP session.

Resolve `oauth_session_id`:
- Tìm `OAuthSession` active theo `(idpSessionId, registeredClientId)`:
  - Tìm thấy → **silent SSO**: reuse existing `oauth_session_id`. EstablishSession sau đó chỉ gọi `onTokenRotated()`, không publish event.
  - Không tìm thấy → **fresh login**: generate ULID mới.
- Nếu `auth_email` null trong session (không có device tracking context) → chỉ attach `oauth_session_id`, không copy device signals.

Copy device signals từ HTTP session vào `OAuth2Authorization.attributes`:

| Attribute              | Nguồn                                  |
|--------------------------|-------------------------------------------|
| `oauth_session_id`     | Resolved hoặc new ULID                 |
| `auth_idp_session_id`  | `session.getId()` (SPRING_SESSION key) |
| `auth_email`           | `session["auth_email"]`                |
| `auth_device_hash`     | `session["auth_device_hash"]`          |
| `auth_user_agent`      | `session["auth_user_agent"]`           |
| `auth_accept_language` | `session["auth_accept_language"]`      |
| `auth_ip_address`      | `session["auth_ip_address"]`           |
| `auth_provider`        | `session["auth_provider"]`             |

### Phase 2 — Access Token issued (server-to-server)

**Trigger**: `save()` khi authorization có `OAuth2AccessToken` và `auth_email` trong attributes.  
Đây là server-to-server token exchange — không có HTTP session, dùng attributes đã bridge ở Phase 1.5.

Gọi `EstablishSession.handle(Command)`:
```
EstablishSession
  ├─ Fresh login: IssueSession
  │     → OAuthSession.issue(...)
  │     → SessionIssuedEvent → EventDispatcher → Outbox → Kafka
  └─ Silent SSO: OAuthSession.onTokenRotated()
                 → không publish event
```

> **Spring AS behavior (refresh token)**: Khi refresh, Spring AS gọi `OAuth2Authorization.from(existing)` — copy toàn bộ attributes kể cả `ATTR_OAUTH_SESSION_ID` và `auth_email`, sau đó UPDATE record cũ (không tạo mới). Condition Phase 2 `hasToken && email != null` vẫn `true` trên mỗi lần refresh. `EstablishSession.handle()` guard bằng early return khi `oldAuthorizationId == newAuthorizationId` — xem kịch bản 2 bên dưới.

---

## 9. `JwtTokenCustomizer`

**Class**: `infrastructure/security/oauth2/JwtTokenCustomizer`

Thêm claims vào JWT token:

**ACCESS_TOKEN**:
- `oss_id` — `OAuth2Authorization.getAttribute("oauth_session_id")` (ULID của `OAuthSession`). web-gateway dùng để lưu mapping session ↔ IDP session trong Redis khi logout.
- `roles` — list authorities từ granted authorities của principal.

**ID_TOKEN**:
- `oss_id` — giống access token, để web-gateway logout handler clean up Redis mapping.
- Third-party claims từ `OidcUser.getIdToken()` (Google claims) — merge vào, bỏ qua standard OIDC claims và claims đã có.

> `app_mfa_enabled` là claim nội bộ trong `DefaultOidcUser` (trong-memory) — không được copy vào ID token cuối cùng vì `ID_TOKEN_CLAIMS` filter loại bỏ `existingClaims` trước, và `app_mfa_enabled` không nằm trong `idToken.getClaims()` của Google.

### Redis key mapping — web-gateway

web-gateway lưu hai chiều mapping giữa Spring Session ([A]) và `OAuthSession` ([F]) bằng `oss_id` từ JWT:

| Key                             | Value           | TTL       | Mục đích                                            |
|-----------------------------------|-------------------|-------------|--------------------------------------------------------|
| `webgw:oauth:{oss_id}`          | `wg-session-id` | Fixed 24h | Tra ngược Spring Session từ `oss_id` khi logout     |
| `webgw:session:{wg-session-id}` | `oss_id`        | Fixed 24h | Tra ngược `oss_id` từ Spring Session để DEL cặp key |

Mapping được tạo bởi `SessionMappingAuthenticationSuccessHandler` sau khi code exchange thành công tại web-gateway. Cleanup khi logout → [`03-logout/logout-impl.md`](../03-logout/implementation).

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
    → OneTimeTokenService.generate(email) → session[mfa_otp] TTL 5 phút
    → user submit OTT → consume() atomic → Spring tự grant FACTOR_OTT (xem mục 5)
    → SecurityContext { FACTOR_PASSWORD (hoặc FACTOR_X509/OIDC tương đương), FACTOR_OTT }

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

## EstablishSession — 3 nhánh

`EstablishSession.handle()` là điểm phân kỳ trong Phase 2, quyết định action dựa trên state:

| Trạng thái      | Điều kiện                                            | Kết quả                                             |
|-------------------|---------------------------------------------------------|---------------------------------------------------------|
| **Refresh**     | `findById(ossId)` → found + `oldAuthId == newAuthId` | Early return — no-op hoàn toàn                      |
| **Silent SSO**  | `findById(ossId)` → found + `oldAuthId != newAuthId` | Xóa old auth + `onTokenRotated()` + upsert          |
| **Fresh login** | `findById(ossId)` → not found                        | Gọi `IssueSession` → tạo mới + `SessionIssuedEvent` |

---

## Session fixation

Session fixation protection rotate IDP session ID 2 lần trong fresh login:
- `as-sid-1 → as-sid-2` (sau password auth)
- `as-sid-2 → as-sid-3` (sau OTT auth)

`OAuthSession` chỉ được tạo sau khi token exchange hoàn thành (Phase 2) với `idpSessionId = as-sid-3`.
Khi as-sid-1 và as-sid-2 bị xóa do session fixation → không có `OAuthSession` nào link → Hook 3 là no-op hoàn toàn trong quá trình login.

---

## Hook fire matrix

| Hook / Phase                  | Fresh Login |  Refresh  | Silent SSO | Logout | Session Expire |
|----------------------------------|:-------------:|:-----------:|:------------:|:--------:|:-----------------:|
| Hook 1 — `AuthSuccessHandler` |      ✅      |     —     |     —      |   —    |       —        |
| Hook 2 — Phase 1.5            |      ✅      |     —     |     ✅      |   —    |       —        |
| Hook 2 — Phase 2              |      ✅      | ✅ (no-op) |     ✅      |   —    |       —        |
| Hook 3 — explicit logout      |      —      |     —     |     —      |   ✅    |       —        |
| Hook 3 — cleanup job          |      —      |     —     |     —      |   —    |  ✅ (deferred)  |

Kịch bản 4 (Logout) và 5 (Session Expire) → [`03-logout/logout-impl.md`](../03-logout/implementation).

---

## Session attributes — lifecycle

| Attribute              | Set bởi                                   | Xóa khi                                                   | Mục đích                             |
|---------------------------|----------------------------------------------|----------------------------------------------------------------|------------------------------------------|
| `pre_auth_device_hash` | `DeviceAwareAuthorizationRequestResolver` | `DeviceAwareAuthenticationSuccessHandler` (ngay sau copy) | Carry deviceHash qua Google redirect |
| `auth_email`           | `DeviceAwareAuthenticationSuccessHandler` | Session expire                                            | OTP delivery, Phase 1.5 bridge       |
| `auth_device_hash`     | `DeviceAwareAuthenticationSuccessHandler` | Session expire                                            | Device tracking                      |
| `auth_user_agent`      | `DeviceAwareAuthenticationSuccessHandler` | Session expire                                            | Device tracking                      |
| `auth_accept_language` | `DeviceAwareAuthenticationSuccessHandler` | Session expire                                            | Device tracking                      |
| `auth_ip_address`      | `DeviceAwareAuthenticationSuccessHandler` | Session expire                                            | Device tracking                      |
| `auth_provider`        | `DeviceAwareAuthenticationSuccessHandler` | Session expire                                            | `LOCAL` hoặc `GOOGLE`                |
| `mfa_otp`              | `EmailOtpOneTimeTokenService.generate()`  | `invalidate()` sau OTP đúng                               | OTP value                            |
| `mfa_otp_username`     | `EmailOtpOneTimeTokenService.generate()`  | `invalidate()` sau OTP đúng                               | OTP username (email)                 |
| `mfa_otp_expiry`       | `EmailOtpOneTimeTokenService.generate()`  | `invalidate()` sau OTP đúng                               | OTP expiry epoch second              |

---

## Domain Events published by oauth2-service (login path)

| Event class              | Kafka topic                  | Published khi                                            | Consumer                                            |
|------------------------------|---------------------------------|---------------------------------------------------------------|-----------------------------------------------------------|
| `UserRegisteredEvent`    | `oauth2.user.registered`     | `ResolveSocialUser` — new OAuth account                  | identity-service: tạo `UserAccount` (status=ACTIVE) |
| `LoginOtpRequestedEvent` | `oauth2.login.otp.requested` | `SendLoginOtp` — OTP generated                           | notification-service: gửi OTP email                 |
| `SessionIssuedEvent`     | `oauth2.session.issued`      | `EstablishSession` — fresh login (không phải silent SSO) | identity-service: record `DeviceLoginRecorded`      |
