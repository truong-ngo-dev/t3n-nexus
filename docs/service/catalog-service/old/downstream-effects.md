# Downstream Effects — catalog-service

> Bổ sung cho `service.md` §Integration Contract: **cái gì thật sự xảy ra** ở service khác khi nhận event từ catalog-service, không chỉ "ai consume". Tách riêng vì nội dung là hành vi cross-BC, không fit vào bảng Publishes/Consumes.

---

## Mô hình Catalog: Seller Catalog, không phải Platform Catalog

| Mô hình | Ví dụ thực tế | Đặc điểm |
|---|---|---|
| Platform catalog | Tiki | Platform quản lý master product, nhiều seller "join" vào cùng 1 listing |
| **Seller catalog (dùng ở đây)** | Shopee | Mỗi seller tự tạo product riêng — cùng loại hàng có nhiều listing khác nhau |

Platform (Admin) chỉ sở hữu `Category` tree và `AttributeTemplate`; mỗi `Product`/`Variant` thuộc về 1 Seller.

---

## → inventory-service

`ProductPublishedEvent` → tạo `StockRecord(skuId, qty=0)` cho **mỗi SKU** trong `skuIds[]` — Seller nhập tồn kho sau, catalog không biết số lượng.
`ProductUnpublishedEvent`/`ProductBlockedEvent` → block reservation mới; reservation đang pending xử lý riêng theo Saga timeout, không phải catalog quyết định.

## → search-service

Search maintain 1 ES document/SPU, denormalize từ nhiều BC (Catalog + Inventory stock status + Review rating):

```json
{
  "productId": "...", "name": "iPhone 15 Pro",
  "categoryIds": ["l1-id", "l2-id", "l3-id"], "categoryPath": ["Electronics", "Phones", "Smartphones"],
  "sellerId": "...", "sellerName": "...", "brandName": "Apple",
  "attributes": { "chip": "A17 Pro", "os": "iOS 17" },
  "variants": { "color": ["Titanium Black", "White Titanium"], "storage": ["128GB", "256GB"] },
  "priceRange": { "min": 28990000, "max": 31990000 },
  "inStock": true, "avgRating": 4.7, "status": "PUBLISHED"
}
```

## → pricing-service

Commission rate: `(categoryId, sellerTier) → rate%`. `categoryId` là foreign key trong Pricing config — khi Admin set Category `INACTIVE`, Pricing cần fallback rate (chưa thiết kế cơ chế fallback cụ thể — xem Open Questions).

## → cart-service

- Add-to-cart: Cart fetch `{ skuId, name, variantLabel, price, imageUrl, sellerId }` từ Catalog, lưu snapshot vào cart item.
- Render cart: **refresh giá** từ Catalog mỗi lần hiển thị (giá có thể đổi kể từ lúc add).
- SKU `INACTIVE` hoặc Product `UNPUBLISHED`/bị block → mark cart item `UNAVAILABLE`, chặn checkout.

## → order-service (snapshot, không gọi lại Catalog)

Tại thời điểm tạo order, Order nhận data đã có sẵn từ Cart và **freeze** vào order line — không gọi lại Catalog sau đó:

```
OrderLineItem {
  skuId, productId
  productName:   "iPhone 15 Pro"
  variantLabel:  "Titanium Black / 256GB"   ← human-readable
  unitPrice:     31990000                   ← frozen tại thời điểm order
  weight:        187                        ← gram, cho Pricing/Fulfillment
  dimensions:    { l: 14.6, w: 7.08, h: 0.83 }
}
```

Order history không bị ảnh hưởng dù seller đổi giá hay xoá product sau đó.

## → reporting-service (DWH)

`dim_product`: `productId, name, categoryPath, sellerId, brandName`. Consume từ Catalog events để maintain dimension. Category rename → SCD Type 1 (overwrite) — đủ cho mục tiêu showcase, không cần Type 2.

---

## Open Questions

| Câu hỏi | Đề xuất (chưa chốt) |
|---|---|
| SKU image override bắt buộc hay optional? | Optional — default inherit SPU gallery |
| Seller sửa giá khi item đang trong cart của user khác? | Cart hiển thị giá mới kèm warning — không block seller sửa giá |
| Admin merge/delete Category khi còn Product tham chiếu? | Require re-categorize trước khi xoá — không cascade delete Product |
| Pricing fallback rate khi Category bị `INACTIVE`? | Chưa thiết kế — cần quyết định trước khi có seller thật dùng |
