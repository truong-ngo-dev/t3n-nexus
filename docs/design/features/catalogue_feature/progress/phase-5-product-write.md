# Phase 5 — Product Write Side

**Status:** TODO  
**Started:** —  
**Completed:** —

## Checklist

### Application — Commands (Seller)

- [ ] `CreateProduct` — `Command(sellerId, categoryId, brandId, name, description, warrantyInfo?, List<ProductAttributeValue>)`, `Result(UUID productId)`, `Handler`  
      Flow: load Category (verify exists, ACTIVE) → load Brand (verify ACTIVE) → `Product.create()` → save
- [ ] `UpdateProduct` — `Command(UUID productId, sellerId, name, description, List<ProductAttributeValue>)`, `Result`, `Handler`  
      Guard: verify `product.sellerId == command.sellerId` (ownership check)
- [ ] `PublishProduct` — `Command(UUID productId, sellerId)`, `Result`, `Handler`
- [ ] `UnpublishProduct` — `Command(UUID productId, sellerId)`, `Result`, `Handler`

### Application — Image Upload (Seller)

- [ ] `StoragePort` interface tại `application/shared/service/` hoặc `domain/` — `generatePresignedPutUrl(objectKey, ttlSeconds): String`, `objectExists(objectKey): boolean`
- [ ] `MinioStorageAdapter implements StoragePort` tại `infrastructure/adapter/service/storage/`  
      Dùng MinIO SDK: `minioClient.getPresignedObjectUrl(...)`, `minioClient.statObject(...)`
- [ ] `MinioConfig` tại `infrastructure/cross-cutting/config/` — `@Bean MinioClient`
- [ ] `RequestImageUploadUrl` — `Command(UUID productId, sellerId, String fileExtension)`, `Result(String presignedUrl, String objectKey)`, `Handler`  
      Generate `objectKey = "products/{productId}/{uuid}.{ext}"` → `StoragePort.generatePresignedPutUrl(objectKey, 300)`
- [ ] `ConfirmImageUpload` — `Command(UUID productId, sellerId, String objectKey, int displayOrder)`, `Result(UUID imageId)`, `Handler`  
      `StoragePort.objectExists(objectKey)` → throw 422 nếu không tồn tại → `product.addImage()`
- [ ] `DeleteProductImage` — `Command(UUID productId, sellerId, UUID imageId)`, `Result`, `Handler`

### Application — Commands (Admin)

- [ ] `BlockProduct` — `Command(UUID productId, String reason)`, `Result`, `Handler`
- [ ] `UnblockProduct` — `Command(UUID productId)`, `Result`, `Handler`

### Presentation

- [ ] `presentation/product/model/`:
  - `CreateProductRequest` — `{ categoryId, brandId?, name, description, warrantyInfo?, attributeValues }`
  - `UpdateProductRequest` — `{ name, description, attributeValues }`
  - `ConfirmImageUploadRequest` — `{ objectKey, displayOrder }`
  - `BlockProductRequest` — `{ reason }`
  - `SellerProductListResponse` — `{ id, name, status, variantCount, thumbnail, createdAt }`
  - `SellerProductDetailResponse` — full detail (name, description, status, attributes, images, variants list)

- [ ] `SellerProductController`:
  - `POST /api/seller/products` → `CreateProduct`
  - `PUT /api/seller/products/{id}` → `UpdateProduct`
  - `POST /api/seller/products/{id}/publish` → `PublishProduct`
  - `POST /api/seller/products/{id}/unpublish` → `UnpublishProduct`
  - `POST /api/seller/products/{id}/images/upload-url` → `RequestImageUploadUrl`
  - `POST /api/seller/products/{id}/images/confirm` → `ConfirmImageUpload`
  - `DELETE /api/seller/products/{id}/images/{imageId}` → `DeleteProductImage`
  - `GET /api/seller/products` → `ListSellerProducts` _(Phase 6)_
  - `GET /api/seller/products/{id}` → `GetSellerProductDetail` _(Phase 6)_

- [ ] `AdminProductController`:
  - `POST /api/admin/products/{id}/block` → `BlockProduct`
  - `POST /api/admin/products/{id}/unblock` → `UnblockProduct`

## Verify

```bash
# Tạo product (cần Brand + Category từ phase trước)
POST /api/seller/products
{
  "categoryId": "{L3_ID}",
  "brandId": "{nikeId}",
  "name": "iPhone 15 Pro",
  "description": "..."
}
# → 201, body.data.productId

# Publish mà không có variant → 422
POST /api/seller/products/{id}/publish
# → 422, errorCode: PUBLISH_REQUIRES_ACTIVE_VARIANT

# Block + publish attempt → 422
POST /api/admin/products/{id}/block { "reason": "spam" }
POST /api/seller/products/{id}/publish
# → 422, errorCode: PRODUCT_BLOCKED

# Image upload flow
POST /api/seller/products/{id}/images/upload-url { "fileExtension": "jpg" }
# → 200, { presignedUrl, objectKey }
# [upload file via presigned URL]
POST /api/seller/products/{id}/images/confirm { "objectKey": "...", "displayOrder": 0 }
# → 201

# Verify DB
SELECT status FROM product WHERE id = ?;   -- DRAFT
SELECT * FROM product_image WHERE product_id = ?;  -- 1 row
```

## Session Log
