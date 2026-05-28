# Docker Compose — Hướng dẫn vận hành

## Khởi động

```bash
# Build kafka-connect image (cần chạy lần đầu hoặc sau khi sửa Dockerfile.connect)
docker compose build kafka-connect

# Khởi động toàn bộ stack
docker compose up -d

# Kiểm tra tất cả services healthy
docker compose ps
```

---

## Đăng ký Debezium Connectors

Chờ `kafka-connect` healthy trước khi đăng ký (healthcheck tự động mỗi 10s, timeout 5 lần).

### Kiểm tra kafka-connect đã sẵn sàng

```bash
curl http://localhost:8083/connectors
```

Trả về `[]` hoặc danh sách connectors → đã sẵn sàng.

---

### Đăng ký identity-outbox-connector

Đọc WAL của `identity_db.public.outbox_events`, route theo `routing_key` → topic tương ứng.

```bash
curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @debezium/connector-identity-outbox.json
```

### Đăng ký notification-connector

Đọc WAL của `notification_db.public.notification_log`, route theo `channel` + `tier` → topic tương ứng.

```bash
curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @debezium/connector-notification.json
```

---

## Kiểm tra trạng thái

```bash
# Liệt kê tất cả connectors
curl http://localhost:8083/connectors

# Trạng thái chi tiết từng connector
curl http://localhost:8083/connectors/identity-outbox-connector/status
curl http://localhost:8083/connectors/notification-connector/status
```

Kết quả mong đợi — connector và task đều `RUNNING`:

```json
{
  "name": "identity-outbox-connector",
  "connector": { "state": "RUNNING" },
  "tasks": [{ "id": 0, "state": "RUNNING" }]
}
```

---

## Cập nhật config connector

Kafka Connect không hỗ trợ PATCH — phải dùng PUT với toàn bộ config:

```bash
curl -X PUT http://localhost:8083/connectors/identity-outbox-connector/config \
  -H "Content-Type: application/json" \
  -d @debezium/connector-identity-outbox.json
```

> Lưu ý: PUT endpoint nhận phần `config` bên trong file JSON, không phải toàn bộ wrapper.
> Nếu file JSON có dạng `{"name": "...", "config": {...}}`, cần trích `config` ra riêng.
> Cách đơn giản nhất là xóa rồi tạo lại (xem bên dưới).

---

## Xóa connector

```bash
curl -X DELETE http://localhost:8083/connectors/identity-outbox-connector
curl -X DELETE http://localhost:8083/connectors/notification-connector
```

**Quan trọng:** Xóa connector không tự drop replication slot trong PostgreSQL.
Nếu không drop slot, WAL sẽ tích lũy và có thể làm đầy disk.

```bash
# Drop replication slot sau khi xóa identity connector
docker compose exec postgres-identity \
  psql -U t3nexus -d identity_db \
  -c "SELECT pg_drop_replication_slot('debezium');"

# Drop replication slot sau khi xóa notification connector
docker compose exec postgres-notification \
  psql -U t3nexus -d notification_db \
  -c "SELECT pg_drop_replication_slot('debezium');"
```

---

## Topics được tạo bởi các connectors

| Topic                                  | Producer                  | Consumer                               |
|----------------------------------------|---------------------------|----------------------------------------|
| `identity.user.registered`             | identity-outbox-connector | customer-service, notification-service |
| `identity.email-verification.reissued` | identity-outbox-connector | notification-service                   |
| `notification.email.transactional`     | notification-connector    | email-worker                           |
| `notification.email.bulk`              | notification-connector    | email-worker                           |
| `notification.inapp.dispatch`          | notification-connector    | (in-app worker)                        |

---

## Dừng và xóa toàn bộ

```bash
# Dừng nhưng giữ volumes (data)
docker compose down

# Dừng và xóa toàn bộ volumes (reset hoàn toàn)
docker compose down -v
```
