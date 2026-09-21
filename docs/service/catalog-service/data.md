# Data Schema — catalog-service

**Engine:** PostgreSQL (port 5435, db: `catalog_db`)

---

## Tables

### `brand`

| Column       | Type        | Nullable | Notes              |
|--------------|-------------|----------|--------------------|
| `id`         | `uuid`      | NO       | PK                 |
| `name`       | `varchar`   | NO       |                    |
| `slug`       | `varchar`   | NO       | UNIQUE             |
| `status`     | `varchar`   | NO       | `ACTIVE, INACTIVE` |
| `created_at` | `timestamp` | NO       |                    |
| `updated_at` | `timestamp` | NO       |                    |

---

### `attribute_template`

| Column         | Type        | Nullable | Notes                                           |
|----------------|-------------|----------|-------------------------------------------------|
| `id`           | `uuid`      | NO       | PK                                              |
| `name`         | `varchar`   | NO       | UNIQUE, immutable — machine key ổn định (cùng vai trò `Brand.slug`/`Category.slug`), không phải vì sợ vỡ tham chiếu (mọi nơi đều tham chiếu bằng ID, không bằng `name`) |
| `display_name` | `varchar`   | NO       |                                                 |
| `input_type`   | `varchar`   | NO       | `SELECT, TEXT, NUMBER, BOOLEAN` — immutable vì lý do KỸ THUẬT thật (khác `name`): đổi SELECT↔TEXT sau khi đã có `AttributeOption`/dữ liệu tham chiếu sẽ làm sai lệch cấu trúc dữ liệu đã lưu |
| `status`       | `varchar`   | NO       | `ACTIVE, INACTIVE` — soft-delete, cùng pattern Brand/Category/AttributeOption |
| `created_at`   | `timestamp` | NO       |                                                 |
| `updated_at`   | `timestamp` | NO       |                                                 |

> **V8:** bỏ cột `scope` — không còn khái niệm GLOBAL/CATEGORY. AttributeTemplate là master data thuần,
> phải được assign tường minh vào category (chỉ leaf/L3) mới có hiệu lực. Xem `service.md` § Attribute
> Value Model.
>
> **V9:** thêm cột `status` — thay cho hard delete (vốn không dùng được vì FK `ON DELETE RESTRICT` từ
> `category_attribute_assignment`/`product_attribute_value`/`variant_combination_item`). Deactivate bị
> chặn nếu template đang `required=true` ở bất kỳ category nào — xem `service.md` § AttributeTemplate
> lifecycle.

### `attribute_option`

| Column          | Type        | Nullable | Notes                     |
|-----------------|-------------|----------|---------------------------|
| `id`            | `uuid`      | NO       | PK                        |
| `template_id`   | `uuid`      | NO       | FK → `attribute_template` |
| `value`         | `varchar`   | NO       | Immutable — machine key ổn định (giống `AttributeTemplate.name`), không unique constraint (không kẹt tên khi reactivate) |
| `display_value` | `varchar`   | NO       |                           |
| `status`        | `varchar`   | NO       | `ACTIVE, INACTIVE`        |
| `usage_count`   | `int`       | NO       | Số lần option xuất hiện trong `variant_combination_item`, bump bởi `AddVariant` cùng transaction — dùng để guard `OPTION_IN_USE` (thay cho query `existsByOptionId` full-scan, xem V10) |
| `created_at`    | `timestamp` | NO       |                           |

> **V10:** thêm cột `usage_count` — thay cho `VariantCombinationItemJpaRepository.existsByOptionId`
> (full scan `variant_combination_item`, bảng ghi liên tục qua mỗi `AddVariant` và không có index trên
> `option_id` — Postgres không tự index cột FK). `AttributeOption.deactivate()` giờ chặn `OPTION_IN_USE`
> bằng in-aggregate check (`usageCount > 0`), không cần query `VariantRepository` nữa.

---

### `category`

| Column       | Type        | Nullable | Notes                             |
|--------------|-------------|----------|-----------------------------------|
| `id`         | `uuid`      | NO       | PK                                |
| `name`       | `varchar`   | NO       |                                   |
| `slug`       | `varchar`   | NO       | UNIQUE                            |
| `parent_id`  | `uuid`      | YES      | FK → `category` (nullable = root) |
| `level`      | `smallint`  | NO       | 1, 2, 3                           |
| `image_url`  | `varchar`   | YES      |                                   |
| `status`     | `varchar`   | NO       | `ACTIVE, INACTIVE` — soft toggle điều hướng (ẩn khỏi `GetCategoryTree`, chặn `CreateProduct` mới), KHÔNG guard theo children/product reference (khác `DeleteCategory` hard-delete) |
| `created_at` | `timestamp` | NO       |                                   |
| `updated_at` | `timestamp` | NO       |                                   |

### `category_closure`

| Column          | Type   | Nullable | Notes                |
|-----------------|--------|----------|----------------------|
| `ancestor_id`   | `uuid` | NO       | PK (composite)       |
| `descendant_id` | `uuid` | NO       | PK (composite)       |
| `depth`         | `int`  | NO       | 0 = self-referencing |

**Indexes:**
- `idx_closure_ancestor` on `(ancestor_id)`
- `idx_closure_descendant` on `(descendant_id)`

**Insert logic khi tạo Category:**
```sql
INSERT INTO category_closure (ancestor_id, descendant_id, depth)
  VALUES (newId, newId, 0)
  UNION ALL
  SELECT ancestor_id, newId, depth + 1
  FROM category_closure WHERE descendant_id = parentId;
```

### `category_attribute_assignment`

| Column                | Type      | Nullable | Notes          |
|-----------------------|-----------|----------|----------------|
| `category_id`         | `uuid`    | NO       | PK (composite) — FK trỏ tới category phải có `level = 3` (enforce ở application layer, không phải DB constraint) |
| `template_id`         | `uuid`    | NO       | PK (composite) |
| `is_required`         | `boolean` | NO       |                |
| `is_filterable`       | `boolean` | NO       |                |
| `is_searchable`       | `boolean` | NO       | Default `false`. Catalog chỉ lưu + phát ra cho search-service tương lai đồng bộ (build full-text index) — catalog KHÔNG tự dùng flag này để query |
| `display_order`       | `int`     | NO       |                |

> **V8:** bỏ cột `is_variant_defining` — không còn được khai báo trước ở đâu, chỉ là hệ quả của việc
> attribute có xuất hiện trong `variant_combination_item` của Variant hay không. Đồng thời: chỉ category
> leaf (`level = 3`) mới có row trong bảng này — không có kế thừa từ category cha (L1/L2 luôn rỗng).
>
> **V13:** thêm `is_searchable` — cùng nhóm quyết định curation của Admin với `is_required`/
> `is_filterable` (per-category, không phải per-attribute-template — cùng 1 template có thể searchable ở
> category này nhưng không ở category khác). Xem `service.md` § Attribute Value Model.

---

### `product`

| Column              | Type        | Nullable | Notes                                                            |
|---------------------|-------------|----------|------------------------------------------------------------------|
| `id`                | `uuid`      | NO       | PK                                                               |
| `seller_id`         | `uuid`      | NO       | không thay đổi sau khi tạo                                       |
| `category_id`       | `uuid`      | NO       | FK → `category`; phải là leaf (`level = 3`, enforce ở application layer) — không thay đổi sau khi có Variant |
| `brand_id`          | `uuid`      | NO       | FK → `brand`                                                     |
| `name`              | `varchar`   | NO       |                                                                  |
| `description`       | `text`      | YES      |                                                                  |
| `status`            | `varchar`   | NO       | `DRAFT, PUBLISHED, UNPUBLISHED` — không có `BLOCKED`             |
| `admin_blocked`     | `boolean`   | NO       | Cờ độc lập với `status` — Admin block/unblock không đổi `status` |
| `warranty_months`   | `int`       | YES      |                                                                  |
| `warranty_type`     | `varchar`   | YES      |                                                                  |
| `warranty_coverage` | `varchar`   | YES      |                                                                  |
| `created_at`        | `timestamp` | NO       |                                                                  |
| `updated_at`        | `timestamp` | NO       |                                                                  |

**Indexes:**
- `idx_product_seller` on `(seller_id)`
- `idx_product_category` on `(category_id)`
- `idx_product_status` on `(status)`

### `product_attribute_value`

| Column        | Type      | Nullable | Notes          |
|---------------|-----------|----------|----------------|
| `product_id`  | `uuid`    | NO       | PK (composite) |
| `template_id` | `uuid`    | NO       | PK (composite) |
| `value`       | `varchar` | NO       | PK (composite) — với template `inputType=SELECT`, chứa `AttributeOptionId` (không phải business value string); với TEXT/NUMBER/BOOLEAN chứa raw value. Tên cột giữ nguyên `value` để tránh migration không cần thiết |

> **V7:** PK mở rộng từ `(product_id, template_id)` sang `(product_id, template_id, value)` — cho phép
> Product khai 1..N giá trị cho 1 attribute (VD 1 tập option con của master pool để Variant chọn 1
> combination cụ thể từ đó).

### `product_variant_defining_attribute`

| Column        | Type      | Nullable | Notes          |
|---------------|-----------|----------|----------------|
| `product_id`  | `uuid`    | NO       | PK (composite), FK → `product` (`ON DELETE CASCADE`) |
| `template_id` | `uuid`    | NO       | PK (composite), FK → `attribute_template` (`ON DELETE RESTRICT`) |

> **V11:** `isVariantDefining` quay lại làm khái niệm khai báo tường minh (V8 từng bỏ ở
> `category_attribute_assignment`), nhưng lần này ở Product/Seller, per-listing, không phải Admin áp đặt
> toàn category. Tách bảng riêng thay vì nhét cột vào `product_attribute_value` — vì đó là thuộc tính của
> CẶP `(product_id, template_id)`, không phải của từng `(product_id, template_id, value)`; nhét vào
> `product_attribute_value` sẽ lặp lại cùng 1 giá trị boolean trên mọi row cùng template, tốn thêm 1 hàng
> lặp mỗi option khi attribute có cardinality cao (VD "Màu sắc" 30 option). Tồn tại row = true, không cần
> cột boolean, giống cách `variant_combination_item` không lưu "false" cho pair không dùng. Xem
> `service.md` § Attribute Value Model.

### `product_image`

| Column          | Type        | Nullable | Notes            |
|-----------------|-------------|----------|------------------|
| `id`            | `uuid`      | NO       | PK               |
| `product_id`    | `uuid`      | NO       | FK → `product`   |
| `object_key`    | `varchar`   | NO       | MinIO object key |
| `display_order` | `int`       | NO       | 0 = thumbnail    |
| `created_at`    | `timestamp` | NO       |                  |

**Indexes:**
- `idx_product_image_product` on `(product_id)`

---

### `variant`

| Column             | Type           | Nullable | Notes                                     |
|--------------------|----------------|----------|-------------------------------------------|
| `id`               | `varchar(26)`  | NO       | PK (= skuId)                              |
| `product_id`       | `varchar(26)`  | NO       | ref → `product.id` (no FK constraint)     |
| `combination_hash` | `varchar(64)`  | NO       | deterministic hash của VariantCombination |
| `sku_code`         | `varchar(100)` | YES      | seller-assigned SKU code                  |
| `price`            | `bigint`       | NO       | đơn vị: đồng, phải > 0                    |
| `status`           | `varchar`      | NO       | `ACTIVE, INACTIVE`                        |
| `created_at`       | `timestamp`    | NO       |                                           |
| `updated_at`       | `timestamp`    | NO       |                                           |

**Unique:** `uq_variant_combination` on `(product_id, combination_hash)`  
**Indexes:**
- `idx_variant_product` on `(product_id)`

> Không có cột `stock`/`quantity` — số lượng tồn kho thuộc sở hữu hoàn toàn của `inventory-service` (aggregate `Stock`, xem `service/inventory-service/data.md`). `VariantCreatedEvent` mang theo `active`/`productPublished` để inventory-service tự khởi tạo `sellerActive`/`productPublished` đúng ngay từ đầu.

### `variant_combination_item`

| Column        | Type   | Nullable | Notes                   |
|---------------|--------|----------|-------------------------|
| `variant_id`  | `uuid` | NO       | PK (composite)          |
| `template_id` | `uuid` | NO       | PK (composite)          |
| `option_id`   | `uuid` | NO       | FK → `attribute_option` |

### `sku_image`

| Column          | Type          | Nullable | Notes                                 |
|-----------------|---------------|----------|---------------------------------------|
| `id`            | `varchar(26)` | NO       | PK                                    |
| `variant_id`    | `varchar(26)` | NO       | ref → `variant.id` (no FK constraint) |
| `object_key`    | `varchar`     | NO       | MinIO object key                      |
| `display_order` | `int`         | NO       |                                       |

---

### `outbox_events`

| Column           | Type          | Notes                        |
|------------------|---------------|------------------------------|
| `id`             | `uuid`        | PK                           |
| `event_id`       | `uuid`        | dedup key                    |
| `aggregate_type` | `varchar`     | e.g. `Product`, `Variant`    |
| `aggregate_id`   | `varchar(36)` |                              |
| `event_type`     | `varchar`     | e.g. `ProductPublishedEvent` |
| `routing_key`    | `varchar`     | Kafka topic                  |
| `payload`        | `jsonb`       | `EventEnvelope` serialized   |
| `occurred_on`    | `timestamp`   |                              |
| `created_at`     | `timestamp`   |                              |
| `published_at`   | `timestamp`   | NULL = chưa publish          |
| `trace_id`       | `varchar`     | OTel trace propagation       |
| `span_id`        | `varchar`     | OTel span propagation        |

---

## Cache (Redis)

**Strategy:** Caffeine L1 + Redis L2. Redis pub/sub broadcast L1 invalidation cross-instance.  
**Key prefix:** `catalog:{entity}:{id}`

| Key                                  | L1 TTL | L2 TTL | Invalidate khi                                                              |
|--------------------------------------|--------|--------|-----------------------------------------------------------------------------|
| `catalog:product:{id}`               | 2 min  | 10 min | Product update/publish/unpublish/block/unblock, Variant activate/deactivate |
| `catalog:product:{id}:variants`      | 2 min  | 10 min | Variant add/update/activate/deactivate                                      |
| `catalog:category:tree`              | 30 min | 1 hr   | `CategoryUpdatedEvent`                                                      |
| `catalog:categories:{id}:attributes` | 30 min | 1 hr   | Admin sửa CategoryAttributeAssignment                                       |
| `brands:active`                      | —      | 30 min | `CreateBrand`, `UpdateBrand`, `DeactivateBrand`                             |

**Invalidation channel:** `catalog:cache:invalidate` (Redis pub/sub)

> Brand không dùng L1 — không đủ hot path để justify per-instance cache. Redis TTL 30 min là acceptable.
