# Phase 3 — customer-service

**Status:** `TODO`
**Started:** —
**Completed:** —

## Checklist

- [x] Tạo `design/services/customer-service.md`
- [x] Tạo `design/data/customer-service.md`
- [x] Implement `CustomerProfile` aggregate
- [x] Implement consumer `identity.customer.registered` → tạo `CustomerProfile` (loyaltyBalance=0)
- [x] Implement idempotency: xử lý event trùng không tạo 2 profile

## Verify
đ
```sql
-- Sau khi identity-service publish identity.customer.registered (có thể delay async):
SELECT * FROM customer_profiles WHERE user_id = '<userId>';
-- Expect: 1 row, loyalty_balance = 0

-- Idempotency — consume cùng event 2 lần:
SELECT COUNT(*) FROM customer_profiles WHERE user_id = '<userId>';
-- Expect: 1
```

## Session Log
