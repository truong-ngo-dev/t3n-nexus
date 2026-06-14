# Security Architecture — t3n-nexus

Chi tiết quyết định tách IAM: `adr/001-iam-services.md`

---

## 1. Authentication Model

### Browser (3 Angular apps)

```
Browser
  │  httpOnly session cookie
  ▼
web-gateway (BFF + Gateway)    ← session validation, routing, proxy — token không expose ra browser
  │  forward với JWT nội bộ
  ▼
Domain services
```

- Flow: **Authorization Code + PKCE** — oauth2-service xử lý toàn bộ
- web-gateway đóng vai cả BFF lẫn API Gateway cho browser — không có hop api-gateway riêng
- Token không bao giờ xuất hiện trên browser — web-gateway exchange session → JWT nội bộ rồi forward
- MFA: email OTP, inject sau first factor, access token chỉ được issue sau khi OTP verified
- Social login: oauth2-service đóng vai OAuth2 Client với Google, transparent với browser

### Shipper (Simulated)

> Chưa có mobile app thực — Shipper client hiện được giả lập bởi `simulator-service`.

**Kiến trúc mục tiêu** (khi có mobile app):
```
Shipper App
  │  Bearer token (trực tiếp)
  ▼
api-gateway                    ← validate JWT
  │
  ▼
Domain services
```

**Hiện tại — simulator-service:** không bị ràng buộc bởi client flow, có thể gọi trực tiếp vào service hoặc qua web-gateway tuỳ scenario — miễn là request kèm đủ data cần thiết. Auth constraint không áp dụng cho internal simulator.

---

## 2. Token Model

| Token          | Loại   | Nơi lưu                                             | Lifetime         |
|----------------|--------|-----------------------------------------------------|------------------|
| Access token   | JWT    | web-gateway memory (browser) / app memory (shipper) | Ngắn (minutes)   |
| Refresh token  | Opaque | oauth2-service DB                                   | Dài (days)       |
| Session cookie | Opaque | Browser (httpOnly)                                  | Theo session TTL |

JWT access token chứa: `userId`, `role`, `deviceId`, `sessionId` — đủ để ABAC evaluate in-process mà không cần gọi identity-service.

---

## 3. Authorization Model

**ABAC (Attribute-Based Access Control)** — evaluation xảy ra **in-process tại mỗi service** qua shared library, không có network call trên critical path.

```
web-gateway / api-gateway      ← AuthN: validate session/JWT signature + expiry
  │  forward với user context (userId, role, deviceId)
  ▼
Domain service
  └── ABAC check (in-process)  ← AuthZ: evaluate policy với attributes từ JWT + resource
```

### Roles

`GUEST` · `CUSTOMER` · `SELLER` · `SHIPPER` · `ADMIN`

**1 user — 1 role.** Muốn vừa mua vừa bán phải đăng ký 2 account riêng biệt.

---

## 4. Trust Boundaries

```
────────────────────────────────────────────────────────
  PUBLIC ZONE
  Browser (3 apps)          Shipper (simulator-service*)
       │                                 │
  web-gateway                       api-gateway
  (BFF + Gateway)                  (JWT validation)
  session validation + routing
────────────────────────────────────────────────────────
  INTERNAL ZONE
  Domain services — trust sau khi gateway đã validate
────────────────────────────────────────────────────────
* Chưa có mobile app thực — simulator-service đóng vai Shipper client
```

**Nguyên tắc:**
- Browser traffic đi qua `web-gateway` — vừa là BFF vừa là gateway, không có thêm hop nào
- Shipper traffic đi qua `api-gateway` — validate JWT trực tiếp *(kiến trúc mục tiêu; hiện tại simulator-service gọi trực tiếp hoặc qua web-gateway, không bị ràng buộc client flow)*
- Hai path độc lập — không overlap
- Domain services **không re-validate** JWT — trust internal network sau gateway
- ABAC tại service level là **authZ** (quyền trên resource), không phải authN
- EMQX (MQTT) là trust boundary riêng — Shipper app connect trực tiếp, authentication qua MQTT credentials do oauth2-service cấp

---

## 5. Account State & Propagation

Account state là source of truth tại `identity-service`, được sync sang `oauth2-service` qua Kafka:

| Event           | Trigger                        | Effect tại oauth2-service        |
|-----------------|--------------------------------|----------------------------------|
| `UserActivated` | Email verification thành công  | `UserCredential.status = ACTIVE` |
| `UserLocked`    | Admin lock hoặc policy trigger | `UserCredential.status = LOCKED` |
| `UserUnlocked`  | Admin unlock                   | `UserCredential.status = ACTIVE` |

`UserCredential.status` là **eventual consistent** với `UserAccount.status` — window nhỏ nhưng tồn tại. Login hot path đọc từ `UserCredential` local tại oauth2-service, không cross-service call.

---

## 6. Session & Device Management

Chi tiết lifecycle và TTL: `spring-security-login-mfa-bff.md`, `spring-security-logout-bff.md`

- oauth2-service generate `deviceId = hash(userAgent + ip + fingerprint)`, lưu vào `OAuthSession`
- identity-service own `Device` và `LoginActivity` — user có thể xem và revoke device
- Session invalidation (logout / revoke device) propagate qua Kafka để invalidate downstream state
