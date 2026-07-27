# Caching Strategy — catalog-service

## Architecture

```
Request
   │
   ├─► L1: Caffeine (per-instance, in-process)
   │     Hit → return immediately (sub-ms)
   │     Miss ↓
   ├─► L2: Redis (shared across all instances)
   │     Hit → return, backfill L1
   │     Miss ↓
   └─► Database (PostgreSQL)
         → backfill cả L2 lẫn L1
```

### Implementation classes

| Class                        | Vai trò                                                                        |
|------------------------------|--------------------------------------------------------------------------------|
| `TwoLevelCacheManager`       | `@Primary` CacheManager — routing: L1+L2 hay L2-only tuỳ tên cache             |
| `TwoLevelCache`              | Implements `Cache` — get check L1→L2→DB, put/evict/clear đều chạm cả hai layer |
| `CacheInvalidationPublisher` | Broadcast evict L1 ra toàn fleet qua Redis pub/sub                             |
| `LocalCacheInvalidator`      | Subscriber — nhận message, evict L1 (Caffeine) trên instance hiện tại          |

`TwoLevelCacheManager` giữ whitelist `{category:tree, product, product-variants}` làm L1+L2.
Tên ngoài whitelist (`brands:active`, `categories:attributes`) được trả thẳng Redis cache — L2-only.

---

## Invalidation flow

### L1 + L2 cache (product, category:tree, product-variants)

```
Command handler
   │
   ├─► @CacheEvict → TwoLevelCacheManager → TwoLevelCache.evict(key)
   │       ├─► l1.evict(key)   ← Caffeine local (instance hiện tại)
   │       └─► l2.evict(key)   ← Redis (shared)
   │
   └─► CacheInvalidationPublisher.evict(cacheName, key)
           └─► Redis PUBLISH "catalog:cache:invalidate" "cacheName::key"
                   └─► mọi instance (kể cả local — no-op) nhận message
                       └─► LocalCacheInvalidator.onInvalidate()
                           └─► caffeineCacheManager.getCache(name).evict(key)
```

### L2-only cache (brands:active, categories:attributes)

```
Command handler
   └─► @CacheEvict → TwoLevelCacheManager → Redis cache trực tiếp
           └─► Redis DEL / SCAN+DEL (allEntries=true)
```

Không cần pub/sub — không có L1 để evict.

---

## Cache Inventory

| Cache Name              | Layer   | TTL L1 / L2        | Max Size L1 | Mục đích                                                                   |
|-------------------------|---------|--------------------|-------------|----------------------------------------------------------------------------|
| `brands:active`         | L2 only | — / 30 min         | —           | Danh sách brand ACTIVE cho public storefront                               |
| `categories:attributes` | L2 only | — / 1 hr           | —           | Attributes của category (GLOBAL + assigned) — dùng khi Seller tạo product  |
| `category:tree`         | L1 + L2 | 30 min / 1 hr      | 1 entry     | Category tree cho public storefront                                        |
| `product`               | L1 + L2 | 2 min / 10 min     | 10,000      | Product detail — hot read path                                             |
| `product-variants`      | L1 + L2 | 2 min / 5 min      | 10,000      | Variant list của product — hot read path                                   |

---

## Invalidation Rules

### `brands:active` — L2 only

| Trigger          | Handler           | Cách evict                     |
|------------------|-------------------|--------------------------------|
| Brand tạo mới    | `CreateBrand`     | `@CacheEvict(allEntries=true)` |
| Brand đổi name   | `UpdateBrand`     | `@CacheEvict(allEntries=true)` |
| Brand deactivate | `DeactivateBrand` | `@CacheEvict(allEntries=true)` |

### `categories:attributes` — L2 only

| Trigger                           | Handler                                                                                               | Cách evict                     |
|-----------------------------------|-------------------------------------------------------------------------------------------------------|--------------------------------|
| AttributeTemplate đổi displayName | `UpdateAttributeTemplate`                                                                             | `@CacheEvict(allEntries=true)` |
| Option thêm vào template          | `AddAttributeOption`                                                                                  | `@CacheEvict(allEntries=true)` |
| Option đổi displayValue           | `UpdateAttributeOption`                                                                               | `@CacheEvict(allEntries=true)` |
| Option deactivate                 | `DeactivateAttributeOption`                                                                           | `@CacheEvict(allEntries=true)` |
| Category assignment thay đổi      | `AssignAttributeToCategory`, `UpdateCategoryAttributeAssignment`, `RemoveCategoryAttributeAssignment` | `@CacheEvict(allEntries=true)` |

> `allEntries=true` vì không biết category nào reference template/option đó.

### `category:tree` — L1 + L2

| Trigger                  | Handler                                              | Cách evict                                       |
|--------------------------|------------------------------------------------------|--------------------------------------------------|
| Category tạo / sửa / xóa | `CreateCategory`, `UpdateCategory`, `DeleteCategory` | `@CacheEvict` + `publisher.clear(CATEGORY_TREE)` |

### `product` — L1 + L2

| Trigger            | Handler             | Cách evict                                                           |
|--------------------|---------------------|----------------------------------------------------------------------|
| Product publish    | `PublishProduct`    | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT, productId)` |
| Product update     | `UpdateProduct`     | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT, productId)` |
| Product unpublish  | `UnpublishProduct`  | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT, productId)` |
| Product block      | `BlockProduct`      | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT, productId)` |
| Product unblock    | `UnblockProduct`    | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT, productId)` |
| Variant deactivate | `DeactivateVariant` | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT, productId)` |
| Variant activate   | `ActivateVariant`   | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT, productId)` |

### `product-variants` — L1 + L2

| Trigger                          | Handler             | Cách evict                                                                                                                         |
|----------------------------------|---------------------|------------------------------------------------------------------------------------------------------------------------------------|
| Variant thêm mới                 | `AddVariant`        | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT_VARIANTS, productId)` — chỉ evict `product-variants`, không đụng `product` |
| Variant update (price / skuCode) | `UpdateVariant`     | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT_VARIANTS, productId)`                                                      |
| Variant deactivate               | `DeactivateVariant` | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT_VARIANTS, productId)`                                                      |
| Variant activate                 | `ActivateVariant`   | `@CacheEvict(key=productId)` + `publisher.evict(PRODUCT_VARIANTS, productId)`                                                      |

---

## Quy tắc invalidation

| Cache layer | `@CacheEvict` làm gì                                                   | `CacheInvalidationPublisher` cần không                 |
|-------------|------------------------------------------------------------------------|--------------------------------------------------------|
| **L2 only** | Evict Redis trực tiếp                                                  | Không                                                  |
| **L1 + L2** | Evict L1 local (Caffeine) **+** L2 (Redis) qua `TwoLevelCache.evict()` | Có — để evict L1 trên các instance còn lại trong fleet |

> Instance gửi pub/sub tự nhận lại message của mình → `LocalCacheInvalidator` gọi `caffeineCacheManager.evict()` thêm lần nữa — no-op, harmless.

---

## Thêm cache mới — Checklist

- [ ] Định nghĩa constant trong `CacheNames`
- [ ] Đăng ký TTL trong `CacheConfig.redisCacheManager()` (L2)
- [ ] Nếu cần L1: đăng ký Caffeine spec trong `CacheConfig.caffeineCacheManager()` **và** thêm tên vào `Set.of(...)` trong `CacheConfig.twoLevelCacheManager()`
- [ ] Thêm `@Cacheable` vào query handler
- [ ] Thêm invalidation vào tất cả command handlers có thể làm stale cache đó
- [ ] Nếu L1: inject `CacheInvalidationPublisher` và gọi sau `repository.save()`
- [ ] Cập nhật bảng **Cache Inventory** và **Invalidation Rules** trong file này
