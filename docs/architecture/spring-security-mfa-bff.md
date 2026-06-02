# Spring Security 7 — BFF Login Flow with MFA (Framework Reference)

**Status**: Reference  
**Applies to**: `oauth2-service` (Spring Authorization Server), `web-gateway` (Spring Cloud Gateway)  
**Spring Boot**: 4.x | **Spring Security**: 7.x  
**Diagram**: [`spring-security-mfa-bff.puml`](spring-security-mfa-bff.puml)

> Tài liệu này ở mức **framework thuần** — không chứa domain logic của dự án.  
> Domain-specific design (MfaConfig entity, event publishing, device tracking...) → `docs/design/features/user-login/design.md`.

---

## Components

```
oauth2-service   # Spring Authorization Server
web-gateway      # Spring Cloud Gateway — đóng vai OAuth2 Client (BFF pattern)
```

`registrationId` = tên client đăng ký trong oauth2-service (ví dụ: `web-gateway`)

---

## Core MFA Classes — Spring Security 7

| Class / Annotation                            | Role                                                                                    |
|-----------------------------------------------|-----------------------------------------------------------------------------------------|
| `@EnableMultiFactorAuthentication`            | Enable MFA globally — yêu cầu tất cả listed authorities trên mọi request                |
| `FactorGrantedAuthority`                      | Đại diện 1 authentication factor — mang `issuedAt` timestamp để enforce `validDuration` |
| `RequiredAuthoritiesRepository`               | Interface: per-user required factors — implement với DB/Redis/in-memory                 |
| `RequiredAuthoritiesAuthorizationManager`     | Wrap `RequiredAuthoritiesRepository` thành `AuthorizationManager`                       |
| `OneTimeTokenService`                         | Interface: OTT generate + consume — **phải tự implement, không có default**             |
| `GeneratedOneTimeTokenHandler`                | Hook: gửi OTT đến user sau khi generate (email/SMS/push)                                |
| `AuthorizationManagerFactories.multiFactor()` | Builder cho selective MFA per endpoint                                                  |
| `AllAuthoritiesAuthorizationManager`          | Yêu cầu tất cả listed authorities có mặt trong SecurityContext                          |

### `FactorGrantedAuthority` — predefined constants

| Constant                                              | Ý nghĩa                                               |
|-------------------------------------------------------|-------------------------------------------------------|
| `FactorGrantedAuthority.PASSWORD_AUTHORITY`           | First factor — user submit đúng password              |
| `FactorGrantedAuthority.OTT_AUTHORITY`                | Second factor — user verify One-Time Token thành công |
| `FactorGrantedAuthority.X509_AUTHORITY`               | Client certificate authentication                     |
| `FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY` | OAuth2 Authorization Code flow                        |

---

## Storage Overview

| Location               | Key / Table           | Content                                           | TTL                 |
|------------------------|-----------------------|---------------------------------------------------|---------------------|
| Redis (web-gateway)    | `spring:session:{id}` | OAuth2AuthorizedClient, SecurityContext           | session timeout     |
| Redis (web-gateway)    | `webgw:oauth:{sid}`   | spring_session_id mapping (logout/revoke)         | session timeout     |
| DB (oauth2-service)    | `SPRING_SESSION`      | auth session — SecurityContext, authorize_context | configurable        |
| DB (oauth2-service)    | `OAuth2Authorization` | access_token, refresh_token, id_token             | token lifetime      |
| Redis (oauth2-service) | `ott:{token}`         | username — custom `OneTimeTokenService` impl      | 5 min (recommended) |

> `ott:{token}` là key do **custom implementation** quản lý — Spring Security chỉ define interface `OneTimeTokenService`.  
> `GETDEL` (atomic) đảm bảo mỗi OTT chỉ consume được đúng 1 lần kể cả concurrent requests.

---

## Web-gateway Session Lifecycle

| Thời điểm                         | Hành động                                      | Session chứa                               |
|-----------------------------------|------------------------------------------------|--------------------------------------------|
| Truy cập lần đầu chưa có session  | Tạo mới                                        | state, nonce, code_verifier, saved_request |
| Sau exchange token thành công     | Tạo mới (rotate — session fixation protection) | OAuth2AuthorizedClient, SecurityContext    |
| Đang dùng bình thường             | Giữ nguyên                                     | OAuth2AuthorizedClient, SecurityContext    |
| Token hết hạn, refresh thành công | Giữ nguyên, update token                       | OAuth2AuthorizedClient mới                 |
| Refresh token hết hạn             | Xóa                                            | —                                          |
| Logout                            | Xóa                                            | —                                          |

---

## Auth Session Lifecycle — oauth2-service (với MFA)

| Thời điểm                               | Hành động                              | Session chứa                                        |
|-----------------------------------------|----------------------------------------|-----------------------------------------------------|
| Nhận authorize request, chưa có session | Tạo mới                                | authorize_context                                   |
| User submit password (first factor OK)  | Tạo mới (rotate)                       | SecurityContext {PASSWORD_AUTHORITY}                |
| OTT generated, awaiting second factor   | Giữ nguyên                             | SecurityContext {PASSWORD_AUTHORITY}                |
| User submit OTT (second factor OK)      | Tạo mới (rotate)                       | SecurityContext {PASSWORD_AUTHORITY, OTT_AUTHORITY} |
| Sau issue authorization_code            | Giữ nguyên                             | SecurityContext (fully authenticated)               |
| Silent re-authentication                | Giữ nguyên, thêm authorize_context mới | SecurityContext + authorize_context mới             |
| Logout                                  | Xóa                                    | —                                                   |

**Session fixation protection** xảy ra 3 lần trong MFA flow:
1. Sau khi password verified → auth session rotate `{as-sid-1}` → `{as-sid-2}`
2. Sau khi OTT verified → auth session rotate `{as-sid-2}` → `{as-sid-3}` — loại bỏ rủi ro attacker dùng session id sniff được trong window giữa 2 factors
3. Sau khi web-gateway exchange token thành công → web-gateway session rotate `{wg-sid-1}` → `{wg-sid-2}`

---

## Step-by-Step Flow

### Step 1 — User initiates login

Có 2 trigger dẫn đến cùng 1 flow, khác nhau ở điểm khởi đầu:

**Trigger A — User chủ động nhấn đăng nhập:**

Angular gọi đến `GET /oauth2/authorization/{registrationId}` — endpoint do Spring Security OAuth2 Client tự expose, không cần implement thủ công. web-gateway bắt đầu Authorization Code Flow + PKCE:
- Generate `state` (CSRF), `nonce` (replay), `code_verifier`, `code_challenge = SHA256(code_verifier)`
- Tạo web-gateway session mới lưu vào Redis: `{state, nonce, code_verifier}` — không có `saved_request`
- Response `302` + set cookie `SESSION={wg-sid-1}` (httpOnly, Secure)
- Redirect đến `/oauth2/authorize?response_type=code&client_id=...&state=...&code_challenge=...&code_challenge_method=S256`

**Trigger B — User truy cập protected resource khi không có session (hoặc session hết hạn):**

web-gateway không tìm thấy session hợp lệ → `AuthenticationEntryPoint` intercept, bắt đầu Authorization Code Flow + PKCE:
- Generate `state`, `nonce`, `code_verifier`, `code_challenge`
- Tạo web-gateway session mới lưu vào Redis: `{state, nonce, code_verifier, saved_request}` — `saved_request` để redirect về đúng URL sau login
- Response `302` + set cookie `SESSION={wg-sid-1}` (httpOnly, Secure)
- Redirect đến `/oauth2/authorize?...`

> Từ Step 2 trở đi cả hai trigger xử lý hoàn toàn giống nhau.

**State sau Step 1:**
```
Redis (web-gateway):
  spring:session:{wg-sid-1} = {
    state, nonce, code_verifier,
    saved_request   (Trigger B only)
  }
```

### Step 2 — oauth2-service nhận authorize request

Chưa có auth session:
- Tạo auth session mới `{as-sid-1}`, lưu `authorize_context`: `{state, nonce, code_challenge, code_challenge_method, client_id, redirect_uri}`
- Render login form `200 OK` + set cookie `SESSION={as-sid-1}` (httpOnly, Secure)

**State sau Step 2:**
```
DB (oauth2-service) SPRING_SESSION {as-sid-1} = {
  authorize_context: { state, nonce, code_challenge, client_id, redirect_uri }
  SecurityContext: anonymous
}
```

### Step 3 — First Factor: Password

User submit `username` + `password` → `POST /login`:
- `UsernamePasswordAuthenticationFilter` xử lý, verify credential
- Thành công → **Auth session ROTATE** (session fixation protection): `{as-sid-1}` bị xóa, tạo `{as-sid-2}` mới copy `authorize_context`, SecurityContext nhận `PASSWORD_AUTHORITY (issuedAt=now)`
- Spring Security kiểm tra: `OTT_AUTHORITY` còn thiếu → chưa fully authenticated
- Custom `AuthenticationSuccessHandler` kích hoạt — gọi `OneTimeTokenService.generate(username)` **server-side**, lưu `ott:{token} = username` vào Redis (TTL 5 phút), sau đó gọi `GeneratedOneTimeTokenHandler` để gửi OTT đến user
- Redirect đến `/login/ott` (form nhập OTP — OTT đã được gửi tự động, không cần user request thêm bước nào)
- Response set cookie `SESSION={as-sid-2}` (rotated)

> `/ott/generate` là endpoint dành cho **passwordless (magic link)** flow — user chủ động yêu cầu OTT làm first factor. Trong MFA context (OTT là second factor), OTT được generate **server-side tự động** ngay sau khi password verified, không qua endpoint đó.

**State sau Step 3:**
```
DB (oauth2-service) SPRING_SESSION {as-sid-2} = {
  authorize_context: { state, nonce, ... },
  SecurityContext: {
    principal: username,
    authorities: [PASSWORD_AUTHORITY]   ← 1 factor, chưa đủ
  }
}
SPRING_SESSION {as-sid-1} đã bị xóa (session fixation protection)

Redis (oauth2-service):
  ott:{token} = username   TTL = 5 min
```

### Step 4 — Second Factor: OTT

User nhập OTT nhận được → `POST /login/ott {token}`:
- `OneTimeTokenAuthenticationFilter` xử lý
- Gọi `OneTimeTokenService.consume(token)`:
  - `GETDEL ott:{token}` — **atomic**, đọc và xóa trong một command
  - Nếu nil → `BadCredentialsException` (expired hoặc đã dùng)
  - Nếu có → return username
- SecurityContext update: add `OTT_AUTHORITY (issuedAt=now)` → `{PASSWORD_AUTHORITY, OTT_AUTHORITY}` → fully authenticated
- **Auth session ROTATE lần 2** (`SessionAuthenticationStrategy` fires sau mỗi authentication event): `{as-sid-2}` bị xóa, tạo `{as-sid-3}` copy `authorize_context` + SecurityContext mới — loại bỏ rủi ro attacker dùng session id đã sniff được trong window giữa password và OTT
- Issue `authorization_code`, lưu vào `OAuth2Authorization`: `{code, code_challenge, client_id, redirect_uri, expiry 30-60s}`
- Response `302` → `{redirect_uri}?code=...&state=...` + set cookie `SESSION={as-sid-3}` (rotated)

**State sau Step 4:**
```
DB (oauth2-service):
  SPRING_SESSION {as-sid-3} = {
    authorize_context: { ... },
    SecurityContext: {
      principal: username,
      authorities: [PASSWORD_AUTHORITY, OTT_AUTHORITY]   ← đủ 2 factors
    }
  }
  SPRING_SESSION {as-sid-2} đã bị xóa (session fixation protection lần 2)

  OAuth2Authorization = {
    code: ..., expiry: now+30-60s,
    code_challenge, client_id, redirect_uri
    (access_token / refresh_token chưa có — chờ exchange)
  }

Redis (oauth2-service):
  ott:{token} đã bị xóa bởi GETDEL → không thể reuse
```

### Step 5 — web-gateway: callback & token exchange

**Callback — web-gateway nhận `code` từ oauth2-service:**

`GET /login/oauth2/code/{registrationId}?code=...&state=...`:
- Đọc web-gateway session `{wg-sid-1}` từ Redis → lấy `state`, `nonce`, `code_verifier`
- Verify `state` → chống CSRF
- Verify `nonce` → chống replay attack
- Nếu không khớp → hủy flow

**Token exchange — gọi oauth2-service:**

```
POST /oauth2/token
  grant_type=authorization_code
  &code=...
  &redirect_uri=...
  &client_id=...
  &client_secret=...
  &code_verifier=...
```

oauth2-service xử lý:
- Verify `client_id` + `client_secret`
- Truy vấn `authorization_code` từ DB — kiểm tra chưa expired, chưa dùng, đúng client
- PKCE: `SHA256(code_verifier) == code_challenge` → verify không bypass flow
- Issue `access_token` (JWT), `id_token` (OIDC JWT), `refresh_token` (Opaque)
- Update `OAuth2Authorization` với đầy đủ tokens

**web-gateway nhận token response:**
- Verify JWT signature bằng public key của oauth2-service, verify `iss`, `aud`
- Verify `nonce` khớp với session → chống replay attack
- **Web-gateway session ROTATE** (session fixation protection): `{wg-sid-1}` bị xóa, tạo `{wg-sid-2}` copy `saved_request`, lưu `OAuth2AuthorizedClient` + `SecurityContext`
- Parse `sid` từ JWT claims → lưu mapping `webgw:oauth:{sid} = {wg-sid-2}` vào Redis
- Set cookie `SESSION={wg-sid-2}` mới (httpOnly, Secure)
- Redirect về `saved_request` hoặc `defaultSuccessUrl`

**State sau Step 5:**
```
Redis (web-gateway):
  spring:session:{wg-sid-2} = {
    OAuth2AuthorizedClient: { access_token, refresh_token, id_token },
    SecurityContext: { principal, authenticated=true }
  }
  spring:session:{wg-sid-1} đã bị xóa (session fixation protection)
  webgw:oauth:{sid} = {wg-sid-2}   ← dùng cho logout/revoke theo sid

DB (oauth2-service):
  OAuth2Authorization = {
    id (= sid), principalName,
    access_token, refresh_token, id_token   ← fully populated
  }
```

### Silent Re-authentication

Kịch bản: web-gateway session `{wg-sid-2}` đã hết hạn hoặc bị xóa, nhưng oauth2-service auth session `{as-sid-3}` vẫn còn hiệu lực với `{PASSWORD_AUTHORITY, OTT_AUTHORITY}`:
- web-gateway redirect đến `/oauth2/authorize` (Trigger B — Step 1)
- oauth2-service đọc auth session → SecurityContext có đủ factors, check `validDuration` nếu cấu hình
- Không hiện login form / OTT form
- Lưu `authorize_context` mới vào session hiện tại → issue `authorization_code` ngay
- Redirect về web-gateway callback → Step 5 bình thường

Silent re-auth **không** hoạt động nếu:
- Auth session `{as-sid-3}` đã expired
- `validDuration` của `PASSWORD_AUTHORITY` hoặc `OTT_AUTHORITY` đã quá hạn → Spring Security redirect về login form (Step 2)

---

## Configuration Examples

### Global MFA — tất cả user phải có 2 factors

```java
@EnableMultiFactorAuthentication(authorities = {
    FactorGrantedAuthority.PASSWORD_AUTHORITY,
    FactorGrantedAuthority.OTT_AUTHORITY
})
@Configuration
public class AuthorizationServerConfig {

    @Bean
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .formLogin(Customizer.withDefaults())
            .oneTimeTokenLogin(Customizer.withDefaults());
        return http.build();
    }
}
```

### Per-user MFA — dựa trên `RequiredAuthoritiesRepository`

```java
@Bean
RequiredAuthoritiesRepository requiredAuthoritiesRepository(MfaConfigLookup lookup) {
    return username -> lookup.isMfaEnabled(username)
        ? List.of(FactorGrantedAuthority.PASSWORD_AUTHORITY, FactorGrantedAuthority.OTT_AUTHORITY)
        : List.of(FactorGrantedAuthority.PASSWORD_AUTHORITY);
}

@Bean
RequiredAuthoritiesAuthorizationManager<Object> mfaAuthorizationManager(
        RequiredAuthoritiesRepository repository) {
    return new RequiredAuthoritiesAuthorizationManager<>(repository);
}
```

> `MfaConfigLookup.isMfaEnabled(username)` nên backed bởi field denormalized trên `Credential` (không query `MfaConfig` table riêng trên hot path).

### Selective MFA — per endpoint

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    var mfa = AuthorizationManagerFactories.multiFactor()
        .requireFactors(
            FactorGrantedAuthority.PASSWORD_AUTHORITY,
            FactorGrantedAuthority.OTT_AUTHORITY
        )
        .build();

    http.authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/admin/**").access(mfa.hasRole("ADMIN"))
        .requestMatchers("/settings/**").access(mfa.authenticated())
        .anyRequest().authenticated()
    );
    return http.build();
}
```

### Time-based re-authentication

```java
var passwordIn30m = AuthorizationManagerFactories.multiFactor()
    .requireFactor(f -> f
        .passwordAuthority()
        .validDuration(Duration.ofMinutes(30))
    )
    .build();

http.authorizeHttpRequests(authorize -> authorize
    .requestMatchers("/payment/**").access(passwordIn30m.authenticated())
    .anyRequest().authenticated()
);
```

---

## `OneTimeTokenService` — Interface & Redis Implementation

Spring Security 7 define interface — không có built-in persistence:

```java
public interface OneTimeTokenService {
    OneTimeToken generate(GenerateOneTimeTokenRequest request);
    OneTimeToken consume(Authentication authentication);
}
```

Redis implementation:

```java
@Component
public class RedisOneTimeTokenService implements OneTimeTokenService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "ott:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisOneTimeTokenService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public OneTimeToken generate(GenerateOneTimeTokenRequest request) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, request.getUsername(), TTL);
        return new DefaultOneTimeToken(token, request.getUsername(), Instant.now().plus(TTL));
    }

    @Override
    public OneTimeToken consume(Authentication authentication) {
        String token = ((OneTimeTokenAuthenticationToken) authentication).getTokenValue();
        String username = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + token);
        if (username == null) {
            throw new BadCredentialsException("OTT invalid or expired");
        }
        return new DefaultOneTimeToken(token, username, null);
    }
}
```

> `getAndDelete()` là single atomic command — không thể race condition dẫn đến OTT dùng 2 lần.

---

## `GeneratedOneTimeTokenHandler` — Hook point gửi OTT

```java
@Component
public class OttNotificationHandler implements GeneratedOneTimeTokenHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       OneTimeToken ott) throws IOException, ServletException {
        // Hook point: gửi ott.getTokenValue() đến user
        // Implementation: publish event / call notification service
        // ...
        response.sendRedirect("/login/ott");
    }
}
```

---

## Lưu ý quan trọng

- Toàn bộ MFA interaction (form password + OTT form) xảy ra **bên trong oauth2-service**. Từ góc nhìn của web-gateway, đây chỉ là delay trước khi nhận callback — không có thêm roundtrip qua BFF.
- Auth session oauth2-service giữ nguyên `PASSWORD_AUTHORITY` trong SecurityContext xuyên suốt bước OTT — session không bị xóa giữa chừng.
- `registrationId` chỉ có ý nghĩa với web-gateway client — oauth2-service không biết đến nó.
- Auth session (oauth2-service) và web-gateway session dùng cùng tên cookie `SESSION` nhưng trên 2 server khác nhau — không conflict.
- `OAuth2Authorization` và auth session tồn tại độc lập: `OAuth2Authorization` bị xóa khi logout/revoke, auth session vẫn còn → silent re-auth vẫn hoạt động cho lần tiếp theo.
- `GETDEL` yêu cầu Redis ≥ 6.2. Với Redis cũ hơn: thay bằng Lua script `GET + DEL` trong 1 transaction.
