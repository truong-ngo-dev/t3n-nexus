# Progress: user-login

> Design: [`design.md`](../design.md) · Implementation plan: [`implementation.md`](../implementation.md)
> Framework reference: [`docs/architecture/spring-security-mfa-bff.md`](../../../../architecture/spring-security-mfa-bff.md)

**Approach:** Framework skeleton trước → chạy được flow end-to-end → uncomment business logic.
Các bước đánh dấu `[B]` = business logic — ban đầu để dạng comment trong code, uncomment sau khi framework pass.

---

## Phase 1 — Core Login Flow (oauth2-service + web-gateway)

> Mục tiêu: flow login chạy được end-to-end theo framework — không có business logic.
> Chi tiết → [`docs/architecture/spring-security-mfa-bff.md`](../../../../architecture/spring-security-mfa-bff.md)

### oauth2-service

- [ ] Spring Authorization Server dependency + `SecurityFilterChain` (`authorizationServer()`)
- [ ] `RegisteredClient` config — web-gateway as client, Authorization Code + PKCE, scopes
- [ ] `AuthorizationServerSettings` — issuer URI, endpoint paths
- [ ] Token settings — access token duration, refresh token rotation
- [ ] `UserDetailsService` backed by `Credential` (email + hashedPassword + status)
- [ ] Custom login form + `POST /login` endpoint
- [ ] Session management — `SessionAuthenticationStrategy`, fixation protection
- [ ] `[B]` Custom `AuthenticationSuccessHandler` → tạo `OAuthSession` + publish `DeviceLoginRecorded` via Outbox

### web-gateway

- [ ] `spring-boot-starter-oauth2-client` + `ClientRegistration` config
- [ ] `SecurityFilterChain` — `oauth2Login()`, `oauth2Client()`, session-based authorized client
- [ ] Token relay filter — forward `access_token` xuống downstream services
- [ ] CSRF / CORS config cho SPA Angular

### frontend (test scaffold)

- [ ] Angular app khởi tạo, route `/login` trigger `GET /oauth2/authorization/{registrationId}`
- [ ] Route `/callback` (hoặc silent) để handle redirect sau khi web-gateway exchange token xong
- [ ] Route `/me` gọi một protected endpoint qua web-gateway → xác nhận token relay hoạt động

---

## Phase 2 — identity-service: Registration Refactor ✅

| # | Bước                                                                                    | Status |
|---|-----------------------------------------------------------------------------------------|--------|
| 1 | Slim down `User` aggregate — xoá `hashedPassword`, `role`                               | ✅ DONE |
| 2 | Xoá `PasswordServiceAdapter`, `SecurityConfiguration` BCrypt bean                       | ✅ DONE |
| 3 | Xoá REST `POST /api/users/register` + `CustomerRegister` use case                       | ✅ DONE |
| 4 | Tạo `UserRegisteredConsumer` + `CreateUserAccount` use case                             | ✅ DONE |
| 5 | Migration V4 — DROP `hashed_password`, `role` columns                                   | ✅ DONE |
| 6 | Kafka consumer config (`application.properties`)                                        | ✅ DONE |
| 7 | `[B]` `VerificationEmailRequested` event + `VerificationEmailRequestedHandler` → Outbox | ✅ DONE |
| 8 | `[B]` notification-service consumer: topic → `identity.verification.email.requested`    | ✅ DONE |
| 9 | `[B]` `CreateUserAccount` idempotent: skip nếu `userId` đã tồn tại                      | ✅ DONE |

---

## Phase 3 — identity-service: Device & Login Activity

### Framework skeleton

| #  | Bước                                                                        | Status |
|----|-----------------------------------------------------------------------------|--------|
| 10 | Domain: `Device` aggregate + `DeviceRepository` port                        | ⬜ TODO |
| 11 | Domain: `LoginActivity` + `LoginActivityRepository` port                    | ⬜ TODO |
| 12 | Infrastructure: `DeviceJpaEntity` + `DeviceRepositoryAdapter`               | ⬜ TODO |
| 13 | Infrastructure: `LoginActivityJpaEntity` + `LoginActivityRepositoryAdapter` | ⬜ TODO |
| 14 | `DeviceLoginRecordedConsumer` + `RecordDeviceLogin` use case                | ⬜ TODO |
| 15 | Use cases: `ListDevices`, `RevokeDevice`, `GetLoginActivity`                | ⬜ TODO |
| 16 | `DeviceController` — 3 endpoints + JWT resource server config               | ⬜ TODO |
| 17 | Migration V5 — CREATE `devices`, `login_activities` tables                  | ⬜ TODO |

### Business logic `[B]`

| #  | Bước                                                                                    | Status |
|----|-----------------------------------------------------------------------------------------|--------|
| 18 | `[B]` Idempotency guard trong `DeviceLoginRecordedConsumer` (Redis + `eventId` TTL 72h) | ⬜ TODO |

---

## Verify Criteria

### Phase 1

- [ ] oauth2-service: `POST /oauth2/authorize` → redirect login form thành công
- [ ] oauth2-service: `POST /login` → authenticate qua `UserDetailsService`, session rotate
- [ ] web-gateway: Authorization Code + PKCE exchange thành công, cookie set
- [ ] web-gateway: access token relay đến downstream service
- [ ] `[B]` oauth2-service: `AuthenticationSuccessHandler` tạo `OAuthSession` + publish `DeviceLoginRecorded`

### Phase 2

- [x] `POST /api/users/register` không còn route trên identity-service (404)
- [x] `User` aggregate compile sạch — không còn field `hashedPassword`, `role`
- [x] `VerifyEmail` use case pass sau slim-down
- [x] `ResendVerification` use case pass sau slim-down
- [x] identity-service consume `oauth2.user.registered` (CREDENTIAL) → tạo `User(PENDING)` + `EmailVerification`
- [ ] identity-service consume `oauth2.user.registered` (OAUTH) → tạo `User(ACTIVE)`, không tạo `EmailVerification`
- [x] `[B]` `VerificationEmailRequested` được push vào Outbox khi tạo UserAccount CREDENTIAL
- [x] `[B]` notification-service nhận event qua topic `identity.verification.email.requested`
- [x] `[B]` `CreateUserAccount` gọi 2 lần cùng `userId` → không duplicate (idempotent)
- [x] Migration V4 chạy thành công

### Phase 3

- [ ] `DeviceLoginRecorded (SUCCESS)` → `Device` upsert + `LoginActivity` append
- [ ] `DeviceLoginRecorded (FAILED/BLOCKED)` → chỉ `LoginActivity` append, không tạo `Device`
- [ ] `GET /api/v1/identity/me/devices` → chỉ trả ACTIVE devices của đúng user
- [ ] `DELETE /api/v1/identity/me/devices/{id}` → 204 khi revoke thành công
- [ ] Revoke device không thuộc về user → 403
- [ ] Revoke device đã REVOKED → 409
- [ ] `GET /api/v1/identity/me/login-activity` → đúng thứ tự `loginAt DESC`, pagination đúng
- [ ] Tất cả `/me/**` endpoints trả 401 khi không có JWT
- [ ] `[B]` Kafka redelivery cùng `eventId` → không tạo duplicate `LoginActivity`
- [ ] Migration V5 chạy thành công

---

Status: ⬜ TODO · 🔄 IN PROGRESS · ✅ DONE · ⏸ BLOCKED
