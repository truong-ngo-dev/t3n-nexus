# Design: Customer Registration

**UC gốc**: Buyer đăng ký tài khoản (`../../../requirement.md`)
**Sequence**: [`sequence.puml`](sequence.puml)
**Implementation plan**: [`implementation.md`](implementation.md)
**Status**: Draft

---

## Services liên quan

| Service                | Vai trò                                           | Loại tham gia          |
|------------------------|---------------------------------------------------|------------------------|
| `web-gateway`          | BFF — nhận request, forward đến identity-service  | Entry point            |
| `identity-service`     | Validate, hash password, tạo user, publish event  | Sync + Event publisher |
| `oauth2-service`       | Phát token khi login (không tham gia register)    | Không tham gia         |
| `customer-service`     | Tạo CustomerProfile khi nhận CustomerRegistered   | Async consumer         |
| `notification-service` | Gửi verification email (link kích hoạt tài khoản) | Async consumer         |

---

## Pre-conditions

- Email chưa tồn tại trong hệ thống

---

## Happy Path

```
Buyer
  → POST /api/users/register  (web-gateway)
  → web-gateway forward → identity-service
  → identity-service validate + hash password
    + tạo User (role=CUSTOMER, status=PENDING)
    + generate verificationToken (lưu vào DB, TTL 24h)
  → identity-service publish identity.customer.registered qua Outbox (Kafka)
  → identity-service trả về 201 Created (không có token)
        ↓ Kafka (async)
  → customer-service nhận identity.customer.registered
    → tạo CustomerProfile (loyaltyBalance=0)
        ↓ Kafka (async)
  → notification-service nhận identity.customer.registered
    → gửi verification email (link kích hoạt chứa verificationToken)

-- Buyer click link trong email --
  → GET /api/users/verify?token={verificationToken}  (web-gateway → identity-service)
  → identity-service validate token + set status=ACTIVE
  → identity-service trả về 200 OK
  → Buyer có thể login qua oauth2-service
```

**Phần synchronous**: web-gateway → identity-service — buyer nhận response ngay.  
**Phần async**: CustomerProfile + verification email tạo sau, buyer không chờ.  
**Email verification bắt buộc**: User mới có status=PENDING — oauth2-service từ chối login cho đến khi status=ACTIVE.  
**Login riêng**: Sau khi verify email, buyer login qua oauth2-service để nhận token.

---

## Error Cases

| Lỗi                               | Xử lý                                          | Response                |
|-----------------------------------|------------------------------------------------|-------------------------|
| Email đã tồn tại                  | identity-service reject ngay (sync)            | 409 Conflict            |
| Password không đủ mạnh            | Validate tại identity-service                  | 400 Bad Request         |
| identity-service down             | web-gateway trả lỗi                            | 503 Service Unavailable |
| customer-service chậm xử lý event | Retry tự động — buyer không bị ảnh hưởng       | —                       |
| verificationToken hết hạn (>24h)  | identity-service từ chối — buyer dùng resend   | 400 Bad Request         |
| verificationToken không tồn tại   | identity-service từ chối (tampered/đã dùng)    | 400 Bad Request         |
| Resend vượt 3 lần/giờ per email   | identity-service reject                        | 429 Too Many Requests   |
| Quá 5 request/phút từ cùng IP     | web-gateway rate limit — không forward request | 429 Too Many Requests   |

Không có compensating saga — phần critical path là sync, phần downstream là fire-and-forget.

---

## Yêu cầu kỹ thuật

| Concern            | Giải pháp                                                                           |
|--------------------|-------------------------------------------------------------------------------------|
| Idempotency        | identity-service dedup theo email — gọi 2 lần không tạo 2 user                      |
| Event delivery     | Outbox Pattern tại identity-service khi publish `identity.customer.registered`          |
| Password           | BCrypt hash tại identity-service — không lưu plaintext                              |
| Token              | Không phát token khi đăng ký — login riêng qua oauth2-service                       |
| UserStatus         | Tạo user với `status=PENDING` — chỉ set `ACTIVE` sau khi verify email thành công    |
| Verification token | Opaque token, TTL 24h, lưu tại bảng `verification_tokens` trong identity-service DB |
| Rate limiting      | web-gateway giới hạn `POST /api/users/register`: 5 req/phút per IP                  |
| Email enumeration  | Response 409 giữ nguyên — chấp nhận vì đây là thông tin không nhạy cảm              |

---

## Ghi chú IAM Services

Các thay đổi cần cho UC này:
- `identity-service`: thêm role `CUSTOMER`, thêm `status=PENDING` khi tạo user, generate và lưu `verificationToken`, publish `identity.customer.registered`, expose `GET /api/users/verify` và `POST /api/users/resend-verification`
- `oauth2-service`: từ chối login nếu `UserStatus=PENDING` — không thay đổi logic khác
- `web-gateway`: cấu hình rate limiting cho `POST /api/users/register`

Chi tiết: [`adr/001-iam-services.md`](../../../architecture/adr/001-iam-services.md)
