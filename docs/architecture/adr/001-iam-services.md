# ADR-001 — Thiết kế IAM Services

**Status:** Accepted

## Context

Hệ thống cần IAM đầy đủ: authentication, MFA, session management, ABAC authorization, device tracking, social login. IAM là generic domain — không tạo competitive advantage, cần thiết kế tách biệt khỏi business domain để các domain service không bị coupling vào logic auth.

NFR: 20,000 concurrent users, 8,000 RPS. Login hot path không thể có cross-service call — credential validation phải in-process tại oauth2-service.

## Decision

Tách IAM thành 2 services độc lập với ownership rõ ràng:

---

### `oauth2-service` — Authorization Server

**Aggregates / domain objects:**

| Object | Mô tả |
|---|---|
| `UserCredential` | email, hashedPassword, role, status (replica) — login hot path |
| `SocialIdentity` | provider, providerSub, userId — social login lookup |
| `MfaConfig` | userId, enabled, method=EMAIL |
| `OAuthSession` | sid, userId, deviceId — wrap SAS OAuth2Authorization |

**Responsibilities:**
- Spring Authorization Server — Authorization Code Flow + PKCE
- Opaque refresh token, JWT access token
- MFA email OTP — intercept sau first factor, issue code chỉ sau OTP verified
- Social login (Google) — oauth2-service đóng vai OAuth2 Client với Google
- Registration entry point — tạo `Credential`, publish `UserRegistered` event
- Generate deviceId từ fingerprint (hash), lưu vào `OAuthSession`, publish `DeviceLoginRecorded`
- Consume event từ identity-service để sync `UserCredential.status`

---

### `identity-service` — Identity & User Management

**Aggregates / domain objects:**

| Object | Mô tả |
|---|---|
| `UserAccount` | userId, email, fullName, phoneNumber, status — source of truth |
| `EmailVerification` | token, userId, expiresAt — registration/verification flow |
| `Device` | deviceId, userId, fingerprint, userAgent, ip — populated từ event |
| `LoginActivity` | userId, deviceId, status, ip, timestamp — populated từ event |

**Responsibilities:**
- Email verification flow — `GET /verify?token=...`, publish `UserActivated`
- Profile management — update fullName, phoneNumber
- Device management API — list, revoke device (user-facing)
- Login history API — user xem lịch sử đăng nhập
- Account lock/unlock — source of truth, publish `UserLocked`/`UserUnlocked`
- ABAC policy management — in-process enforcement qua shared library, không có network call

---

### Interaction giữa 2 services

**Registration flow** (oauth2-service là entry point):
```
POST /api/v1/auth/register → oauth2-service
  tạo UserCredential (email, hash, role, status=PENDING)
  publish UserRegistered { userId, email, fullName, role, registrationMethod }
      └── identity-service: tạo UserAccount + EmailVerification (CREDENTIAL only)
          ├── publish VerificationEmailRequested { userId, email, token }
          │       └── notification-service: gửi email
          └── publish CustomerAccountCreated { userId, email, fullName }
                  └── customer-service: tạo CustomerProfile
```

**Email verification** (identity-service xử lý):
```
GET /api/v1/identity/verify?token=... → identity-service
  validate token → UserAccount.status = ACTIVE
  publish UserActivated { userId }
      └── oauth2-service: UserCredential.status = ACTIVE
```

**Credential status sync** (Kafka, identity-service → oauth2-service):
```
UserActivated  → UserCredential.status = ACTIVE
UserLocked     → UserCredential.status = LOCKED
UserUnlocked   → UserCredential.status = ACTIVE
```

**Device + LoginActivity** (oauth2-service write, identity-service own):
```
oauth2-service: authenticate
  → generate deviceId = hash(userAgent + ip + fingerprint)
  → lưu deviceId vào OAuthSession
  → publish DeviceLoginRecorded { deviceId, userId, userAgent, ip, status, timestamp }

identity-service: consume DeviceLoginRecorded
  → upsert Device entity
  → append LoginActivity record
```

---

### Roles

`GUEST`, `CUSTOMER`, `SELLER`, `SHIPPER`, `ADMIN`

**1 user — 1 role.** Muốn vừa mua vừa bán phải đăng ký 2 account riêng.
Schema: `credential.role VARCHAR(20)` single value, không dùng junction table.

---

### BFF & Token

- Browser flow: `web-gateway` (BFF) — httpOnly session cookie, token không expose ra Angular
- Shipper mobile app: token trực tiếp, không qua web-gateway
- 1 shared `web-gateway` cho cả 3 Angular app

## Consequences

**+** Login hot path hoàn toàn local tại oauth2-service — không cross-service call  
**+** identity-service là source of truth cho account state — domain BC chỉ cần gọi identity-service để lock/unlock  
**+** Device + LoginActivity serve từ identity-service — user-facing API không phụ thuộc oauth2-service  
**+** ABAC enforcement in-process — không có network call trên critical path  
**+** Registration flow rõ ràng — oauth2-service là entry point duy nhất tạo credential  
**−** Registration phải đổi entry point từ identity-service sang oauth2-service — impact implementation hiện tại  
**−** UserCredential.status là eventual consistent với UserAccount.status — window nhỏ nhưng tồn tại
