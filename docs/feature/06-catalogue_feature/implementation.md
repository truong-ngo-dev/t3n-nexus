# Catalog Service — Implementation Plan

**Service:** `catalog-service`
**Design refs:** `service/catalog-service/service.md`, `service/catalog-service/cache.md`

---

> ✅ **Đối chiếu code thật xong (2026-08-08):** Phase 4–7 đã DONE thật — verify trực tiếp: `ProductController`/`VariantController` tồn tại đầy đủ endpoint, 17 file trong `application/product/`, 9 file trong `application/variant/`, `CacheConfig.java` khớp 100% với `cache.md` (TTL, L1/L2 whitelist). Model `Product.status` (3 giá trị) + `adminBlocked` (cờ độc lập) — đúng theo `service.md` hiện tại, không phải model 4-trạng-thái gộp cũ ghi trong checklist Phase 4 gốc bên dưới.
>
> Đồng thời phát hiện + xử lý: (1) **catalog-service chưa có route nào ở `api-gateway`/`web-gateway`** — đã bổ sung route qua `web-gateway` + `SecurityFilterChain` permitAll cho 5 GET public endpoint (trước đó thiếu hoàn toàn, mọi request kể cả Guest sẽ 401 do Spring Boot default `anyRequest().authenticated()`); (2) `GetProductImageUploadUrl` thiếu rate-limit — đã thêm `@RateLimit`; (3) ownership check (Product/Variant write endpoint dùng `X-Seller-Id` header không đáng tin cậy) — **cố ý chưa fix**, xem `deferred.md` #1 (chờ Seller có auth thật).

---

## Phase Overview

| Phase | Tên                    | Nội dung                                                              | Status |
|-------|------------------------|-------------------------------------------------------------------------|------|
| 0     | Bootstrap              | Module setup, Flyway DDL, base config                                 | DONE |
| 1     | Brand                  | Full stack — domain → infra → app → presentation                      | DONE |
| 2     | AttributeTemplate      | Full stack — bao gồm AttributeOption + soft-delete guard              | DONE |
| 3     | Category               | Full stack — closure table, attribute assignment merge                | DONE |
| 4     | Product Domain + Infra | Aggregate + persistence (Variant, Image, VO) — phức tạp nhất          | DONE (verify 2026-08-08) |
| 5     | Product Write Side     | Command handlers + presentation (create, update, publish, image)      | DONE (verify 2026-08-08) |
| 6     | Product Read + Cache   | Query handlers, Caffeine L1 + Redis L2, pub/sub invalidation          | DONE (verify 2026-08-08) |
| 7     | Variant                | Full stack — Variant commands, price change, deactivate               | DONE (verify 2026-08-08) |
| 8     | Events + Outbox        | Outbox wiring, Kafka topics, all domain event → EventEnvelope mapping | DONE |
| 9     | Gateway Routing + NFR  | web-gateway route, SecurityFilterChain, rate-limit image-upload-url   | DONE (2026-08-08) |

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

## Phase 4 — Product Domain + Infrastructure `DONE` (verify 2026-08-08)

- [x] Domain VO: `ProductId`, `VariantId`, `WarrantyInfo`, `VariantCombination` — model thật theo `service.md`: `status` (DRAFT/PUBLISHED/UNPUBLISHED, 3 giá trị) + `adminBlocked` (cờ độc lập), **không phải** 4-trạng-thái gộp `ProductStatus` ghi ở checklist gốc trước đây
- [x] `Product` AR: `create`, `update`, `addVariant`, `publish`/`unpublish`, `block`/`unblock`, `addImage`/`removeImage`
- [x] `Variant` entity: `create`, `update`, `activate`/`deactivate`, `changePrice`
- [x] Infra: `ProductJpaEntity`/`VariantJpaEntity` + mapper + persistence adapter

**Verify:** chưa chạy lại test suite trong session này (ngoài scope) — chỉ xác nhận class/method tồn tại đúng chữ ký qua đọc code trực tiếp.

## Phase 5 — Product Write Side `DONE` (verify 2026-08-08)

`ProductController` (`/api/seller/products/**`, `/api/admin/products/**`, `/api/products/{id}` public) đầy đủ endpoint: CreateProduct, UpdateProduct, PublishProduct, UnpublishProduct, BlockProduct, UnblockProduct, GetProductImageUploadUrl (presigned URL, TTL 300s), ConfirmProductImageUpload, RemoveProductImage. 17 file trong `application/product/`.

⚠️ `sellerId` lấy từ `@RequestHeader("X-Seller-Id")` (create/list) hoặc không kiểm tra ownership gì cả (update/publish/image) — biết trước, xem `deferred.md` #1.

## Phase 6 — Product Read + Cache `DONE` (verify 2026-08-08)

`CacheConfig.java` khớp 100% với `cache.md`: Caffeine L1 (category:tree 30p/1 entry, product 2p/10K entries, product-variants 2p/10K entries) + Redis L2 (TTL đúng bảng Cache Inventory), pub/sub invalidation qua `CacheInvalidationPublisher`/`LocalCacheInvalidator`. Không phát hiện lệch giữa doc và code ở phase này.

## Phase 7 — Variant `DONE` (verify 2026-08-08)

`VariantController` (`/api/seller/products/{productId}/variants/**`, `/api/products/{productId}/variants` public) đầy đủ: AddVariant, UpdateVariant, ActivateVariant, DeactivateVariant, GetProductVariants. 9 file trong `application/variant/`. Cùng gap ownership như Phase 5 — không nhận `sellerId`/không verify caller sở hữu product.

## Phase 8 — Events + Outbox `DONE` (2026-06-15)

8 domain event wired qua Outbox (`OutboxEventHandlers`, 8 `EventHandler` bean). `V2__fix_variant_schema.sql` fix schema thật thiếu `sku_code`/`stock`, rename `variant_image`→`sku_image` — phát hiện lệch giữa design gốc và nhu cầu thật lúc code Variant.

## Phase 9 — Gateway Routing + NFR `DONE` (2026-08-08)

Trước phase này, catalog-service **không route được từ browser** — `api-gateway` chỉ có `/web/**`, `/mobile/**`, `/auth/**`; `web-gateway` chỉ route oauth2/identity/customer-service. Đã bổ sung:

- [x] `web-gateway/RouteConfiguration.java` — route `catalog-service` (`/api/catalog/**`, `/web/api/catalog/**`), theo đúng pattern rewritePath của 3 route có sẵn
- [x] `web-gateway/SecurityConfiguration.java` — permitAll cho 5 GET public endpoint (category tree, category attributes, brand list, product detail, product variants) — không permitAll thì mọi request kể cả Guest bị chặn ở `.pathMatchers("/api/**").authenticated()` trước khi kịp tới catalog-service
- [x] `catalog-service/SecurityConfig.java` — **mới, trước đây không có `SecurityFilterChain` nào** (cùng gap đã gặp ở `identity-service`) — Spring Boot default `anyRequest().authenticated()` sẽ chặn 401 toàn bộ GET public nếu không có bean này, kể cả sau khi route đã đúng ở web-gateway
- [x] `webgateway.routes.catalog-service.uri=http://localhost:8005` — property mới
- [x] `GetProductImageUploadUrl` — thêm `@RateLimit(key = productId, limit=20, windowSeconds=3600)` — chặn spam issue presigned URL/tạo rác object-key MinIO
- [x] `rate-limiter-starter` — thêm dependency vào `catalog-service/pom.xml` (chưa có trước đây)
- [ ] Ownership check (Product/Variant write) — **cố ý deferred**, xem `deferred.md` #1

---

## Session Log

_(chỉ giữ quyết định/deviation bất ngờ — routine "làm xong đúng plan" không ghi, xem Phase Overview để biết trạng thái)_

- **2026-06-13 (Phase 0):** `outbox_events` dùng schema bản mới nhất (routing_key + trace/span columns) ngay từ đầu — tránh phải alter migration sau này. Package đặt `vn.t3nexus.catalog` theo convention project, khác package cũ ghi trong doc thiết kế ban đầu.
- **2026-06-15 (Phase 3):** `CategoryAttributeAssignment` quản lý tách biệt khỏi `Category` (không cascade) vì composite PK gây phức tạp với `orphanRemoval`. `CategoryUpdatedEvent.eventId` dùng `UUID.randomUUID()` trực tiếp vì domain layer không inject `ULIDGenerator` (đây là Spring bean).
- **2026-06-15 (Phase 8):** Phát hiện schema `variant` thật thiếu `sku_code`/`stock` so với thiết kế Phase 0, và `variant_image` cần đổi tên `sku_image` — xử lý bằng `V2__fix_variant_schema.sql` thay vì sửa lại V1.
- **2026-08-08 (Phase 4-7 verify + Phase 9):** Đối chiếu checklist Phase 4-7 với code thật — toàn bộ đã DONE, chỉ checklist chưa cập nhật từ trước. Phát hiện catalog-service chưa từng có route gateway lẫn `SecurityFilterChain` — bổ sung cả 2. Ownership check cho Product/Variant write endpoint cố ý deferred vì Seller chưa có auth thật (`X-Seller-Id` header là placeholder tạm).
- **2026-08-04 (dọn doc):** Phát hiện checklist Phase 4–7 không khớp code thật (xem cảnh báo đầu file) — nén 9 file `progress/phase-*.md` (857 dòng) về file này, xoá thư mục `progress/`.
