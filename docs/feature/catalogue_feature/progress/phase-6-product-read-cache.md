# Phase 6 — Product Read Side + Cache

**Status:** TODO  
**Started:** —  
**Completed:** —

## Checklist

### Cache Infrastructure
- [ ] `CacheConfig` tại `infrastructure/cross-cutting/config/`  
      Caffeine L1 config per cache name:
      ```java
      Caffeine.newBuilder()
          .maximumSize(100_000)
          .expireAfterWrite(2, MINUTES)
          .recordStats()
      ```
      Redis L2 via `RedisCacheManager` — TTL per cache name (xem bảng bên dưới)
- [ ] Cache name constants:
  | Cache name | L1 TTL | L2 TTL |
  |---|---|---|
  | `catalog-product-detail` | 2m | 10m |
  | `catalog-product-variants` | 2m | 10m |
  | `catalog-category-tree` | 30m | 1h |
  | `catalog-category-attributes` | 30m | 1h |
- [ ] `CacheInvalidationListener` — subscribe Redis pub/sub channel `catalog:cache:invalidate`  
      Parse message `{cacheName}:{key}` → `localCaffeine.invalidate(key)`
- [ ] `CacheInvalidationPublisher` tại `application/shared/service/` — `invalidate(cacheName, key)`:  
      (1) evict Redis key `catalog:{cacheName}:{key}`  
      (2) publish pub/sub message `catalog:cache:invalidate` → `{cacheName}:{key}`

### Application — Queries (Read Side)

- [ ] `ProductQueryAdapter` tại `infrastructure/adapter/query/product/` — native JPQL queries, returns ReadModel DTOs trực tiếp (bypass domain aggregate)
  - `findPublicProductDetail(productId)` — chỉ product `status=PUBLISHED`
  - `findProductVariantsByProductId(productId)` — variants + combination items
  - `findSellerProducts(sellerId, Pageable)` — paged list
  - `findSellerProductDetail(productId, sellerId)` — full detail

- [ ] `GetPublicProductDetail` — `Query(UUID productId)`, `Result(PublicProductDetailDto)`, `Handler`  
      L1 check → L2 check → `ProductQueryAdapter.findPublicProductDetail()` → populate both caches → return
- [ ] `GetPublicProductVariants` — `Query(UUID productId)`, `Result(List<VariantDto>)`, `Handler`  
      Cache `catalog-product-variants:{productId}`
- [ ] `ListSellerProducts` — `Query(UUID sellerId, int page, int size)`, `Result(Page<SellerProductSummary>)`, `Handler`  
      Không cache — seller list thay đổi thường xuyên
- [ ] `GetSellerProductDetail` — `Query(UUID productId, UUID sellerId)`, `Result(SellerProductDetailDto)`, `Handler`  
      Không cache — cần ownership check, real-time status

### Application — Event Handlers (Cache Invalidation)

- [ ] `ProductUpdatedEventHandler` — evict `catalog-product-detail:{productId}` via `CacheInvalidationPublisher`
- [ ] `ProductUnpublishedEventHandler` — evict `catalog-product-detail:{productId}`
- [ ] `ProductBlockedEventHandler` — evict `catalog-product-detail:{productId}`
- [ ] `ProductUnblockedEventHandler` — evict `catalog-product-detail:{productId}`
- [ ] `VariantPriceChangedEventHandler` — evict `catalog-product-variants:{productId}`
- [ ] `VariantDeactivatedEventHandler` — evict `catalog-product-variants:{productId}`
- [ ] `CategoryUpdatedEventHandler` — evict `catalog-category-tree:tree` + `catalog-category-attributes:{categoryId}`

### Presentation

- [ ] `presentation/product/model/`:
  - `PublicProductDetailResponse` — `{ id, name, description, brand, category, status, attributes, images, warrantyInfo }`
  - `PublicVariantListResponse` — `{ variants: [{ skuId, combination, price, originalPrice, weight, status, images }] }`
  - `SellerProductSummaryResponse` — `{ id, name, status, variantCount, thumbnail, createdAt }`

- [ ] `PublicProductController`:
  - `GET /api/products/{id}` → `GetPublicProductDetail`
  - `GET /api/products/{id}/variants` → `GetPublicProductVariants`

- [ ] Thêm vào `SellerProductController`:
  - `GET /api/seller/products` → `ListSellerProducts`
  - `GET /api/seller/products/{id}` → `GetSellerProductDetail`

## Verify

```bash
# Setup: publish 1 product với 1 active variant

# First call — miss, populate cache
GET /api/products/{id}
# → 200, product detail

# Second call — L1/L2 hit (verify via log "cache hit" hoặc Actuator metrics)
GET /api/products/{id}
# → 200 (nhanh hơn, hoặc check metrics: catalog-product-detail.hit_count tăng)

# Update product → cache evicted
PUT /api/seller/products/{id} { "name": "New Name" }
GET /api/products/{id}
# → 200, name = "New Name"  (fresh, không bị stale)

# Unpublished product không accessible
POST /api/seller/products/{id}/unpublish
GET /api/products/{id}
# → 404

# Cache metrics via Actuator
GET /actuator/metrics/cache.gets?tag=name:catalog-product-detail&tag=result:hit
```

## Session Log
