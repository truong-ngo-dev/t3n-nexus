# Implementation Plan: Customer Registration

**Design**: [`design.md`](design.md)
**Sequence**: [`sequence.puml`](sequence.puml)

---

## Tài liệu cần tạo / cập nhật

| Tài liệu                                  | Hành động | Nội dung cần thêm                                                                                                                                       |
|-------------------------------------------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `design/services/identity-service.md`     | Tạo mới   | Aggregate: `User`, Commands: `CreateUser`, `VerifyEmail`, `ResendVerification`, Events: `identity.customer.registered`, `identity.user.verification-resent` |
| `design/services/customer-service.md`     | Tạo mới   | Aggregate: `CustomerProfile`, Consumer: `identity.customer.registered`                                                                                      |
| `design/services/notification-service.md` | Tạo mới   | Consumer: `identity.customer.registered` → verification email; `identity.user.verification-resent` → resend email                                           |
| `design/api/identity-service.yaml`        | Tạo mới   | `POST /api/users/register`, `GET /api/users/verify`, `POST /api/users/resend-verification`                                                              |
| `design/data/identity-service.md`         | Tạo mới   | Table: `users` (thêm cột `status`), `outbox`, `verification_tokens`                                                                                     |
| `design/data/customer-service.md`         | Tạo mới   | Table: `customer_profiles`                                                                                                                              |
| `design/events/event-catalog.md`          | Cập nhật  | Thêm 2 topics: `identity.customer.registered` và `identity.user.verification-resent`                                                                        |

---

## Thứ tự triển khai

### Bước 1 — Shared libs (nếu chưa có)

- [ ] `common-domain`: `AggregateRoot`, `DomainEvent`
- [ ] `common-web`: `ApiResponse`, `GlobalExceptionHandler`
- [ ] `observability-starter`: OTel + structured logging
- [ ] `outbox-starter`: Outbox auto-config

### Bước 2 — identity-service

identity-service là nguồn phát event — làm trước để xác định contract cho downstream.

- [ ] Tạo `design/services/identity-service.md`
- [ ] Tạo `design/data/identity-service.md` — bao gồm bảng `verification_tokens` (token, userId, expiresAt)
- [ ] Tạo `design/api/identity-service.yaml` — `POST /api/users/register`, `GET /api/users/verify`, `POST /api/users/resend-verification`
- [ ] Thêm role `CUSTOMER` vào enum roles
- [ ] Tạo user với `status=PENDING` (không phải ACTIVE ngay)
- [ ] Generate `verificationToken` (opaque, TTL 24h), lưu vào `verification_tokens`
- [ ] Implement `GET /api/users/verify?token=...` → validate token, set `status=ACTIVE`, xóa token
- [ ] Implement `POST /api/users/resend-verification` → invalidate token cũ, generate token mới, publish `identity.user.verification-resent`; rate limit 3 lần/giờ per email
- [ ] Implement publish `identity.customer.registered` event (payload gồm `verificationToken`) sau khi tạo user thành công
- [ ] Implement Outbox tại identity-service (áp dụng cho cả 2 events)
- [ ] Verify: tạo user → `status=PENDING` + event xuất hiện trong outbox table

### Bước 3 — customer-service

- [ ] Tạo `design/services/customer-service.md`
- [ ] Tạo `design/data/customer-service.md`
- [ ] Implement `CustomerProfile` aggregate
- [ ] Implement consumer `identity.customer.registered` → tạo `CustomerProfile` (loyaltyBalance=0)
- [ ] Implement idempotency: xử lý event trùng không tạo 2 profile

### Bước 4 — notification-service

- [ ] Tạo `design/services/notification-service.md` (nếu chưa có)
- [ ] Implement consumer `identity.customer.registered` → gửi verification email
- [ ] Implement consumer `identity.user.verification-resent` → gửi lại verification email (cùng template, token mới)
- [ ] Config email template — nội dung phải có link kích hoạt: `{baseUrl}/verify?token={verificationToken}`
- [ ] Implement idempotency cho cả 2 consumers: xử lý event trùng không gửi 2 email

### Bước 5 — Integration & docs

- [ ] Cập nhật `design/events/event-catalog.md` — 2 topics: `identity.customer.registered` và `identity.user.verification-resent`, partition key = `userId`
- [ ] Cập nhật sequence diagram nếu flow thực tế thay đổi
- [ ] Cấu hình rate limiting tại web-gateway: `POST /api/users/register` — 5 req/phút per IP
- [ ] Tạo ADR nếu có quyết định kỹ thuật mới

---

## Checklist hoàn thành UC

- [ ] Đăng ký thành công → nhận 201 Created (không có token)
- [ ] User mới có `status=PENDING` ngay sau đăng ký
- [ ] Verification email được gửi với link kích hoạt hợp lệ
- [ ] Click link kích hoạt → `status=ACTIVE`, có thể login bình thường
- [ ] verificationToken hết hạn (>24h) → `GET /verify` trả về 400
- [ ] Đăng ký email trùng → 409 Conflict
- [ ] `CustomerProfile` được tạo sau khi đăng ký (có thể delay async)
- [ ] Gọi register 2 lần cùng email → không tạo 2 user (idempotent)
- [ ] Gọi verify 2 lần cùng token → lần 2 trả về 400 (token đã xóa)
- [ ] Resend thành công → token cũ bị invalidate, token mới được gửi
- [ ] Resend vượt 3 lần/giờ cùng email → 429 Too Many Requests
- [ ] Resend với user ACTIVE hoặc email không tồn tại → 400 (response đồng nhất, không lộ thông tin)
- [ ] >5 request/phút từ cùng IP → 429 Too Many Requests
- [ ] Outbox hoạt động — `identity.customer.registered` không bị mất khi identity-service restart
- [ ] Tất cả service docs đã cập nhật đúng thực tế
