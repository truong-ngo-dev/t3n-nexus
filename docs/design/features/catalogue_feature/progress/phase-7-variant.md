# Phase 7 — Variant

**Status:** TODO  
**Started:** —  
**Completed:** —

## Checklist

### Application — Commands

- [ ] `AddVariant` — `Command(UUID productId, UUID sellerId, VariantCombinationDto combination, Money price, Money? originalPrice, BigDecimal weight, DimensionsDto dimensions, String? barcode)`, `Result(UUID skuId)`, `Handler`  
      Flow:  
      1. Load product, verify ownership (`sellerId`)  
      2. Validate combination: mỗi `(templateId, optionId)` phải thuộc category attributes với `isVariantDefining=true`  
      3. `product.addVariant(combination, ...)` → domain guard: combination unique  
      4. save → dispatch
- [ ] `UpdateVariant` — `Command(UUID productId, UUID skuId, UUID sellerId, price, originalPrice?, weight, dimensions, barcode?)`, `Result`, `Handler`  
      combination là immutable — request không nhận `combination` field  
      Guard: verify ownership
- [ ] `ActivateVariant` — `Command(UUID productId, UUID skuId, UUID sellerId)`, `Result`, `Handler`
- [ ] `DeactivateVariant` — `Command(UUID productId, UUID skuId, UUID sellerId)`, `Result`, `Handler`

### Application — Image Commands (Variant-level)

- [ ] `RequestVariantImageUploadUrl` — `Command(productId, skuId, sellerId, fileExtension)`, `Result(presignedUrl, objectKey)`, `Handler`
- [ ] `ConfirmVariantImageUpload` — `Command(productId, skuId, sellerId, objectKey, displayOrder)`, `Result(UUID imageId)`, `Handler`

### Domain — Thêm vào Product

- [ ] `Product.activateVariant(skuId)` — tìm Variant trong list, gọi `variant.activate()`
- [ ] `Product.deactivateVariant(skuId)` — gọi `variant.deactivate()`; **không** guard publish — variant có thể deactivate bất kỳ lúc nào (nhưng publish sẽ fail nếu còn 0 active)
- [ ] `Product.updateVariant(skuId, price, ...)` — tìm Variant, gọi update method + `variant.changePrice()` nếu price thay đổi
- [ ] `Product.addVariantImage(skuId, imageId, objectKey, displayOrder)`

### Presentation

- [ ] `presentation/variant/model/`:
  - `AddVariantRequest` — `{ combination: [{ templateId, optionId }], price, originalPrice?, weight, dimensions, barcode? }`
  - `UpdateVariantRequest` — `{ price, originalPrice?, weight, dimensions, barcode? }`
  - `VariantDetailResponse` — `{ skuId, combination: [{ templateId, templateName, optionId, optionDisplayValue }], price, originalPrice?, weight, dimensions, barcode, status, images }`

- [ ] `VariantController`:
  - `POST /api/seller/products/{productId}/variants` → `AddVariant`
  - `PUT /api/seller/products/{productId}/variants/{skuId}` → `UpdateVariant`
  - `POST /api/seller/products/{productId}/variants/{skuId}/activate` → `ActivateVariant`
  - `POST /api/seller/products/{productId}/variants/{skuId}/deactivate` → `DeactivateVariant`
  - `POST /api/seller/products/{productId}/variants/{skuId}/images/upload-url` → `RequestVariantImageUploadUrl`
  - `POST /api/seller/products/{productId}/variants/{skuId}/images/confirm` → `ConfirmVariantImageUpload`

## Verify

```bash
# Add variant (cần Color template assigned to category với isVariantDefining=true)
POST /api/seller/products/{productId}/variants
{
  "combination": [
    { "templateId": "{colorTemplateId}", "optionId": "{blackOptionId}" }
  ],
  "price": 2899000,
  "weight": 187
}
# → 201, body.data.skuId

# Duplicate combination → 409
POST /api/seller/products/{productId}/variants { same combination }
# → 409, errorCode: VARIANT_COMBINATION_EXISTS

# Now publish should succeed (1 active variant)
POST /api/seller/products/{productId}/publish
# → 200

# Change price
PUT /api/seller/products/{productId}/variants/{skuId}
{ "price": 2799000, "weight": 187 }
# → 200

# Verify combination unchanged
GET /api/products/{productId}/variants
# → combination: [{ option: "Đen" }], price: 2799000

# Deactivate last variant → publish guard
POST .../variants/{skuId}/deactivate
POST /api/seller/products/{productId}/publish
# → 422: PUBLISH_REQUIRES_ACTIVE_VARIANT

# DB: combination_hash không thay đổi sau update
SELECT combination_hash FROM variant WHERE id = ?;  -- same hash
```

## Session Log
