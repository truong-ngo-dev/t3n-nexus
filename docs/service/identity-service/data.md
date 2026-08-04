# Data Design — identity-service

**DB**: PostgreSQL  
**Schema owner**: identity-service (không service nào khác được đọc trực tiếp)

---

## Bảng `users`

| Column            | Type           | Constraint                  | Ghi chú                                |
|-------------------|----------------|-----------------------------|----------------------------------------|
| `id`              | `VARCHAR(26)`  | PK                          | ULID                                   |
| `email`           | `VARCHAR(255)` | UNIQUE, NOT NULL            |                                        |
| `phone_number`    | `VARCHAR(255)` | UNIQUE, NULLABLE            | Không bắt buộc khi đăng ký             |
| `hashed_password` | `VARCHAR(255)` | NOT NULL                    | BCrypt                                 |
| `full_name`       | `VARCHAR(100)` | NULLABLE                    | NULL khi đăng ký qua Google (không có displayName) |
| `role`            | `VARCHAR(20)`  | NOT NULL                    | CUSTOMER \| SELLER \| SHIPPER \| ADMIN |
| `status`          | `VARCHAR(10)`  | NOT NULL, DEFAULT `PENDING` | ACTIVE \| LOCKED \| PENDING            |
| `locked_at`       | `TIMESTAMPTZ`  | NULLABLE                    | NULL khi chưa bị lock                  |
| `created_at`      | `TIMESTAMPTZ`  | NOT NULL                    |                                        |
| `updated_at`      | `TIMESTAMPTZ`  | NOT NULL                    |                                        |

**Indexes**
- `idx_users_email` on `email` — dedup check khi register

---

## Bảng `verification_tokens`

| Column       | Type                                  | Constraint                                   | Ghi chú          |
|--------------|---------------------------------------|----------------------------------------------|------------------|
| `id`         | `BIGINT GENERATED ALWAYS AS IDENTITY` | PK                                           |                  |
| `user_id`    | `VARCHAR(26)`                         | NOT NULL, FK → `users(id)` ON DELETE CASCADE |                  |
| `token`      | `VARCHAR(255)`                        | UNIQUE, NOT NULL                             | Opaque token     |
| `expires_at` | `TIMESTAMPTZ`                         | NOT NULL                                     | created_at + 24h |
| `created_at` | `TIMESTAMPTZ`                         | NOT NULL                                     |                  |

**Indexes**
- `idx_verification_tokens_token` on `token` — lookup khi verify
- `idx_verification_tokens_user_id` on `user_id` — invalidate token cũ khi resend

---

## Bảng `outbox_events`

Managed bởi `outbox-starter`. Debezium đọc WAL (logical replication), không cần polling.

| Column           | Type                                  | Constraint | Ghi chú                                     |
|------------------|---------------------------------------|------------|---------------------------------------------|
| `id`             | `BIGINT GENERATED ALWAYS AS IDENTITY` | PK         |                                             |
| `event_id`       | `VARCHAR(100)`                        | NOT NULL   | UUID — unique identifier của event          |
| `aggregate_type` | `VARCHAR(100)`                        | NOT NULL   | e.g. `User`                                 |
| `aggregate_id`   | `VARCHAR(26)`                         | NOT NULL   | `userId`                                    |
| `event_type`     | `VARCHAR(100)`                        | NOT NULL   | e.g. `identity.customer.registered`         |
| `payload`        | `TEXT`                                | NOT NULL   | JSON                                        |
| `occurred_on`    | `TIMESTAMPTZ`                         | NOT NULL   |                                             |
| `created_at`     | `TIMESTAMPTZ`                         | NOT NULL   |                                             |

**Indexes**
- `idx_outbox_events_created_at` on `created_at`

---

## ABAC Tables

Policy evaluation theo chuẩn XACML. Policy được load và compile vào cache khi khởi động hoặc khi có thay đổi — DB không được query trực tiếp trên hot path.

### `authx_named_expression`

Leaf node của expression tree — reusable trên UI.

| Column        | Type                                  | Constraint       | Ghi chú   |
|---------------|---------------------------------------|------------------|-----------|
| `id`          | `BIGINT GENERATED ALWAYS AS IDENTITY` | PK               |           |
| `code`        | `VARCHAR(255)`                        | UNIQUE, NOT NULL |           |
| `description` | `TEXT`                                | NOT NULL         |           |
| `spel`        | `TEXT`                                | UNIQUE, NOT NULL | dedup key |

### `authx_expression`

Cây expression — COMPOSITION node (AND/OR) hoặc LITERAL node (leaf → named_expression).

| Column                | Type                                  | Constraint                    | Ghi chú                            |
|-----------------------|---------------------------------------|-------------------------------|------------------------------------|
| `id`                  | `BIGINT GENERATED ALWAYS AS IDENTITY` | PK                            |                                    |
| `parent_id`           | `BIGINT`                              | FK → self, CASCADE            | NULL = root                        |
| `node_type`           | `VARCHAR(20)`                         | NOT NULL                      | LITERAL \| COMPOSITION             |
| `operator`            | `VARCHAR(10)`                         | NULLABLE                      | AND \| OR — chỉ có khi COMPOSITION |
| `named_expression_id` | `BIGINT`                              | FK → `authx_named_expression` | chỉ có khi LITERAL                 |

### `authx_policy_set`

Container phân cấp — chứa policy_set con hoặc policy.

| Column                 | Type                                  | Constraint              | Ghi chú                               |
|------------------------|---------------------------------------|-------------------------|---------------------------------------|
| `id`                   | `BIGINT GENERATED ALWAYS AS IDENTITY` | PK                      |                                       |
| `parent_id`            | `BIGINT`                              | FK → self, CASCADE      | NULL = root                           |
| `code`                 | `VARCHAR(255)`                        | UNIQUE, NOT NULL        |                                       |
| `description`          | `TEXT`                                | NOT NULL                |                                       |
| `target_expression_id` | `BIGINT`                              | FK → `authx_expression` | NULL = match all                      |
| `combine_algorithm`    | `VARCHAR(50)`                         | NOT NULL                | deny-overrides, permit-overrides, ... |
| `attributes`           | `JSONB`                               | NULLABLE                | metadata mở rộng                      |
| `created_at`           | `BIGINT`                              | NOT NULL                | epoch millis                          |
| `updated_at`           | `BIGINT`                              | NOT NULL                | epoch millis                          |

### `authx_policy`

| Column                 | Type                                  | Constraint                                 | Ghi chú          |
|------------------------|---------------------------------------|--------------------------------------------|------------------|
| `id`                   | `BIGINT GENERATED ALWAYS AS IDENTITY` | PK                                         |                  |
| `policy_set_id`        | `BIGINT`                              | NOT NULL, FK → `authx_policy_set`, CASCADE |                  |
| `code`                 | `VARCHAR(255)`                        | UNIQUE, NOT NULL                           |                  |
| `description`          | `TEXT`                                | NOT NULL                                   |                  |
| `target_expression_id` | `BIGINT`                              | FK → `authx_expression`                    | NULL = match all |
| `combine_algorithm`    | `VARCHAR(50)`                         | NOT NULL                                   |                  |
| `attributes`           | `JSONB`                               | NULLABLE                                   |                  |
| `created_at`           | `BIGINT`                              | NOT NULL                                   |                  |
| `updated_at`           | `BIGINT`                              | NOT NULL                                   |                  |

### `authx_rule`

| Column                    | Type                                  | Constraint                             | Ghi chú           |
|---------------------------|---------------------------------------|----------------------------------------|-------------------|
| `id`                      | `BIGINT GENERATED ALWAYS AS IDENTITY` | PK                                     |                   |
| `policy_id`               | `BIGINT`                              | NOT NULL, FK → `authx_policy`, CASCADE |                   |
| `code`                    | `VARCHAR(255)`                        | UNIQUE, NOT NULL                       |                   |
| `description`             | `TEXT`                                | NOT NULL                               |                   |
| `target_expression_id`    | `BIGINT`                              | FK → `authx_expression`                | NULL = match all  |
| `condition_expression_id` | `BIGINT`                              | FK → `authx_expression`                | điều kiện bổ sung |
| `effect`                  | `VARCHAR(10)`                         | NOT NULL                               | PERMIT \| DENY    |
| `order_index`             | `INT`                                 | NOT NULL, DEFAULT 0                    |                   |
| `attributes`              | `JSONB`                               | NULLABLE                               |                   |
| `created_at`              | `BIGINT`                              | NOT NULL                               |                   |
| `updated_at`              | `BIGINT`                              | NOT NULL                               |                   |

---

## Transaction boundary

```
BEGIN
  INSERT INTO users (...)
  INSERT INTO verification_tokens (...)   ← cùng transaction với user creation
  INSERT INTO outbox_events (...)         ← cùng transaction
COMMIT
```

ABAC tables được write độc lập (admin UI) — không tham gia transaction đăng ký user.

---

## Query pattern ABAC

Load toàn bộ policy context dùng `WITH RECURSIVE` để traverse:
1. `authx_policy_set` hierarchy (parent → children)
2. `authx_expression` tree (root → leaf → named_expression)

Kết quả được compile và cache — không query lại trừ khi policy thay đổi.
