# identity-service

**Vai trò**: User account management — tạo và quản lý `UserAccount`, email verification, Device, LoginActivity.  
**DB**: PostgreSQL  
**Libs**: `common-domain`, `outbox-starter`, `common-events`, `common-web`, `observability-starter`

---

## Domain Model

| Aggregate | Fields | Notes |
|---|---|---|
| `UserAccount` | userId, email, fullName, phoneNumber, status | Source of truth cho account state |
| `EmailVerification` | id, userId, email, token, expiresAt, status | Token TTL 24h, rotate khi reissue |
| `Device` | deviceId, userId, fingerprint, userAgent, ip | Populated từ event, user-facing revoke |
| `LoginActivity` | userId, deviceId, status, ip, timestamp | Append-only, read-only với user |

---

## API

### GET /api/users/verify?token={token}

Kích hoạt tài khoản. Validate token → `UserAccount.status = ACTIVE` → publish `UserActivated`.

**Response — 200 OK**
```json
{ "success": true, "data": { "userId": "01HXYZ..." } }
```

| Code | Condition |
|---|---|
| 404 | Token không tồn tại |
| 410 | Token đã hết hạn |
| 409 | Email đã verified |

---

### POST /api/users/resend-verification

Giới hạn 3 lần/giờ per email. Chỉ áp dụng khi `status=PENDING`.

**Request**
```json
{ "email": "buyer@example.com" }
```

**Response — 204 No Content**

| Code | Condition |
|---|---|
| 400 | User không ở trạng thái PENDING hoặc email không tồn tại (response đồng nhất) |
| 429 | Vượt 3 lần/giờ per email |

---

## Events Consumed

| Topic | Event | Handler |
|---|---|---|
| `oauth2.user.registered` | `UserRegistered` | `CreateUserAccount` — tạo `UserAccount` + `EmailVerification`, publish `VerificationEmailRequested` |
| `oauth2.device.login-recorded` | `DeviceLoginRecorded` | Upsert `Device`, append `LoginActivity` |

---

## Events Published

| Event | Topic | Trigger |
|---|---|---|
| `CustomerAccountCreated` | `identity.customer-account.created` | Sau khi tạo `UserAccount` với role=CUSTOMER |
| `VerificationEmailRequested` | `identity.email-verification.requested` | Sau khi tạo `EmailVerification` (CREDENTIAL only) |
| `VerificationReissuedEvent` | `identity.email-verification.reissued` | Sau khi reissue token |
| `EmailVerifiedEvent` | `identity.email-verification.verified` | Sau khi verify thành công |
| `UserActivated` | `identity.user.activated` | Sau khi `UserAccount.status = ACTIVE` |

---

## Business Rules

- `userId` do oauth2-service generate (ULID), identity-service nhận qua event — không tự generate
- `UserAccount` tạo với `status=PENDING` khi `registrationMethod=CREDENTIAL`, `status=ACTIVE` khi OAuth
- `EmailVerification` token: opaque 32-byte random, Base64URL encoded, TTL 24h
- Reissue token rotate hoàn toàn — token cũ bị invalidate ngay lập tức
- Account không cần biết password — password owned by oauth2-service

---

## Dependencies

| Dependency | Lý do |
|---|---|
| `common-domain` | `AggregateRoot`, `DomainEvent` |
| `outbox-starter` | Publish events reliable qua Outbox Pattern + CDC |
| `common-events` | `EventEnvelope`, `OutboxEventData` — Kafka contract |
| `common-web` | `ApiResponse`, `GlobalExceptionHandler` |
| `observability-starter` | Tracing + structured logging |
