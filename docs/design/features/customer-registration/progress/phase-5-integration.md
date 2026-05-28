# Phase 5 — Integration & Docs

**Status:** `TODO`
**Started:** —
**Completed:** —

## Checklist

- [x] Cập nhật `design/events/event-catalog.md` — thêm 2 topics: `identity.customer.registered` và `identity.user.verification-resent`, partition key = `userId`
- [x] Cập nhật sequence diagram nếu flow thực tế thay đổi
- [ ] Tạo ADR nếu có quyết định kỹ thuật mới


## Verify

End-to-end checklist từ `implementation.md`:

- [x] Đăng ký thành công → 201 Created, không có token
- [x] User mới có `status=PENDING` ngay sau đăng ký
- [x] Verification email được gửi với link kích hoạt hợp lệ
- [x] Click link kích hoạt → `status=ACTIVE`, có thể login bình thường
- [x] verificationToken hết hạn (>24h) → `GET /verify` trả về 400
- [x] Đăng ký email trùng → 409 Conflict
- [x] `CustomerProfile` được tạo sau khi đăng ký (có thể delay async)
- [x] Gọi register 2 lần cùng email → không tạo 2 user (idempotent)
- [x] Gọi verify 2 lần cùng token → lần 2 trả về 400 (token đã xóa)
- [x] Resend thành công → token cũ bị invalidate, email mới được gửi
- [x] Resend vượt 3 lần/giờ cùng email → 429 Too Many Requests
- [x] Resend với user ACTIVE hoặc email không tồn tại → 400
- [x] Outbox hoạt động — events không bị mất khi identity-service restart
- [x] Tất cả service docs đã cập nhật đúng thực tế

## Session Log
