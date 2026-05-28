# identity-service

**Vai trò**: User management — tạo, quản lý user accounts, credentials, roles và email verification.  
**DB**: PostgreSQL  
**Libs**: `common-domain`, `outbox-starter`, `common-web`, `observability-starter`, `security-commons`

---

## API

### POST /api/users/register

Tạo user mới với `role=CUSTOMER`, `status=PENDING`. Không phát token.

**Request**
```json
{
  "email": "buyer@example.com",
  "password": "Test@1234",
  "fullName": "Nguyen Van A"
}
```

**Validation**
- `email`: format hợp lệ, unique trong hệ thống
- `password`: tối thiểu 8 ký tự, có chữ hoa, chữ thường, số, ký tự đặc biệt
- `fullName`: không rỗng, tối đa 100 ký tự — nullable ở DB để hỗ trợ Google login (displayName không đảm bảo có)

**Response — 201 Created**
```json
{
  "success": true,
  "data": { "userId": "01HXYZ..." }
}
```

**Errors**

| Code | Condition                                   |
|------|---------------------------------------------|
| 400  | Validation fail                             |
| 409  | Email đã tồn tại                            |
| 429  | Vượt rate limit (enforced tại web-gateway)  |

---

### GET /api/users/verify?token={verificationToken}

Kích hoạt tài khoản. Validate token, set `status=ACTIVE`, xóa token.

**Response — 200 OK**
```json
{
  "success": true,
  "data": { "message": "Account activated. Please log in." }
}
```

**Errors**

| Code | Condition                          |
|------|------------------------------------|
| 400  | Token hết hạn (`TOKEN_EXPIRED`)    |
| 400  | Token không tồn tại / đã dùng (`TOKEN_INVALID`) |

---

### POST /api/users/resend-verification

Invalidate token cũ, generate token mới (TTL 24h), publish `identity.user.verification-resent`.  
Giới hạn 3 lần/giờ per email.

**Request**
```json
{
  "email": "buyer@example.com"
}
```

**Response — 200 OK**
```json
{
  "success": true,
  "data": { "message": "Verification email resent." }
}
```

**Errors**

| Code | Condition                                                                    |
|------|------------------------------------------------------------------------------|
| 400  | User đã `ACTIVE` hoặc email không tồn tại (response đồng nhất, không lộ thông tin) |
| 429  | Vượt 3 lần/giờ per email                                                     |

---

## Business Rules

- Password hash bằng **BCrypt** — không lưu plaintext
- `userId` dùng **ULID** (sortable, không đoán được)
- Role mặc định khi đăng ký: `CUSTOMER`
- User mới tạo với `status=PENDING` — oauth2-service từ chối login cho đến khi `status=ACTIVE`
- `verificationToken`: opaque, TTL 24h, lưu bảng `verification_tokens` — xóa sau khi dùng
- Sau khi tạo user thành công → publish `identity.customer.registered` qua **Outbox Pattern** trong cùng transaction

---

## Events Published

| Event                              | Topic                                   | Trigger                              |
|------------------------------------|-----------------------------------------|--------------------------------------|
| `CustomerRegistered`               | `identity.customer.registered`          | Sau khi tạo user thành công          |
| `UserVerificationResent`           | `identity.user.verification-resent`     | Sau khi resend verification thành công |

**`identity.customer.registered` payload**
```json
{
  "userId": "01HXYZ...",
  "email": "buyer@example.com",
  "fullName": "Nguyen Van A",
  "role": "CUSTOMER",
  "verificationToken": "a1b2c3...",
  "occurredOn": "2026-05-24T10:00:00Z"
}
```

**`identity.user.verification-resent` payload**
```json
{
  "userId": "01HXYZ...",
  "email": "buyer@example.com",
  "verificationToken": "x9y8z7...",
  "occurredOn": "2026-05-24T10:10:00Z"
}
```

---

## Dependencies

| Dependency              | Lý do                                        |
|-------------------------|----------------------------------------------|
| `common-domain`         | `AggregateRoot`, `DomainEvent`               |
| `outbox-starter`        | Publish events reliable qua Outbox Pattern   |
| `common-web`           | `ApiResponse`, `GlobalExceptionHandler`      |
| `observability-starter` | Tracing + structured logging                 |
| `security-commons`      | JWT utils, Spring Security config            |
