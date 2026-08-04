# Data Design — notification-service

**DB**: PostgreSQL  
**Schema owner**: notification-service (không service nào khác được đọc trực tiếp)

---

## Bảng `notification_log`

> **Phase 4**: tạo cùng V1 migration. Dùng ngay cho email channel.

CDC source (Debezium đọc WAL). Immutable hoàn toàn sau INSERT — không có UPDATE nào. Workers không write vào bảng này.

| Column              | Type           | Constraint | Ghi chú                                          |
|---------------------|----------------|------------|--------------------------------------------------|
| `id`                | `VARCHAR(26)`  | PK         | ULID                                             |
| `event_id`          | `VARCHAR(26)`  | NOT NULL   | ID của source domain event                       |
| `notification_type` | `VARCHAR(50)`  | NOT NULL   | `VERIFICATION_EMAIL`, `ORDER_CONFIRMED`, v.v.    |
| `channel`           | `VARCHAR(10)`  | NOT NULL   | `EMAIL` \| `IN_APP`                              |
| `tier`              | `VARCHAR(20)`  | NOT NULL   | `TRANSACTIONAL` \| `BULK`                        |
| `user_id`           | `VARCHAR(26)`  | NOT NULL   | Recipient userId                                 |
| `recipient`         | `VARCHAR(255)` | NULLABLE   | Email address — chỉ có khi `channel = EMAIL`     |
| `payload`           | `JSONB`        | NOT NULL   | Full content workers cần — xem cấu trúc bên dưới |
| `created_at`        | `TIMESTAMPTZ`  | NOT NULL   |                                                  |

**Constraints**
- `UNIQUE (event_id, channel)` — idempotency tại DB level. INSERT trùng bị reject, không sinh CDC event thứ 2.

**Indexes**
- `idx_notification_log_event_id_channel` on `(event_id, channel)` — backing index của UNIQUE constraint
- `idx_notification_log_user_id_created_at` on `(user_id, created_at DESC)` — audit query theo user

**Cấu trúc `payload` JSONB**

```json
{
  "userId": "01HXYZ...",
  "title": "Xác nhận email của bạn",
  "body": "Nhấp vào link bên dưới để kích hoạt tài khoản.",
  "actionUrl": "/verify?token=abc123",
  "templateVars": {
    "fullName": "Nguyen Van A",
    "verificationToken": "abc123"
  }
}
```

`payload` chứa toàn bộ thông tin workers cần để xử lý — CDC event mang theo dữ liệu này, worker không cần query DB thêm. `templateVars` chỉ có với email channel (dùng để render Thymeleaf template).

---

## Bảng `notification_inbox`

> **Phase 4**: tạo cùng V1 migration. Sẽ được populate khi implement IN_APP channel (later).

User-facing inbox. Chỉ tạo row khi `channel = IN_APP`. Được INSERT bởi notification-service trong cùng transaction với `notification_log`.

| Column                | Type           | Constraint                                            | Ghi chú                          |
|-----------------------|----------------|-------------------------------------------------------|----------------------------------|
| `id`                  | `VARCHAR(26)`  | PK                                                    | ULID                             |
| `notification_log_id` | `VARCHAR(26)`  | NOT NULL, FK → `notification_log(id)` ON DELETE CASCADE | Liên kết với audit log          |
| `user_id`             | `VARCHAR(26)`  | NOT NULL                                              |                                  |
| `title`               | `VARCHAR(255)` | NOT NULL                                              |                                  |
| `body`                | `VARCHAR(500)` | NOT NULL                                              |                                  |
| `action_url`          | `VARCHAR(500)` | NULLABLE                                              | Link khi user click notification |
| `is_read`             | `BOOLEAN`      | NOT NULL, DEFAULT `false`                             |                                  |
| `created_at`          | `TIMESTAMPTZ`  | NOT NULL                                              |                                  |

**Indexes**
- `idx_notification_inbox_user_id_is_read` on `(user_id, is_read)` — badge count query
- `idx_notification_inbox_user_id_created_at` on `(user_id, created_at DESC)` — inbox list với pagination

**Key queries**

```sql
-- Badge count (số notification chưa đọc)
SELECT COUNT(*)
FROM notification_inbox
WHERE user_id = ? AND is_read = false;

-- Inbox list (pagination)
SELECT id, title, body, action_url, is_read, created_at
FROM notification_inbox
WHERE user_id = ?
ORDER BY created_at DESC
LIMIT 20 OFFSET ?;

-- Mark as read
UPDATE notification_inbox
SET is_read = true
WHERE id = ? AND user_id = ?;

-- Mark all as read
UPDATE notification_inbox
SET is_read = true
WHERE user_id = ? AND is_read = false;
```

---

## Transaction boundary

```
BEGIN
  INSERT INTO notification_log (id, event_id, notification_type, channel, tier, user_id, recipient, payload, created_at)
  INSERT INTO notification_inbox (...)   ← chỉ khi channel = IN_APP
COMMIT
→ CDC (Debezium) đọc WAL, fire event từ notification_log
```

Hai bảng luôn nhất quán vì cùng transaction. Nếu COMMIT fail → không có CDC event → không có delivery. Không có partial state.

---

## Tại sao tách 2 bảng

| | `notification_log` | `notification_inbox` |
|---|---|---|
| Consumer | System — CDC source, audit | User — UI display, badge count |
| Mutability | Immutable sau INSERT | Mutable — `is_read` update |
| Retention | 90 ngày (audit window) | User-defined, có thể archive |
| Query pattern | Theo `event_id`, date range | Theo `user_id + is_read`, paginate |

Nếu gộp: audit log phải biết về `is_read`, `title`, `body` — mixed concern giữa system tracking và user UX.
