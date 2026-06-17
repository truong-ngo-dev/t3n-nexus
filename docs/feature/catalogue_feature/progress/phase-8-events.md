/# Phase 8 — Events + Outbox

**Status:** Done  
**Started:** 2026-06-15  
**Completed:** 2026-06-15

## Checklist

### Outbox Wiring

- [x] `OutboxEventStore` bean từ `outbox-starter` được inject vào `EventDispatcher` config  
      `OutboxEventHandlers.java` đăng ký 8 `EventHandler` beans, mỗi bean delegate `store.store(event)`.  
      `EventDispatcherConfig.java` autowire tất cả `EventHandler<?>` beans vào `EventDispatcher`.
- [x] Mỗi domain event dispatch → `OutboxEventStore.store(eventEnvelope)` → persist vào `outbox_events` table trong cùng transaction với aggregate save
- [ ] Debezium connector config cho `catalog-service` outbox table (infra config — ngoài scope code)

### V2 Migration — Schema Fix

- [x] `V2__fix_variant_schema.sql` — add `sku_code`, `stock` to `variant`; rename `variant_image` → `sku_image`

### Kafka Topics — khai báo

- [x] `catalog.product.published`
- [x] `catalog.product.unpublished`
- [x] `catalog.product.blocked`
- [x] `catalog.product.unblocked`
- [x] `catalog.product.updated`
- [x] `catalog.variant.price-changed`
- [x] `catalog.variant.deactivated`
- [x] `catalog.category.updated`

### Domain Event → EventEnvelope Mapping

- [x] `ProductPublishedEvent` → `catalog.product.published`  
      Payload: `{ productId, sellerId, categoryId, brandName, skuIds: [], name }`
- [x] `ProductUnpublishedEvent` → `catalog.product.unpublished`  
      Payload: `{ productId, sellerId }`
- [x] `ProductBlockedEvent` → `catalog.product.blocked`  
      Payload: `{ productId, sellerId, reason }`
- [x] `ProductUnblockedEvent` → `catalog.product.unblocked`  
      Payload: `{ productId }`
- [x] `ProductUpdatedEvent` → `catalog.product.updated`  
      Payload: `{ productId }`
- [x] `VariantPriceChangedEvent` → `catalog.variant.price-changed`  
      Payload: `{ skuId, productId, newPrice }` (long, đồng VND)
- [x] `VariantDeactivatedEvent` → `catalog.variant.deactivated`  
      Payload: `{ skuId, productId }`
- [x] `CategoryUpdatedEvent` → `catalog.category.updated`  
      Payload: `{ categoryId }`

### Application Handlers — Event Dispatch Wired

- [x] `PublishProduct` — dispatch sau `productRepository.save()`
- [x] `UnpublishProduct` — dispatch sau `productRepository.save()`
- [x] `BlockProduct` — dispatch sau `productRepository.save()`
- [x] `UnblockProduct` — dispatch sau `productRepository.save()`
- [x] `UpdateProduct` — dispatch sau `productRepository.save()`
- [x] `UpdateVariant` — dispatch sau `variantRepository.save()` (chỉ emit khi price changed)
- [x] `DeactivateVariant` — dispatch sau `variantRepository.save()`
- [x] `UpdateCategory` — dispatch sau `categoryRepository.save()`

## Session Log

**2026-06-15**: Implement Phase 8. Fix schema mismatch variant (V2 migration). Wire 8 domain events với getRoutingKey/getPayload. Tạo OutboxEventHandlers với 8 EventHandler beans. Update 8 command handlers để dispatch sau save.
