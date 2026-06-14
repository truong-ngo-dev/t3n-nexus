# catalog-service

**Vai trò**: Quản lý toàn bộ Catalog — Category taxonomy, AttributeTemplate, Brand, Product (SPU), Variant (SKU).  
**Domain**: Core Domain  
**DB**: PostgreSQL  
**Libs**: `common-domain`, `outbox-starter`, `common-events`, `common-web`, `observability-starter`

---

## Ubiquitous Language

| Thuật ngữ                     | Định nghĩa                                                                                                                      | Ví dụ thực tế                                                                         |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `Product`                     | **Aggregate Root** — đại diện một "loại sản phẩm" trừu tượng (SPU). Do Seller tạo, thuộc 1 Seller + 1 Category                  | "iPhone 16 Pro" — 1 product, nhiều màu và dung lượng                                  |
| `Variant`                     | **Aggregate Root** — một biến thể cụ thể có thể mua được (SKU). Inventory, Cart, Order BC đều ref trực tiếp vào đây qua `skuId` | "iPhone 16 Pro / 256GB / Titan Đen" — đây là thứ user thêm vào giỏ                    |
| `SkuId`                       | ID của Variant — stable identifier dùng chung giữa Catalog, Inventory, Cart, Order BC                                           | `01JXYZ...` — khi Order reference SKU này, nó không đi qua Product                    |
| `VariantCombination`          | **Value Object bất biến** — tổ hợp (template, option) định nghĩa Variant là "cái gì". Không thay đổi sau khi tạo                | `{Màu sắc: Titan Đen, Dung lượng: 256GB}` — đổi combination = SKU khác                |
| `AttributeTemplate`           | **Aggregate Root** — định nghĩa một đặc tính sản phẩm (tên, kiểu input, option pool). Scope GLOBAL hoặc CATEGORY                | "Màu sắc" (SELECT, CATEGORY), "Xuất xứ" (TEXT, GLOBAL)                                |
| `AttributeOption`             | **Entity trong AttributeTemplate** — một giá trị hợp lệ khi inputType=SELECT                                                    | "Titan Đen", "Titan Trắng" trong template "Màu sắc"                                   |
| `CategoryAttributeAssignment` | **Value Object trong Category** — config cách 1 template được dùng trong category đó                                            | Category "Smartphone" assign "Màu sắc" với `isVariantDefining=true`, `displayOrder=1` |
| `Category`                    | **Aggregate Root** — taxonomy node trong cây phân loại, Admin sở hữu. Tối đa 3 level                                            | "Điện tử" (L1) → "Điện  thoại" (L2) → "Smartphone" (L3)                               |
| `Brand`                       | **Aggregate Root** — whitelist thương hiệu Admin-managed, tránh trùng lặp do typo                                               | "Apple", "Samsung" — Seller chọn từ danh sách này, không tự điền                      |
| `Publish`                     | Transition Product sang PUBLISHED — đưa ra storefront                                                                           | Seller nhấn "Đăng bán" → `ProductPublishedEvent` → Inventory tạo StockRecord          |
| `Block`                       | Admin force-gỡ Product — Seller không thể tự Publish lại                                                                        | Vi phạm chính sách → Admin block → Seller phải liên hệ support                        |

---

## Domain Model

### Aggregate Roots

| Aggregate           | Owned By | Trách nhiệm                                                                 |
|---------------------|----------|-----------------------------------------------------------------------------|
| `Product`           | Seller   | SPU — thông tin sản phẩm, metadata, ảnh. Không chứa Variant trong aggregate |
| `Variant`           | Seller   | SKU — biến thể có thể mua, target trực tiếp của Inventory/Cart/Order BC     |
| `Category`          | Admin    | Taxonomy node, chứa CategoryAttributeAssignment                             |
| `AttributeTemplate` | Admin    | Định nghĩa attribute — dùng chung (GLOBAL) hoặc per-category (CATEGORY)     |
| `Brand`             | Admin    | Whitelist thương hiệu — tránh "Nike" / "NIKE" / "nike" tồn tại song song    |

> **Tại sao Variant là Aggregate Root, không phải Entity trong Product:**  
> Inventory BC, Cart BC, Order BC đều reference `skuId` trực tiếp — không đi qua Product.  
> Commands như `deactivate()`, `changePrice()` nhắm thẳng vào 1 Variant; nếu là Entity thì phải load toàn bộ Product (có thể hàng trăm variants) chỉ để thay đổi 1 dòng.  
> Guard `publish()` "phải có ít nhất 1 Variant ACTIVE" là cross-AR query, không phải in-aggregate consistency check.

### Entities & Value Objects

```
Product (AR)                                    -- Ví dụ: "iPhone 16 Pro"
  ├─ productId, sellerId, categoryId, brandId
  ├─ name, description (rich text)
  ├─ status: DRAFT | PUBLISHED | UNPUBLISHED | BLOCKED
  ├─ warrantyInfo: WarrantyInfo                 [Value Object]
  │    └─ { months, type, coverage }            -- "12 tháng, chính hãng, toàn cầu"
  ├─ attributeValues: ProductAttributeValue[]   [Value Object list — non-variant attrs]
  │    └─ { attributeTemplateId, value }        -- { "Xuất xứ": "Mỹ" }, { "OS": "iOS 18" }
  └─ images: ProductImage[]                     [Entity — ordered, MinIO objectKey]
       └─ { imageId, objectKey, displayOrder }  -- ảnh đầu = thumbnail

Variant (AR)                                    -- Ví dụ: "iPhone 16 Pro / 256GB / Titan Đen"
  ├─ skuId, productId                           -- productId là cross-AR ref (String), không FK object
  ├─ combination: VariantCombination            [Value Object — IMMUTABLE after create]
  │    └─ [(templateId, optionId), ...]         -- [(Màu sắc, Titan Đen), (Dung lượng, 256GB)]
  ├─ price: BIGINT (đơn vị: đồng)              -- 29_990_000
  ├─ originalPrice: BIGINT? (phải > price)      -- 34_990_000 → hiển thị gạch ngang
  ├─ weight: DECIMAL (gram)                     -- 227.0
  ├─ dimensions: { length, width, height } (cm) -- 15.9 × 7.6 × 0.83
  ├─ barcode: string?                           -- "194253417514"
  ├─ images: SkuImage[]                         [Value Object list — optional per-SKU override]
  └─ status: ACTIVE | INACTIVE

Category (AR)                                   -- Ví dụ: "Smartphone" (L3)
  ├─ categoryId, name, slug, parentId, level (1|2|3)
  ├─ imageUrl, status: ACTIVE | INACTIVE
  └─ assignments: CategoryAttributeAssignment[] [Value Object list]
       └─ { templateId, isVariantDefining,      -- isVariantDefining=true → "Màu sắc" tạo ra variants
             isRequired, isFilterable,           -- isFilterable=true → hiện trong bộ lọc storefront
             displayOrder }

AttributeTemplate (AR)                          -- Ví dụ: "Màu sắc"
  ├─ templateId, name, displayName
  ├─ inputType: SELECT | TEXT | NUMBER | BOOLEAN
  ├─ scope: GLOBAL | CATEGORY
  │    GLOBAL  → mọi Product đều có (Xuất xứ, Bảo hành)
  │    CATEGORY → chỉ hiện khi Category assign vào (Màu sắc, Dung lượng)
  └─ options: AttributeOption[]                 [Entity — chỉ có khi inputType=SELECT]
       └─ { optionId, value, displayValue,      -- { "titan-black", "Titan Đen", ACTIVE }
             status: ACTIVE | INACTIVE }

Brand (AR)                                      -- Ví dụ: "Apple"
  └─ brandId, name, slug, status: ACTIVE | INACTIVE
```

### Tại Sao AttributeTemplate Là Global (Không Phải Owned By Category)

Tham khảo Tiki, Shopee, Lazada: cả 3 platform đều expose attribute theo `categoryId` từ phía API (`GET /categories/{id}/attributes`) nhưng `attribute_id` là global stable ID — "Color" có cùng ID dù xuất hiện trong category Áo thun hay Điện thoại.

Nếu AttributeTemplate là entity **trong** Category:
- Admin thêm màu "Đỏ" → phải cập nhật 3 category riêng biệt
- "Color" trong Áo thun và "Color" trong Quần là 2 object khác nhau → mất đồng bộ option pool
- Search BC không thể facet "Màu sắc" nhất quán cross-category

Giải pháp: **AttributeTemplate là Aggregate Root riêng**. Category chỉ assign vào và config cách dùng (`isVariantDefining`, `isRequired`, `displayOrder`) qua `CategoryAttributeAssignment`.

---

## Product Lifecycle

```
        publish()              unpublish()
DRAFT ──────────► PUBLISHED ◄────────────► UNPUBLISHED
                      │
               block() [Admin]
                      ▼
                   BLOCKED
                      │
              unblock() [Admin]
                      ▼
                UNPUBLISHED
```

| Transition                | Guard                                                                                                  |
|---------------------------|--------------------------------------------------------------------------------------------------------|
| `DRAFT → PUBLISHED`       | `variantRepository.existsActiveByProductId(productId)` — cross-AR query, không phải in-aggregate check |
| `BLOCKED → *`             | Chỉ Admin — Seller không thể tự thoát BLOCKED                                                          |
| `UNPUBLISHED → PUBLISHED` | Guard tương tự DRAFT                                                                                   |

---

## Flow Seller Tạo Product

```
1. Seller chọn Category
2. System load:
     - GLOBAL AttributeTemplates (luôn hiển thị)
     - CategoryAttributeAssignment của Category đó (thêm vào)
3. Seller điền non-variant attributes (Brand, RAM, OS...)
4. Seller chọn variant-defining templates + options:
     Color: [Đen, Trắng]  ×  Size: [S, M, L]
5. System auto-generate Cartesian matrix → 6 Variant rows
6. Seller điền price, weight, barcode cho từng Variant (bulk-edit)
7. Upload ảnh (flow bên dưới) → save → DRAFT
8. publish() → PUBLISHED → emit ProductPublishedEvent
```

### Image Upload (Presigned URL Flow)

```
1. Seller POST /seller/products/{id}/images/upload-url
2. catalog-service → MinIO: generate presigned PUT URL (TTL 5 phút)
3. Browser upload thẳng lên MinIO (bypass catalog-service)
4. Browser POST /seller/products/{id}/images/confirm { objectKey }
5. catalog-service verify object tồn tại → append vào Product.images
```

---

## API

### Category (Admin)

| Method   | Path                                                 | Mô tả                                                               |
|----------|------------------------------------------------------|---------------------------------------------------------------------|
| `GET`    | `/api/categories`                                    | Trả về cây category (tree)                                          |
| `POST`   | `/api/admin/categories`                              | Tạo category mới                                                    |
| `PUT`    | `/api/admin/categories/{id}`                         | Sửa name, slug, image                                               |
| `DELETE` | `/api/admin/categories/{id}`                         | Chỉ cho phép nếu không có Product reference                         |
| `GET`    | `/api/categories/{id}/attributes`                    | Trả về attributes áp dụng cho category (GLOBAL + CATEGORY-assigned) |
| `POST`   | `/api/admin/categories/{id}/attributes`              | Assign AttributeTemplate vào Category                               |
| `PUT`    | `/api/admin/categories/{id}/attributes/{templateId}` | Sửa config assignment (isVariantDefining, displayOrder...)          |
| `DELETE` | `/api/admin/categories/{id}/attributes/{templateId}` | Bỏ assignment                                                       |

**GET /api/categories/{id}/attributes — response:**
```json
[
  {
    "templateId": "AT-001",
    "name": "color",
    "displayName": "Màu sắc",
    "inputType": "SELECT",
    "scope": "CATEGORY",
    "isVariantDefining": true,
    "isRequired": false,
    "isFilterable": true,
    "displayOrder": 1,
    "options": [
      { "optionId": "AO-001", "value": "black", "displayValue": "Đen" },
      { "optionId": "AO-002", "value": "white", "displayValue": "Trắng" }
    ]
  },
  {
    "templateId": "AT-BRAND",
    "name": "brand",
    "displayName": "Thương hiệu",
    "inputType": "TEXT",
    "scope": "GLOBAL",
    "isVariantDefining": false,
    "isRequired": true,
    "isFilterable": true,
    "displayOrder": 0
  }
]
```

---

### AttributeTemplate (Admin)

| Method   | Path                                                     | Mô tả                              |
|----------|----------------------------------------------------------|------------------------------------|
| `GET`    | `/api/admin/attribute-templates`                         | Danh sách tất cả templates         |
| `POST`   | `/api/admin/attribute-templates`                         | Tạo template mới                   |
| `PUT`    | `/api/admin/attribute-templates/{id}`                    | Sửa displayName                    |
| `POST`   | `/api/admin/attribute-templates/{id}/options`            | Thêm option (chỉ inputType=SELECT) |
| `DELETE` | `/api/admin/attribute-templates/{id}/options/{optionId}` | Soft-delete option                 |

---

### Brand (Admin)

| Method   | Path                     | Mô tả                       |
|----------|--------------------------|-----------------------------|
| `GET`    | `/api/brands`            | Danh sách brand đang ACTIVE |
| `POST`   | `/api/admin/brands`      | Tạo brand                   |
| `PUT`    | `/api/admin/brands/{id}` | Sửa name                    |
| `DELETE` | `/api/admin/brands/{id}` | Soft-delete (INACTIVE)      |

---

### Product — Seller

| Method   | Path                                          | Mô tả                                                 |
|----------|-----------------------------------------------|-------------------------------------------------------|
| `POST`   | `/api/seller/products`                        | Tạo Product (DRAFT)                                   |
| `GET`    | `/api/seller/products`                        | Danh sách product của seller                          |
| `GET`    | `/api/seller/products/{id}`                   | Chi tiết product                                      |
| `PUT`    | `/api/seller/products/{id}`                   | Sửa thông tin product (name, description, attributes) |
| `POST`   | `/api/seller/products/{id}/publish`           | publish() → PUBLISHED                                 |
| `POST`   | `/api/seller/products/{id}/unpublish`         | unpublish() → UNPUBLISHED                             |
| `POST`   | `/api/seller/products/{id}/images/upload-url` | Lấy presigned PUT URL từ MinIO                        |
| `POST`   | `/api/seller/products/{id}/images/confirm`    | Confirm upload, append vào images                     |
| `DELETE` | `/api/seller/products/{id}/images/{imageId}`  | Xóa ảnh                                               |

---

### Variant — Seller

| Method | Path                                                           | Mô tả                                              |
|--------|----------------------------------------------------------------|----------------------------------------------------|
| `POST` | `/api/seller/products/{productId}/variants`                    | Tạo Variant — combination phải unique              |
| `PUT`  | `/api/seller/products/{productId}/variants/{skuId}`            | Sửa price, weight, barcode (không sửa combination) |
| `POST` | `/api/seller/products/{productId}/variants/{skuId}/deactivate` | INACTIVE                                           |
| `POST` | `/api/seller/products/{productId}/variants/{skuId}/activate`   | ACTIVE                                             |

---

### Product — Public (Guest/Customer)

| Method | Path                          | Mô tả                            |
|--------|-------------------------------|----------------------------------|
| `GET`  | `/api/products/{id}`          | Chi tiết product (chỉ PUBLISHED) |
| `GET`  | `/api/products/{id}/variants` | Danh sách variant + giá + status |

---

### Product — Admin

| Method | Path                               | Mô tả                   |
|--------|------------------------------------|-------------------------|
| `POST` | `/api/admin/products/{id}/block`   | block(reason) → BLOCKED |
| `POST` | `/api/admin/products/{id}/unblock` | unblock() → UNPUBLISHED |

---

## Events Published

| Event                      | Topic                           | Trigger                 | Consumers                                                 |
|----------------------------|---------------------------------|-------------------------|-----------------------------------------------------------|
| `ProductPublishedEvent`    | `catalog.product.published`     | `product.publish()`     | Search BC (index), Inventory BC (tạo StockRecord per SKU) |
| `ProductUnpublishedEvent`  | `catalog.product.unpublished`   | `product.unpublish()`   | Search BC (remove), Inventory BC (block reservation)      |
| `ProductBlockedEvent`      | `catalog.product.blocked`       | `product.block()`       | Search BC (remove), Inventory BC (block reservation)      |
| `ProductUnblockedEvent`    | `catalog.product.unblocked`     | `product.unblock()`     | —                                                         |
| `ProductUpdatedEvent`      | `catalog.product.updated`       | `product.update()`      | Search BC (re-index)                                      |
| `VariantPriceChangedEvent` | `catalog.variant.price-changed` | `variant.changePrice()` | Search BC (update priceRange)                             |
| `VariantDeactivatedEvent`  | `catalog.variant.deactivated`   | `variant.deactivate()`  | Inventory BC, Search BC                                   |
| `CategoryUpdatedEvent`     | `catalog.category.updated`      | `category.update()`     | Search BC (update facet metadata)                         |

### Payload mẫu — ProductPublishedEvent

```json
{
  "eventId": "01HXYZ...",
  "eventType": "ProductPublishedEvent",
  "aggregateId": "PROD-001",
  "occurredAt": "2026-06-13T08:00:00Z",
  "payload": {
    "productId": "PROD-001",
    "sellerId": "SELLER-001",
    "categoryId": "CAT-L3-TOPS",
    "skuIds": ["SKU-001", "SKU-002", "SKU-003"],
    "name": "Áo thun Polo nam",
    "brandName": "Polo Ralph Lauren"
  }
}
```

---

## Events Consumed

catalog-service không consume event từ BC khác. Là upstream — chỉ publish.

---

## Business Rules

### AttributeTemplate

- `scope=GLOBAL`: áp dụng mọi Product, không cần CategoryAttributeAssignment. Ví dụ: Brand, Xuất xứ.
- `scope=CATEGORY`: chỉ hiển thị khi Category đã assign vào và config.
- `AttributeOption` chỉ có thể soft-delete (INACTIVE) — không hard-delete nếu bất kỳ Variant nào đang reference option đó.
- `displayName` có thể sửa. `inputType` và `name` không sửa sau khi có Product reference.

### Category

- Depth tối đa: Level 3. Node L3 không thể có con.
- Không hard-delete Category khi có Product reference — phải re-categorize tất cả Product trước.
- `CategoryAttributeAssignment` là Value Object — replace toàn bộ khi Admin sửa config assignment.
- Cùng `attributeTemplateId` không được assign 2 lần trong cùng Category.

### Product

- Phải có ít nhất 1 Variant ACTIVE trước khi `publish()` được gọi.
- `publish()` ném exception nếu `status=BLOCKED`.
- `sellerId` không thể thay đổi sau khi tạo.
- `categoryId` không thể thay đổi sau khi có Variant (vì AttributeTemplate của Variant gắn với category đó).

### Variant

- `VariantCombination` là **bất biến sau khi tạo**. Lý do: Inventory BC đã tạo StockRecord cho `skuId` đó, Order history đã reference — nếu combination đổi thì toàn bộ lịch sử mất nghĩa.
- `VariantCombination` phải unique trong cùng Product (constraint tại DB + domain guard).
- `VariantCombination` chỉ được dùng `AttributeOption` của các template có `isVariantDefining=true` trong category của Product.
- `originalPrice` nếu có: phải `> price`. Validate tại domain method.
- `price` phải `> 0`.

### Image Upload

- Presigned URL TTL: 5 phút.
- Sau khi confirm: catalog-service verify object tồn tại trên MinIO trước khi lưu. Nếu không tồn tại → 422.
- Thứ tự ảnh (`displayOrder`) do Seller quản lý — ảnh đầu tiên là thumbnail.

---

## Caching

| Key                            | TTL     | Invalidate khi                                                          |
|--------------------------------|---------|-------------------------------------------------------------------------|
| `product:{productId}`          | 10 phút | `ProductUpdatedEvent`, `ProductUnpublishedEvent`, `ProductBlockedEvent` |
| `product:{productId}:variants` | 10 phút | `VariantPriceChangedEvent`, `VariantDeactivatedEvent`                   |
| `category:tree`                | 1 giờ   | `CategoryUpdatedEvent`                                                  |
| `categories:{id}:attributes`   | 1 giờ   | Admin sửa CategoryAttributeAssignment                                   |

Cache ở tầng catalog-service (Redis). Mục tiêu: product detail P99 < 100ms với 5,000,000 SKUs trên PostgreSQL.

---

## Dependencies

| Dependency              | Lý do                                            |
|-------------------------|--------------------------------------------------|
| `common-domain`         | `AggregateRoot`, `DomainEvent`                   |
| `outbox-starter`        | Publish events reliable qua Outbox Pattern + CDC |
| `common-events`         | `EventEnvelope` — Kafka contract                 |
| `common-web`            | `ApiResponse`, `GlobalExceptionHandler`          |
| `observability-starter` | Tracing + structured logging                     |
