# Data Design — customer-service

**DB**: PostgreSQL  
**Schema owner**: customer-service (không service nào khác được đọc trực tiếp)

---

## Bảng `customer_profiles`

> **Phase 3**: tạo khi consume `identity.customer.registered`.

| Column       | Type          | Constraint       | Ghi chú                                   |
|--------------|---------------|------------------|-------------------------------------------|
| `id`         | `VARCHAR(26)` | PK               | ULID                                      |
| `user_id`    | `VARCHAR(26)` | UNIQUE, NOT NULL | Ref sang identity-service — không dùng FK |
| `created_at` | `TIMESTAMPTZ` | NOT NULL         |                                           |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL         |                                           |

**Indexes**
- `idx_customer_profiles_user_id` on `user_id`

**Idempotency**

UNIQUE constraint trên `user_id` xử lý duplicate event mà không cần bảng riêng:

```sql
INSERT INTO customer_profiles (id, user_id, created_at, updated_at)
VALUES (?, ?, NOW(), NOW())
ON CONFLICT (user_id) DO NOTHING;
```

---

## Bảng `delivery_addresses`

> **Phase later**: thêm khi implement API quản lý địa chỉ.

| Column           | Type           | Constraint                                               | Ghi chú               |
|------------------|----------------|----------------------------------------------------------|-----------------------|
| `id`             | `VARCHAR(26)`  | PK                                                       | ULID                  |
| `customer_id`    | `VARCHAR(26)`  | NOT NULL, FK → `customer_profiles(id)` ON DELETE CASCADE |                       |
| `label`          | `VARCHAR(50)`  | NULLABLE                                                 | Nhà, Công ty, v.v.    |
| `recipient_name` | `VARCHAR(100)` | NOT NULL                                                 |                       |
| `phone`          | `VARCHAR(20)`  | NOT NULL                                                 |                       |
| `address_line`   | `VARCHAR(255)` | NOT NULL                                                 | Số nhà, tên đường     |
| `ward`           | `VARCHAR(100)` | NOT NULL                                                 |                       |
| `district`       | `VARCHAR(100)` | NOT NULL                                                 |                       |
| `province`       | `VARCHAR(100)` | NOT NULL                                                 |                       |
| `is_default`     | `BOOLEAN`      | NOT NULL, DEFAULT `false`                                | Chỉ 1 địa chỉ default |
| `created_at`     | `TIMESTAMPTZ`  | NOT NULL                                                 |                       |

**Indexes**
- `idx_delivery_addresses_customer_id` on `customer_id`

---

## Bảng `loyalty_point_ledger`

> **Phase later**: thêm khi implement loyalty points từ Order BC events.

| Column        | Type                                  | Constraint                             | Ghi chú                                                 |
|---------------|---------------------------------------|----------------------------------------|---------------------------------------------------------|
| `id`          | `BIGINT GENERATED ALWAYS AS IDENTITY` | PK                                     |                                                         |
| `customer_id` | `VARCHAR(26)`                         | NOT NULL, FK → `customer_profiles(id)` |                                                         |
| `entry_type`  | `VARCHAR(10)`                         | NOT NULL                               | `EARN` \| `REDEEM` \| `EXPIRE` \| `REFUND` \| `ADJUST`  |
| `amount`      | `INTEGER`                             | NOT NULL                               | Dương (EARN/REFUND/ADJUST+), âm (REDEEM/EXPIRE/ADJUST-) |
| `source_ref`  | `VARCHAR(100)`                        | NULLABLE                               | orderId, jobId, v.v.                                    |
| `expire_at`   | `TIMESTAMPTZ`                         | NULLABLE                               | Chỉ có khi `entry_type=EARN`                            |
| `occurred_on` | `TIMESTAMPTZ`                         | NOT NULL                               |                                                         |

**Indexes**
- `idx_loyalty_ledger_customer_id` on `customer_id`
- `idx_loyalty_ledger_expire_at` on `expire_at` WHERE `entry_type = 'EARN'` — Scheduler job query batch quá hạn

**Query balance**

```sql
SELECT COALESCE(SUM(amount), 0) AS balance
FROM loyalty_point_ledger
WHERE customer_id = ?;
```

**Query FIFO redeem (ưu tiên batch sắp hết hạn)**

```sql
SELECT id, amount, expire_at
FROM loyalty_point_ledger
WHERE customer_id = ? AND entry_type = 'EARN' AND expire_at > NOW()
ORDER BY expire_at ASC;
```
