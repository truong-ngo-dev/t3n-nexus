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
| `name`         | `varchar`   | NO       | UNIQUE — không sửa sau khi có Product reference |
| `display_name` | `varchar`   | NO       |                                                 |
| `input_type`   | `varchar`   | NO       | `SELECT, TEXT, NUMBER, BOOLEAN`                 |
| `scope`        | `varchar`   | NO       | `GLOBAL, CATEGORY`                              |
| `created_at`   | `timestamp` | NO       |                                                 |
| `updated_at`   | `timestamp` | NO       |                                                 |

### `attribute_option`

| Column          | Type        | Nullable | Notes                     |
|-----------------|-------------|----------|---------------------------|
| `id`            | `uuid`      | NO       | PK                        |
| `template_id`   | `uuid`      | NO       | FK → `attribute_template` |
| `value`         | `varchar`   | NO       |                           |
| `display_value` | `varchar`   | NO       |                           |
| `status`        | `varchar`   | NO       | `ACTIVE, INACTIVE`        |
| `created_at`    | `timestamp` | NO       |                           |

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
| `status`     | `varchar`   | NO       | `ACTIVE, INACTIVE`                |
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
| `category_id`         | `uuid`    | NO       | PK (composite) |
| `template_id`         | `uuid`    | NO       | PK (composite) |
| `is_variant_defining` | `boolean` | NO       |                |
| `is_required`         | `boolean` | NO       |                |
| `is_filterable`       | `boolean` | NO       |                |
| `display_order`       | `int`     | NO       |                |

---

### `product`

| Column              | Type        | Nullable | Notes                                              |
|---------------------|-------------|----------|----------------------------------------------------|
| `id`                | `uuid`      | NO       | PK                                                 |
| `seller_id`         | `uuid`      | NO       | không thay đổi sau khi tạo                         |
| `category_id`       | `uuid`      | NO       | FK → `category`; không thay đổi sau khi có Variant |
| `brand_id`          | `uuid`      | NO       | FK → `brand`                                       |
| `name`              | `varchar`   | NO       |                                                    |
| `description`       | `text`      | YES      |                                                    |
| `status`            | `varchar`   | NO       | `DRAFT, PUBLISHED, UNPUBLISHED, BLOCKED`           |
| `warranty_months`   | `int`       | YES      |                                                    |
| `warranty_type`     | `varchar`   | YES      |                                                    |
| `warranty_coverage` | `varchar`   | YES      |                                                    |
| `created_at`        | `timestamp` | NO       |                                                    |
| `updated_at`        | `timestamp` | NO       |                                                    |

**Indexes:**
- `idx_product_seller` on `(seller_id)`
- `idx_product_category` on `(category_id)`
- `idx_product_status` on `(status)`

### `product_attribute_value`

| Column        | Type      | Nullable | Notes          |
|---------------|-----------|----------|----------------|
| `product_id`  | `uuid`    | NO       | PK (composite) |
| `template_id` | `uuid`    | NO       | PK (composite) |
| `value`       | `varchar` | NO       |                |

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

| Column             | Type          | Nullable | Notes                                     |
|--------------------|---------------|----------|-------------------------------------------|
| `id`               | `varchar(26)` | NO       | PK (= skuId)                              |
| `product_id`       | `varchar(26)` | NO       | ref → `product.id` (no FK constraint)     |
| `combination_hash` | `varchar(64)` | NO       | deterministic hash của VariantCombination |
| `sku_code`         | `varchar(100)`| YES      | seller-assigned SKU code                  |
| `price`            | `bigint`      | NO       | đơn vị: đồng, phải > 0                    |
| `stock`            | `int`         | NO       | default 0                                 |
| `status`           | `varchar`     | NO       | `ACTIVE, INACTIVE`                        |
| `created_at`       | `timestamp`   | NO       |                                           |
| `updated_at`       | `timestamp`   | NO       |                                           |

**Unique:** `uq_variant_combination` on `(product_id, combination_hash)`  
**Indexes:**
- `idx_variant_product` on `(product_id)`

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

| Key                                  | L1 TTL | L2 TTL | Invalidate khi                                                          |
|--------------------------------------|--------|--------|-------------------------------------------------------------------------|
| `catalog:product:{id}`               | 2 min  | 10 min | `ProductUpdatedEvent`, `ProductUnpublishedEvent`, `ProductBlockedEvent` |
| `catalog:product:{id}:variants`      | 2 min  | 10 min | `VariantPriceChangedEvent`, `VariantDeactivatedEvent`                   |
| `catalog:category:tree`              | 30 min | 1 hr   | `CategoryUpdatedEvent`                                                  |
| `catalog:categories:{id}:attributes` | 30 min | 1 hr   | Admin sửa CategoryAttributeAssignment                                   |
| `brands:active`                      | —      | 30 min | `CreateBrand`, `UpdateBrand`, `DeactivateBrand`                         |

**Invalidation channel:** `catalog:cache:invalidate` (Redis pub/sub)

> Brand không dùng L1 — không đủ hot path để justify per-instance cache. Redis TTL 30 min là acceptable.
