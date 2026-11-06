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

### Connector hộp thư event của BC mới (dùng script)

BC phát event theo ADR-0008 (từ C1a) đăng ký connector bằng `debezium/outbox-connector.sh`, không dùng các tệp `connector-*.json` bên dưới. Script chạy lại được (đăng ký lần hai chỉ cập nhật), không để mật khẩu trong repo, và xóa khe sao chép khi gỡ.

```bash
cd infra/debezium
export DB_HOST=postgres-seller DB_NAME=seller_db DB_USER=seller_app DB_PASSWORD=...   # DB_HOST: tên mà container kafka-connect thấy
./outbox-connector.sh render   seller      # xem cấu hình sẽ gửi (mật khẩu bị che)
./outbox-connector.sh register seller      # PUT cấu hình, đợi RUNNING; tên connector: seller-outbox-connector
./outbox-connector.sh status   seller
DB_CONTAINER=infra-postgres-seller-1 ./outbox-connector.sh remove seller   # gỡ connector rồi xóa khe seller_outbox_slot
```

- **Đăng ký connector trước khi BC ghi hàng đầu tiên vào hộp thư.** Cấu hình dùng `snapshot.mode=no_data`: hàng ghi trước khi connector tồn tại không bao giờ ra bus.
- Mỗi connector cần `TOPIC_PREFIX` (mặc định là tên BC) và `SLOT_NAME` (mặc định `<bc>_outbox_slot`) riêng; script từ chối khi connector khác đã giữ. Trùng `topic.prefix` làm connector sau kẹt ở bước quét.
- Thông điệp ra bus là JSON trần (khóa là `aggregate_id`, giá trị là khung event, tiêu đề `id` là `event_id`), không bọc `schema`/`payload` của Kafka Connect.
- `remove` không có `DB_CONTAINER` thì vẫn gỡ connector nhưng thoát với mã 2 và in lệnh xóa khe bằng tay; `--keep-slot` bỏ qua việc xóa khe (kèm cảnh báo).
- Kiểm thử: `debezium/test-outbox-connector.sh` (phần cấu hình, không cần hạ tầng); thêm `LIVE=1 CONNECT_URL=... DB_HOST=... DB_CONTAINER=... KAFKA_CONTAINER=...` để chạy trên Kafka Connect và Postgres thật (nên dùng stack thử riêng, không dùng Kafka dev).

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

### Đăng ký order-outbox-connector

Đọc WAL của `order_db.public.outbox_events`, route theo `routing_key` → topic tương ứng.

```bash
curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @debezium/connector-order-outbox.json
```

### Đăng ký scheduler-outbox-connector

Đọc WAL của `scheduler_db.public.outbox_events`, route theo `routing_key` → topic tương ứng
(`ScheduledJobFiredEvent.getRoutingKey()` = `scheduler.job.fired`, khớp regex route toàn chuỗi nên
`topic.prefix` không prepend vào tên topic cuối — giống hệt cơ chế của `order-outbox-connector`).

```bash
curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @debezium/connector-scheduler-outbox.json
```

> **Lưu ý**: hiện `ScheduledJobFiredHandler` (`scheduler-service`) mới chỉ `log.info`, CHƯA ghi vào
> `outbox_events` (xem `implementation.md` Phase 4 TODO) — đăng ký connector này trước là chuẩn bị hạ
> tầng, chưa có event nào chảy qua cho tới khi handler đó đổi sang `outboxEventStore.store(event)`.
> **2026-09-22**: dòng đó đã viết sẵn nhưng đang comment (cùng nhịp với `order-service`'s
> `ScheduledJobFiredConsumer` cũng đang comment `@KafkaListener`) — bật cả 2 phía cùng lúc khi test
> full component (2 `ScheduledJob` `ORDER_INVENTORY_TIMEOUT_*` cho `feature/07-place-order`).

### Đăng ký inventory-outbox-connector

Đọc WAL của `inventory_db.public.outbox_events`, route theo `routing_key` → topic tương ứng
(`InventoryReservedEvent`/`InventoryReservationFailedEvent`/`StockReplenishedEvent`/`StockDepletedEvent`).
**2026-09-20**: phát hiện connector này chưa từng tồn tại (không có file, không đăng ký) trong lúc rà soát
luồng đặt hàng COD — `inventory-service` vẫn ghi đúng vào `outbox_events` mỗi lần reserve/release, nhưng
không ai đọc bảng đó nên `order-service.InventoryReservedConsumer` không bao giờ nhận được gì, saga đứng
im ở `Order.CREATED` vĩnh viễn dù toàn bộ code phía sau đúng — cùng lớp lỗi với `order-outbox-connector`
đã ghi nhận ở `feature/07-place-order/implementation.md` Phase 0. Đã tạo file + đăng ký.

```bash
curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @debezium/connector-inventory-outbox.json
```

### Đăng ký catalog-outbox-connector

Đọc WAL của `catalog_db.public.outbox_events`, route theo `routing_key` → topic tương ứng. Cần cho
search-service (`catalog.*.search-snapshot`) và inventory-service (`catalog.product.*`, `catalog.variant.*`).

```bash
curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @debezium/connector-catalog-outbox.json
```

---

## Cơ sở dữ liệu của Người bán (seller-service)

| Thành phần | Giá trị | Ghi chú |
|---|---|---|
| `postgres-seller` | cổng `5441`, cơ sở dữ liệu `seller_db` | Cần `wal_level=logical` (Debezium đọc bảng hộp thư `outbox_events`) |
| Tài khoản `seller_app` | mật khẩu dev `seller_app` | Chủ cơ sở dữ liệu, vừa chạy migration vừa chạy dịch vụ; `SELLER_DB_USERNAME`, `SELLER_DB_PASSWORD` ghi đè |

Connector `seller` đăng ký bằng `debezium/outbox-connector.sh register seller` **trước** khi ghi hàng hộp thư đầu tiên (xem mục Debezium bên dưới).

## Cơ sở dữ liệu của Nhật ký kiểm toán (audit-service)

| Thành phần | Giá trị | Ghi chú |
|---|---|---|
| `postgres-audit` | cổng `5440`, cơ sở dữ liệu `audit_db` | Không cần `wal_level=logical` (Nhật ký không phát event) |
| Tài khoản `audit_owner` | mật khẩu dev `audit_owner` | Chủ cơ sở dữ liệu và bảng; **chỉ** dùng chạy migration (Flyway của dịch vụ) |
| Tài khoản `audit_app` | mật khẩu dev `audit_app` | Dịch vụ chạy bằng tài khoản này; chỉ có `INSERT`, `SELECT` trên bảng bản ghi (ADR-0009) |
| Tài khoản `audit_admin` | mật khẩu dev `audit_admin` | Quản trị bộ chứa, chỉ để chạy tệp khởi tạo |

Hai tài khoản `audit_owner` và `audit_app` do `audit/init-audit-roles.sql` tạo **một lần** khi cơ sở dữ liệu được tạo lần đầu. Đã có volume cũ thì phải xóa volume `postgres_audit_data` mới chạy lại tệp này.

```bash
docker compose up -d postgres-audit
```

---

## Elasticsearch (search-service)

| Service         | Port   | Ghi chú                                              |
|-----------------|--------|------------------------------------------------------|
| `elasticsearch` | `9200` | 1 node, tắt security (chỉ dùng cho dev), heap 512MB |
| `kibana`        | `5601` | Tuỳ chọn — xem index/document qua Dev Tools          |

```bash
# Kiểm tra cluster
curl http://localhost:9200/_cluster/health?pretty

# Liệt kê index (search-service tự tạo index + alias lúc khởi động)
curl "http://localhost:9200/_cat/indices?v"
curl "http://localhost:9200/_cat/aliases?v"
```

**Analyzer tiếng Việt** — không cần plugin, khai báo trong settings của từng index (search-service):
field chính dùng `standard` + `lowercase`; sub-field `.folded` thêm `asciifolding` để có dấu/không dấu
khớp nhau. Kiểm tra nhanh:

```bash
curl -X POST http://localhost:9200/_analyze -H "Content-Type: application/json" -d '{
  "tokenizer": "standard",
  "filter": ["lowercase", "asciifolding"],
  "text": "Điện thoại màu Đỏ"
}'
# → dien, thoai, mau, do
```

---

## Kiểm tra trạng thái

```bash
# Liệt kê tất cả connectors
curl http://localhost:8083/connectors

# Trạng thái chi tiết từng connector
curl http://localhost:8083/connectors/identity-outbox-connector/status
curl http://localhost:8083/connectors/notification-connector/status
curl http://localhost:8083/connectors/order-outbox-connector/status
curl http://localhost:8083/connectors/scheduler-outbox-connector/status
curl http://localhost:8083/connectors/inventory-outbox-connector/status
curl http://localhost:8083/connectors/catalog-outbox-connector/status
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

# Drop replication slot sau khi xóa order connector
docker compose exec postgres-order \
  psql -U t3nexus -d order_db \
  -c "SELECT pg_drop_replication_slot('debezium');"

# Drop replication slot sau khi xóa scheduler connector
docker compose exec postgres-scheduler \
  psql -U t3nexus -d scheduler_db \
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
| `order.order.created`                  | order-outbox-connector    | inventory-service                      |
| `order.order.confirmed`                | order-outbox-connector    | notification-service                   |
| `order.order.cancelled`                | order-outbox-connector    | inventory-service, notification-service |
| `scheduler.job.fired`                  | scheduler-outbox-connector | customer-service, cart-service, payment-service *(theo `taskType`, chưa implement phía consumer — xem `service.md` scheduler)* |

---

## Dừng và xóa toàn bộ

```bash
# Dừng nhưng giữ volumes (data)
docker compose down

# Dừng và xóa toàn bộ volumes (reset hoàn toàn)
docker compose down -v
```
