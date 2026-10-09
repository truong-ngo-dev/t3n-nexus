# catalog-service

> **Trạng thái:** DRAFT · **Cập nhật:** `2026-09-28`
>
> Tầng kỹ thuật: **cách hiện thực** nghiệp vụ ở [`analysis.md`](./analysis.md), cùng giải pháp cho NFR và các vấn đề
> tích hợp (idempotency, concurrency, độ tin cậy). Không định nghĩa lại nghiệp vụ.

---

## 1. Tổng quan kỹ thuật

Hiện thực nguồn sự thật về sản phẩm, đơn vị bán được và master data (danh mục, thuộc tính, thương hiệu); chỉ phát event,
không nhận. Nghiệp vụ đầy đủ: [`analysis.md`](./analysis.md) §1–§3.

- **Loại service:** domain (có aggregate), upstream thuần.
- **Stack:** Java 21, Spring Boot, PostgreSQL, Redis (cache L2 + pub/sub), Caffeine (cache L1), Kafka (qua outbox + Debezium),
  MinIO (ảnh).
- **Kiến trúc trong service:** hexagonal · **Module:** `domain` (aggregate, domain service) · `application` (use case) ·
  `infrastructure` (persistence, messaging, cache, object storage) · `presentation` (REST).

---

## 2. Ánh xạ domain → code

### 2.1 Aggregate

| Aggregate (analysis) | Root | Entity / VO bên trong | Package | Ghi chú |
|---|---|---|---|---|
| `AGG-CAT-01` Thuộc tính | `AttributeTemplate` | `AttributeOption` (entity) | `domain.attribute` | |
| `AGG-CAT-02` Thương hiệu | `Brand` | — | `domain.brand` | |
| `AGG-CAT-03` Danh mục | `Category` | `CategoryAttributeAssignment` (VO, gồm `AttributeConstraints`, `Discovery`) | `domain.category` | Tổ tiên của 1 node tính qua bảng closure, không nằm trong aggregate (T-01) |
| `AGG-CAT-04` Sản phẩm | `Product` | `ProductAttributeValue` (VO), `ProductImage` (entity), `WarrantyInfo` (VO) | `domain.product` | |
| `AGG-CAT-05` Đơn vị bán được | `Variant` | `VariantCombination` (VO), `SkuImage` (entity) | `domain.variant` | Tham chiếu `Product` bằng ID, không giữ object |

Domain service dùng chung: `AttributeTemplateDomainService` — kiểm giá trị thuộc tính của sản phẩm theo template + assignment
(`INV-CAT-91`, `INV-CAT-92`, `INV-CAT-97`).

### 2.2 Thuật ngữ khác tên

| Thuật ngữ (analysis) | Tên trong code |
|---|---|
| Thuộc tính | `AttributeTemplate` |
| Giá trị chuẩn | `AttributeOption` (`value` = mã, `displayValue` = nhãn) |
| Cấu hình thuộc tính áp dụng (ở lá) | `CategoryAttributeAssignment` (`required`, `constraints`, `discovery`) |
| Đơn vị bán được | `Variant` (định danh `SkuId`) |
| Tổ hợp phân loại | `VariantCombination` — danh sách cặp `(templateId, optionId)` |
| Trục để phân loại | `ProductAttributeValue.isVariantDefining = true` |
| Dùng được | `isUsable()` (thuộc tính); "ACTIVE và mọi tổ tiên ACTIVE" (danh mục) |
| Đang soạn / Đang bán / Ngừng bán | `DRAFT` / `PUBLISHED` / `UNPUBLISHED` |
| Bị chặn | `adminBlocked = true` |
| Còn mua / Hết (đơn vị bán được) | `ACTIVE` / `INACTIVE` |

### 2.3 Trạng thái

| Trục trạng thái (analysis) | Biểu diễn | Giá trị |
|---|---|---|
| Thuộc tính — trục A | `AttributeTemplate.status` | `ACTIVE`, `INACTIVE` |
| Thuộc tính — trục B (từng giá trị) | `AttributeOption.status` | `ACTIVE`, `INACTIVE` |
| Thuộc tính "dùng được" | `AttributeTemplate.isUsable()` | `ACTIVE && (!isSelect() \|\| có option ACTIVE)` |
| Thương hiệu | `Brand.status` | `ACTIVE`, `INACTIVE` |
| Danh mục | `Category.status` | `ACTIVE`, `INACTIVE` |
| Danh mục "dùng được" | Truy vấn tổ tiên qua closure | `status = ACTIVE && không tổ tiên nào INACTIVE` |
| Sản phẩm — trục A (ý muốn bán) | `Product.status` | `DRAFT`, `PUBLISHED`, `UNPUBLISHED` |
| Sản phẩm — trục B (bị chặn) | `Product.adminBlocked` | `true`, `false` |
| Được xem công khai (`INV-CAT-044`) | `Product.isPubliclyVisible()` — predicate duy nhất, dùng cho mọi truy vấn buyer và cho snapshot search | `status == PUBLISHED && !adminBlocked` |
| Đơn vị bán được | `Variant.status` | `ACTIVE`, `INACTIVE` |

---

## 3. Đảm bảo quy tắc

> - **Liên tục**: chặn ở mọi đường có thể làm sai — trong aggregate + ràng buộc DB nếu cần.
> - **Tiên quyết**: kiểm trong use case của hành động đó, tại thời điểm chạy.
>
> Cột Test: `—` = **chưa có test** (việc còn nợ).

| INV | Loại | Tầng | Cơ chế | Test |
|---|---|---|---|---|
| `INV-CAT-011` | Tiên quyết | Application + DB | Kiểm trùng trong `CreateAttributeTemplate`; UNIQUE `attribute_template.name` chặn race | `CreateAttributeTemplateTest` |
| `INV-CAT-012` | Tiên quyết | Domain + DB | `AttributeTemplate.addOption` kiểm trùng; unique index `(template_id, LOWER(value))` chặn race | `AttributeTemplateTest#rejectsDuplicateIgnoringCase`, `#rejectsDuplicateOfDeactivatedOption` |
| `INV-CAT-013` | Tiên quyết | Domain | `AttributeTemplate.addOption` từ chối khi template `INACTIVE` | `AttributeTemplateTest#rejectsWhenTemplateInactive`, `#allowedWhenActiveButAllOptionsDeactivated` |
| `INV-CAT-014` | Liên tục | Domain | `inputType`, `unit` không có setter, không có command sửa | Không cần — bảo đảm bằng cấu trúc (field `final`, cột `updatable = false`) |
| `INV-CAT-021` | Tiên quyết | Application + DB | Kiểm trùng trong `CreateBrand`, `UpdateBrand`; unique index `LOWER(name)` chặn race | `BrandNameUniqueTest` |
| `INV-CAT-022` | Liên tục | Domain | `slug` không có setter; `Brand.create` chỉ nhận `[a-z0-9]+(-[a-z0-9]+)*`, ≤ 100 ký tự | `BrandTest` |
| `INV-CAT-031` | Tiên quyết | Application | Kiểm trùng trong `CreateCategory`, `UpdateCategory` (không có unique index — xem C-04) | — |
| `INV-CAT-032` | Liên tục | Domain | `parentId` không có setter, không có command di chuyển | Không cần — bảo đảm bằng cấu trúc (field `final`) |
| `INV-CAT-033` | Liên tục | Domain | `Category.replaceAssignments` chỉ nhận assignment khi `level = 3` (lá ⇔ tầng 3, T-01) | `CategoryTest#onlyLeafAcceptsAssignments` |
| `INV-CAT-034` | Liên tục | Domain + DB | Domain kiểm trùng `templateId` trong list; PK `(category_id, template_id)` | `CategoryTest#attributeAssignedAtMostOnce` |
| `INV-CAT-035` | Tiên quyết | Application + DB | `DeleteCategory` kiểm có con; FK `parent_id` `ON DELETE RESTRICT` | — |
| `INV-CAT-041` | Liên tục | Domain | Máy trạng thái của `Product` không có chuyển nào về `DRAFT` | `ProductTest#cannotUnpublishDraft` (không có chuyển nào về `DRAFT`) |
| `INV-CAT-042` | Tiên quyết | Domain | `Product.publish()` từ chối khi `adminBlocked` | `ProductTest#cannotPublishWhileBlocked` |
| `INV-CAT-043` | Tiên quyết | Domain | `Product.markDeleted()` chỉ cho khi `DRAFT` | `ProductTest#onlyDraftCanBeDeleted` |
| `INV-CAT-044` | Liên tục | Domain | Predicate duy nhất `Product.isPubliclyVisible()` (§2.3); `GetPublishedProduct` dùng cho chi tiết + variant công khai | `ProductTest#publiclyVisibleOnlyWhenPublishedAndNotBlocked` |
| `INV-CAT-051` | Liên tục | Domain + DB | `VariantCombination` là VO bất biến, không có command sửa; unique `(product_id, combination_hash)` | `VariantTest#combinationEqualityIgnoresOrder` |
| `INV-CAT-052` | Liên tục | Application | Không có command xoá `Variant`; chỉ `DeleteProduct` xoá theo cascade | Không cần — không có đường xoá riêng (repository ném lỗi) |
| `INV-CAT-053` | Liên tục | Application + DB | `AddVariant` kiểm trùng; unique `(product_id, combination_hash)` chặn race | — |
| `INV-CAT-054` | Liên tục | Domain | `Variant` kiểm khi tạo và khi đổi giá: `price > 0` | `VariantTest` |
| `INV-CAT-91` | Tiên quyết | Domain service | `AttributeTemplateDomainService` kiểm giá trị mới/đổi, đọc template trong cùng transaction | `ProductAttributeValidationTest` |
| `INV-CAT-92` | Tiên quyết | Domain service | Cùng service, so với bản đã lưu **theo từng giá trị**: giá trị có sẵn được miễn kiểm (cả trạng thái lẫn `constraints`) | `ProductAttributeValidationTest#unchangedValueOutsideTightenedConstraintIsExempt`, `#removedAttributeCanBeKept` |
| `INV-CAT-93` | Tiên quyết | Application | `ReplaceCategoryAttributeAssignments` kiểm `isUsable()` chỉ với template **mới thêm** vào list | — |
| `INV-CAT-94` | Tiên quyết | Application | `CreateProduct`/`UpdateProduct` (khi đổi danh mục): `level = 3` + không có tổ tiên `INACTIVE` (truy vấn closure) | — |
| `INV-CAT-95` | Tiên quyết | Application | `CreateProduct`/`UpdateProduct` (khi đổi thương hiệu): `Brand.status = ACTIVE` (lỗi `BRAND_INACTIVE`) | — |
| `INV-CAT-96` | Tiên quyết | Application | `PublishProduct` đếm variant `ACTIVE` trong cùng transaction (không khoá — C-03) | — |
| `INV-CAT-97` | Tiên quyết | Domain service | `VariantCombinationPolicy` (gọi từ `AddVariant`, nạp template theo lô): thuộc tính và option đang dùng được | `VariantCombinationPolicyTest#deactivatedOptionIsRejectedForNewVariant`, `#deactivatedTemplateIsRejectedForNewVariant` |
| `INV-CAT-98` | Tiên quyết | Application | `DeleteCategory` kiểm có sản phẩm (không FK — T-09, race chấp nhận ở C-05) | — |
| `INV-CAT-99` | Liên tục | Application | `VariantCombinationPolicy`: đúng 1 giá trị cho mỗi trục (`isVariantDefining`), không thừa/thiếu/trùng trục, option thuộc tập Product đã khai. `UpdateProduct`: nạp mọi `Variant` của sản phẩm (kể cả `INACTIVE`) — có variant thì tập trục phải giữ nguyên (so cả tập); không bỏ giá trị trục đang có variant dùng | `VariantCombinationPolicyTest` |

---

## 4. Commands

> Mọi command: 1 transaction, 1 aggregate được ghi (trừ `CMD-CAT-033`, `CMD-CAT-045` — xem cột khoá). Integration event
> (§7.1) ghi trong cùng transaction.

| CMD (analysis) | Handler | API | Quyền | Transaction / khoá | Idempotency | Publishes |
|---|---|---|---|---|---|---|
| `CMD-CAT-011` | `CreateAttributeTemplate` | `POST /api/admin/attribute-templates` | Admin | 1 tx | Unique theo `name` | — |
| `CMD-CAT-012` | `UpdateAttributeTemplate` | `PUT /api/admin/attribute-templates/{id}` | Admin | 1 tx, optimistic — C-01 | Tự nhiên (ghi đè) | — |
| `CMD-CAT-013` | `DeactivateAttributeTemplate` / `ActivateAttributeTemplate` | `DELETE /api/admin/attribute-templates/{id}` / `POST .../{id}/activate` | Admin | 1 tx, optimistic — C-01 | Tự nhiên (đích trạng thái) | — |
| `CMD-CAT-014` | `AddAttributeOption` | `POST /api/admin/attribute-templates/{id}/options` | Admin | 1 tx, optimistic — C-01 | Unique theo `value` | — |
| `CMD-CAT-015` | `UpdateAttributeOption` | `PUT .../{id}/options/{optionId}` | Admin | 1 tx, optimistic — C-01 | Tự nhiên | — |
| `CMD-CAT-016` | `ReorderAttributeOptions` | `PUT .../{id}/options/order` | Admin | 1 tx, optimistic — C-01 | Tự nhiên | — |
| `CMD-CAT-017` | `DeactivateAttributeOption` / `ActivateAttributeOption` | `DELETE .../{id}/options/{optionId}` / `POST .../{optionId}/activate` | Admin | 1 tx, optimistic — C-01 | Tự nhiên | — |
| `CMD-CAT-021` | `CreateBrand` | `POST /api/admin/brands` | Admin | 1 tx | Unique theo `LOWER(name)` | — |
| `CMD-CAT-022` | `UpdateBrand` | `PUT /api/admin/brands/{id}` | Admin | 1 tx | Tự nhiên | — |
| `CMD-CAT-023` | `DeactivateBrand` / `ActivateBrand` | `DELETE /api/admin/brands/{id}` / `POST .../{id}/activate` | Admin | 1 tx | Tự nhiên | — |
| `CMD-CAT-031` | `CreateCategory` | `POST /api/admin/categories` | Admin | 1 tx (thêm dòng closure cùng tx) | Không — xem C-04 | `CategoryUpdated` |
| `CMD-CAT-032` | `UpdateCategory` | `PUT /api/admin/categories/{id}` | Admin | 1 tx | Tự nhiên | `CategoryUpdated` |
| `CMD-CAT-033` | `ReorderCategories` | `PUT /api/admin/categories/reorder` | Admin | 1 tx, ghi mọi anh em cùng cha | Tự nhiên | `CategoryUpdated` |
| `CMD-CAT-034` | `DeactivateCategory` / `ActivateCategory` | `POST /api/admin/categories/{id}/deactivate` / `.../activate` | Admin | 1 tx | Tự nhiên | `CategoryUpdated` |
| `CMD-CAT-035` | `DeleteCategory` | `DELETE /api/admin/categories/{id}` | Admin | 1 tx; FK RESTRICT chặn race — C-05 | Không cần (lần 2 trả 404) | `CategoryUpdated` |
| `CMD-CAT-036` `037` `038` | `ReplaceCategoryAttributeAssignments` (replace-all, T-03) | `PUT /api/admin/categories/{id}/attributes` | Admin | 1 tx; fan-out snapshot giao cho J-01 | Tự nhiên (thay toàn bộ) | `CategoryUpdated` |
| `CMD-CAT-041` | `CreateProduct` | `POST /api/seller/products` | Seller | 1 tx | Không — TQ-05 | — |
| `CMD-CAT-042` | `UpdateProduct`; ảnh: `GetProductImageUploadUrl`, `ConfirmProductImage`, `RemoveProductImage` | `PUT /api/seller/products/{id}`; `POST .../images/upload-url`, `POST .../images/confirm`, `DELETE .../images/{imageId}` | Seller, chủ sở hữu | 1 tx; không khoá — TQ-06 | Tự nhiên (replace-all) | `ProductUpdated` |
| `CMD-CAT-043` | `PublishProduct` | `POST /api/seller/products/{id}/publish` | Seller, chủ sở hữu | 1 tx — C-03 | Không cần | `ProductPublished` |
| `CMD-CAT-044` | `UnpublishProduct` | `POST /api/seller/products/{id}/unpublish` | Seller, chủ sở hữu | 1 tx | Không cần | `ProductUnpublished` |
| `CMD-CAT-045` | `DeleteProduct` | `DELETE /api/seller/products/{id}` | Seller, chủ sở hữu | 1 tx, xoá Product + mọi Variant | Không cần (lần 2 trả 404) | `ProductDeleted`, `VariantDeleted` × N |
| `CMD-CAT-046` | `BlockProduct` | `POST /api/admin/products/{id}/block` | Admin | 1 tx | Không cần | `ProductBlocked` |
| `CMD-CAT-047` | `UnblockProduct` | `POST /api/admin/products/{id}/unblock` | Admin | 1 tx | Không cần | `ProductUnblocked` |
| `CMD-CAT-051` | `AddVariant` | `POST /api/seller/products/{productId}/variants` | Seller, chủ sở hữu | 1 tx — C-07 | Unique theo tổ hợp (`combination_hash`) | `VariantCreated` |
| `CMD-CAT-052` | `UpdateVariant` | `PUT .../variants/{skuId}` | Seller, chủ sở hữu | 1 tx | Tự nhiên | `VariantPriceChanged` (khi giá đổi) |
| `CMD-CAT-053` | `ActivateVariant` / `DeactivateVariant` | `POST .../variants/{skuId}/activate` / `.../deactivate` | Seller, chủ sở hữu | 1 tx | Không cần | `VariantActivated` / `VariantDeactivated` |

"Không cần" = thao tác người dùng bấm tay; gửi lại nhận lỗi trạng thái rõ ràng (ma trận hành động × trạng thái của analysis),
client coi là đã xong.

---

## 5. Queries

| RM (analysis) | Handler | API | Quyền | Nguồn đọc | Điều kiện hiển thị | Phục vụ NFR |
|---|---|---|---|---|---|---|
| `RM-CAT-01` | `GetCategoryTree` | `GET /api/categories` | Public | Cache `category:tree` L1+L2 (§12.1) | Bỏ node `INACTIVE` → cả nhánh con không còn đường vào; sắp theo `sortOrder` | Tải đọc đỉnh |
| `RM-CAT-02` | `GetPublishedProduct` | `GET /api/products/{id}` | Public | Cache `product` L1+L2 | `isPubliclyVisible()`; không thoả → 404 | Chi tiết < 100ms, tải đọc đỉnh |
| `RM-CAT-03` | `GetProductVariants` | `GET /api/products/{id}/variants` | Public | Cache `product-variants` L1+L2 | Cùng điều kiện `RM-CAT-02`; variant `INACTIVE` vẫn trả, kèm cờ không mua được | Chi tiết < 100ms |
| `RM-CAT-04` | `ListBrands` | `GET /api/brands` | Public | DB (không cache — T-06) | `ACTIVE`, tìm theo tên, phân trang | — |
| `RM-CAT-05` | `GetCategoryAttributes` | `GET /api/categories/{id}/attributes` | Public (dùng ở form seller) | Cache `categories:attributes` L2 | Template `isUsable()`, option `ACTIVE`, kèm `hint`/`unit`/`constraints` | — |
| `RM-CAT-06` | `SearchBrandsForSeller` | `GET /api/seller/brands` | Seller | DB | `ACTIVE`, khớp từ khoá, tối đa 20 | — |
| `RM-CAT-07` | `GetAdminCategoryTree` | `GET /api/admin/categories/tree` | Admin | DB | Mọi trạng thái | — |
| `RM-CAT-08` | `GetCategoryAttributeAssignments` | `GET /api/admin/categories/{id}/assignments` | Admin | DB | Đủ, kể cả template đã tắt | — |
| `RM-CAT-09` | `ListAssignableAttributeTemplates` | `GET /api/admin/categories/{id}/assignable-templates` | Admin | DB | `isUsable()` và chưa gán | — |
| `RM-CAT-10` | `GetAdminProduct` | `GET /api/admin/products/{id}` | Admin | Nạp aggregate `Product` + `Variant` qua repository (không cache) | Mọi trạng thái | — |

Query có trong service nhưng chưa có RM ở analysis: `ListAttributeTemplates`, `GetAttributeTemplate`, `ListAdminBrands`,
`GetBrand`, các màn đọc sản phẩm của chính seller — xem TQ-02.

---

## 6. Use cases — tham gia

| Feature | Vai trò | Bước của service này | Bước bù trừ | Publishes |
|---|---|---|---|---|
| [06-catalogue_feature](../../feature/06-catalogue_feature/implementation.md) | Coordinator (luồng nội bộ) | Toàn bộ command §4 | Không có — không tham gia saga | Mọi event §7.1 |
| [07-place-order](../../feature/07-place-order/design.md) | Không tham gia trực tiếp | Cung cấp `skuId` ổn định; inventory tạo `Stock` từ `VariantCreated` trước khi có đơn | Không có — không nằm trong saga đặt hàng | `VariantCreated`, `VariantActivated`/`VariantDeactivated` |

---

## 7. Integration contract

### 7.1 Publishes

| EVT (analysis) | Event | Topic | Loại | Partition key | Schema | Cơ chế phát |
|---|---|---|---|---|---|---|
| `EVT-CAT-031` | `CategoryUpdatedEvent` | `catalog.category.updated` | Domain | `categoryId` | `EventEnvelope` (JSON) | `outbox_events` + Debezium |
| `EVT-CAT-041` | `ProductPublishedEvent` | `catalog.product.published` | Domain | `productId` | `EventEnvelope` | `outbox_events` + Debezium |
| `EVT-CAT-042` | `ProductUnpublishedEvent` | `catalog.product.unpublished` | Domain | `productId` | `EventEnvelope` | `outbox_events` + Debezium |
| `EVT-CAT-043` | `ProductBlockedEvent` | `catalog.product.blocked` | Domain | `productId` | `EventEnvelope` | `outbox_events` + Debezium |
| `EVT-CAT-044` | `ProductUnblockedEvent` | `catalog.product.unblocked` | Domain | `productId` | `EventEnvelope` | `outbox_events` + Debezium |
| `EVT-CAT-045` | `ProductUpdatedEvent` | `catalog.product.updated` | Domain | `productId` | `EventEnvelope` | `outbox_events` + Debezium |
| `EVT-CAT-046` | `ProductDeletedEvent` | `catalog.product.deleted` | Domain | `productId` | `EventEnvelope` | `outbox_events` + Debezium |
| `EVT-CAT-051` | `VariantCreatedEvent` | `catalog.variant.created` | Domain | `skuId` | `EventEnvelope` | `outbox_events` + Debezium |
| `EVT-CAT-052` | `VariantPriceChangedEvent` | `catalog.variant.price-changed` | Domain | `skuId` | `EventEnvelope` | `outbox_events` + Debezium |
| `EVT-CAT-053` | `VariantActivatedEvent` / `VariantDeactivatedEvent` | `catalog.variant.activated` / `catalog.variant.deactivated` | Domain | `skuId` | `EventEnvelope` | `outbox_events` + Debezium |
| `EVT-CAT-054` | `VariantDeletedEvent` | `catalog.variant.deleted` | Domain | `skuId` | `EventEnvelope` | `outbox_events` + Debezium |
| Gom `EVT-CAT-041`…`046`, `051`…`054` + thay đổi ảnh | `ProductSearchSnapshotEvent` | `catalog.product.search-snapshot` | Integration (state-transfer) | `productId` | `EventEnvelope` | `integration_outbox_events` + connector riêng (T-04) |
| Gom `EVT-CAT-031` + thay đổi thuộc tính/thương hiệu | Snapshot metadata (category, attribute, brand) | *(chưa đặt — TQ-03)* | Integration (state-transfer) | ID của đối tượng | `EventEnvelope` | `integration_outbox_events` (T-04) |

Payload chi tiết từng event: [`event-catalog.md`](../../global/2.architecture/5.%20event-catalog.md).

**`ProductSearchSnapshotEvent`**
- **Trigger:** integration handler nghe domain event trong cùng transaction, **gom theo `productId`** → mỗi transaction tối đa
  1 snapshot / sản phẩm. Không gọi publisher tường minh cuối từng use case.
- **Version:** `UPDATE product SET search_version = search_version + 1 ... RETURNING` — C-02.
- **Không phát** cho sản phẩm chưa từng công khai (`publishedAt = null`): search không cần bản nháp, và bản nháp xoá cứng được.
- **Fan-out do đổi master data** (nhãn option, `discovery` của assignment): không dựng trong transaction của Admin — J-01.
- **Backfill:** `ReplayProductSearchSnapshots` (§10).

- **Thứ tự:** đảm bảo theo từng key (`productId` / `skuId` / `categoryId`). **Không** đảm bảo giữa event sản phẩm và event
  variant (khác key), cũng không giữa `outbox_events` và `integration_outbox_events`. Hệ quả: mỗi consumer chỉ dựa vào **1
  nhánh** — search chỉ đọc snapshot; inventory chỉ đọc domain event. `VariantCreatedEvent` mang sẵn `productPublished` để
  inventory không phụ thuộc thứ tự với `ProductPublished`.
- **Tiến hoá schema:** chỉ thêm field (tương thích ngược); đổi nghĩa/xoá field = event mới.

### 7.2 Consumes

N/A — upstream thuần (analysis §8.2).

### 7.3 Sync calls

| Chiều | Đối tác | Giao thức | Endpoint | Timeout | Retry | Khi đối tác lỗi |
|---|---|---|---|---|---|---|
| Outbound | MinIO | S3 API | Tạo presigned PUT URL (TTL 5 phút); kiểm object tồn tại khi confirm | 2s | 1 lần (chỉ thao tác đọc) | Báo lỗi cho seller, không ghi `ProductImage` |
| Inbound | web-gateway | REST | `/api/**` (gateway rewrite từ `/api/catalog/**`) | | | |

---

## 8. Projection

N/A — catalog không giữ read model dữ liệu của BC khác.

---

## 9. Concurrency

| ID | Điểm tranh chấp | Ai tranh chấp | Cơ chế | Thứ tự khoá | Khi xung đột | Phục vụ |
|---|---|---|---|---|---|---|
| `C-01` | Sửa cùng 1 `AttributeTemplate` (kể cả option bên trong) | 2 Admin | Optimistic `@Version` | — | 409, Admin tải lại | `INV-CAT-012`, `INV-CAT-013` |
| `C-02` | Tăng `search_version` của 1 sản phẩm | 2 transaction cùng sửa Product/Variant của 1 sản phẩm | Row lock của `UPDATE ... RETURNING`, giữ tới hết transaction | — | Transaction sau chờ; version đơn điệu | §7.1 snapshot |
| `C-03` | `PublishProduct` song song với `DeactivateVariant` variant ACTIVE cuối cùng | Seller 2 tab | Không khoá | — | Có thể ra "đang bán, không variant nào mua được" — trạng thái analysis chấp nhận (hết đơn vị bán được không tự ngừng bán) | `INV-CAT-96` |
| `C-04` | Tạo/đổi tên 2 danh mục trùng tên cùng cha | 2 Admin | Kiểm ở application, không có unique index | — | Có thể lọt trùng; hiếm, Admin sửa tay — TQ-07 | `INV-CAT-031` |
| `C-05` | `DeleteCategory` song song `CreateProduct` (hoặc đổi danh mục) vào chính danh mục đó; `DeleteCategory` song song `CreateCategory` con | Admin + Seller | Danh mục con: FK `parent_id` `ON DELETE RESTRICT`. Sản phẩm: **không FK** (T-09) — chỉ kiểm `hasProductReference` | — | Danh mục con: bên xoá lỗi FK → 409. Sản phẩm: chấp nhận race — hiếm (xoá chỉ dùng cho danh mục tạo nhầm, chưa ai dùng), hệ quả là sản phẩm trỏ tới danh mục không còn | `INV-CAT-035`, `INV-CAT-98` |
| `C-06` | Trùng mã/tên khi tạo (template, option, brand, tổ hợp variant) | 2 request | Unique index | — | 409 | `INV-CAT-011`, `012`, `021`, `053` |
| `C-07` | `UpdateProduct` bỏ giá trị trục song song `AddVariant` dùng đúng giá trị đó | Seller 2 tab | Chưa có — cả 2 cùng qua kiểm tra → variant mang giá trị không còn trong tập | — | Vi phạm `INV-CAT-99` — TQ-10 | `INV-CAT-99` |

---

## 10. Độ tin cậy

| Hạng mục | Thiết kế | Phục vụ |
|---|---|---|
| Ghi DB + phát event nguyên tử | Transactional outbox: `outbox_events` (domain) và `integration_outbox_events` (integration), ghi cùng transaction với aggregate; Debezium đọc 2 bảng bằng 2 connector | §7.1 |
| Cô lập 2 luồng event | Replay/fan-out snapshot chỉ đổ vào `integration_outbox_events` → không làm trễ domain event cho inventory | §7.1 |
| Phục hồi read model phía search | `ReplayProductSearchSnapshots` (Admin, `POST /api/admin/search/replay/products`): duyệt keyset theo id, mỗi sản phẩm 1 transaction, chỉ sản phẩm đã từng công khai; idempotent nhờ version | §7.1 |
| Resilience | Timeout cho MinIO; không có sync call nào khác | §7.3 |
| Cache sai lệch sau ghi | Evict L1+L2 sau commit + pub/sub evict L1 toàn fleet; TTL là lưới an toàn | §12.1 |

---

## 11. Job định kỳ
> **Chưa hiện thực** — toàn bộ mục này là thiết kế đích; hoãn có chủ đích, xem `TD-01`…`TD-03` (§18).

| ID | Job | Trigger (task type scheduler) | Tần suất | Phục vụ | Idempotent (cách) | Khi chạy lỗi | Test |
|---|---|---|---|---|---|---|---|
| `J-01` | Dựng snapshot cho sản phẩm bị ảnh hưởng bởi đổi master data | *(chưa đặt)* | Liên tục / vài giây | §7.1 fan-out | Transaction Admin chỉ ghi marker `productId`; worker gom theo key, dựng snapshot với version mới | Marker còn → lần sau dựng lại | — |
| `J-02` | Dọn dòng đã phát trong 2 bảng outbox | *(chưa đặt)* | Hằng ngày | §10 outbox | Xoá theo `published_at` cũ hơn N ngày | Lần sau xoá tiếp | — |

---

## 12. NFR → giải pháp
> **Chưa đo, chưa kiểm** — cột Kiểm chứng là kế hoạch, chưa có số liệu thật; các điểm yếu đã biết và rate limit: `TD-04`…`TD-09` (§18).

| Kỳ vọng (analysis) | Giải pháp | Chi tiết | Kiểm chứng |
|---|---|---|---|
| Chi tiết sản phẩm < 100ms (`RM-CAT-02`, `RM-CAT-03`) | Cache 2 tầng Caffeine L1 + Redis L2 | [`cache.md`](cache.md) | Load test: P99 < 100ms, tỉ lệ hit ≥ 95% trên tập sản phẩm nóng |
| Đọc ~8.000 req/s thường, ~80.000 req/s đỉnh | L1 trong tiến trình gánh sản phẩm nóng (tránh hot key Redis); service stateless, scale ngang | [`cache.md`](cache.md) | Load test kịch bản flash sale: 80.000 req/s dồn vào ~100 sản phẩm |
| ~5.000.000 đơn vị bán được | Index theo `product_id`, `seller_id`, `category_id`; không có truy vấn quét toàn bảng ở luồng buyer | [`data.md`](data.md) | `EXPLAIN` các truy vấn §5 trên dữ liệu 5M |
| Seller sửa sản phẩm P99 < 500ms (đề xuất) | Làm giàu snapshot đồng bộ trong transaction chỉ cho sửa lẻ; fan-out hàng loạt đẩy sang J-01 | §7.1 | Đo thời gian use case `UpdateProduct`, `AddVariant` |

### 12.1 Cache (tóm tắt)

| Cache gì | Tầng | Key | TTL (L1 / L2) | Invalidate khi | Phục vụ |
|---|---|---|---|---|---|
| Chi tiết sản phẩm | L1 + L2 | `catalog:product:{id}` | 2 phút / 10 phút | `CMD-CAT-042`…`047`, `CMD-CAT-053` | `RM-CAT-02` |
| Variant của sản phẩm | L1 + L2 | `catalog:product:{id}:variants` | 2 phút / 5 phút | `CMD-CAT-051`…`053` | `RM-CAT-03` |
| Cây danh mục | L1 + L2 | `catalog:category:tree` | 30 phút / 1 giờ | `CMD-CAT-031`…`035` | `RM-CAT-01` |
| Thuộc tính của danh mục | L2 | `catalog:categories:{id}:attributes` | — / 1 giờ | `CMD-CAT-012`…`017`, `CMD-CAT-036`…`038` (evict toàn bộ — không biết danh mục nào dùng template) | `RM-CAT-05` |

L1 evict trên mọi instance qua Redis pub/sub `catalog:cache:invalidate`.

### 12.2 Observability

- **Metric:** latency P99 theo endpoint §5; cache hit ratio theo tên cache; outbox lag (2 bảng, tách riêng); số marker J-01
  đang chờ.
- **Span:** use case command → ghi outbox; query → cache L1/L2/DB.
- **Alert:** hit ratio `product` < 90%; outbox lag > 30s; marker J-01 tồn > 5 phút.

---

## 13. Quyền & danh tính

| Hạng mục | Thiết kế | Phụ thuộc |
|---|---|---|
| Danh tính caller | JWT do web-gateway relay; `sub` là định danh người gọi | oauth2-service, web-gateway |
| Quyền sở hữu | Use case Seller nạp Product rồi gọi `Product.assertOwnedBy(sellerId)`; khác → 403 `NOT_PRODUCT_OWNER`. Thao tác trên variant còn kiểm variant thuộc đúng sản phẩm (không → 404) | — (nguồn `sellerId`: TQ-08) |
| Role | Chưa kiểm role — chờ hệ phân quyền riêng (TQ-12). Hiện chỉ yêu cầu đã đăng nhập | — |
| Truy cập công khai | 5 GET của §5 (`RM-CAT-01`…`05`) permitAll ở cả web-gateway lẫn service | web-gateway |

---

## 14. Quyết định kỹ thuật

| ID | Câu hỏi | Chọn | Phương án bị loại (vì sao) | Phục vụ |
|---|---|---|---|---|
| `T-01` | Biểu diễn cây danh mục | Cố định 3 tầng (`level`), lá ⇔ tầng 3; bảng closure cho tổ tiên | Chỉ `parent_id` + đệ quy — mỗi lần kiểm "dùng được" phải đệ quy; materialized path — lợi thế dời nhánh không cần vì cha bất biến | `INV-CAT-033`, `INV-CAT-94`, `RM-CAT-01` |
| `T-02` | Lưu `constraints`/`discovery` của assignment | `jsonb`, domain validate shape theo `inputType` | Cột quan hệ — mỗi kiểu một nhóm cột, đa số `NULL` chéo nhau | `AGG-CAT-03` |
| `T-03` | API gán thuộc tính vào danh mục | Replace-all 1 endpoint cho `CMD-CAT-036`/`037`/`038` | CRUD từng assignment — 3 endpoint cho 1 VO list vốn được lưu kiểu xoá-rồi-chèn | `CMD-CAT-036`…`038` |
| `T-04` | Đồng bộ sang search | Domain event mỏng + integration event (snapshot) tách 2 bảng outbox, cùng transaction | Chỉ domain event — search phải gọi ngược; làm giàu domain event — domain event bị méo theo consumer | → ADR (TQ-04) |
| `T-05` | Version của snapshot | Cột `search_version`, tăng bằng 1 câu `UPDATE ... RETURNING` | Timestamp — phụ thuộc đồng hồ; đọc-rồi-tăng — 2 transaction đọc cùng giá trị | §7.1 |
| `T-06` | Cache đọc buyer | Caffeine L1 + Redis L2 + pub/sub evict; danh sách brand không cache | Chỉ Redis — hot key lúc flash sale; chỉ L1 — không invalidate được toàn fleet; cache brand — có tìm kiếm + phân trang, khó invalidate đúng | NFR chi tiết < 100ms |
| `T-07` | Upload ảnh | Presigned URL, trình duyệt upload thẳng MinIO | Upload qua service — băng thông và bộ nhớ của service | `CMD-CAT-042` |
| `T-08` | Giá trị SELECT trên Product | Lưu `optionId`, không lưu chuỗi giá trị | Lưu chuỗi — đổi nhãn/mã phải sửa mọi sản phẩm | `INV-CAT-92` |
| `T-09` | Toàn vẹn `product.category_id` | Không FK xuống `category` — kiểm ở application | FK `ON DELETE RESTRICT` — mỗi lần ghi sản phẩm phải kiểm/khoá dòng cha (ưu tiên hiệu năng luồng ghi sản phẩm) | `INV-CAT-98`, C-05 |
| `T-10` | Phần chữ trong đường dẫn danh mục | Tự sinh từ tên lúc tạo và mỗi lần đổi tên (bỏ dấu, chữ thường, `[a-z0-9]+(-[a-z0-9]+)*`, ≤ 100 ký tự); **không** duy nhất, **không** bất biến; mọi tra cứu theo ID; Admin không nhập | Admin tự nhập, duy nhất toàn sàn, bất biến (như thương hiệu) — 2 nhánh cùng tên con phải đặt slug gượng ép; slug lệch tên sau khi đổi tên | analysis `AGG-CAT-03` (Thành phần — Đường dẫn) |

---

## 15. Chưa chốt (kỹ thuật)

| ID | Câu hỏi | Ảnh hưởng | Phụ thuộc |
|---|---|---|---|
| `TQ-01` | Có cho đổi danh mục/seller sau khi sản phẩm đã có đơn vị bán được không? Analysis chưa có quy tắc này (`INV-CAT-94` cho đổi danh mục, không nói tới đơn vị bán được). Đổi danh mục có thể làm tập thuộc tính hợp lệ khác đi → cần phân tích ở analysis trước | §3 | `analysis.md` §6.4 |
| `TQ-02` | Thiếu RM trong analysis cho: danh sách/chi tiết thuộc tính (Admin), danh sách/chi tiết thương hiệu (Admin), màn đọc sản phẩm của chính seller — màn này phải tự trả tên thuộc tính + nhãn giá trị cho thuộc tính đã bị gỡ khỏi danh mục (không có trong `RM-CAT-05`), kèm cờ "được bỏ không" (`INV-CAT-99`) | §5 | `analysis.md` §10 |
| `TQ-03` | Analysis §6.1/§6.2 ghi "Thuộc tính/Thương hiệu không phát sự kiện", nhưng search cần metadata để ghép nhãn facet (search analysis Q17) → bổ sung EVT, rồi đặt topic + payload snapshot metadata | §7.1 | `analysis.md` §6.1, §6.2; search-service |
| `TQ-04` | Viết ADR cho mô hình 2 nhánh event (T-04) — ảnh hưởng catalog, inventory, search | §7.1, §10 | inventory, search |
| `TQ-05` | Chống tạo trùng khi client gửi lại `CreateProduct` (Idempotency-Key hay chấp nhận bản nháp trùng) | §4 | web |
| `TQ-06` | Hai lần sửa đồng thời cùng sản phẩm (replace-all) ghi đè nhau — có cần `@Version` cho `Product`? | §4, §9 | — |
| `TQ-07` | Unique index cho tên danh mục trong cùng cha (`COALESCE(parent_id, …)`, `LOWER(name)`) để chặn race C-04 | §9 | — |
| `TQ-08` | Nguồn `sellerId`: dùng thẳng `sub` (khoá chung 1-1 như customer) hay claim riêng khi có seller BC | §13 | oauth2-service, seller BC |
| `TQ-09` | Chống dồn tải DB khi cache sản phẩm nóng hết hạn cùng lúc (flash sale) | §12 | — |
| `TQ-12` | Hệ phân quyền riêng (thay kiểm role theo claim `roles`): mô hình quyền, nơi kiểm (gateway hay service), cách cấp quyền cho service account | §13, L1 | oauth2-service |
| `TQ-10` | Chặn race C-07 (`INV-CAT-99` là Liên tục): khoá dòng `product` (`SELECT … FOR UPDATE`) ở đầu cả `UpdateProduct` lẫn `AddVariant` — gộp chung với lời giải TQ-06 | §9 | — |

---

## 16. Lệch giữa thiết kế và code

> Gồm cả phần tử analysis chưa có chỗ hiện thực (quy ước 3). Rà theo aggregate — **đã rà:** `AGG-CAT-01`…`05`
> (2026-09-28). §11 (job) và §12 (NFR) đã rà 2026-09-30: phần hoãn có chủ đích chuyển sang §18. L1–L3 áp dụng chung cho mọi aggregate, không lặp lại.

| # | Thiết kế quy định | Code hiện tại | Hệ quả | Trạng thái |
|---|---|---|---|---|
| L1 | §13: command Admin/Seller chỉ cho đúng vai trò | `SecurityConfig` chỉ yêu cầu đã đăng nhập (`anyRequest().authenticated()`), không kiểm role | Bất kỳ user đã đăng nhập (buyer, seller) gọi được mọi command Admin — `CMD-CAT-011`…`017` và các command Admin khác | Hoãn — chờ hệ phân quyền riêng (TQ-12) |
| L2 | §9 C-01, C-06: xung đột trả 409 | Vi phạm unique (`INV-CAT-011`, `012` khi race) và lỗi optimistic lock rơi vào handler `Exception` chung → 500 | Dữ liệu vẫn đúng (DB chặn), nhưng client nhận lỗi hệ thống thay vì "đã tồn tại / dữ liệu đã đổi, tải lại" | Đã sửa — `PersistenceConflictExceptionHandler` trả 409 |
| L3 | §3 cột Test | catalog-service không có test nào | Mọi INV của `AGG-CAT-01` chưa có test | Một phần — `AGG-CAT-01`…`05` đã có test (§3); use case (handler) chưa có test riêng |
| L4 | §10: evict cache sau commit | `@CacheEvict` đặt cùng `@Transactional` trên handler, không cấu hình thứ tự — evict không cấu hình thứ tự với transaction, evict có thể chạy trước commit | Request đọc chen giữa evict và commit nạp lại bản cũ vào `categories:attributes`, giữ tới hết TTL 1 giờ | Đã sửa cho L2 Redis (`RedisCacheManager.transactionAware()`); L1 Caffeine + pub/sub của cache sản phẩm vẫn evict trước commit — xử lý khi rà `AGG-CAT-04` |
| L5 | `AGG-CAT-01` không có hành động xoá | `AttributeTemplateRepository.delete` (xoá cứng template + option) vẫn tồn tại, không nơi nào gọi | Chưa gây lỗi; mở đường xoá cứng trái analysis nếu ai đó dùng | Đã sửa — `delete` trong repository ném `UnsupportedOperationException`, bỏ code xoá cứng |
| L6 | — | Comment trong `DeactivateAttributeOption` nói guard `OPTION_IN_USE` còn chạy — guard đã bỏ | Gây hiểu nhầm khi đọc code | Đã sửa |
| L7 | `INV-CAT-022`: định danh đường dẫn của thương hiệu ổn định, dùng làm URL | `slug` bất biến đúng (không setter, `updatable = false`), nhưng chỉ `@NotBlank` — không kiểm định dạng (khoảng trắng, chữ hoa, dấu tiếng Việt lọt vào được); kiểm trùng phân biệt hoa/thường | URL xấu/không hợp lệ, không sửa lại được vì bất biến; `Apple` và `apple` thành 2 slug khác nhau | Đã sửa cho thương hiệu — `Brand.create` kiểm định dạng (lỗi `BRAND_SLUG_INVALID`); danh mục theo T-10 (L14) |
| L8 | `AGG-CAT-02` không có hành động xoá | `BrandRepository.delete` còn tồn tại, không nơi nào gọi | Như L5 | Đã sửa — như L5 |
| L9 | — | Comment trong `DeactivateBrand` trỏ tới "service.md § Category lifecycle" — mục đó không còn | Tham chiếu gãy | Đã sửa |
| L10 | `EVT-CAT-031` phát ở `CMD-CAT-031`…`038` (analysis: đổi tên/ảnh/trạng thái/cấu hình thuộc tính, tạo, xoá) | `CategoryUpdated` chỉ phát trong `Category.update` (`CMD-CAT-032`); tạo, sắp thứ tự, bật/tắt, xoá, đổi assignment không phát | Search không biết cây/luật thuộc tính đổi — cây danh mục phía search và facet lệch | Đã sửa — `Category` phát ở tạo, đổi tên/ảnh, sắp thứ tự, bật/tắt, đổi assignment, xoá (`markDeleted`); mọi handler dispatch (`CategoryTest#everyChangeRaisesCategoryUpdated`) |
| L11 | §12.1: `category:tree` evict ở `CMD-CAT-031`…`035`, L1 evict toàn fleet qua pub/sub | `DeactivateCategory`/`ActivateCategory` chỉ `@CacheEvict` (L1 instance hiện tại + L2), **không** gọi `cacheInvalidationPublisher.clear` | Instance khác vẫn trả cây cũ từ Caffeine tới hết TTL 30 phút — danh mục đã đóng vẫn hiện | Đã sửa — thêm `cacheInvalidationPublisher.clear`, `@CacheEvict(allEntries = true)` |
| L12 | §9 C-05 (bản trước) giả định có FK `product.category_id` | FK đã bị bỏ (migration V3 — "cross-AR reference"); chỉ còn kiểm `hasProductReference` ở application | `DeleteCategory` và `CreateProduct` song song → sản phẩm trỏ tới danh mục không tồn tại (`INV-CAT-98` vỡ) | Chấp nhận — bỏ FK có chủ đích (T-09), race ghi ở C-05 |
| L13 | `INV-CAT-033` đảm bảo ở Domain | Kiểm `level == L3` nằm trong `ReplaceCategoryAttributeAssignments`, `Category.replaceAssignments` không tự kiểm | Đường gọi khác vào aggregate bỏ qua được quy tắc | Đã sửa — kiểm trong `Category.replaceAssignments` |
| L14 | T-10: slug danh mục tự sinh từ tên, không duy nhất, đổi theo tên | Admin nhập `slug` khi tạo, kiểm trùng toàn sàn (unique ở DB), bất biến, không kiểm định dạng | Admin phải tự đặt slug; 2 nhánh cùng tên con va nhau; đổi tên xong slug lệch | Đã sửa — `CategorySlug.from(name)` (bỏ dấu, `đ`→`d`); request tạo bỏ `slug`; đổi tên sinh lại; V19 bỏ `uq_category_slug` |
| L15 | `CMD-CAT-035` xoá lần 2 trả 404 (§4) | `DeleteCategory` không kiểm tồn tại — id không có vẫn trả thành công | Admin không phân biệt được "đã xoá" với "sai id" | Đã sửa — nạp danh mục trước, không có → 404 |
| L16 | — | Comment trỏ tới mục tài liệu không còn (`service.md § Attribute Value Model`, `analysis.md §5.3.1`, `§5.3.2`) trong `ReplaceCategoryAttributeAssignments`, `CategoryRepository`, `CategoryAttributeAssignment`… | Tham chiếu gãy | Đã sửa — tham chiếu analysis theo ID aggregate (`AGG-CAT-0x`, `INV-CAT-92`) thay vì số mục cũ |
| L17 | `INV-CAT-044`: `RM-CAT-02`/`03` chỉ trả sản phẩm được xem công khai (predicate duy nhất `isPubliclyVisible()`) | `GetProduct` (`GET /api/products/{id}`, public) trả **mọi** sản phẩm — kể cả đang soạn, đã ngừng bán, đang bị chặn; không có predicate chung | Ai biết ID là xem được bản nháp và sản phẩm bị Admin chặn | Đã sửa — `Product.isPubliclyVisible()`; `GetPublishedProduct` (404 nếu không công khai) dùng cho cả chi tiết lẫn variant công khai |
| L18 | §13 + analysis §4 "Ranh giới sở hữu": hành vi Seller chỉ trên sản phẩm của chính mình | `sellerId` lấy từ header `X-Seller-Id` do client tự gửi (chỉ khi tạo/liệt kê); sửa, đăng/gỡ, xoá, ảnh, variant **không kiểm chủ sở hữu** | Seller bất kỳ (hoặc ai đã đăng nhập) sửa/xoá/đăng sản phẩm của người khác | Một phần — mọi use case Seller nhận `sellerId` và gọi `Product.assertOwnedBy` (403); variant còn kiểm thuộc đúng sản phẩm. Nguồn `sellerId` vẫn là header `X-Seller-Id` — chờ TQ-08/TQ-12 |
| L19 | Ma trận hành động × trạng thái `AGG-CAT-04`: sửa nội dung, ảnh, ngừng bán **được** khi bị chặn | `guardNotBlocked()` trong `update`, `updateCategory`, `unpublish`, `addImage`, `removeImage` | Seller bị chặn không tự khắc phục được, không tự ngừng bán được | Đã sửa — bỏ `guardNotBlocked`, chỉ `publish` kiểm chặn |
| L20 | Ma trận: ngừng bán ✗ ở "đang soạn"/"ngừng bán"; đăng bán ✗ khi đã "đang bán"; chặn ✗ khi đã chặn; bỏ chặn ✗ khi chưa chặn | `Product` không kiểm trạng thái nguồn: `unpublish()` trên bản nháp → `UNPUBLISHED` dù chưa từng bán (`publishedAt` null); gọi lặp chặn/bỏ chặn/đăng bán phát lại event | Trạng thái vô nghĩa ("ngừng bán" một thứ chưa từng bán); consumer nhận event lặp | Đã sửa — kiểm trạng thái nguồn, lỗi `INVALID_PRODUCT_TRANSITION` (409) |
| L21 | `INV-CAT-95`: thương hiệu phải **đang dùng** lúc tạo | `CreateProduct` chỉ kiểm thương hiệu tồn tại | Tạo được sản phẩm với thương hiệu đã tắt | Đã sửa — `BRAND_INACTIVE` (422) |
| L22 | Ràng buộc riêng theo danh mục (`constraints` của assignment) áp khi seller khai giá trị mới/đổi (analysis `AGG-CAT-03`, `INV-CAT-92`) | `validateProductAttributes` **không đọc `constraints`** — chỉ kiểm guard mặc định của template | `maxLength`, `min`/`max`, `integerOnly`, `maxSelections`, khoảng ngày Admin cấu hình không có tác dụng | Đã sửa — `AttributeConstraintChecker` (TEXT/NUMBER/DATE, kể cả mốc tương đối `now-30d`) + `maxSelections`; giá trị giữ nguyên được miễn; lỗi `ATTRIBUTE_VALUE_CONSTRAINT_VIOLATED` |
| L23 | Analysis `AGG-CAT-04`: chỉ thuộc tính kiểu **chọn 1** làm trục phân loại | `VARIANT_DEFINING_REQUIRES_SELECT` dùng `isSelect()` — nhận cả `MULTI_SELECT` | Trục phân loại kiểu chọn nhiều — mâu thuẫn "mỗi đơn vị đúng 1 giá trị" | Đã sửa — chỉ `SINGLE_SELECT` |
| L24 | Analysis `AGG-CAT-04`: thuộc tính đã bị gỡ khỏi danh mục chỉ giữ nguyên hoặc bỏ hẳn | Miễn kiểm ở cấp template: thuộc tính đã có ở bản cũ thì giá trị **mới** cho nó vẫn được nhận (chỉ kiểm trạng thái/định dạng) | Seller thêm/sửa được giá trị cho thuộc tính danh mục đã bỏ | Đã sửa — giá trị mới cho thuộc tính không còn trong danh mục → `ATTRIBUTE_NOT_IN_CATEGORY` |
| L25 | `EVT-CAT-046` `ProductDeleted` khi xoá bản nháp | `DeleteProduct` chỉ phát `VariantDeleted` từng variant | Consumer không nhận được sự kiện cấp sản phẩm | Đã sửa — `Product.markDeleted()` phát `ProductDeleted` (topic `catalog.product.deleted`) |
| L26 | §7.1: snapshot phát từ integration handler, gom theo `productId`, bảng `integration_outbox_events` riêng | `PublishProductSearchSnapshot.publish()` gọi tường minh cuối từng use case; ghi chung `outbox_events` | Use case mới quên gọi → search lệch; replay chen hàng đợi domain event | Hoãn — cần bảng `integration_outbox_events` + connector Debezium riêng + đổi cấu hình topic (hạ tầng, ADR ở TQ-04) |
| L27 | `EVT-CAT-045` phát khi sửa nội dung (`CMD-CAT-042` gồm cả ảnh) | Thêm/xoá ảnh không phát `ProductUpdated` (chỉ snapshot) | Consumer domain event bỏ sót thay đổi ảnh (hiện chỉ search, đọc snapshot — chưa ảnh hưởng) | Đã sửa — `addImage`/`removeImage` phát `ProductUpdated` |
| L28 | §10: evict cache sau commit (phần còn lại của L4) | `product`/`product-variants`: L1 Caffeine evict ngay trong `@CacheEvict` và `cacheInvalidationPublisher.evict` gọi **trong** transaction — trước commit | Instance khác/ request chen giữa nạp lại bản cũ vào L1, giữ tới 2 phút | Đã sửa — `CacheInvalidationPublisher` chỉ phát sau commit (`TransactionSynchronization`); message tới cả instance gửi nên L1 local cũng được evict lại sau commit |
| L29 | TQ-06, TQ-10 (C-07) | `Product` không có `@Version`, không khoá dòng | Lost update khi sửa song song; race bỏ giá trị trục ↔ tạo variant (`INV-CAT-99`) | Một phần — `UpdateProduct`, `AddVariant` khoá dòng `product` (`findByIdForUpdate`) → hết race C-07. Lost update khi 2 lần sửa nối tiếp từ form cũ vẫn còn (TQ-06) |
| L30 | — | `Product.updateCategory` không có use case/API nào gọi | Code chết; đổi danh mục chưa hỗ trợ (đúng với TQ-01 chưa chốt) | Đã sửa — xoá `updateCategory` |
| L31 | `INV-CAT-97`: giá trị mỗi trục phải **đang dùng được** lúc tạo đơn vị bán được | `AddVariant` cố ý không kiểm trạng thái option (chỉ kiểm option thuộc template) — comment ghi "không check ACTIVE" | Tạo được đơn vị bán được mới với màu/size Admin đã ngừng dùng | Đã sửa — `VariantCombinationPolicy` kiểm thuộc tính và giá trị đang dùng được (lỗi `TEMPLATE_INACTIVE`, `INVALID_ATTRIBUTE_VALUE`) |
| L32 | `INV-CAT-99`: **đúng 1** giá trị cho **mỗi** trục, không thừa trục | `AddVariant` chỉ kiểm không thiếu trục. Không chặn: 2 cặp cùng 1 trục (`Màu:Đỏ` + `Màu:Xanh`); cặp cho thuộc tính **không phải** trục (VD `Xuất xứ:Nhật`) | Đơn vị bán được mang 2 màu, hoặc tổ hợp chứa thông số chung — bộ chọn trên trang chi tiết không dựng đúng được | Đã sửa — `VariantCombinationPolicy`: đúng 1 giá trị/trục (`VARIANT_DUPLICATE_AXIS`), không cặp ngoài trục (`VARIANT_ATTRIBUTE_NOT_AXIS`), không thiếu trục |
| L33 | `INV-CAT-054`: giá niêm yết > 0 và là số nguyên đồng, kiểm trong domain | Trước đây giá > 0 chỉ ở request (`@Min(1)`) | Đường gọi khác (replay, import sau này) lọt giá 0 | Đã sửa — `Variant.create`/`changePrice` kiểm (`VARIANT_PRICE_INVALID`); kiểu `long` đồng. Giá gốc/gạch giá không thuộc catalog (analysis `AGG-CAT-05`) |
| L34 | Analysis `AGG-CAT-05` Thành phần: khối lượng, kích thước (thông tin vận chuyển); `CMD-CAT-052` sửa được | `Variant` không có khối lượng/kích thước | Chưa tính được phí vận chuyển theo đơn vị bán được | Mở — thiếu tính năng |
| L35 | Ma trận `AGG-CAT-05`: bật/tắt đơn vị bán được ✓ ở mọi trạng thái (không bị trạng thái sản phẩm chi phối) | `ActivateVariant`/`DeactivateVariant` từ chối khi sản phẩm bị chặn (`PRODUCT_BLOCKED`) | Seller bị chặn không tắt được đơn vị lỗi để khắc phục | Đã sửa — bỏ kiểm chặn ở bật/tắt |
| L36 | `INV-CAT-052`: không xoá đơn vị bán được độc lập | `VariantRepository.delete(id)` còn tồn tại, không nơi nào gọi | Như L5 | Đã sửa — `delete(VariantId)` ném `UnsupportedOperationException`, bỏ code xoá cứng |
| L37 | `@RateLimit` của lấy URL upload ảnh chỉ tính hạn mức cho người sở hữu | Aspect chạy **trước** `assertOwnedBy`, key theo `productId` → người khác gọi với `productId` của seller A (bị 403) vẫn tiêu hết 20 lần/giờ của A | Seller A nhận 429 ở sản phẩm của chính mình; khai thác được mà không cần chiếm quyền | Mở — sửa sớm: kiểm chủ sở hữu trước rồi gọi `RateLimiter.tryAcquire` bằng code |

---

## 17. Dependencies

- **Services:** web-gateway (routing + token relay), oauth2-service (JWK). Không cần service nào khác để chạy.
- **Infrastructure:** PostgreSQL (`catalog_db`), Redis, MinIO (bucket ảnh), Kafka + Debezium (connector cho `outbox_events`
  và `integration_outbox_events`), các topic ở §7.1.

---

## 18. Nợ kỹ thuật

> Việc đã biết, đã quyết hoãn có chủ đích. Thiết kế ở các mục trên vẫn là đích đúng; ở đây chỉ ghi phần chưa làm.
> Lỗi đang khai thác được (bảo mật, toàn vẹn) **không** để ở đây — xem §16 (vd L37).

**Job định kỳ (§11)**

| ID | Hạng mục | Hiện trạng | Hệ quả nếu chưa trả | Vì sao hoãn | Trả khi nào | Liên quan |
|---|---|---|---|---|---|---|
| `TD-01` | `J-01` dựng lại snapshot khi Admin đổi master data | Chưa có job; đổi nhãn option hay `discovery` của danh mục không phát lại snapshot | Search giữ nhãn/cờ lọc cũ vô thời hạn | Search-service chưa chạy thật, chưa có người đọc snapshot | Trước khi search đọc snapshot ở môi trường thật (Phase B) | §7.1, §11 `J-01`, `TQ-04` |
| `TD-02` | `J-02` dọn dòng đã phát ở bảng outbox | Chưa có; `outbox_events` chỉ tăng, `OutboxEventRepository` không có thao tác dọn | Bảng phình, thao tác của Debezium và truy vấn chậm dần | Chưa có tải đáng kể | Trước go-live, hoặc khi bảng vượt vài triệu dòng | §10, §11 `J-02` |
| `TD-03` | Replay snapshot cho search | Chạy **đồng bộ trong một request HTTP**, duyệt toàn bộ sản phẩm đã đăng, mỗi sản phẩm một transaction; không tiếp tục được khi request đứt; đổ vào cùng bảng outbox với domain event của saga; kết quả chỉ có số lượng | Timeout ở gateway khi dữ liệu lớn; replay làm chậm event của inventory | Dữ liệu dev còn nhỏ | Cùng đợt với `TD-01`: chuyển sang chạy theo lô qua scheduler-service, có con trỏ tiếp tục và chỉ một lần chạy tại một thời điểm | §10, L26 |

**NFR (§12)**

| ID | Hạng mục | Hiện trạng | Hệ quả nếu chưa trả | Vì sao hoãn | Trả khi nào | Liên quan |
|---|---|---|---|---|---|---|
| `TD-04` | Kiểm chứng NFR bằng số đo | Chưa có load test; cache miss của chi tiết sản phẩm chạy khoảng 6–8 truy vấn nhưng chưa đo | Không chứng minh được "chi tiết < 100ms" và 8.000/80.000 req/s | Chưa có môi trường đo, chưa có dữ liệu 5M | Trước khi chốt NFR với `requirement.md`; cần cho flash sale | §12 |
| `TD-05` | Bảo vệ đường đọc công khai | Cache penetration: ID không tồn tại trả 404 và không được cache; cache stampede khi sản phẩm nóng hết hạn cùng lúc; tìm thương hiệu công khai dùng `LIKE '%…%'`, không cache, không index | Kẻ tấn công dùng ID ngẫu nhiên hoặc từ khoá tuỳ ý đập thẳng DB | Chưa có lưu lượng thật | Trước go-live; stampede cùng lúc với flash sale | `TQ-09`, §12.1 |
| `TD-06` | Cấu hình tài nguyên và quan sát | Lấy mẫu vết 100% (`sampling.probability=1.0`); pool DB mặc định; danh sách sản phẩm của seller không có trần `size`; chưa có index `(seller_id, created_at)`; chưa có metric tuỳ chỉnh (hit ratio, outbox lag, số lỗi replay) | Tốn tài nguyên ở tải cao; một request `size` lớn kéo cả DB; không có số liệu để chỉnh | Chưa có tải | Trước go-live; chặn `size` có thể làm sớm vì rất rẻ | §12.2 |

**Rate limit**

| ID | Hạng mục | Hiện trạng | Hệ quả nếu chưa trả | Vì sao hoãn | Trả khi nào | Liên quan |
|---|---|---|---|---|---|---|
| `TD-07` | Giới hạn theo seller cho hành động ghi | Chỉ có giới hạn chung 300 req/phút theo user ở web-gateway. Mỗi lần tạo sản phẩm, thêm đơn vị bán được hay sửa đều khuếch đại thành snapshot, xoá cache và (với đơn vị bán được) một dòng tồn kho ở inventory | Tài khoản bị lộ hoặc script lỗi tạo hàng loạt bản nháp và dòng tồn kho | Chưa có định danh seller đáng tin để làm key | Sau `TQ-08`/`TQ-12`. Ngưỡng đề xuất: tạo sản phẩm 60/giờ, thêm đơn vị bán được 600/giờ, sửa 120/phút, xác nhận ảnh 60/giờ (cộng trần số ảnh mỗi sản phẩm) | §13, `TQ-08`, `TQ-12` |
| `TD-08` | Cơ chế của `rate-limiter-starter` | 429 từ service không có `Retry-After`; lỗi Redis dẫn tới 500 thay vì cho request đi tiếp (chưa có tuỳ chọn `failOpen`); thời gian lấy từ đồng hồ app, không phải Redis; chưa có bộ đếm số lần bị chặn theo luật | Client không biết chờ bao lâu; sự cố Redis kéo theo lỗi ghi; không có số liệu để chỉnh ngưỡng | Chỉ ảnh hưởng vài luồng ghi tần suất thấp | Khi thêm giới hạn thứ hai ở bất kỳ service nào | `rate-limiting-layers.md` |
| `TD-09` | Lớp gateway (ngoài catalog, ghi lại vì ảnh hưởng luồng duyệt hàng) | Ngưỡng theo IP 300 req/60s chung cho mọi path; IP lấy từ `remoteAddress` (cần xác nhận khi đứng sau load balancer/CDN, nếu không cả sàn dùng chung một bucket); thuật toán ZSET tốn bộ nhớ theo `limit` mỗi key; mỗi request một chuyến Redis | Người dùng chung NAT bị 429 oan; Redis dùng chung với cache L2 bị đè | Đang chạy local, chưa có sơ đồ triển khai | **Trước khi triển khai môi trường thật**: xác nhận cách lấy IP. Khi lên tải: tách ngưỡng theo loại request, đổi sang thuật toán bộ nhớ cố định, đặt `Cache-Control` cho GET công khai | `rate-limiting-layers.md`, api-gateway, web-gateway |

## Tài liệu liên quan

- [`analysis.md`](analysis.md) — nghiệp vụ (nguồn sự thật cho quy tắc)
- [`data.md`](data.md) — schema
- [`api.yaml`](api.yaml) — hợp đồng API
- [`cache.md`](cache.md) — chiến lược cache 2 tầng
- [`downstream-effects.md`](downstream-effects.md) — hành vi của BC khác khi nhận event catalog
- [`../search-service/analysis.md`](../search-service/analysis.md) — lý do mô hình snapshot (Q15, Q16)
