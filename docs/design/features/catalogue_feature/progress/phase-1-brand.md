# Phase 1 — Brand

**Status:** IN PROGRESS  
**Started:** 2026-06-13  
**Completed:** —

## Checklist

### Domain
- [x] `BrandId` — typed String wrapper (ULID, theo convention project)
- [x] `BrandStatus` enum: `ACTIVE | INACTIVE`
- [x] `BrandErrorCode` enum implements `ErrorCode` — `BRAND_NOT_FOUND(404)`, `BRAND_SLUG_ALREADY_EXISTS(409)`, `BRAND_IN_USE(409)`
- [x] `Brand` aggregate root — fields: `brandId, name, slug, status`
- [x] `Brand.create(BrandId, name, slug)` static factory — private constructor
- [x] `Brand.reconstitute(...)` static factory — rebuild từ persistence
- [x] `Brand.update(name)` method
- [x] `Brand.deactivate()` method — không raise event (Admin-only, không cần downstream notify)
- [x] `BrandRepository` interface — `findById`, `existsBySlug`, `save`, `findAllByStatus`

### Infrastructure — Persistence
- [x] `BrandJpaEntity` tại `infrastructure/persistence/brand/`
- [x] `BrandJpaRepository extends JpaRepository<BrandJpaEntity, String>` (String vì ULID)
- [x] `BrandMapper` — `toDomain(JpaEntity)`, `toJpaEntity(Brand)` — static methods, final class, no Spring
- [x] `BrandPersistenceAdapter implements BrandRepository` tại `infrastructure/adapter/repository/brand/`

### Application — Commands
- [x] `CreateBrand` — `Command(name, slug)`, `Result(String id)`, `Handler`  
      Handler: check slug unique (BrandRepository) → `Brand.create()` → save → **evict `brands:active`** → return id
- [x] `UpdateBrand` — `Command(String id, name)`, `Result(String id)`, `Handler`  
      Handler: findById → `brand.update(name)` → save → **evict `brands:active`**
- [x] `DeactivateBrand` — `Command(String id)`, `Result`, `Handler`  
      Handler: findById → `brand.deactivate()` → save → **evict `brands:active`**  
      NOTE: BRAND_IN_USE check để lại — cần `ProductRepository` (implement khi có Product feature)

### Application — Queries
- [ ] `ListActiveBrands` — `Query()`, `Result(List<BrandSummary>)`, `Handler`  
      `BrandSummary(String id, String name, String slug)`  
      Handler: **`@Cacheable("brands:active")`** → `BrandRepository.findAllByStatus(ACTIVE)`

### Cache — Brand
> Brand không dùng L1 (Caffeine). Không phải hot path đủ để justify per-instance cache.
> Redis TTL đủ làm safety net. Không cần pub/sub invalidation vì không có L1.

| Key | Store | TTL | Invalidate khi |
|---|---|---|---|
| `brands:active` | Redis | 30 min | `CreateBrand`, `UpdateBrand`, `DeactivateBrand` |

**Tại sao không có L1:**
- Brand list tương đối static (Admin-only write), nhưng không phải hot read path như product detail hay category tree.
- Stale 30 min là acceptable — user thấy brand mới chậm vài phút không gây ra vấn đề nghiêm trọng.
- Thêm L1 không giảm đáng kể latency vì Redis round-trip cho một key nhỏ đã đủ nhanh (~1ms).

**Eviction pattern:**
```java
// Command handlers
@CacheEvict(value = "brands:active", allEntries = true)
public CreateBrand.Result handle(CreateBrand.Command command) { ... }

// Query handler
@Cacheable("brands:active")
public ListActiveBrands.Result handle(ListActiveBrands.Query query) { ... }
```

### Presentation
- [ ] `presentation/brand/model/`: `CreateBrandRequest`, `UpdateBrandRequest`, `BrandResponse`
- [ ] `BrandController`:
  - `GET /api/brands` → `ListActiveBrands`
  - `POST /api/admin/brands` → `CreateBrand`
  - `PUT /api/admin/brands/{id}` → `UpdateBrand`
  - `DELETE /api/admin/brands/{id}` → `DeactivateBrand`

## Verify

```bash
# Tạo brand
POST /api/admin/brands { "name": "Nike", "slug": "nike" }
# → 201, body.data.id = <ULID string>

# List
GET /api/brands
# → 200, body.data = [{ id, name: "Nike", slug: "nike" }]

# Duplicate slug
POST /api/admin/brands { "name": "Nike copy", "slug": "nike" }
# → 409

# Deactivate
DELETE /api/admin/brands/{id}
GET /api/brands
# → 200, body.data = []
```

## Session Log
