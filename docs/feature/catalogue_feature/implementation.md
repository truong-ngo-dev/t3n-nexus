# Catalog Service — Implementation Plan

**Service:** `catalog-service`
**Design refs:** `service/catalog-service/service.md`, `service/catalog-service/cache.md`

---

> ⚠️ **Phát hiện khi migrate doc (2026-08-04):** checklist Phase 4–7 dưới đây còn nguyên trạng "chưa tick" từ bản gốc, nhưng code thật trong `services/catalog-service/src/main/java/vn/t3nexus/catalog/domain/{product,variant}/` đã có đầy đủ class (`Product`, `Variant`, tất cả domain event, error code...) kể cả `.class` đã compile. Tài liệu phase 4-7 **không phản ánh đúng hiện trạng thật** — chưa xác minh được tick tới đâu là chính xác (application/presentation layer, test coverage) vì không nằm trong scope đợt dọn doc này. Cần 1 session riêng đối chiếu code thật với checklist trước khi tin vào Status bên dưới.

---

## Phase Overview

| Phase | Tên                    | Nội dung                                                              | Status (theo doc gốc, CHƯA xác minh lại 4-7) |
|-------|------------------------|-------------------------------------------------------------------------|------|
| 0     | Bootstrap              | Module setup, Flyway DDL, base config                                 | DONE |
| 1     | Brand                  | Full stack — domain → infra → app → presentation                      | DONE |
| 2     | AttributeTemplate      | Full stack — bao gồm AttributeOption + soft-delete guard              | DONE |
| 3     | Category               | Full stack — closure table, attribute assignment merge                | DONE |
| 4     | Product Domain + Infra | Aggregate + persistence (Variant, Image, VO) — phức tạp nhất          | TODO (nghi ngờ — xem cảnh báo trên) |
| 5     | Product Write Side     | Command handlers + presentation (create, update, publish, image)      | TODO (nghi ngờ) |
| 6     | Product Read + Cache   | Query handlers, Caffeine L1 + Redis L2, pub/sub invalidation          | TODO (nghi ngờ) |
| 7     | Variant                | Full stack — Variant commands, price change, deactivate               | TODO (nghi ngờ) |
| 8     | Events + Outbox        | Outbox wiring, Kafka topics, all domain event → EventEnvelope mapping | DONE |

**Dependency order:** Phase 0 → 1 → 2 → 3 → 4 → 5, 6, 7 (parallel) → 8

---

## Cross-Cutting Concerns

Những thứ implement 1 lần, dùng xuyên suốt — không lặp lại trong từng phase:

- **DDD convention**: `global/4.convention/ddd-structure.md` — bắt buộc, không thương lượng
- **Libs dùng**: `common-domain` (AggregateRoot, Money), `outbox-starter`, `common-events` (EventEnvelope), `common-web` (ApiResponse), `observability-starter`
- **No var**: khai báo type tường minh, không dùng `var`
- **Event dispatch**: luôn sau `repository.save()` — không dispatch trước persist
- **Domain event**: aggregate raise, không phải handler
- **CommandHandler**: trả `Result`, không trả `Void`

Kiến trúc cache 2 tầng (Caffeine L1 + Redis L2): xem `service/catalog-service/cache.md` — không lặp lại ở đây.

---

## Phase 0 — Bootstrap `DONE` (2026-06-13)

Module setup, Flyway `V1__init_catalog_schema.sql` (13 bảng), `EventDispatcherConfig`. Chi tiết schema: `service/catalog-service/data.md`.

## Phase 1 — Brand `DONE` (2026-06-13 → 06-14)

Full stack CRUD + soft-delete. Không dùng L1 cache (không phải hot path, Redis TTL 30' đủ) — quyết định ghi trong `cache.md`.

## Phase 2 — AttributeTemplate `DONE` (2026-06-14 → 06-15)

Full stack + `AttributeOption` (soft-delete only, domain service guard `validateOptionNotUsedByVariant` trước khi deactivate). `name`/`inputType` là `final` sau khi tạo.

## Phase 3 — Category `DONE` (2026-06-15)

Full stack, closure table cho tree query, `CategoryAttributeAssignment` quản lý riêng (không cascade từ `Category` — composite PK + `orphanRemoval` phức tạp), replace toàn bộ khi update.

## Phase 4 — Product Domain + Infrastructure `TODO theo doc / code đã tồn tại — xem cảnh báo đầu file`

Checklist gốc (chưa xác minh lại theo code thật):

- [ ] Domain VO: `ProductId`, `VariantId`, `ProductStatus` (DRAFT/PUBLISHED/UNPUBLISHED/BLOCKED — **lưu ý**: `service.md` hiện tại mô tả model mới hơn, 2 trục `status`(3 giá trị)/`adminBlocked` độc lập, khác 4-trạng-thái gộp ở đây — code thật theo model nào cần verify), `WarrantyInfo`, `VariantCombination` (hash order-insensitive)
- [ ] `Product` AR: `create`, `update`, `addVariant` (guard combination unique), `publish`/`unpublish`/`block`/`unblock`, `addImage`/`removeImage`
- [ ] `Variant` entity: `create`, `update`, `activate`/`deactivate`, `changePrice`
- [ ] Infra: `ProductJpaEntity`/`VariantJpaEntity` + mapper + persistence adapter

**Verify (chưa chạy lại):** unit test guard publish-without-active-variant, duplicate-combination, blocked-cannot-publish; integration test save/load round-trip.

## Phase 5 — Product Write Side `TODO theo doc / xem cảnh báo đầu file`

Checklist gốc: `CreateProduct`/`UpdateProduct`/`PublishProduct`/`UnpublishProduct`, image upload flow (`StoragePort` + `MinioStorageAdapter` + presigned URL), `BlockProduct`/`UnblockProduct`, `SellerProductController`/`AdminProductController`.

## Phase 6 — Product Read + Cache `TODO theo doc / xem cảnh báo đầu file`

Checklist gốc: `ProductQueryAdapter` (bypass aggregate, native JPQL), `GetPublicProductDetail`/`GetPublicProductVariants` (2-tier cache), `ListSellerProducts`/`GetSellerProductDetail` (không cache), 7 event handler evict cache tương ứng.

## Phase 7 — Variant `TODO theo doc / xem cảnh báo đầu file`

Checklist gốc: `AddVariant` (validate combination thuộc category attributes `isVariantDefining=true`), `UpdateVariant` (combination immutable), `ActivateVariant`/`DeactivateVariant`, variant-level image upload.

## Phase 8 — Events + Outbox `DONE` (2026-06-15)

8 domain event wired qua Outbox (`OutboxEventHandlers`, 8 `EventHandler` bean). `V2__fix_variant_schema.sql` fix schema thật thiếu `sku_code`/`stock`, rename `variant_image`→`sku_image` — phát hiện lệch giữa design gốc và nhu cầu thật lúc code Variant.

---

## Session Log

_(chỉ giữ quyết định/deviation bất ngờ — routine "làm xong đúng plan" không ghi, xem Phase Overview để biết trạng thái)_

- **2026-06-13 (Phase 0):** `outbox_events` dùng schema bản mới nhất (routing_key + trace/span columns) ngay từ đầu — tránh phải alter migration sau này. Package đặt `vn.t3nexus.catalog` theo convention project, khác package cũ ghi trong doc thiết kế ban đầu.
- **2026-06-15 (Phase 3):** `CategoryAttributeAssignment` quản lý tách biệt khỏi `Category` (không cascade) vì composite PK gây phức tạp với `orphanRemoval`. `CategoryUpdatedEvent.eventId` dùng `UUID.randomUUID()` trực tiếp vì domain layer không inject `ULIDGenerator` (đây là Spring bean).
- **2026-06-15 (Phase 8):** Phát hiện schema `variant` thật thiếu `sku_code`/`stock` so với thiết kế Phase 0, và `variant_image` cần đổi tên `sku_image` — xử lý bằng `V2__fix_variant_schema.sql` thay vì sửa lại V1.
- **2026-08-04 (dọn doc):** Phát hiện checklist Phase 4–7 không khớp code thật (xem cảnh báo đầu file) — nén 9 file `progress/phase-*.md` (857 dòng) về file này, xoá thư mục `progress/`.
