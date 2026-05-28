# Phase 6 — Rate Limiter

**Status:** `TODO`
**Started:** —
**Completed:** —

## Checklist

- [x] Build `rate-limiter-starter` tại `services/libs/rate-limiter-starter` — Redis sliding window, key expression linh hoạt
- [ ] Áp dụng vào web-gateway: `POST /api/users/register` — 5 req/phút per IP
- [x] Áp dụng vào identity-service: `POST /api/users/resend-verification` — 3 req/giờ per email

## Verify

```http
### Register: gửi 6 request liên tiếp từ cùng IP
POST /api/users/register
# Request 1–5: HTTP 201 hoặc 409 (xử lý bình thường)
# Request 6: HTTP 429 Too Many Requests

### Resend: gửi 4 lần trong 1 giờ cùng email
POST /api/users/resend-verification
{ "email": "test@example.com" }
# Request 1–3: HTTP 200
# Request 4: HTTP 429 Too Many Requests
```

## Session Log
