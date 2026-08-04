# Deferred — customer-registration

Những việc được xác định trong feature này nhưng **chưa impl**, sẽ làm ở phase khác.

---

## 1. loyaltyBalance

**Làm khi nào:** Phase thiết kế cơ chế điểm thưởng (loyalty points).

Hiện tại `customer_profiles` không có column `loyalty_balance` vì bảng chưa được thiết kế đến.
Khi làm loyalty points, cần bổ sung đồng bộ toàn bộ stack:

- `CustomerProfile` domain class — thêm field `loyaltyBalance`, khởi tạo `= 0` trong `create()`
- `CustomerProfileJpaEntity` — thêm column `loyalty_balance INTEGER NOT NULL DEFAULT 0`
- Flyway migration — thêm column vào `customer_profiles`
- `CustomerProfilePersistenceAdapter.save()` + `insertIgnoreConflict` — truyền giá trị
- `CustomerProfileMapper` — map field cả hai chiều

---

## 2. Temporal replay — idempotency crash window

**Làm khi nào:** Khi dựng Temporal workflow cho `CreateCustomerProfile`.

**Vấn đề:** `CustomerRegisteredConsumer` hiện dùng Redis `tryAcquire` làm primary guard.
Nếu pod crash sau khi Redis key đã được set nhưng trước khi `handle()` hoàn thành,
Temporal activity retry sẽ thấy `tryAcquire → false` và skip — profile không bao giờ được tạo cho đến khi TTL 72h hết.

Với Kafka `DefaultErrorHandler` hiện tại điều này không xảy ra vì exception path giải phóng key.
Nhưng Temporal retry không đi qua exception path nếu lock còn sống.

**Cần chọn một trong hai khi dựng Temporal:**

**Option A — DB-only idempotency (đơn giản hơn):**
- Xóa `idempotencyGuard.tryAcquire/release` khỏi consumer
- `ON CONFLICT (user_id) DO NOTHING` trong `insertIgnoreConflict` đủ để handle hoàn toàn
- Temporal retry gọi lại activity → DB silent-ignore → safe

**Option B — Giữ Redis nhưng đảo thứ tự:**
- Set Redis key **sau khi** `handle()` thành công, không phải trước
- Redis chỉ dùng để tránh DB hit cho event đã xử lý, không guard write

File liên quan:
- `services/customer-service/.../messaging/customer/CustomerRegisteredConsumer.java`
- `services/customer-service/.../application/customer/CreateCustomerProfile.java`
- `services/customer-service/.../adapter/repository/customer/CustomerProfilePersistenceAdapter.java`
