# catalog-service

**Domain:** Core Domain  
**DB:** PostgreSQL  
**Libs:** `common-domain`, `outbox-starter`, `common-events`, `common-web`, `observability-starter`

## Trách nhiệm

Quản lý toàn bộ Catalog — Category taxonomy, AttributeTemplate, Brand, Product (SPU), Variant (SKU).  
Là **upstream thuần túy** — không consume event từ BC nào, chỉ publish.

---

## Ubiquitous Language

| Thuật ngữ                     | Định nghĩa                                                                                                                  |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `Product`                     | Aggregate Root — đại diện một "loại sản phẩm" trừu tượng (SPU). Do Seller tạo, thuộc 1 Seller + 1 Category                  |
| `Variant`                     | Aggregate Root — một biến thể cụ thể có thể mua được (SKU). Inventory, Cart, Order BC đều ref trực tiếp vào đây qua `skuId` |
| `SkuId`                       | ID của Variant — stable identifier dùng chung giữa Catalog, Inventory, Cart, Order BC                                       |
| `VariantCombination`          | Value Object bất biến — tổ hợp (template, option) định nghĩa Variant là "cái gì". Không thay đổi sau khi tạo                |
| `AttributeTemplate`           | Aggregate Root — định nghĩa một đặc tính sản phẩm (tên, kiểu input, option pool). Scope GLOBAL hoặc CATEGORY                |
| `AttributeOption`             | Entity trong AttributeTemplate — một giá trị hợp lệ khi inputType=SELECT                                                    |
| `CategoryAttributeAssignment` | Value Object trong Category — config cách 1 template được dùng trong category đó                                            |
| `Category`                    | Aggregate Root — taxonomy node trong cây phân loại, Admin sở hữu. Tối đa 3 level                                            |
| `Brand`                       | Aggregate Root — whitelist thương hiệu Admin-managed, tránh trùng lặp do typo                                               |
| `Publish`                     | Transition Product sang PUBLISHED — đưa ra storefront                                                                       |
| `Block`                       | Admin force-gỡ Product — Seller không thể tự Publish lại                                                                    |

---

## Domain Model

### Aggregates

| Aggregate           | Owned By | Trách nhiệm                                                              |
|---------------------|----------|--------------------------------------------------------------------------|
| `Product`           | Seller   | SPU — thông tin sản phẩm, metadata, ảnh                                  |
| `Variant`           | Seller   | SKU — biến thể có thể mua, target trực tiếp của Inventory/Cart/Order BC  |
| `Category`          | Admin    | Taxonomy node, chứa CategoryAttributeAssignment                          |
| `AttributeTemplate` | Admin    | Định nghĩa attribute — dùng chung (GLOBAL) hoặc per-category (CATEGORY)  |
| `Brand`             | Admin    | Whitelist thương hiệu — tránh "Nike" / "NIKE" / "nike" tồn tại song song |

> **Tại sao Variant là Aggregate Root, không phải Entity trong Product:**  
> Inventory/Cart/Order BC đều reference `skuId` trực tiếp — không đi qua Product.  
> Commands như `deactivate()`, `changePrice()` nhắm thẳng vào 1 Variant; nếu là Entity thì phải load toàn bộ Product chỉ để thay đổi 1 dòng.

### Structure

```
Product (AR)
  ├─ productId, sellerId, categoryId, brandId
  ├─ name, description (rich text)
  ├─ status: DRAFT | PUBLISHED | UNPUBLISHED     [3 giá trị — KHÔNG có BLOCKED]
  ├─ adminBlocked: boolean                       [cờ độc lập với status — Admin block/unblock]
  ├─ warrantyInfo: WarrantyInfo                 [VO: { months, type, coverage }]
  ├─ attributeValues: ProductAttributeValue[]   [VO list: { attributeTemplateId, value }]
  └─ images: ProductImage[]                     [Entity: { imageId, objectKey, displayOrder }]

Variant (AR)
  ├─ skuId, productId                           [productId = cross-AR ref, không phải FK object]
  ├─ combination: VariantCombination            [VO IMMUTABLE: [(templateId, optionId), ...]]
  ├─ price: BIGINT (đồng), originalPrice: BIGINT? (phải > price)
  ├─ weight: DECIMAL (gram), dimensions: { length, width, height } (cm)
  ├─ barcode: string?
  ├─ images: SkuImage[]                         [VO list — optional per-SKU override]
  └─ status: ACTIVE | INACTIVE

Category (AR)
  ├─ categoryId, name, slug, parentId, level (1|2|3), imageUrl, status
  └─ assignments: CategoryAttributeAssignment[] [VO: { templateId, isVariantDefining, isRequired, isFilterable, displayOrder }]

AttributeTemplate (AR)
  ├─ templateId, name, displayName
  ├─ inputType: SELECT | TEXT | NUMBER | BOOLEAN
  ├─ scope: GLOBAL | CATEGORY
  └─ options: AttributeOption[]                 [Entity: { optionId, value, displayValue, status }]

Brand (AR)
  └─ brandId, name, slug, status: ACTIVE | INACTIVE
```

### Product Lifecycle

`status` và `adminBlocked` là **2 trục độc lập** — không nén chung thành 1 enum (đúng bài học rút ra từ Stock aggregate ở inventory-service, xem `service/inventory-service/service.md`).

```
Trục 1 — status (seller điều khiển qua publish/unpublish):
        publish()              unpublish()
DRAFT ──────────► PUBLISHED ◄────────────► UNPUBLISHED

Trục 2 — adminBlocked (admin điều khiển qua block/unblock, độc lập với status):
false ──block()──► true ──unblock()──► false
```

Mọi guard write-operation (`update`, `updateCategory`, `publish`, `unpublish`, `addImage`, `removeImage`) đều gọi `guardNotBlocked()` trước — nếu `adminBlocked=true` thì reject bất kể `status` đang là gì. `block()`/`unblock()` tự nó **không đổi `status`**.

| Transition                 | Guard                                                                                             |
|----------------------------|---------------------------------------------------------------------------------------------------|
| `DRAFT → PUBLISHED`        | `variantRepository.existsActiveByProductId(productId)` — cross-AR query, cộng `guardNotBlocked()` |
| `adminBlocked: false→true` | Chỉ Admin (`BlockProduct`) — không có guard nào chặn, admin block được ở mọi status               |
| `adminBlocked: true→false` | Chỉ Admin (`UnblockProduct`) — Seller không thể tự gọi                                            |
| `UNPUBLISHED → PUBLISHED`  | Guard tương tự DRAFT                                                                              |

---

## Use Cases

### Admin

| Use Case                      | Command / Query                                                                                       | Endpoint                                                                 |
|-------------------------------|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Quản lý Brand                 | `CreateBrand`, `UpdateBrand`, `DeactivateBrand`, `ListActiveBrands`                                   | `POST/PUT/DELETE/GET /api/admin/brands`                                  |
| Quản lý Category tree         | `CreateCategory`, `UpdateCategory`, `DeleteCategory`                                                  | `POST/PUT/DELETE /api/admin/categories`                                  |
| Assign attribute vào Category | `AssignAttributeToCategory`, `UpdateCategoryAttributeAssignment`, `RemoveCategoryAttributeAssignment` | `POST/PUT/DELETE /api/admin/categories/{id}/attributes`                  |
| Quản lý AttributeTemplate     | `CreateAttributeTemplate`, `UpdateAttributeTemplate`                                                  | `POST/PUT /api/admin/attribute-templates`                                |
| Quản lý AttributeOption       | `AddAttributeOption`, `UpdateAttributeOption`, `DeactivateAttributeOption`                            | `POST/PUT/DELETE /api/admin/attribute-templates/{id}/options/{optionId}` |
| Block / Unblock Product       | `BlockProduct`, `UnblockProduct`                                                                      | `POST /api/admin/products/{id}/block                                     |unblock` |

### Seller

| Use Case                   | Command / Query                                                          | Endpoint                                                          |
|----------------------------|--------------------------------------------------------------------------|-------------------------------------------------------------------|
| Tạo Product (DRAFT)        | `CreateProduct`                                                          | `POST /api/seller/products`                                       |
| Sửa Product                | `UpdateProduct`                                                          | `PUT /api/seller/products/{id}`                                   |
| Publish / Unpublish        | `PublishProduct`, `UnpublishProduct`                                     | `POST /api/seller/products/{id}/publish                           |unpublish` |
| Upload ảnh (presigned URL) | `GetProductImageUploadUrl`, `ConfirmProductImage`, `RemoveProductImage`  | `POST /api/seller/products/{id}/images/upload-url                 |confirm`, `DELETE` |
| Quản lý Variant            | `CreateVariant`, `UpdateVariant`, `ActivateVariant`, `DeactivateVariant` | `POST/PUT /api/seller/products/{id}/variants`, `POST .../activate |deactivate` |

### Guest / Customer (Read-only)

| Use Case                    | Query                   | Endpoint                              |
|-----------------------------|-------------------------|---------------------------------------|
| Xem category tree           | `GetCategoryTree`       | `GET /api/categories`                 |
| Xem attributes của category | `GetCategoryAttributes` | `GET /api/categories/{id}/attributes` |
| Xem danh sách brand         | `ListActiveBrands`      | `GET /api/brands`                     |
| Xem product detail          | `GetPublishedProduct`   | `GET /api/products/{id}`              |
| Xem variants của product    | `GetProductVariants`    | `GET /api/products/{id}/variants`     |

### Image Upload Flow

```
1. Seller → POST /seller/products/{id}/images/upload-url
2. catalog-service → MinIO: generate presigned PUT URL (TTL 5 phút)
3. Browser upload thẳng lên MinIO (bypass catalog-service)
4. Seller → POST /seller/products/{id}/images/confirm { objectKey }
5. catalog-service verify object tồn tại → append vào Product.images
```

---

## Business Rules

### AttributeTemplate
- `scope=GLOBAL`: áp dụng mọi Product, không cần CategoryAttributeAssignment.
- `scope=CATEGORY`: chỉ hiển thị khi Category đã assign.
- `AttributeOption` chỉ có thể soft-delete — không hard-delete nếu bất kỳ Variant nào đang reference.
- `inputType` và `name` không sửa sau khi có Product reference.

### Category
- Depth tối đa: Level 3. Node L3 không thể có con.
- Không hard-delete Category khi có Product reference.
- `CategoryAttributeAssignment` là Value Object — replace toàn bộ khi Admin sửa.
- Cùng `attributeTemplateId` không được assign 2 lần trong cùng Category.

### Product
- Phải có ít nhất 1 Variant ACTIVE trước khi `publish()`.
- `publish()` ném exception nếu `status=BLOCKED`.
- `sellerId` và `categoryId` không thể thay đổi sau khi có Variant.

### Variant
- `VariantCombination` là **bất biến sau khi tạo** — Inventory/Cart/Order đã reference `skuId` này.
- `VariantCombination` phải unique trong cùng Product (constraint tại DB + domain guard).
- `VariantCombination` chỉ dùng `AttributeOption` của templates có `isVariantDefining=true` trong category.
- `originalPrice` nếu có: phải `> price`.

---

## Integration Contract

### Publishes (Kafka)

| Topic                           | Event                      | Partition Key | Consumers                             |
|---------------------------------|----------------------------|---------------|---------------------------------------|
| `catalog.product.published`     | `ProductPublishedEvent`    | `productId`   | `search-service`, `inventory-service` |
| `catalog.product.unpublished`   | `ProductUnpublishedEvent`  | `productId`   | `search-service`, `inventory-service` |
| `catalog.product.blocked`       | `ProductBlockedEvent`      | `productId`   | `search-service`, `inventory-service` |
| `catalog.product.unblocked`     | `ProductUnblockedEvent`    | `productId`   | `inventory-service`                   |
| `catalog.product.updated`       | `ProductUpdatedEvent`      | `productId`   | `search-service`                      |
| `catalog.variant.created`       | `VariantCreatedEvent`      | `skuId`       | `inventory-service`                   |
| `catalog.variant.price-changed` | `VariantPriceChangedEvent` | `skuId`       | `search-service`                      |
| `catalog.variant.activated`     | `VariantActivatedEvent`    | `skuId`       | `search-service`, `inventory-service` |
| `catalog.variant.deactivated`   | `VariantDeactivatedEvent`  | `skuId`       | `search-service`, `inventory-service` |
| `catalog.category.updated`      | `CategoryUpdatedEvent`     | `categoryId`  | `search-service`                      |

### Consumes (Kafka)

Không consume event từ BC nào. catalog-service là **upstream thuần túy**.

### Sync Calls

Không có outbound sync call. Inbound từ `web-gateway` qua REST.

---

## Dependencies

| Dependency              | Lý do                                            |
|-------------------------|--------------------------------------------------|
| `common-domain`         | `AggregateRoot`, `DomainEvent`                   |
| `outbox-starter`        | Publish events reliable qua Outbox Pattern + CDC |
| `common-events`         | `EventEnvelope` — Kafka contract                 |
| `common-web`            | `ApiResponse`, `GlobalExceptionHandler`          |
| `observability-starter` | Tracing + structured logging                     |
| PostgreSQL (port 5436)  | Primary store                                    |
| Redis                   | L2 cache + pub/sub invalidation                  |
| MinIO                   | Object storage cho product/variant images        |
