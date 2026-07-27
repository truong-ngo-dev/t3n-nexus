# Data Schema — Inventory Service

## Database

**Engine:** PostgreSQL
**Lý do:** Reservation yêu cầu ACID — check `availableQty` và tăng `reservedQty` phải là một transaction; row-level lock (`SELECT FOR UPDATE`) là cơ chế chính chống oversell.

**Redis** (bổ sung, không thay thế PostgreSQL):
- `inventory:limited:{skuId}:slots` — remaining slots counter, kiểu `STRING` (integer)
- `inventory:sold-out:bloom` — Bloom Filter key (Redisson `RBloomFilter`)

---

## Tables

### `stock`

Mỗi SKU có đúng 1 row. `reserved_quantity` do service quản lý, không do seller nhập.

`seller_active`/`product_published`/`admin_blocked` là **3 trục độc lập** — không nén chung vào 1 `status` enum như thiết kế ban đầu (V1/V2). Bài học từ bug thực tế: variant thêm mới vào product đã publish bị kẹt "không sellable" vì lúc đó `sellerActive` phải cố đại diện luôn cho cả trạng thái publish của product — xem `V3`/`V4` bên dưới. `isSellable() = seller_active AND product_published AND NOT admin_blocked`.

| Column                | Type        | Nullable | Notes                                                                                                    |
|-----------------------|-------------|----------|----------------------------------------------------------------------------------------------------------|
| `id`                  | `uuid`      | NO       | PK                                                                                                       |
| `sku_id`              | `uuid`      | NO       | UNIQUE — reference sang Catalog BC                                                                       |
| `product_id`          | `uuid`      | NO       | Dùng để publish/unpublish/block/unblock theo nhóm                                                        |
| `seller_id`           | `uuid`      | NO       |                                                                                                          |
| `total_quantity`      | `int`       | NO       | Default 0; không được âm                                                                                 |
| `reserved_quantity`   | `int`       | NO       | Default 0; không được âm; ≤ `total_quantity`                                                             |
| `seller_active`       | `boolean`   | NO       | Trục variant — mirror `Variant.status ACTIVE/INACTIVE` bên catalog-service                               |
| `product_published`   | `boolean`   | NO       | Trục product — mirror `Product.status PUBLISHED/UNPUBLISHED`, áp cho toàn bộ SKU của product (thêm ở V4) |
| `admin_blocked`       | `boolean`   | NO       | Trục admin — mirror `Product.adminBlocked`, độc lập với 2 trục trên                                      |
| `low_stock_threshold` | `int`       | NO       | Default 0; emit `StockDepleted` khi `available < threshold`                                              |
| `version`             | `bigint`    | NO       | Default 0; JPA `@Version` — optimistic lock fallback                                                     |
| `created_at`          | `timestamp` | NO       |                                                                                                          |
| `updated_at`          | `timestamp` | NO       |                                                                                                          |

**Indexes:**
- `uk_stock_sku_id` UNIQUE on `(sku_id)` — lookup chính, 1 SKU = 1 Stock
- `idx_stock_product_id` on `(product_id)` — bulk update khi ProductPublished/Unpublished/Blocked/Unblocked
- `idx_stock_seller_id` on `(seller_id)` — seller dashboard, list tất cả SKU của mình

**Locking note:**
`SELECT FOR UPDATE` lock theo `sku_id`. Khi 1 order có nhiều SKU, luôn acquire lock theo thứ tự `sku_id` ASC để tránh deadlock giữa các concurrent transaction.

---

### `reservation`

Một row per Order. `order_id` là business key — dùng để dedup khi `OrderCreated` arrive nhiều lần.

| Column       | Type          | Nullable | Notes                                                   |
|--------------|---------------|----------|---------------------------------------------------------|
| `id`         | `uuid`        | NO       | PK                                                      |
| `order_id`   | `uuid`        | NO       | UNIQUE                                                  |
| `status`     | `varchar(20)` | NO       | `PENDING` \| `RELEASED` \| `CANCELLED`                  |
| `expires_at` | `timestamp`   | NO       | TTL khớp với order auto-cancel window của order-service |
| `created_at` | `timestamp`   | NO       |                                                         |
| `updated_at` | `timestamp`   | NO       |                                                         |

**Indexes:**
- `uk_reservation_order_id` UNIQUE on `(order_id)` — Saga lookup + idempotency guard
- `idx_reservation_status_expires` on `(status, expires_at)` — Scheduler query: tìm PENDING đã quá TTL

> `ReserveInventory.handle()` check `existsByOrderId()` trước khi insert (fast pre-check, không phải nguồn bảo vệ chính) — data integrity thật sự do `UNIQUE(order_id)` ở DB đảm bảo, insert trùng sẽ fail ở tầng DB nếu 2 transaction race qua được check-then-act. Gap hiện tại: exception vi phạm constraint chưa được catch riêng để xử lý "graceful — coi như duplicate, skip" mà rơi vào nhánh lỗi chung (`OrderCreatedConsumer` release Redis guard rồi rethrow → Kafka retry) — không mất data integrity, chỉ chưa tối ưu retry path.

---

### `reservation_item`

Các SKU trong một reservation. Không có update sau khi tạo — append-only.

| Column           | Type   | Nullable | Notes                  |
|------------------|--------|----------|------------------------|
| `id`             | `uuid` | NO       | PK                     |
| `reservation_id` | `uuid` | NO       | FK → `reservation(id)` |
| `sku_id`         | `uuid` | NO       |                        |
| `qty`            | `int`  | NO       | > 0                    |

**Indexes:**
- `idx_reservation_item_reservation_id` on `(reservation_id)` — load items khi release

---

### `limited_offer`

Shadow của config từ Promotion BC. Inventory lưu local để check nhanh khi reservation request đến.

| Column           | Type          | Nullable | Notes                                     |
|------------------|---------------|----------|-------------------------------------------|
| `id`             | `uuid`        | NO       | PK                                        |
| `sku_id`         | `uuid`        | NO       | UNIQUE                                    |
| `max_quantity`   | `int`         | NO       | Tổng số slot cho phép                     |
| `window_seconds` | `int`         | NO       | Thời gian hiệu lực tính từ `activated_at` |
| `status`         | `varchar(20)` | NO       | `INACTIVE` \| `ACTIVE` \| `ENDED`         |
| `activated_at`   | `timestamp`   | YES      | NULL khi chưa kích hoạt lần nào           |
| `ended_at`       | `timestamp`   | YES      | NULL khi chưa kết thúc                    |
| `created_at`     | `timestamp`   | NO       |                                           |
| `updated_at`     | `timestamp`   | NO       |                                           |

**Indexes:**
- `uk_limited_offer_sku_id` UNIQUE on `(sku_id)`
- `idx_limited_offer_status` on `(status)` — tìm tất cả ACTIVE offers (admin dashboard)

---

### `processed_event`

Idempotency store cho tất cả Kafka consumer. Mỗi consumer check bảng này trước khi xử lý.

| Column         | Type        | Nullable | Notes                             |
|----------------|-------------|----------|-----------------------------------|
| `event_id`     | `uuid`      | NO       | PK — `eventId` từ `EventEnvelope` |
| `processed_at` | `timestamp` | NO       |                                   |

**Không có index thêm** — PK lookup đã đủ. Cleanup job xóa records cũ hơn 7 ngày (Scheduler BC).

---

### `outbox_events`

Standard Outbox table. Debezium đọc CDC từ bảng này và push lên Kafka.

| Column           | Type           | Nullable | Notes                                          |
|------------------|----------------|----------|------------------------------------------------|
| `id`             | `uuid`         | NO       | PK                                             |
| `aggregate_type` | `varchar(100)` | NO       | Ví dụ: `Stock`, `Reservation`                  |
| `aggregate_id`   | `uuid`         | NO       | ID của aggregate phát event                    |
| `event_type`     | `varchar(200)` | NO       | Fully-qualified class name hoặc routing key    |
| `routing_key`    | `varchar(200)` | NO       | Kafka topic routing key                        |
| `payload`        | `jsonb`        | NO       | Event payload serialized                       |
| `created_at`     | `timestamp`    | NO       |                                                |
| `published_at`   | `timestamp`    | YES      | NULL = chưa publish; Debezium set sau khi push |

**Indexes:**
- `idx_outbox_published_at` on `(published_at)` WHERE `published_at IS NULL` — partial index, Debezium poll unpublished events

---

## Flyway Migration Plan

| Version | File                                              | Nội dung                                                                                                                                   |
|---------|---------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| V1      | `V1__init_schema.sql`                             | Tạo tất cả tables và indexes — `stock.status` ban đầu là `ACTIVE`\|`INACTIVE`                                                              |
| V2      | `V2__add_blocked_status.sql`                      | Thêm `BLOCKED` vào `chk_stock_status` — bước tạm, bị thay thế hoàn toàn ở V3                                                               |
| V3      | `V3__split_stock_seller_active_admin_blocked.sql` | Bỏ cột `status`, thêm `seller_active`/`admin_blocked` (boolean) — tách trục variant vs trục admin                                          |
| V4      | `V4__add_stock_product_published.sql`             | Thêm `product_published` (boolean, default `TRUE`, backfill `= seller_active` cho data cũ) — tách nốt trục product ra khỏi `seller_active` |

---

## Constraint Summary

```
stock.total_quantity        >= 0   (CHECK constraint)
stock.reserved_quantity     >= 0   (CHECK constraint)
stock.reserved_quantity     <= stock.total_quantity  (enforce tại domain, không CHECK — quá đắt với concurrent update)
reservation.order_id        UNIQUE (DB constraint — nguồn bảo vệ chính chống duplicate reservation, không phải Redis)
reservation_item.qty        > 0    (CHECK constraint)
limited_offer.max_quantity  > 0    (CHECK constraint)
limited_offer.window_seconds > 0   (CHECK constraint)
```

`reserved_quantity <= total_quantity` không đặt CHECK constraint ở DB vì `SELECT FOR UPDATE` + aggregate invariant đã đảm bảo — CHECK constraint gây lỗi khó debug khi có bug logic, không phải lớp bảo vệ phù hợp ở đây.
