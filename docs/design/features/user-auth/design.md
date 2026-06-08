# Design: User Login — Device Tracking & Login History

**Sequence (login + device)**: [`sequence-login-device.puml`](sequence-login-device.puml)  
**Status**: In Progress

---

## Context — Tại sao có refactor registration?

Theo ADR-001, `oauth2-service` là entry point duy nhất cho registration và authentication.
Implementation cũ vi phạm điều này: identity-service expose `POST /api/users/register`, lưu password và role.
Feature này align lại: oauth2-service làm chủ registration, publish `oauth2.user.registered`,
identity-service trở thành consumer tạo `UserAccount` + `EmailVerification`.

---

## Services liên quan

| Service                | Vai trò                                                | Loại tham gia          |
|------------------------|--------------------------------------------------------|------------------------|
| `web-gateway`          | BFF — quản lý httpOnly cookie, session store           | Entry point            |
| `oauth2-service`       | Registration entry point, authentication, session mgmt | Sync + Event publisher |
| `identity-service`     | UserAccount, Device, LoginActivity                     | Async consumer + REST  |
| `customer-service`     | Tạo CustomerProfile khi nhận `UserRegistered`          | Async consumer         |
| `notification-service` | Gửi OTP email, verification email                      | Async consumer         |

---

## Sub-features

### A. Registration

oauth2-service là entry point duy nhất. `POST /api/auth/register` → tạo `UserCredential` (PENDING) → publish `oauth2.user.registered`. Identity-service consume → tạo `UserAccount` + `EmailVerification` → gửi verification email qua notification-service. Sau khi user click link → identity-service activate user → publish `identity.user.activated` → oauth2-service activate `Credential`.

Google OAuth: `SocialLoginOidcUserService` auto-register `UserCredential` (ACTIVE) nếu email chưa tồn tại. Account linking: email đã tồn tại → link vào account cũ.

### B. Login & MFA

Authorization Code Flow + PKCE. web-gateway là OAuth2 Client (BFF pattern). MFA là per-user opt-in (default `false`) — dùng OTP qua email. Sau khi fully authenticated, `SessionEstablishingAuthorizationService` gọi `EstablishSession` để tạo hoặc reuse `OAuthSession`. `JwtTokenCustomizer` embed `oss_id` + `roles` vào JWT.

Silent SSO: web-gateway session hết hạn nhưng IDP session còn → no-op với `OAuthSession` (reuse).

### C. Logout & Remote Revocation

User POST `/webgw/auth/logout` → web-gateway invalidate BFF session → navigate sang `GET /connect/logout` tại oauth2-service → AS invalidate IDP session → explicit cleanup `OAuthSession` + `OAuth2Authorization` → back-channel revoke web-gateway session còn lại.

### D. Device Management & Login History

identity-service expose REST APIs sau khi login:
- `GET /api/v1/identity/me/devices` — ACTIVE devices
- `DELETE /api/v1/identity/me/devices/{deviceId}` — revoke device
- `GET /api/v1/identity/me/login-activity` — lịch sử login (offset/limit)

---

## Documentation Index

| Doc                                                                                                      | Mô tả                                                                           |
|----------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| [`login-impl.md`](login-impl.md)                                                                         | Implementation chi tiết: hook points, classes, kịch bản 1–3 (login/SSO/refresh) |
| [`logout-impl.md`](logout-impl.md)                                                                       | Implementation chi tiết: kịch bản 4–5 (logout/expire), remote revocation        |
| [`session-management.md`](session-management.md)                                                         | Notation [A]–[F], TTL, invariant, domain model, events                          |
| [`deferred.md`](deferred.md)                                                                             | Pending items: session cleanup job, short TTL cho access token, v.v.            |
| [`docs/architecture/spring-security-mfa-bff.md`](../../../architecture/spring-security-login-mfa-bff)       | Framework: login + MFA flow                                                     |
| [`docs/architecture/spring-security-logout-bff.md`](../../../architecture/spring-security-logout-bff.md) | Framework: logout + remote revocation                                           |

