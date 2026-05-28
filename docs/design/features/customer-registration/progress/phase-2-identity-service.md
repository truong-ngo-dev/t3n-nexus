# Phase 2 — identity-service

**Status:** `TODO`
**Started:** —
**Completed:** —

## Checklist

- [x] Tạo `design/services/identity-service.md`
- [x] Tạo `design/data/identity-service.md` — bao gồm bảng `verification_tokens` (token, userId, expiresAt)
- [x] Tạo `design/api/identity-service.yaml` — `POST /api/users/register`, `GET /api/users/verify`, `POST /api/users/resend-verification`
- [x] Thêm role `CUSTOMER` vào enum roles
- [x] Tạo user với `status=PENDING` (không phải ACTIVE ngay)
- [x] Generate `verificationToken` (opaque, TTL 24h), lưu vào `verification_tokens`
- [x] Implement `GET /api/users/verify?token=...` → validate token, set `status=ACTIVE`, xóa token
- [x] Implement `POST /api/users/resend-verification` → invalidate token cũ, generate token mới, publish `identity.user.verification-resent`
- [x] Implement publish `identity.customer.registered` (payload gồm `verificationToken`) sau khi tạo user thành công
- [x] Implement Outbox tại identity-service (áp dụng cho cả 2 events)

## Verify

```sql
-- 1. Sau khi tạo user mới: status phải là PENDING
SELECT id, email, status FROM users ORDER BY created_at DESC LIMIT 1;
-- Expect: status = 'PENDING'

-- 2. verificationToken được tạo
SELECT * FROM verification_tokens WHERE user_id = '<userId>';
-- Expect: 1 row, expires_at = created_at + 24h

-- 3. Event xuất hiện trong outbox
SELECT event_type, payload FROM outbox ORDER BY created_at DESC LIMIT 1;
-- Expect: event_type = 'identity.customer.registered', payload chứa userId + email + verificationToken
```

```http
### Verify email thành công
GET /api/users/verify?token={verificationToken}
# Expect: HTTP 200, {"message": "Account activated. Please log in."}

### Verify lần 2 cùng token (token đã xóa)
GET /api/users/verify?token={verificationToken}
# Expect: HTTP 400

### Token hết hạn
GET /api/users/verify?token={expiredToken}
# Expect: HTTP 400, {"message": "Verification link expired. Request a new one."}

### Resend thành công
POST /api/users/resend-verification
Content-Type: application/json

{ "email": "test@example.com" }
# Expect: HTTP 200

### Resend với user đã ACTIVE (response đồng nhất — không lộ thông tin)
POST /api/users/resend-verification
Content-Type: application/json

{ "email": "active@example.com" }
# Expect: HTTP 400

### Rate limit resend: gửi lần thứ 4 trong 1 giờ
# Expect: HTTP 429
```

## Session Log
