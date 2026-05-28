# customer-service

**Vai trò**: Quản lý Customer Profile, địa chỉ giao hàng, Loyalty Points Ledger, lịch sử đơn hàng (read model).  
**Domain**: Supporting Domain  
**DB**: PostgreSQL  
**Libs**: `common-domain`, `common-web`, `observability-starter`

---

## Events Consumed

| Event                | Topic                          | Action                              | Phase |
|----------------------|--------------------------------|-------------------------------------|-------|
| `CustomerRegistered` | `identity.customer.registered` | Tạo `CustomerProfile`               | 3     |
| `OrderCompleted`     | `order.order.completed`        | Tạo EARN entry trong loyalty ledger | later |
| `OrderCancelled`     | `order.order.cancelled`        | Tạo REFUND entry nếu điểm đã dùng   | later |
| `OrderCreated`       | `order.order.created`          | Append vào order history read model | later |

## Events Published

| Event                    | Topic                            | Trigger                                      | Phase  |
|--------------------------|----------------------------------|----------------------------------------------|--------|
| `LoyaltyPointsEarned`    | `customer.loyalty.earned`        | Sau khi tạo EARN entry                       | later  |
| `LoyaltyPointsExpired`   | `customer.loyalty.expired`       | Sau khi Scheduler trigger EXPIRE batch       | later  |

---

## Business Rules

### CustomerProfile
- Được tạo **async** sau khi nhận `identity.customer.registered` — buyer không chờ.
- **Idempotency**: UNIQUE constraint trên `user_id` — consume cùng event 2 lần không tạo 2 profile (`ON CONFLICT DO NOTHING`).
- Không có FK sang identity-service DB — DB isolation theo service boundary.

### Loyalty Points Ledger
- Mọi thay đổi điểm là entry **bất biến** — không update, chỉ append.
- Entry types: `EARN` | `REDEEM` | `EXPIRE` | `REFUND` | `ADJUST`
- Balance hiện tại = `SUM(amount)` toàn bộ ledger của customer (amount âm cho REDEEM/EXPIRE).
- Mỗi `EARN` entry có `expire_at` — Scheduler BC chạy job hàng ngày tạo `EXPIRE` entry cho batch quá hạn.
- Khi redeem: ưu tiên trừ batch sắp hết hạn trước (FIFO by expiry).

---

## Dependencies

| Dependency              | Lý do                                   |
|-------------------------|-----------------------------------------|
| `common-domain`         | `AggregateRoot`, `DomainEvent`          |
| `common-web`            | `ApiResponse`, `GlobalExceptionHandler` |
| `observability-starter` | Tracing + structured logging            |
