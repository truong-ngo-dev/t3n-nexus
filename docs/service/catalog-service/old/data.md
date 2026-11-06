# Data Schema — catalog-service

**Engine:** PostgreSQL (port 5435, db: `catalog_db`)

---

## Tables

### `brand`

| Column       | Type        | Nullable | Notes              |
|--------------|-------------|----------|--------------------|
| `id`         | `uuid`      | NO       | PK                 |
| `name`       | `varchar`   | NO       | UNIQUE (không phân biệt hoa/thường — `uq_brand_name` trên `LOWER(name)`, V17) |
| `slug`       | `varchar`   | NO       | UNIQUE             |
| `status`     | `varchar`   | NO       | `ACTIVE, INACTIVE` |
| `created_at` | `timestamp` | NO       |                    |
| `updated_at` | `timestamp` | NO       |                    |

> Không có cột nào tham chiếu `category` — Brand không giới hạn theo danh mục (xem analysis.md §5.3.2 mục 5).
> "Chính hãng"/đại lý ủy quyền là một khái niệm khác (seller ↔ brand, có thể kèm phạm vi), nếu làm sẽ là aggregate
> riêng (`BrandAuthorization`), không sửa bảng này.

---

### `attribute_template`

| Column         | Type        | Nullable | Notes                                           |
|----------------|-------------|----------|-------------------------------------------------|
| `id`           | `uuid`      | NO       | PK                                              |
| `name`         | `varchar`   | NO       | UNIQUE, immutable — machine key ổn định (mọi nơi tham chiếu bằng ID, không bằng `name`) |
| `display_name` | `varchar`   | NO       |                                                 |
| `hint`         | `varchar(200)` | YES   | Gợi ý cách nhập cho seller (V16) |
| `input_type`   | `varchar`   | NO       | `SINGLE_SELECT, MULTI_SELECT, TEXT, NUMBER, BOOLEAN, DATE` (V16) — immutable |
| `unit`         | `varchar(20)` | YES    | Chỉ khi `input_type = NUMBER` (CHECK `chk_attribute_template_unit`); immutable (V16) |
| `status`       | `varchar`   | NO       | `ACTIVE, INACTIVE` — tắt/bật, không guard |
| `version`      | `bigint`    | NO       | Optimistic lock (V16) — 2 Admin sửa cùng template: bên sau lỗi |
| `created_at`   | `timestamp` | NO       |                                                 |
| `updated_at`   | `timestamp` | NO       |                                                 |

> **V8:** bỏ cột `scope` (không còn GLOBAL/CATEGORY). **V9:** thêm `status` (thay hard delete).
>
> **V16:** tách `SELECT` → `SINGLE_SELECT`/`MULTI_SELECT` (dữ liệu cũ: template có sản phẩm khai > 1 giá trị cho thuộc
> tính không phải trục biến thể → `MULTI_SELECT`, còn lại `SINGLE_SELECT`), thêm `DATE`; thêm `unit`, `hint`, `version`.
> Guard "không tắt template đang required" đã bỏ (xem `service.md` § AttributeTemplate — hành vi và guard).

### `attribute_option`

| Column          | Type        | Nullable | Notes                     |
|-----------------|-------------|----------|---------------------------|
| `id`            | `uuid`      | NO       | PK                        |
| `template_id`   | `uuid`      | NO       | FK → `attribute_template` |
| `value`         | `varchar`   | NO       | Immutable — machine key; **duy nhất trong template, không phân biệt hoa/thường** (unique index `uq_attribute_option_template_value` trên `(template_id, LOWER(value))`, V16) |
| `display_value` | `varchar`   | NO       |                           |
| `status`        | `varchar`   | NO       | `ACTIVE, INACTIVE` — tắt/bật, không guard |
| `sort_order`    | `int`       | NO       | Thứ tự chuẩn trong template (V16; khởi tạo theo `created_at`) |
| `created_at`    | `timestamp` | NO       |                           |

> **V10** từng thêm `usage_count` để guard "không tắt option đang được variant dùng"; **V16 bỏ** cột này cùng guard
> (tắt chỉ chặn lựa chọn mới). V16 cũng đổi `value` của bản trùng trong dữ liệu cũ (`value || '_' || 6 ký tự cuối id`,
> giữ bản tạo sớm nhất) trước khi tạo unique index — an toàn vì mọi tham chiếu dùng `id`.
>
> Option **không bao giờ bị xoá**: lưu template là upsert từng dòng option, không xoá-rồi-chèn-lại (dòng option đang được
> `variant_combination_item` tham chiếu bằng FK).

---

### `category`

| Column       | Type        | Nullable | Notes                             |
|--------------|-------------|----------|-----------------------------------|
| `id`         | `uuid`      | NO       | PK                                |
| `name`       | `varchar`   | NO       | Duy nhất trong cùng `parent_id` (không phân biệt hoa/thường) — kiểm ở application layer (V18), không unique index DB vì `parent_id NULL` (root) không so sánh bằng nhau được bằng index thường |
| `slug`       | `varchar`   | NO       | Tự sinh từ tên, **không** unique, đổi theo tên — tra cứu theo `id` (V19 bỏ `uq_category_slug`; service.md T-10) |
| `parent_id`  | `uuid`      | YES      | FK → `category` (nullable = root) |
| `level`      | `smallint`  | NO       | 1, 2, 3                           |
| `image_url`  | `varchar`   | YES      |                                   |
| `sort_order` | `int`       | NO       | Thứ tự giữa anh em cùng `parent_id` trên storefront (V18) — trước đó cây xếp theo tên |
| `status`     | `varchar`   | NO       | `ACTIVE, INACTIVE` — soft toggle điều hướng (ẩn khỏi `GetCategoryTree`, chặn `CreateProduct` mới), KHÔNG guard theo children/product reference (khác `DeleteCategory` hard-delete). "Dùng được" = ACTIVE + mọi tổ tiên ACTIVE (tính qua `category_closure`, không lưu cột riêng) |
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
| `display_order`       | `int`     | NO       |                |
| `constraints`         | `jsonb`   | NO       | Ràng buộc theo ngành hàng — shape theo `inputType` của template (§5.3.1): TEXT{maxLength} \| NUMBER{min,max,integerOnly} \| MULTI_SELECT{maxSelections} \| DATE{inputPrecision,minDate,maxDate}. Không ràng buộc thêm = mọi field `null` |
| `discovery`            | `jsonb`   | NO       | Cách tham gia tìm hàng: `{search, filter, sort}` — mỗi nhánh độc lập, `null` = tắt. Xem `analysis.md` §5.3.3 cho shape đầy đủ (`FilterConfig`/`SortConfig`) |

> **V8:** bỏ cột `is_variant_defining` — không còn được khai báo trước ở đâu, chỉ là hệ quả của việc
> attribute có xuất hiện trong `variant_combination_item` của Variant hay không. Đồng thời: chỉ category
> leaf (`level = 3`) mới có row trong bảng này — không có kế thừa từ category cha (L1/L2 luôn rỗng).
>
> **V18:** `is_filterable`/`is_searchable` (2 cờ phẳng) → `constraints` + `discovery` (jsonb). Lý do: cách 1
> thuộc tính tham gia tìm kiếm không còn là on/off đơn giản (trọng số search, kiểu filter SELECT/BOOLEAN/
> RANGE, bucket, sort) và ràng buộc nhập liệu khác nhau hẳn theo `inputType` — ép vào cột quan hệ sẽ ra một
> đống cột luôn NULL chéo nhau giữa các kiểu. Domain (`AttributeConstraints`/`Discovery`, record Java thuần)
> validate shape đúng kiểu trước khi lưu; persistence chỉ serialize/deserialize, không tự diễn giải nội dung.

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
| `published_at`      | `timestamptz` | YES    | Thời điểm publish **lần đầu** — giữ nguyên khi unpublish rồi publish lại. `NULL` = chưa từng publish → không phát `ProductSearchSnapshotEvent` (V15) |
| `search_version`    | `bigint`    | NO       | Default 0. Version tăng đơn điệu của `ProductSearchSnapshotEvent` — tăng mỗi lần Product hoặc Variant của nó đổi. **Không map vào `ProductJpaEntity`** (mapper dựng entity mới mỗi lần save → merge sẽ ghi đè về 0); chỉ đọc/ghi qua native query (V15) |
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

**Invalidation channel:** `catalog:cache:invalidate` (Redis pub/sub)

> Brand (`brands:active`) đã bỏ cache — danh sách công khai/droplist giờ có tìm kiếm + phân trang, luôn đọc
> DB trực tiếp để đảm bảo tươi ngay sau khi admin sửa (đặc biệt sau khi thêm ràng buộc tên duy nhất).
