# Catalog BC — Phân Tích Thiết Kế: SPU, SKU, Attribute System

> Tài liệu phân tích khái niệm và thiết kế chi tiết cho Catalog BC.
> Liên quan: `../domain/bounded-contexts.md` (Catalog BC section), `../requirement.md` (Actors, NFR).

---

## 1. Khái Niệm

### SPU (Standard Product Unit)

SPU là đơn vị sản phẩm **ở mức khái niệm** — mô tả "ý tưởng về một loại hàng hoá", không phải thứ cụ thể có thể mua được.

Ví dụ: **"iPhone 15 Pro"** là một SPU. Nó có tên, mô tả, thương hiệu, danh mục, hình ảnh đại diện. Nhưng câu hỏi "giá bao nhiêu, màu gì, còn hàng không?" không có nghĩa ở tầng SPU — SPU không đủ cụ thể để trả lời.

SPU tồn tại để:
- Nhóm các biến thể lại dưới một danh tính chung
- Là đơn vị để viết review, xem mô tả, xem gallery ảnh
- Là đơn vị để search và browse catalog

### SKU (Stock Keeping Unit)

SKU là đơn vị **cụ thể, có thể giao dịch được** — thứ thực sự được thêm vào giỏ hàng, đặt hàng, kiểm đếm tồn kho.

```
SPU: iPhone 15 Pro
  SKU_001 — Titanium Black / 128GB — 28.990.000đ
  SKU_002 — Titanium Black / 256GB — 31.990.000đ
  SKU_003 — White Titanium / 128GB — 28.990.000đ
  SKU_004 — White Titanium / 256GB — 31.990.000đ
```

Mỗi SKU có:
- **Giá riêng** — 256GB đắt hơn 128GB
- **Tồn kho riêng** — Black có thể hết, White còn
- **Barcode riêng** — kho vật lý scan từng cái
- **Trọng lượng / kích thước** — cho tính phí ship
- **Tổ hợp attributes duy nhất** — không thể có 2 SKU cùng (Black + 256GB) trong 1 product

**Quan hệ cốt lõi:** `1 SPU (Product) → N SKU`

SPU không thể mua được. SKU mới mua được.

---

### Attribute — Cơ Chế Tạo Ra SKU Từ SPU

Attribute là đặc tính mô tả sản phẩm. Có hai loại:

**Variant-defining attributes (sale attributes):** khi giá trị thay đổi → tạo ra SKU mới.
- Color: Black vs White → 2 SKU khác nhau
- Storage: 128GB vs 256GB → 2 SKU khác nhau
- Size: S vs M → 2 SKU khác nhau

**Non-variant attributes (SPU-level attributes):** mô tả product nhưng không tạo SKU mới.
- Brand: Apple (chung cả product)
- Chip: A17 Pro (không đổi theo màu hay dung lượng)
- Xuất xứ: Việt Nam

```
SPU attributes (chung):
  Brand: Apple | Chip: A17 Pro | Display: 6.1" | OS: iOS 17

Variant-defining attributes → matrix SKU:
  Color:   [Titanium Black, White Titanium, Blue Titanium, Natural Titanium]
  Storage: [128GB, 256GB, 512GB, 1TB]

→ 4 × 4 = tối đa 16 SKU từ 1 SPU
```

---

### Category và Attribute Template

Category không chỉ là nhãn phân loại — nó **định nghĩa schema attribute** cho toàn bộ product thuộc về nó. Admin sở hữu schema này.

```
Category: Điện thoại
  AttributeTemplate:
    - Storage    SELECT [64GB, 128GB, 256GB, 512GB]  isVariantDefining: true   isFilterable: true
    - Color      SELECT [Black, White, Gold, ...]     isVariantDefining: true   isFilterable: true
    - RAM        SELECT [6GB, 8GB, 12GB, 16GB]        isVariantDefining: false  isFilterable: true
    - OS         TEXT                                  isVariantDefining: false  isFilterable: false
    - Screen     NUMBER (inch)                         isVariantDefining: false  isFilterable: true

Category: Áo thun
  AttributeTemplate:
    - Size       SELECT [XS, S, M, L, XL, XXL]       isVariantDefining: true   isFilterable: true
    - Color      SELECT [...]                          isVariantDefining: true   isFilterable: true
    - Material   SELECT [Cotton, Polyester, Linen]    isVariantDefining: false  isFilterable: true
    - Style      SELECT [Regular, Slim, Oversize]     isVariantDefining: false  isFilterable: true
```

Seller chọn Category trước → hệ thống load đúng bộ attribute template → seller điền vào. Không tự nghĩ ra attribute. Admin đã chuẩn hoá sẵn.

---

### Mô Hình Catalog: Seller Catalog vs Platform Catalog

| Mô hình | Ví dụ thực tế | Đặc điểm |
|---|---|---|
| **Platform catalog** | Tiki | Platform quản lý master product. Nhiều seller "join" vào cùng 1 listing. 1 product page, nhiều offer |
| **Seller catalog** | Shopee | Mỗi seller tự tạo product riêng. Cùng loại hàng có nhiều listing khác nhau |

Hệ thống này dùng **Seller catalog model** — mỗi seller sở hữu product của mình. Platform sở hữu Category tree và Attribute template.

---

## 2. Data Model

```
Category
├─ id, name, slug, parentId, level (L1/L2/L3)
├─ imageUrl
├─ status: ACTIVE | INACTIVE
└─ attributeTemplates: AttributeTemplate[]
     ├─ id, categoryId, name, displayName
     ├─ inputType: SELECT | TEXT | NUMBER | BOOLEAN
     ├─ isVariantDefining: boolean        ← quan trọng nhất
     ├─ isRequired: boolean
     ├─ isFilterable: boolean             ← Search BC tạo facet từ flag này
     ├─ displayOrder: int
     └─ options: AttributeOption[]        ← chỉ có nếu inputType = SELECT
          └─ id, value, displayValue

Brand
└─ id, name, slug, status: ACTIVE | INACTIVE  (Admin-managed)

Product  [SPU]
├─ id, sellerId, categoryId
├─ name, description (rich text)
├─ brandId → Brand
├─ status: DRAFT | PUBLISHED | UNPUBLISHED | BLOCKED
├─ images: ProductImage[]                ← MinIO objectKey, ordered
├─ warrantyInfo: { months, type, coverage }
└─ attributeValues: ProductAttributeValue[]   ← non-variant attrs
     └─ { attributeTemplateId, value }

SKU
├─ id, productId
├─ variantCombination: SkuAttributeValue[]    ← variant-defining attrs only
│    └─ { attributeTemplateId, attributeOptionId }
│    Constraint: tổ hợp phải unique trong cùng product
├─ price: Money
├─ originalPrice: Money                  ← optional, display-only, phải > price
├─ weight: decimal (gram)
├─ dimensions: { length, width, height } (cm)
├─ barcode: string
├─ images: SkuImage[]                    ← optional override per-SKU (e.g., ảnh riêng mỗi màu)
└─ status: ACTIVE | INACTIVE
```

---

## 3. Product Lifecycle

```
DRAFT ──────────────→ PUBLISHED ⇆ UNPUBLISHED
                           │
                      (Admin force)
                           ↓
                        BLOCKED
```

- `DRAFT → PUBLISHED`: Seller chủ động publish. Không có approval flow — seller đã được trust từ Seller BC onboarding.
- `PUBLISHED ⇆ UNPUBLISHED`: Seller tự toggle.
- `BLOCKED`: Admin force khi vi phạm chính sách. Seller không thể tự unblock.

**SKU status** độc lập với Product status:
- `ACTIVE`: có thể mua
- `INACTIVE`: seller tắt tạm thời (ví dụ: hết hàng dài hạn, tạm ngừng bán màu đó)

---

## 4. Flow Tạo Product (Seller)

```
1. Seller chọn Category
2. Hệ thống load AttributeTemplate của category
3. Seller điền non-variant attributes (Brand, OS, Chip...)
4. Seller chọn variant-defining attrs và options
     Color: [Black, White] × Storage: [128GB, 256GB]
5. Hệ thống auto-generate Cartesian matrix → 4 SKU rows
6. Seller điền price + weight + barcode cho từng SKU (bulk-edit)
7. Upload ảnh: SPU gallery + optional per-SKU override
8. Save → DRAFT
9. Publish → PUBLISHED → emit ProductPublished
```

**Image upload flow (presigned URL):**
```
1. Seller request upload → catalog-service → MinIO presigned PUT URL (TTL 5 phút)
2. Browser upload thẳng lên MinIO (bypass catalog-service)
3. Browser callback catalog-service với objectKey
4. catalog-service verify object tồn tại → lưu URL vào Product/SKU
```

---

## 5. Events Emitted

| Event | Trigger | Consumers |
|---|---|---|
| `ProductPublished` | Seller publish | Search BC (index), Inventory BC (tạo StockRecord per SKU) |
| `ProductUpdated` | Seller sửa thông tin / giá | Search BC (re-index) |
| `SkuPriceChanged` | Seller đổi giá SKU | Search BC (update priceRange) |
| `ProductUnpublished` | Seller/Admin unpublish | Search BC (remove), Inventory BC (block reservation) |
| `ProductBlocked` | Admin block | Search BC (remove), Inventory BC (block reservation) |
| `CategoryUpdated` | Admin sửa category | Search BC (update facets/category metadata) |

---

## 6. Ảnh Hưởng Ra Các BC Khác

### → Inventory BC

- `ProductPublished` → tạo `StockRecord(skuId, qty=0)` cho **mỗi SKU**. Seller sau đó mới nhập tồn kho.
- `ProductUnpublished` / `ProductBlocked` → block reservation mới. Reservation đang pending → xử lý riêng (cancel hoặc giữ đến khi hết timeout Saga).

### → Search BC

Search BC maintain 1 ES document per SPU, denormalize từ nhiều nguồn:

```json
{
  "productId": "...",
  "name": "iPhone 15 Pro",
  "categoryIds": ["l1-id", "l2-id", "l3-id"],
  "categoryPath": ["Electronics", "Phones", "Smartphones"],
  "sellerId": "...",
  "sellerName": "...",
  "brandName": "Apple",
  "attributes": {
    "chip": "A17 Pro",
    "os": "iOS 17"
  },
  "variants": {
    "color": ["Titanium Black", "White Titanium"],
    "storage": ["128GB", "256GB"]
  },
  "priceRange": { "min": 28990000, "max": 31990000 },
  "inStock": true,
  "avgRating": 4.7,
  "status": "PUBLISHED"
}
```

Search BC consume từ: Catalog events, Inventory events (stock status), Review events (avgRating).

### → Pricing BC

Commission rate: `(categoryId, sellerTier) → rate%`. `categoryId` từ Catalog là foreign key trong Pricing config. Khi Admin INACTIVE một Category → Pricing BC cần fallback rate.

### → Cart BC

- Add-to-cart: Cart BC fetch `{ skuId, name, variantLabel, price, imageUrl, sellerId }` từ Catalog, lưu vào cart item.
- Render cart: **refresh price** từ Catalog (giá có thể thay đổi kể từ lúc add).
- Nếu SKU `INACTIVE` hoặc Product `UNPUBLISHED/BLOCKED` → mark cart item `UNAVAILABLE`, chặn checkout.

### → Order BC (snapshot)

Tại thời điểm tạo order, Order BC nhận data từ Cart (đã đủ) và **freeze** vào order line. Không gọi lại Catalog sau đó:

```
OrderLineItem {
  skuId, productId
  productName:   "iPhone 15 Pro"
  variantLabel:  "Titanium Black / 256GB"   ← human-readable
  unitPrice:     31990000                   ← frozen tại thời điểm order
  imageUrl:      "..."
  weight:        187                        ← gram, cho Pricing/Fulfillment
  dimensions:    { l: 14.6, w: 7.08, h: 0.83 }
}
```

Order history không bị ảnh hưởng dù seller đổi giá hay xóa product sau đó.

### → Reporting BC (DWH)

`dim_product`: `productId, name, categoryPath, sellerId, brandName`.

Consume từ Catalog events để maintain dimension. Category rename → Slowly Changing Dimension Type 1 (overwrite) — đủ cho mục tiêu showcase, không cần Type 2.

---

## 7. NFR Considerations

| Concern | Giải pháp |
|---|---|
| 5,000,000 SKUs trên PostgreSQL | Read replica. Redis cache tại catalog-service cho `GET /products/{id}` và `GET /skus/{id}` |
| Product detail P99 < 100ms | Cache invalidate khi `ProductUpdated` event được publish |
| Category tree query | Closure table trên PostgreSQL. Depth tối đa L3 |
| Search P99 < 200ms | ES index maintain bởi Search BC từ Catalog events — Catalog không query ES trực tiếp |

---

## 8. Quyết Định Đã Chốt

- **Không có approval flow** cho product — seller tự publish, Admin chỉ force-block khi vi phạm.
- **Brand là entity** trong Catalog, Admin-managed — tránh "Nike" / "NIKE" / "nike" tồn tại cùng lúc.
- **Category Attribute Template có** — schema per category, Admin-managed. Là kỹ thuật interesting và realistic.
- **Giá nằm ở SKU** trong Catalog (`SKU.price`). Pricing BC chỉ tính shipping fee và commission — không biết product price.
- **`originalPrice` là display-only** — phải > `price`, validate tại API layer. Không ảnh hưởng business logic.
- **Order snapshot từ Cart** — Cart carry đủ data (fetch từ Catalog khi add-to-cart), Order nhận từ Cart và freeze. Catalog không bị gọi tại checkout path.
- **Attribute option soft delete** — khi Admin xóa option, SKU hiện có vẫn valid. Chỉ block option đó khỏi selection khi tạo SKU mới.

---

## 9. Open Questions

| Câu hỏi | Recommendation |
|---|---|
| SKU image override bắt buộc hay optional? | Optional — default inherit SPU gallery |
| Seller sửa giá khi item đang trong cart user khác? | Cart hiển thị giá mới với warning — không block seller |
| Khi Admin merge/delete Category, product cũ xử lý thế nào? | Require re-categorize trước khi delete. Không cascade delete product. |
