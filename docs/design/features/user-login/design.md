# Design: User Login — Device Tracking & Login History

**Related UCs**: Buyer đăng nhập, quản lý thiết bị, xem lịch sử đăng nhập
**Sequence (login + device)**: [`sequence-login-device.puml`](sequence-login-device.puml)
**Sequence (registration refactor)**: [`sequence-registration-refactor.puml`](sequence-registration-refactor.puml)
**Implementation plan**: [`implementation.md`](implementation.md)
**Session invalidation strategy**: [`session-invalidation.md`](session-invalidation.md)
**Deferred**: [`deferred.md`](deferred.md)
**Status**: Draft

---

## Context — Tại sao có refactor customer-registration?

Theo ADR-001 (`docs/architecture/adr/001-iam-services.md`), `oauth2-service` là **entry point duy nhất**
cho registration và authentication. Tuy nhiên implementation hiện tại vi phạm điều này ở 3 điểm:

| Điểm vi phạm | Hiện tại | Đúng theo ADR |
|---|---|---|
| Registration entry point | `identity-service` (`POST /api/users/register`) | `oauth2-service` (`POST /api/auth/register`) |
| Password lưu ở đâu | `User.hashedPassword` trong identity-service | `Credential.hashedPassword` trong oauth2-service |
| Role lưu ở đâu | `User.role` trong identity-service | `Credential.role` trong oauth2-service |
| Publisher của "user registered" event | identity-service | oauth2-service |

Feature này giải quyết cả hai vấn đề song song:
1. **Refactor registration** — align identity-service với ADR, đưa entry point về oauth2-service
2. **Login feature** — implement Device tracking + LoginActivity consuming event từ oauth2-service

---

## Services liên quan

| Service | Vai trò | Loại tham gia |
|---|---|---|
| `web-gateway` | BFF — nhận request, forward, quản lý httpOnly cookie | Entry point |
| `oauth2-service` | Registration entry point, authentication, publish events | Sync + Event publisher |
| `identity-service` | Quản lý UserAccount, EmailVerification, Device, LoginActivity | Async consumer + REST |
| `customer-service` | Tạo CustomerProfile khi nhận `UserRegistered` | Async consumer |
| `notification-service` | Gửi verification email khi nhận `VerificationEmailRequested` | Async consumer |

---

## A. Refactored Registration Flow

### Domain model thay đổi trong identity-service

`User` aggregate được slim down — chỉ giữ trách nhiệm thuộc identity-service:

| Field | Trước | Sau |
|---|---|---|
| `email` | ✅ giữ | ✅ giữ |
| `fullName` | ✅ giữ | ✅ giữ |
| `phoneNumber` | ✅ giữ | ✅ giữ |
| `status` | ✅ giữ | ✅ giữ |
| `hashedPassword` | ❌ sai BC | ✂️ xoá → sang oauth2-service |
| `role` | ❌ sai BC | ✂️ xoá → sang oauth2-service |

### Pre-conditions

- Email chưa tồn tại trong `Credential` table của oauth2-service

### Happy Path (refactored)

```
Buyer
  → POST /api/auth/register  {email, password, fullName}
  → web-gateway forward → oauth2-service
  → oauth2-service:
      validate input
      check email unique (trong Credential table — local, không cross-service)
      hash password
      tạo Credential (email, hashedPassword, role=CUSTOMER, status=PENDING)
      publish oauth2.user.registered {userId, email, fullName, registrationMethod=CREDENTIAL}  [via Outbox]
  → oauth2-service return 201 Created {userId}

        ↓ Kafka async — oauth2.user.registered

  → identity-service consume UserRegistered:
      tạo User (status=PENDING)
      tạo EmailVerification (token, expiresAt=now+24h)
      publish identity.verification.email.requested {userId, email, fullName, verificationToken}  [via Outbox]

  → customer-service consume UserRegistered:
      tạo CustomerProfile (loyaltyBalance=0)

        ↓ Kafka async — identity.verification.email.requested

  → notification-service consume VerificationEmailRequested:
      gửi verification email chứa link kích hoạt

-- Buyer click link trong email --
  → GET /api/identity/users/verify?token={verificationToken}
  → web-gateway → identity-service
  → identity-service:
      validate token (tồn tại, chưa expired, status=PENDING)
      UserAccount.status = ACTIVE
      publish identity.user.activated {userId}  [via Outbox]
  → return 200 OK

        ↓ Kafka async — identity.user.activated

  → oauth2-service consume UserActivated:
      Credential.status = ACTIVE
```

### Error Cases — Registration

| Lỗi | Xử lý | Response |
|---|---|---|
| Email đã tồn tại | oauth2-service kiểm tra Credential table, reject sync | 409 Conflict |
| Password không đủ mạnh | oauth2-service validate | 400 Bad Request |
| UserRegistered event xử lý 2 lần | identity-service idempotent (find-or-skip theo userId) | — silent |
| Token hết hạn (>24h) | identity-service reject | 400 TokenExpired |
| Token không tồn tại | identity-service reject | 400 |
| Resend quá 3 lần/giờ | identity-service rate limit | 429 Too Many Requests |
| Resend với user ACTIVE | identity-service reject — response đồng nhất (không lộ trạng thái) | 400 |

### Events mới so với flow cũ

| Event cũ (identity-service) | Event mới | Publisher mới |
|---|---|---|
| `identity.user.registered` | `oauth2.user.registered` | oauth2-service |
| `identity.user.registered` (dùng cho verification email) | `identity.verification.email.requested` | identity-service |
| _(không có)_ | `identity.user.activated` | identity-service |

> notification-service cần cập nhật topic consumer:
> `identity.user.registered` → `identity.verification.email.requested`

---

## B. Login — Authentication & Device Recording

Authentication (validate credential, issue token) hoàn toàn thuộc `oauth2-service`.
`identity-service` **không tham gia vào login synchronous path** — chỉ consume event sau khi login xong.

### Pre-conditions

- User đã có Credential với status=ACTIVE
- `Credential.mfaEnabled` được check in-process — không có extra DB query (denormalized field)

### MFA — Cách tiếp cận

`MfaConfig` là entity riêng trong oauth2-service lưu OTP secret. Tuy nhiên `mfaEnabled` flag được
denormalize thành column trên `Credential` để login hot path chỉ cần 1 DB read duy nhất (load
Credential để validate password, check status, check mfaEnabled — cùng lúc).

Khi user bật/tắt MFA: cập nhật `MfaConfig.enabled` và `Credential.mfaEnabled` trong cùng 1 transaction.

MFA là **user opt-in** (default `false`). Framework: Spring Security 7 `@EnableMultiFactorAuthentication`
+ `RequiredAuthoritiesRepository` backed bởi `Credential.mfaEnabled`.
Chi tiết framework flow → [`docs/architecture/spring-security-mfa-bff.md`](../../../architecture/spring-security-mfa-bff.md)

### Happy Path — Login (không có MFA)

```
Buyer
  → POST /api/auth/login  {email, password}  (+ UserAgent, IP từ header)
  → web-gateway → oauth2-service (Authorization Code Flow + PKCE)
  → oauth2-service:
      validate Credential (email, hashedPassword)
      check status: PENDING → reject, LOCKED → reject, ACTIVE → proceed
      check Credential.mfaEnabled = false → skip OTT flow
      generate deviceId = hash(userAgent + ip + fingerprint)
      tạo OAuthSession (sid, userId, deviceId)
      issue JWT access token + opaque refresh token
      publish oauth2.device.login.recorded
             {deviceId, userId, userAgent, ip, loginStatus=SUCCESS, loginAt}  [via Outbox]
  → return token → web-gateway lưu vào httpOnly cookie

        ↓ Kafka async — oauth2.device.login.recorded

  → identity-service consume DeviceLoginRecorded:
      upsert Device:
        nếu deviceId chưa có → Device.record(deviceId, userId, userAgent, ip)
        nếu đã có → device.refreshLastSeen()
      append LoginActivity (userId, deviceId, loginStatus=SUCCESS, ip, userAgent, loginAt)
```

### Happy Path — Login (có MFA)

```
Buyer
  → POST /api/auth/login  {email, password}
  → oauth2-service:
      validate Credential → ACTIVE, mfaEnabled = true
      [First factor OK] session rotate → SecurityContext { PASSWORD_AUTHORITY }
      OTT auto-generate server-side (OneTimeTokenService) → lưu Redis TTL 5 phút
      GeneratedOneTimeTokenHandler → publish event gửi OTP qua email
      redirect → /login/ott (form nhập OTP)

  Buyer nhập OTP → POST /login/ott {token}
  → oauth2-service:
      consume OTT (GETDEL atomic) → verify
      [Second factor OK] session rotate → SecurityContext { PASSWORD_AUTHORITY, OTT_AUTHORITY }
      → fully authenticated
      generate deviceId, tạo OAuthSession, issue tokens
      publish oauth2.device.login.recorded {..., loginStatus=SUCCESS}  [via Outbox]
  → return token → web-gateway lưu vào httpOnly cookie

        ↓ Kafka async — oauth2.device.login.recorded (giống non-MFA)
```

> `publish oauth2.device.login.recorded` chỉ xảy ra sau khi **cả 2 factors** verified thành công.
> `loginAt` phản ánh thời điểm hoàn thành MFA, không phải thời điểm nhập password.

### Happy Path — Login thất bại (sai password)

```
Buyer → POST /api/auth/login {email, wrongPassword}
oauth2-service:
  validate credential → sai password
  publish oauth2.device.login.recorded
         {deviceId, userId, userAgent, ip, loginStatus=FAILED, loginAt}
  return 401 Unauthorized

  ↓ Kafka async

identity-service consume DeviceLoginRecorded:
  KHÔNG upsert Device (chỉ append LoginActivity với status=FAILED)
  append LoginActivity (loginStatus=FAILED)
```

> Lý do: FAILED login không confirm device là của user này — chỉ ghi nhận để audit.

### Happy Path — Login với account bị lock

```
oauth2-service:
  Credential.status=LOCKED → reject ngay
  publish oauth2.device.login.recorded {..., loginStatus=BLOCKED}
  return 423 Locked

identity-service:
  append LoginActivity (loginStatus=BLOCKED)
```

### Error Cases — Login

| Lỗi | Xử lý | Response |
|---|---|---|
| Email không tồn tại | oauth2-service reject | 401 (không tiết lộ email tồn tại hay không) |
| Sai password | oauth2-service reject | 401 |
| status=PENDING | oauth2-service reject | 403 EmailNotVerified |
| status=LOCKED | oauth2-service reject | 423 Locked |
| MFA OTP sai hoặc expired | oauth2-service reject (OneTimeTokenService.consume → nil) | 401 |

---

## C. Device Management REST APIs

Sau khi login, user có thể xem và quản lý các thiết bị đã đăng nhập.
`identity-service` expose 3 endpoints — tất cả yêu cầu JWT hợp lệ (userId lấy từ `sub` claim).

### UC-1: Xem danh sách thiết bị

```
GET /api/v1/identity/me/devices
Authorization: Bearer {access_token}

Response 200 OK:
{
  "devices": [
    {
      "deviceId": "01HX...",
      "userAgent": "Mozilla/5.0 ...",
      "ip": "192.168.1.1",
      "firstSeenAt": "2025-05-01T08:00:00Z",
      "lastSeenAt": "2025-05-30T10:00:00Z"
    }
  ]
}
```

Chỉ trả về devices có status=ACTIVE.

### UC-2: Revoke thiết bị

```
DELETE /api/v1/identity/me/devices/{deviceId}
Authorization: Bearer {access_token}

Response 204 No Content

Errors:
  404 — deviceId không tồn tại
  409 — device đã bị revoke trước đó
  403 — device không thuộc về userId trong token
```

Revoke không terminate session hiện tại của device đó (session được quản lý bởi oauth2-service).
Revoke chỉ đánh dấu device là không còn được trust — oauth2-service cần consume event `DeviceRevoked`
để invalidate session liên quan (deferred — Phase 2).

### UC-3: Xem lịch sử đăng nhập

```
GET /api/v1/identity/me/login-activity?offset=0&limit=20
Authorization: Bearer {access_token}

Response 200 OK:
{
  "activities": [
    {
      "id": "01HX...",
      "deviceId": "01HX...",
      "loginStatus": "SUCCESS",
      "ip": "192.168.1.1",
      "userAgent": "Mozilla/5.0 ...",
      "loginAt": "2025-05-30T10:00:00Z"
    }
  ],
  "total": 42,
  "offset": 0,
  "limit": 20
}
```

Trả về tất cả login attempts (SUCCESS + FAILED + BLOCKED), sắp xếp `loginAt DESC`.

---

## Domain Model mới trong identity-service

### `Device` Aggregate

| Field | Type | Mô tả |
|---|---|---|
| `deviceId` | DeviceId (ULID) | PK, do oauth2-service generate |
| `userId` | UserId | Foreign key tới users |
| `userAgent` | String | Browser/app identifier |
| `ip` | String | IP tại thời điểm first seen |
| `status` | DeviceStatus | ACTIVE \| REVOKED |
| `firstSeenAt` | Instant | Lần đầu login từ device này |
| `lastSeenAt` | Instant | Login gần nhất từ device này |

**Behaviors:**
- `Device.record(deviceId, userId, userAgent, ip)` — factory, status=ACTIVE
- `device.refreshLastSeen()` — update lastSeenAt
- `device.revoke()` — status=REVOKED, guard nếu đã REVOKED

### `LoginActivity` Entity (immutable)

| Field | Type | Mô tả |
|---|---|---|
| `id` | LoginActivityId (ULID) | PK |
| `userId` | UserId | Ai đăng nhập |
| `deviceId` | DeviceId | Từ thiết bị nào |
| `loginStatus` | LoginStatus | SUCCESS \| FAILED \| BLOCKED |
| `ip` | String | IP tại thời điểm login |
| `userAgent` | String | |
| `loginAt` | Instant | Thời điểm login attempt |

Immutable record — không có behavior, chỉ factory `LoginActivity.record(...)`.

---

## Kafka Events

### Inbound (consumed by identity-service)

| Topic | Publisher | Khi nào | Payload |
|---|---|---|---|
| `oauth2.user.registered` | oauth2-service | User đăng ký | `{userId, email, fullName, registrationMethod}` |
| `oauth2.device.login.recorded` | oauth2-service | Mỗi login attempt | `{deviceId, userId, userAgent, ip, loginStatus, loginAt}` |

### Outbound (published by identity-service)

| Topic | Khi nào | Payload |
|---|---|---|
| `identity.verification.email.requested` | Sau khi tạo UserAccount (CREDENTIAL) | `{userId, email, fullName, verificationToken}` |
| `identity.user.activated` | Sau khi verify email thành công | `{userId, email, fullName}` |
| `identity.email-verification.reissued` | Sau resend verification | `{userId, email, fullName, verificationToken}` |

> Các event outbound vẫn dùng Outbox pattern (OutboxEventStore → poller → Kafka).

---

## Post-conditions

**Sau khi refactor registration:**
- `POST /api/users/register` không còn tồn tại trên identity-service
- `User` aggregate không còn chứa `hashedPassword` và `role`
- identity-service không còn phụ thuộc vào `PasswordService`
- notification-service consume `identity.verification.email.requested` thay vì `identity.user.registered`

**Sau khi login feature hoàn thành:**
- Mỗi lần login thành công → Device được upsert, LoginActivity được append
- Mỗi lần login thất bại → LoginActivity được append (device không được tạo)
- User có thể xem danh sách devices và revoke từng device
- User có thể xem toàn bộ lịch sử login (kể cả failed attempts)
