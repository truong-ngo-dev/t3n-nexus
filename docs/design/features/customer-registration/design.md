# Design: Customer Registration

**Sequence**: [`sequence.puml`](sequence.puml)
**Status**: Draft

---

## Services liên quan

| Service                | Vai trò                                                           | Loại tham gia       |
|------------------------|-------------------------------------------------------------------|---------------------|
| `web-gateway`          | BFF — nhận request, forward đến oauth2-service                    | Entry point         |
| `oauth2-service`       | Validate input, hash password, tạo UserCredential, publish event  | Sync + Event publisher |
| `identity-service`     | Tạo UserAccount + EmailVerification, publish events downstream    | Async + Event publisher |
| `customer-service`     | Tạo CustomerProfile khi nhận CustomerAccountCreated               | Async consumer      |
| `notification-service` | Gửi verification email khi nhận VerificationEmailRequested        | Async consumer      |

---

## Happy Path — CREDENTIAL

```
Buyer → POST /api/auth/register {email, password, fullName}
  → oauth2-service: tạo UserCredential (role=CUSTOMER, status=PENDING)
  → publish oauth2.user.registered {userId, email, fullName, role=CUSTOMER, registrationMethod=CREDENTIAL}

  [async] identity-service:
    → tạo UserAccount (status=PENDING) + EmailVerification (TTL 24h)
    → publish identity.email-verification.requested
    → publish identity.customer-account.created

  [async] customer-service ← identity.customer-account.created:
    → tạo CustomerProfile (loyaltyBalance=0)

  [async] notification-service ← identity.email-verification.requested:
    → gửi verification email

-- Buyer click link trong email --
  → GET /api/identity/users/verify?token={token}
  → identity-service: UserAccount.status = ACTIVE
  → publish identity.user.activated

  [async] oauth2-service ← identity.user.activated:
    → UserCredential.status = ACTIVE
    → Buyer có thể login
```

## Happy Path — OAUTH (Google)

```
Buyer → POST /api/auth/register/oauth2 {provider=GOOGLE, code=...}
  → oauth2-service: exchange code → Google profile
    tạo UserCredential (role=CUSTOMER, status=ACTIVE)
    tạo SocialIdentity
  → publish oauth2.user.registered {registrationMethod=OAUTH}
  → trả về tokens ngay (login thành công)

  [async] identity-service:
    → tạo UserAccount (status=ACTIVE, không có EmailVerification)
    → publish identity.customer-account.created

  [async] customer-service ← identity.customer-account.created:
    → tạo CustomerProfile
```

---

## Error Cases

| Lỗi | Nơi xử lý | Response |
|---|---|---|
| Email đã tồn tại | oauth2-service (sync) | 409 Conflict |
| oauth2-service down | web-gateway | 503 Service Unavailable |
| Token không tồn tại | identity-service (sync) | 404 |
| Token hết hạn | identity-service (sync) | 410 |
| Email đã verified | identity-service (sync) | 409 |
| Resend vượt 3 lần/giờ | identity-service (sync) | 429 Too Many Requests |
| customer-service chậm xử lý | Kafka retry tự động | — |

Không có compensating saga — critical path là sync, downstream là fire-and-forget.

---

## Technical Constraints

| Concern | Giải pháp |
|---|---|
| Idempotency | identity-service skip nếu userId đã tồn tại; customer-service `ON CONFLICT DO NOTHING` |
| Event delivery | Outbox Pattern + CDC tại oauth2-service và identity-service |
| Password | BCrypt hash tại oauth2-service — identity-service không biết password |
| Email verification | Chỉ áp dụng cho `registrationMethod=CREDENTIAL`, không áp dụng cho OAUTH |
| Token | Opaque 32-byte random, Base64URL, TTL 24h, rotate khi reissue |
| CustomerProfile timing | Tạo async sau UserAccount — eventual consistency chấp nhận được |
