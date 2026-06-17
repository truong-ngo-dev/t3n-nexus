# Phase 4 — Product Domain + Infrastructure

**Status:** TODO  
**Started:** —  
**Completed:** —

## Checklist

### Domain — Value Objects
- [ ] `ProductId` — typed UUID
- [ ] `VariantId` — typed UUID (alias SkuId cross-BC)
- [ ] `ProductImageId`, `SkuImageId` — typed UUID
- [ ] `ProductStatus` enum: `DRAFT | PUBLISHED | UNPUBLISHED | BLOCKED`
- [ ] `VariantStatus` enum: `ACTIVE | INACTIVE`
- [ ] `WarrantyInfo` value object — record `{ int months, String type, String coverage }`
- [ ] `ProductAttributeValue` value object — record `{ AttributeTemplateId templateId, String value }`
- [ ] `VariantAttributePair` value object — record `{ AttributeTemplateId templateId, AttributeOptionId optionId }`
- [ ] `VariantCombination` value object — record wrapping `List<VariantAttributePair>` (immutable via `List.copyOf`)  
      Override `equals`/`hashCode` — order-insensitive (sort pairs before compare)  
      `String toHash()` — deterministic hash cho DB unique constraint

### Domain — Entities
- [ ] `ProductImage` entity — `{ imageId, objectKey, displayOrder }`
- [ ] `SkuImage` entity — `{ imageId, objectKey, displayOrder }`
- [ ] `Variant` entity — `{ skuId, productId, combination: VariantCombination, price: Money, originalPrice: Money?, weight, dimensions, barcode, images, status }`
- [ ] `Variant.create(skuId, productId, combination, price, weight)` static factory  
      Validate: `price > 0`, `originalPrice > price` nếu có
- [ ] `Variant.update(price, originalPrice, weight, dimensions, barcode)` — combination là immutable field, không có setter
- [ ] `Variant.activate()` → raise `VariantActivatedEvent`  _(internal, không publish ra Kafka)_
- [ ] `Variant.deactivate()` → raise `VariantDeactivatedEvent`
- [ ] `Variant.changePrice(newPrice)` → raise `VariantPriceChangedEvent`

### Domain — Aggregate Root
- [ ] `Product` aggregate root
- [ ] `Product.create(id, sellerId, categoryId, brandId, name, description, warrantyInfo, attributeValues)` static factory  
      `status = DRAFT`
- [ ] `Product.update(name, description, attributeValues)` → raise `ProductUpdatedEvent`  
      **Guard**: `categoryId` không thể thay đổi — method không nhận categoryId
- [ ] `Product.addVariant(combination, price, weight, ...)` → tạo `Variant` bên trong  
      **Guard**: `combination` phải unique trong Product (check theo `combination.toHash()`)  
      Return `VariantId` mới
- [ ] `Product.publish()` → `PUBLISHED` + raise `ProductPublishedEvent`  
      **Guard 1**: `status == BLOCKED` → throw  
      **Guard 2**: không có Variant nào `ACTIVE` → throw
- [ ] `Product.unpublish()` → `UNPUBLISHED` + raise `ProductUnpublishedEvent`
- [ ] `Product.block(reason)` → `BLOCKED` + raise `ProductBlockedEvent`
- [ ] `Product.unblock()` → `UNPUBLISHED` + raise `ProductUnblockedEvent`
- [ ] `Product.addImage(imageId, objectKey, displayOrder)`
- [ ] `Product.removeImage(imageId)` — throw nếu không tìm thấy

### Domain — Events
- [ ] `ProductPublishedEvent` — `{ productId, sellerId, categoryId, List<VariantId> skuIds, name, brandName }`
- [ ] `ProductUnpublishedEvent` — `{ productId, sellerId }`
- [ ] `ProductBlockedEvent` — `{ productId, sellerId, reason }`
- [ ] `ProductUnblockedEvent` — `{ productId }`
- [ ] `ProductUpdatedEvent` — `{ productId }`
- [ ] `VariantPriceChangedEvent` — `{ skuId, productId, newPrice }`
- [ ] `VariantDeactivatedEvent` — `{ skuId, productId }`

### Domain — Repository + ErrorCode
- [ ] `ProductErrorCode` enum — `PRODUCT_NOT_FOUND(404)`, `VARIANT_COMBINATION_EXISTS(409)`, `PUBLISH_REQUIRES_ACTIVE_VARIANT(422)`, `PRODUCT_BLOCKED(422)`, `IMAGE_NOT_FOUND(404)`, `CATEGORY_LOCKED_AFTER_VARIANT(422)`
- [ ] `ProductRepository` interface — `findById`, `save`, `findBySellerIdPaged`
- [ ] `VariantRepository` interface — `existsByOptionId(optionId)` (dùng bởi AttributeTemplateDomainService)

### Infrastructure — JPA + Adapters
- [ ] `ProductJpaEntity` — `@OneToMany variants`, `@OneToMany images`, `@ElementCollection attributeValues`
- [ ] `VariantJpaEntity` — `combination_hash` VARCHAR(64), `@OneToMany combinationItems`, `@OneToMany images`
- [ ] `VariantCombinationItemJpaEntity` — `(variant_id, template_id, option_id)`
- [ ] `ProductImageJpaEntity`, `SkuImageJpaEntity`
- [ ] `ProductAttributeValueJpaEntity`
- [ ] `ProductJpaRepository` — `findBySellerIdOrderByCreatedAtDesc(sellerId, Pageable)`
- [ ] `VariantJpaRepository` — `existsByOptionIdInCombination(optionId)` — custom JPQL
- [ ] `ProductMapper` — map toàn bộ aggregate (variant list, image list, attribute values)
- [ ] `ProductPersistenceAdapter implements ProductRepository`
- [ ] `VariantPersistenceAdapter implements VariantRepository`

## Verify

Unit test (không cần Spring context):

```java
// Guard: publish without active variant
Product p = Product.create(...);
assertThrows(DomainException.class, () -> p.publish());

// Guard: duplicate combination
VariantCombination c = new VariantCombination(List.of(...));
p.addVariant(c, ...);
assertThrows(DomainException.class, () -> p.addVariant(c, ...)); // same hash

// Guard: blocked cannot publish
p.block("spam");
assertThrows(DomainException.class, () -> p.publish());

// VariantCombination hash is order-insensitive
VariantCombination c1 = new VariantCombination(List.of(pair(t1, o1), pair(t2, o2)));
VariantCombination c2 = new VariantCombination(List.of(pair(t2, o2), pair(t1, o1)));
assertEquals(c1.toHash(), c2.toHash());
```

Integration test (Spring context + H2 hoặc testcontainer PostgreSQL):
```java
// Save product với 2 variants → findById → verify variants + combinations loaded
// Verify DB: variant.combination_hash = expected hash
SELECT combination_hash FROM variant WHERE id = ?;
```

## Session Log
