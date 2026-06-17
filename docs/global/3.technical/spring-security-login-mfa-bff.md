# Spring Security 7 — BFF Login Flow with MFA

**Status**: Implementation Reference  
**Applies to**: `oauth2-service` (Spring Authorization Server 7.0.5), `web-gateway` (Spring Cloud Gateway)  
**Spring Boot**: 4.0.x | **Spring Security**: 7.0.3 | **Spring Authorization Server**: 7.0.5  
**Xem thêm**: [`spring-security-logout-bff.md`](spring-security-logout-bff.md) — Logout & Remote Revocation

> Tài liệu này ở mức **framework** — không chứa business logic.  
> Domain-specific design → `docs/design/features/user-login/design.md`.

---

## Components

```
oauth2-service   # Spring Authorization Server + OAuth2 Client (social login)
web-gateway      # Spring Cloud Gateway — OAuth2 Client (BFF pattern)
```

---

## Core Framework Classes

| Class / Interface                               | Role                                                                                                           |
|-------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `FactorGrantedAuthority`                        | Đại diện 1 authentication factor — mang `authority` string và `issuedAt` timestamp                             |
| `OneTimeTokenService`                           | Interface: generate + consume OTT — không có default implementation                                            |
| `OneTimeTokenGenerationSuccessHandler`          | Hook sau khi generate OTT (delivery channel)                                                                   |
| `OAuth2UserService<OidcUserRequest, OidcUser>`  | Interface: load/enrich OidcUser sau Google callback                                                            |
| `SavedRequestAwareAuthenticationSuccessHandler` | Base class: redirect về saved request sau auth thành công                                                      |
| `OncePerRequestFilter`                          | Base class: custom filter trong Security filter chain                                                          |
| `@EnableMultiFactorAuthentication`              | *(Chưa apply)* Annotation global — tự động add `FactorGrantedAuthority` tương ứng sau mỗi authentication event |

### `FactorGrantedAuthority` — predefined constants

| Constant                                                       | Ý nghĩa                        |
|----------------------------------------------------------------|--------------------------------|
| `PASSWORD_AUTHORITY` = `"FACTOR_PASSWORD"`                     | Password authentication        |
| `OTT_AUTHORITY` = `"FACTOR_OTT"`                               | One-Time Token authentication  |
| `AUTHORIZATION_CODE_AUTHORITY` = `"FACTOR_AUTHORIZATION_CODE"` | OAuth2 Authorization Code flow |

---

## Customization Points

Danh sách các extension point đã implement trong `oauth2-service`. Không có business logic ở đây — mỗi class chỉ thực hiện đúng trách nhiệm framework giao.

### `SocialLoginOidcUserService`
**Implements**: `OAuth2UserService<OidcUserRequest, OidcUser>`  
**Wired tại**: `oauth2Login().userInfoEndpoint().oidcUserService(...)`

Sau khi delegate xuống `OidcUserService` mặc định (gọi Google UserInfo endpoint):
- Thêm `FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY` (issuedAt = `idToken.issuedAt`) vào authorities — bắt buộc vì `JwtGenerator.getAuthenticationTime()` yêu cầu ít nhất 1 `FactorGrantedAuthority` trong authentication
- Thêm custom claim `app_mfa_enabled` vào `OidcIdToken` nếu user có MFA bật — để `MfaEnforcementFilter` đọc mà không cần query DB
- Override `sub` claim bằng internal `systemUserId` (Google `sub` không dùng làm principal)

### Custom `UserDetails` (`UserCredentialDetails`)
**Extends**: `org.springframework.security.core.userdetails.User`

Custom `UserDetails` để carry thêm hai trường mà framework không có sẵn:
- `email` — tách biệt với `getUsername()` vì `getUsername()` trả về `userId` (dùng làm JWT `sub` claim). Email cần thiết riêng để làm delivery target trong OTT flow, vì sau OTT authentication principal là `UserCredentialDetails` chứ không còn là `OidcUser`.
- `mfaEnabled` — đọc bởi `MfaEnforcementFilter` để quyết định per-user có cần second factor không (LOCAL path).

### Custom `AuthenticationSuccessHandler`
**Extends**: `SavedRequestAwareAuthenticationSuccessHandler`

Extension point để capture thêm context sau khi authentication thành công (áp dụng cho cả LOCAL và GOOGLE path). Delegate `super.onAuthenticationSuccess()` để giữ nguyên redirect behavior về saved request. Không ảnh hưởng đến MFA flow.

### `MfaEnforcementFilter`
**Extends**: `OncePerRequestFilter`  
**Wired tại**: `.addFilterBefore(new MfaEnforcementFilter(), AuthorizationFilter.class)` trong AS filter chain (Order 1)

Safety net / MFA gate trước khi AS xử lý authorization request:
- Kiểm tra authentication hiện tại có cần MFA không:
  - LOCAL: đọc `UserCredentialDetails.isMfaEnabled()`
  - GOOGLE: đọc `OidcUser.getClaim("app_mfa_enabled")`
- Nếu cần MFA và chưa có `FACTOR_OTT`: `requestCache.saveRequest()` → redirect `/mfa`
- Nếu đủ factor hoặc không cần MFA: pass through

> Normal path với LOCAL login: `CustomAuthenticationSuccessHandler` redirect về `/oauth2/authorize` → filter này bắt và redirect sang `/mfa`. Filter không can thiệp vào flow password hay Google — chỉ gate ở AS endpoints.

### `MfaBridgeController`
**Type**: Spring MVC `@Controller` tại `/mfa`

Auto-initiates OTT generation — bypass endpoint `/ott/generate` mặc định (dành cho passwordless flow, không phù hợp MFA context):
- `GET /mfa`: gọi `OneTimeTokenService.generate()` → `OneTimeTokenGenerationSuccessHandler.handle()` → redirect `/mfa/verify`
  - Guard: nếu đã có active token cho user hiện tại → redirect `/mfa/verify` ngay, không generate mới (tránh gửi email trùng)
- `GET /mfa/verify`: render OTP form, đọc `?error` param để hiển thị lỗi

### Custom `OneTimeTokenService`
**Implements**: `OneTimeTokenService`  
**Wired tại**: `oneTimeTokenLogin().tokenService(...)`

Session-based token storage — token lifecycle gắn với session lifecycle (nếu session hết hạn thì token vô nghĩa). Ngoài `generate()` và `consume()` theo interface, implement thêm `hasActiveToken(username)` để `MfaBridgeController` guard tránh generate trùng.

> Delivery channel (email trong ví dụ này) là implementation detail — có thể thay bằng SMS, push notification, hay authenticator app.

### Custom `OneTimeTokenGenerationSuccessHandler`
**Implements**: `OneTimeTokenGenerationSuccessHandler`  
**Wired tại**: `oneTimeTokenLogin().tokenGenerationSuccessHandler(...)`

Delivery hook sau khi token được generate. Cần resolve identity từ principal — xử lý nhiều loại principal (password auth và social login trả về principal type khác nhau) để lấy đúng delivery target.

### `OAuth2AuthorizationService` decorator (`SessionEstablishingAuthorizationService`)
**Wraps**: `JdbcOAuth2AuthorizationService`  
**Wired tại**: `@Bean OAuth2AuthorizationService`

Hook vào persistence lifecycle của `OAuth2Authorization` — khi authorization được persist (tức là auth code được issue), thực hiện các side-effect ở tầng infrastructure.

---

## Storage Overview

| Location            | Key / Table                   | Content                                                                          | TTL             |
|---------------------|-------------------------------|----------------------------------------------------------------------------------|-----------------|
| Redis (web-gateway) | `spring:session:{wg-sid}`     | `OAuth2AuthorizedClient`, `SecurityContext`                                      | session timeout |
| Redis (web-gateway) | session ↔ IDP session mapping | mapping giữa web-gateway session và auth session của AS — dùng cho logout/revoke | session timeout |
| DB (oauth2-service) | `SPRING_SESSION`              | auth session — `SecurityContext`, OTP, saved request                             | configurable    |
| DB (oauth2-service) | `OAuth2Authorization`         | `access_token`, `refresh_token`, `id_token`                                      | token lifetime  |

**OTP storage**: lưu trong HTTP session, không tách store riêng.  
Lý do: OTP lifecycle = session lifecycle — nếu session hết hạn thì OTP vô nghĩa.

---

## Auth Session Lifecycle — oauth2-service

| Thời điểm                                 | Hành động  | Session chứa                                               |
|-------------------------------------------|------------|------------------------------------------------------------|
| Nhận `/oauth2/authorize`, chưa có session | Tạo mới    | `authorize_context`                                        |
| Auth thành công (LOCAL hoặc GOOGLE)       | Rotate     | `SecurityContext` {factor 1}, device signals, `auth_email` |
| MFA: sau `MfaEnforcementFilter` redirect  | Giữ nguyên | thêm saved request (`/oauth2/authorize`), OTP data         |
| OTT verified thành công                   | Rotate     | `SecurityContext` {`FACTOR_OTT`}                           |
| Issue authorization code                  | Giữ nguyên | SecurityContext (fully authenticated)                      |
| Silent re-authentication                  | Giữ nguyên | thêm `authorize_context` mới                               |
| Logout                                    | Xóa        | —                                                          |

**Session fixation protection** xảy ra tại:
1. Sau authentication thành công (LOCAL password hoặc Google callback)
2. Sau OTT verified (Spring Security `SessionAuthenticationStrategy`)
3. Sau token exchange thành công tại web-gateway

---

## Authentication Paths

### Path A — LOCAL login, MFA bật

```
POST /login  →  UsernamePasswordAuthenticationFilter
  → CustomAuthenticationSuccessHandler
      → capture state / extra data
      → super → redirect /oauth2/authorize

GET /oauth2/authorize  →  MfaEnforcementFilter
  → needsMfa(): UserCredentialDetails.isMfaEnabled() = true, no FACTOR_OTT → true
  → saveRequest(/oauth2/authorize) → redirect /mfa

GET /mfa  →  MfaBridgeController.initiate()
  → hasActiveToken() = false → generate OTP → send → redirect /mfa/verify

POST /login/ott  →  OneTimeTokenAuthenticationFilter
  → CustomOneTimeTokenService.consume() → OTP valid
  → OAuth2UserDetailsService.loadUserByUsername(email) → UserCredentialDetails
  → OneTimeTokenAuthentication { FACTOR_OTT }
  → redirect saved request → GET /oauth2/authorize

GET /oauth2/authorize  →  MfaEnforcementFilter
  → needsMfa(): has FACTOR_OTT → false → pass
  → issue authorization_code → redirect BFF callback
```

### Path B — GOOGLE login, MFA bật

```
GET /oauth2/authorization/google  →  CustomAuthorizationRequestResolver
  → hook when call to render google login uri → redirect Google

Google callback  →  OAuth2LoginAuthenticationFilter
  → SocialLoginOidcUserService.loadUser()
      → DefaultOidcUser { FACTOR_AUTHORIZATION_CODE, app_mfa_enabled=true in claims }
  → CustomAuthenticationSuccessHandler
      → capture external oidc user and extra data
      → super → redirect /oauth2/authorize

GET /oauth2/authorize  →  MfaEnforcementFilter
  → needsMfa(): OidcUser.getClaim("app_mfa_enabled") = true, no FACTOR_OTT → true
  → saveRequest(/oauth2/authorize) → redirect /mfa

GET /mfa  →  MfaBridgeController.initiate()
  → email = session.getAttribute("auth_email")
  → hasActiveToken() = false → generate OTP → send → redirect /mfa/verify

POST /login/ott  →  OneTimeTokenAuthenticationFilter
  → CustomOneTimeTokenService.consume() → OTP valid
  → OAuth2UserDetailsService.loadUserByUsername(email) → UserCredentialDetails
      (password = "" cho OAuth user — không dùng trong OTT flow)
  → OneTimeTokenAuthentication { FACTOR_OTT }
  → redirect saved request → GET /oauth2/authorize

GET /oauth2/authorize  →  MfaEnforcementFilter → pass → issue code
```

### Path C — LOCAL hoặc GOOGLE, không có MFA

```
(Sau authentication thành công)
  → CustomAuthenticationSuccessHandler → redirect /oauth2/authorize
  → MfaEnforcementFilter → needsMfa() = false → pass → issue authorization_code
```

---

## Step-by-Step: Authorization Code Flow + PKCE

### Step 1 — BFF khởi tạo flow

**Trigger A** — User chủ động login:
- Angular gọi `GET /oauth2/authorization/{registrationId}` (Spring Security OAuth2 Client tự expose)
- web-gateway generate `state`, `nonce`, `code_verifier`, `code_challenge = SHA256(code_verifier)`
- Lưu vào Redis session, redirect `/oauth2/authorize?...`

**Trigger B** — Truy cập protected resource khi không có session:
- `AuthenticationEntryPoint` intercept, lưu thêm `saved_request` vào session

### Step 2 — AS nhận authorize request

- Chưa có auth session → tạo mới, lưu `authorize_context`
- Render login form

### Step 3 — Authentication (LOCAL hoặc GOOGLE)

Xem **Authentication Paths** bên trên.

### Step 4 — Issue authorization code

Sau khi `MfaEnforcementFilter` pass:
- AS issue `authorization_code`, lưu vào `OAuth2Authorization`
- Redirect `{redirect_uri}?code=...&state=...`

### Step 5 — BFF token exchange

```
GET /login/oauth2/code/{registrationId}?code=...&state=...
  → Verify state (CSRF), nonce (replay)
  → POST /oauth2/token { code, code_verifier, ... }
  → Verify PKCE: SHA256(code_verifier) == code_challenge
  → Issue access_token (JWT), id_token (OIDC), refresh_token (Opaque)
  → BFF: verify JWT, session rotate, lưu OAuth2AuthorizedClient
  → Redirect saved_request
```

---

## Silent Re-authentication

web-gateway session hết hạn, oauth2-service auth session còn hiệu lực:
- BFF redirect `/oauth2/authorize` (Trigger B)
- `MfaEnforcementFilter`: authentication đủ factors → pass
- AS issue authorization code ngay, không hiển thị login form

Không hoạt động nếu:
- Auth session đã expired
- *(Khi apply `@EnableMultiFactorAuthentication` với `validDuration`)* Factor authority đã quá hạn → redirect login form

---

## OTT Wrong Attempt Behavior

- OTP **không bị xóa** khi nhập sai (brute-force protection thuộc rate-limiter layer)
- Failure handler: `SimpleUrlAuthenticationFailureHandler("/mfa/verify?error")`
- `MfaBridgeController.showForm()` đọc `?error` param → model attribute `error` → hiển thị lỗi trên form
- User có thể retry đến khi OTP expire (5 phút)

---

## Lưu ý quan trọng

- Toàn bộ MFA interaction xảy ra bên trong `oauth2-service`. Từ góc nhìn BFF đây là delay trước khi nhận callback.
- `MfaEnforcementFilter` đặt trong **AS filter chain** (Order 1), không phải default filter chain (Order 3) — chỉ gate các AS endpoints.
- Sau OTT auth, principal chuyển từ `OidcUser`/`UserCredentialDetails` sang `UserCredentialDetails` (do `OAuth2UserDetailsService.loadUserByUsername()`). Session attributes (`auth_email`, `auth_device_hash`...) vẫn còn nguyên.
- `app_mfa_enabled` là claim tạm thời trong `OidcIdToken` của `DefaultOidcUser` — không được publish ra ngoài (không có trong access token hay id token cuối cùng).
- Auth session (oauth2-service) và web-gateway session dùng cùng tên cookie `SESSION` nhưng trên 2 server khác nhau — không conflict.
- `OAuth2Authorization` và auth session tồn tại độc lập: revoke token không xóa auth session → silent re-auth vẫn hoạt động.
