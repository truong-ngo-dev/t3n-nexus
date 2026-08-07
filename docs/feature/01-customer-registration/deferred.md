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

## 2. Temporal replay — idempotency crash window (RESOLVED — mục đích ban đầu đã đạt bằng Option A)

**Trạng thái thật tại thời điểm review (2026-08-05):** `CustomerRegisteredConsumer` **không** dùng Redis — không có `idempotency-support` dependency trong `pom.xml` của `customer-service`, không có Redis config nào. Guard duy nhất là DB `UNIQUE(user_id)` + `ON CONFLICT DO NOTHING` trong `insertIgnoreConflict`. Đây chính là Option A ở dưới, và đã là trạng thái implement hiện tại — không phải việc còn chờ khi dựng Temporal.

Vì vậy vấn đề "Redis key set trước khi `handle()` hoàn thành, Temporal retry thấy lock còn sống → skip" **không áp dụng được** cho code hiện tại — không có Redis nào để bị stuck cả. Khi dựng Temporal workflow cho `CreateCustomerProfile`, activity retry gọi lại → DB `ON CONFLICT DO NOTHING` tự silent-ignore → an toàn ngay, không cần thêm gì.

**Giữ lại mục này chỉ để cảnh báo tương lai:** nếu sau này có nhu cầu thêm Redis pre-check trước DB (Lớp 1, xem `3.technical/idempotency-layering.md` — chỉ cần khi volume duplicate-delivery chạm ngưỡng thật, hiện chưa chạm), **bắt buộc** set Redis key **sau khi** DB đã xác nhận (insert mới hoặc conflict xác nhận trùng), không phải trước — đúng thứ tự Option B cũ ở đây. Set trước sẽ tái tạo lại chính bug crash-window này.

File liên quan:
- `services/customer-service/.../messaging/customer/CustomerRegisteredConsumer.java`
- `services/customer-service/.../application/customer/CreateCustomerProfile.java`
- `services/customer-service/.../adapter/repository/customer/CustomerProfilePersistenceAdapter.java`
