# oauth2-service

**Vai trò**: Authorization Server — authentication, MFA (email OTP), social login (Google), session/token issuance, registration entry point. Login hot path chạy hoàn toàn in-process (không cross-service call) — xem `adr/001-iam-services.md`.
**DB**: PostgreSQL (`oauth2_db`) — JPA cho domain aggregate, `spring-boot-session-jdbc` cho HTTP session (form login/MFA flow), JDBC cho `OAuth2Authorization`/JWK persistence (Spring Authorization Server schema)
**Cache**: Redis — OTP session state (`EmailOtpOneTimeTokenService`)
**Libs**: `common-domain`, `common-utils`, `outbox-starter`, `common-web`, `observability-starter`, `rate-limiter-starter`

> Session lifecycle chi tiết (component notation [A]-[F], TTL, quan hệ [B]/[F]/[C]): `session.md`.

---

## Domain Model

| Aggregate | Fields | Notes |
|---|---|---|
| `UserCredential` | userId, email, password (hashed), role, registrationMethod, status, mfaEnabled | Login hot path. `status` là **replica** của `UserAccount.status` (identity-service = source of truth), sync qua `UserActivated`/`UserLocked`/`UserUnlocked` |
| `OAuthSession` | id (ossId), userId, idpSessionId, authorizationId, ipAddress, registeredClientId, status | Wrap `OAuth2Authorization` (SAS) — 1:1 với IDP session per client (invariant enforce ở `IssueSession`) |

**Khác với `adr/001-iam-services.md` (aspirational):** ADR liệt kê thêm `SocialIdentity` và `MfaConfig` như aggregate riêng — thực tế code không tách bảng: social login chỉ dùng `RegistrationMethod.OAUTH` trên `UserCredential` (không lookup bảng riêng), và MFA chỉ là 1 field `mfaEnabled: boolean` trên `UserCredential` (không có cấu hình method/config riêng vì hiện chỉ có email OTP).

---

## API

### `POST /register`

Path trần (không `/api` prefix) — khớp route `api-gateway`: `/auth/register` → stripPrefix(1) → `/register`. Nằm ngoài `/api/**` nên không bị resource-server JWT filter chặn.

Rate limit: 5 lần/giờ per IP (`@RateLimit`, key = clientIp).

**Request**
```json
{ "email": "buyer@example.com", "password": "...", "role": "CUSTOMER", "fullName": "..." }
```
**Response — 201 Created** → `{ "userId": "01HXYZ..." }`
| Code | Condition |
|---|---|
| 409 | Email đã tồn tại (check trước + unique constraint DB làm chốt chặn cuối cho race condition) |

---

### `GET /login`, `POST /login` — Spring Security form login

`LoginPageController` (`GET /login`) đưa `email` vào Model cho template `login.html` đọc qua `${email}` — **không** đọc thẳng `${param.email}` (Thymeleaf 3.1 chặn object instantiation khi `th:attr` đọc implicit object `param`, xem Session Log). Fail handler (`DeviceAwareAuthenticationFailureHandler`) tách `LockedException` → `/login?locked` và `DisabledException` (chưa verify email) → `/login?unverified&email=...`.

---

### `GET /mfa`, `GET /mfa/verify` — MFA email OTP

`MfaBridgeController` — auto-generate OTP bằng principal đã authenticated (Factor 1 pass), bypass form `/ott/generate` mặc định của Spring (hỏi lại username). OTP lưu session-scoped, TTL 5 phút (`EmailOtpOneTimeTokenService.VERIFY_WINDOW`).

---

### `POST /password/setup`, `GET /password/setup/success` — `PasswordSetupController`

Set password lần đầu cho tài khoản OAuth (Google login chưa từng có password). Token HMAC, TTL 24h, cooldown resend 60s (`app.password-setup.*`).

---

### `PUT /api/v1/me/password`, `POST /api/v1/me/password/setup-request`, `GET /api/v1/me/password/status`, `DELETE /api/v1/me/sessions/{ossId}` — `MeController`

Yêu cầu JWT (resource server, `/api/**`). `DELETE .../sessions/{ossId}` — user tự thu hồi 1 session khác của chính mình; chặn tự thu hồi session hiện tại (`OAuthSessionException.cannotRevokeCurrent`), verify ownership qua `belongsTo(userId)`.

---

### Spring Authorization Server managed endpoints

`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/connect/logout`, `/login/oauth2/code/google` — không có controller riêng, auto-register bởi `spring-boot-starter-security-oauth2-authorization-server` + `OAuth2AuthorizationServerConfig`. Issuer public-facing là `http://localhost:8000/auth` (qua `api-gateway`), không phải port nội bộ `8004`.

---

## Events Consumed

| Topic | Event | Handler | Idempotency |
|---|---|---|---|
| `identity.user.activated` | `UserActivated` | `ActivateUserCredential` (qua `UserActivatedConsumer`) — `UserCredential.status = ACTIVE` | Chỉ activate khi `credential.isPending()`; nếu đã `ACTIVE` (redeliver) hoặc `LOCKED` (admin khoá sau khi event gốc phát ra) → no-op, không throw |

Consumer Kafka đầu tiên của service này (trước đây thuần publisher) — DLQ riêng `oauth2-service.dlq`, retry 3 lần/2s (`MessagingConfig`).

---

## Events Published

| Event | Topic | Trigger |
|---|---|---|
| `UserRegisteredEvent` | `oauth2.user.registered` | Sau khi tạo `UserCredential` (`RegisterUser`, `ResolveSocialUser`) |
| `LoginOtpRequestedEvent` | `oauth2.login-otp.requested` | Factor 1 pass, trước khi issue authorization code (`SendLoginOtp`) |
| `LoginFailedEvent` | `oauth2.login.failed` | Mọi lần login fail có match được `UserCredential` theo email (`PublishLoginFailed`) |
| `PasswordSetupResentEvent` | `oauth2.credential.password-setup-resent` | Resend link set password (`RequestPasswordSetup`) |
| `SessionIssuedEvent` | `oauth2.session.issued` | Sau khi `OAuthSession.issue()` — identity-service consume để upsert `Device` + `LoginActivity` |
| `SessionRevokedEvent` | `oauth2.session.revoked` | Logout / thu hồi session (`RevokeSession`, `EndIdpSession`) |
| `SessionsBulkExpiredEvent` | `oauth2.session.expired.bulk` | Batch job hết hạn session hàng loạt |

Đồng bộ với `5. event-catalog.md` §oauth2-service — bảng đó trước đây thiếu 4 event trên và ghi nhầm tên `DeviceLoginRecorded` (ADR-001 gốc) thay vì `SessionIssuedEvent` (tên thực tế trong code), đã sửa.

---

## Business Rules

- Login hot path đọc `UserCredential.status` local — không gọi identity-service (NFR 8,000 RPS)
- `1 user — 1 role`, lưu single value `role VARCHAR`, không junction table
- Password hash: BCrypt, strength 10 (`SecurityConfiguration.passwordEncoder()`)
- MFA email OTP: TTL 5 phút, issue authorization code chỉ sau khi OTP verified
- SSO invariant: 1 `OAuthSession` ACTIVE / (idpSessionId, registeredClientId) — session cũ tự động revoke khi issue session mới cùng cặp này (`IssueSession`)
- Refresh token (cùng `authorizationId`) không rotate `OAuthSession`; access token mới do silent SSO (`authorizationId` đổi) mới rotate (`EstablishSession`)
- `RevokeSession` chặn user tự thu hồi session hiện tại đang dùng để gọi API (`cannotRevokeCurrent`)
- OAuth social login (Google) — tài khoản mới tạo `status=ACTIVE` ngay (email đã verified bởi Google), kèm `PasswordSetupTokenService` để user set password lần đầu nếu muốn dùng login bằng credential sau này

---

## Dependencies

| Dependency | Lý do |
|---|---|
| `common-domain` | `AggregateRoot`, `DomainEvent`, `CommandHandler`/`QueryHandler` |
| `common-utils` | `ULIDGenerator` — sinh `userId`, `sessionId` |
| `outbox-starter` | Publish events reliable qua Outbox Pattern + CDC |
| `common-web` | `ApiResponse`, `GlobalExceptionHandler` |
| `observability-starter` | Tracing + structured logging |
| `rate-limiter-starter` | `@RateLimit` trên `/register` |
