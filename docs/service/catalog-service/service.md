# catalog-service

**Domain:** Core Domain  
**DB:** PostgreSQL  
**Libs:** `common-domain`, `outbox-starter`, `common-events`, `common-web`, `observability-starter`

## Trách nhiệm

Quản lý toàn bộ Catalog — Category taxonomy, AttributeTemplate, Brand, Product (SPU), Variant (SKU).  
Là **upstream thuần túy** — không consume event từ BC nào, chỉ publish.

---

## Ubiquitous Language

| Thuật ngữ                     | Định nghĩa                                                                                                                  |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `Product`                     | Aggregate Root — đại diện một "loại sản phẩm" trừu tượng (SPU). Do Seller tạo, thuộc 1 Seller + 1 Category                  |
| `Variant`                     | Aggregate Root — một biến thể cụ thể có thể mua được (SKU). Inventory, Cart, Order BC đều ref trực tiếp vào đây qua `skuId` |
| `SkuId`                       | ID của Variant — stable identifier dùng chung giữa Catalog, Inventory, Cart, Order BC                                       |
| `VariantCombination`          | Value Object bất biến — tổ hợp (template, option) định nghĩa Variant là "cái gì". Không thay đổi sau khi tạo                |
| `AttributeTemplate`           | Aggregate Root — master data thuần cho 1 đặc tính sản phẩm (tên, kiểu input, option pool nếu SELECT). Không tự áp dụng ở đâu — phải được category (leaf) assign tường minh mới có hiệu lực |
| `AttributeOption`             | Entity trong AttributeTemplate — một giá trị hợp lệ khi inputType=SELECT                                                    |
| `CategoryAttributeAssignment` | Value Object trong Category — config cách 1 template được dùng trong category đó (chỉ tồn tại ở category leaf/L3)           |
| `Category`                    | Aggregate Root — taxonomy node trong cây phân loại, Admin sở hữu. Đúng 3 level, chỉ leaf (L3) mới assign attribute và chỉ leaf mới được Product tham chiếu |
| `Brand`                       | Aggregate Root — whitelist thương hiệu Admin-managed, tránh trùng lặp do typo                                               |
| `Publish`                     | Transition Product sang PUBLISHED — đưa ra storefront                                                                       |
| `Block`                       | Admin force-gỡ Product — Seller không thể tự Publish lại                                                                    |

---

## Domain Model

### Aggregates

| Aggregate           | Owned By | Trách nhiệm                                                              |
|---------------------|----------|--------------------------------------------------------------------------|
| `Product`           | Seller   | SPU — thông tin sản phẩm, metadata, ảnh                                  |
| `Variant`           | Seller   | SKU — biến thể có thể mua, target trực tiếp của Inventory/Cart/Order BC  |
| `Category`          | Admin    | Taxonomy node, chứa CategoryAttributeAssignment                          |
| `AttributeTemplate` | Admin    | Master data attribute — không tự áp dụng, phải được category leaf assign |
| `Brand`             | Admin    | Whitelist thương hiệu — tránh "Nike" / "NIKE" / "nike" tồn tại song song |

> **Tại sao Variant là Aggregate Root, không phải Entity trong Product:**  
> Inventory/Cart/Order BC đều reference `skuId` trực tiếp — không đi qua Product.  
> Commands như `deactivate()`, `changePrice()` nhắm thẳng vào 1 Variant; nếu là Entity thì phải load toàn bộ Product chỉ để thay đổi 1 dòng.

### Structure

```
Product (AR)
  ├─ productId, sellerId, categoryId, brandId
  ├─ name, description (rich text)
  ├─ status: DRAFT | PUBLISHED | UNPUBLISHED     [3 giá trị — KHÔNG có BLOCKED]
  ├─ adminBlocked: boolean                       [cờ độc lập với status — Admin block/unblock]
  ├─ warrantyInfo: WarrantyInfo                 [VO: { months, type, coverage }]
  ├─ attributeValues: ProductAttributeValue[]   [VO list: { attributeTemplateId, values: String[] }]
  └─ images: ProductImage[]                     [Entity: { imageId, objectKey, displayOrder }]

Variant (AR)
  ├─ skuId, productId                           [productId = cross-AR ref, không phải FK object]
  ├─ combination: VariantCombination            [VO IMMUTABLE: [(templateId, optionId), ...]]
  ├─ price: BIGINT (đồng), originalPrice: BIGINT? (phải > price)
  ├─ weight: DECIMAL (gram), dimensions: { length, width, height } (cm)
  ├─ barcode: string?
  ├─ images: SkuImage[]                         [VO list — optional per-SKU override]
  └─ status: ACTIVE | INACTIVE

Category (AR)
  ├─ categoryId, name, slug, parentId, level (1|2|3), imageUrl, status
  └─ assignments: CategoryAttributeAssignment[] [VO: { templateId, isRequired, isFilterable, isSearchable, displayOrder } — chỉ non-empty khi level=L3]

AttributeTemplate (AR)
  ├─ templateId, name, displayName
  ├─ inputType: SELECT | TEXT | NUMBER | BOOLEAN
  ├─ status: ACTIVE | INACTIVE                  [soft-delete, cùng pattern Brand/Category/AttributeOption]
  └─ options: AttributeOption[]                 [Entity: { optionId, value, displayValue, status }]

Brand (AR)
  └─ brandId, name, slug, status: ACTIVE | INACTIVE
```

> **Tại sao `AttributeTemplate` là Aggregate Root riêng, không phải entity trong `Category`:**  
> Tham khảo Tiki/Shopee/Lazada: `attribute_id` là global stable ID — "Màu sắc" giữ cùng ID dù xuất hiện ở category nào. Nếu để `AttributeTemplate` sống trong `Category`, thêm 1 option mới ("Đỏ") phải sửa nhiều category riêng biệt, "Màu sắc" ở 2 category khác nhau thành 2 object mất đồng bộ option pool, và Search BC không facet nhất quán được cross-category. Giải pháp: `AttributeTemplate` là AR riêng, `Category` chỉ assign + config cách dùng qua `CategoryAttributeAssignment`.

### Attribute Value Model — Options + Variants

Đã trải qua nhiều vòng brainstorm (xem Session Log ở `feature/06-catalogue_feature/implementation.md`) để
đi đến bản cuối cùng — bỏ hẳn `AttributeScope` (GLOBAL/CATEGORY), và `isVariantDefining` chuyển từ chỗ từng
bị bỏ hoàn toàn sang khai báo lại ở đúng 1 chỗ duy nhất — Product (không phải Category):

```
AttributeTemplate   master data thuần — không tự áp dụng ở đâu cả, không phân biệt GLOBAL/CATEGORY
                        │
                        ▼ Admin assign tường minh — CHỈ leaf (L3) mới được assign
Category (L3)       định nghĩa STRUCTURE + COMPLETENESS/CURATION POLICY: attribute nào áp dụng cho leaf
                     này + isRequired (Product bắt buộc khai giá trị) + isFilterable (hiện facet sidebar)
                     + isSearchable (đưa vào full-text index — catalog chỉ lưu, KHÔNG tự dùng, phát cho
                     1 search-service tương lai đồng bộ) — KHÔNG có isVariantDefining ở tầng này (đó là
                     quyết định của Seller per-listing, không phải Admin áp đặt cả category — không kế
                     thừa giữa các level, mỗi leaf độc lập)
                        │
                        ▼
Product              phải thuộc category L3; chỉ dùng đúng tập attribute leaf đó đã assign (đủ những cái
                     required, phần còn lại tự chọn subset); mỗi entry tự đánh dấu `isVariantDefining`
                     (chỉ hợp lệ với SELECT, khoá lại sau khi có Variant đầu tiên) — đây mới là cờ quyết
                     định trục nào bắt buộc dùng để tách SKU
                        │
                        ▼
Variant              `combination` PHẢI cover đủ mọi attribute Product đánh dấu `isVariantDefining=true`
                     (không thiếu trục nào) — mỗi optionId phải resolve ra 1 value nằm trong tập Product
                     đã khai cho đúng templateId
```

**`isVariantDefining` — quay lại làm 1 khái niệm khai báo tường minh, nhưng ở Product (Seller tự khai
per-listing), KHÔNG phải Category:** bản thiết kế trước bỏ hẳn khái niệm này (coi "variant-defining" chỉ
là hệ quả emergent từ việc attribute có xuất hiện trong `Variant.combination` hay không, không cần khai
báo trước) — nhưng thiếu khai báo tường minh khiến không có gì đảm bảo TÍNH NHẤT QUÁN giữa các Variant của
cùng 1 Product (Variant A chỉ khai `color`, Variant B chỉ khai `storage`, cả 2 đều "hợp lệ" vì không ai
enforce phải khai đủ trục nào). Cân nhắc đưa `isVariantDefining` trở lại ở **Category** (như thiết kế cũ
đã bỏ) nhưng bác bỏ: category (`required`) là chính sách completeness của ADMIN (bắt buộc Product phải
khai giá trị, VD "Xuất xứ" — lý do compliance/filter), hoàn toàn khác quyết định "attribute này có tách SKU
hay không" — 1 attribute Admin bắt buộc khai (Xuất xứ) hoàn toàn có thể KHÔNG phải trục biến thể mà Seller
muốn dùng. Gộp 2 khái niệm này làm 1 (thử nghiệm ban đầu: gán `required ⟹ bắt buộc là trục combination`)
sẽ ép mọi Product phải tách SKU theo attribute mà Admin chỉ muốn bắt buộc khai để lọc/tuân thủ — sai. So
Shopee/Lazada (Seller tự chọn "Phân loại 1/2" NGAY khi tạo listing, không phải Admin category quyết định
sẵn) và Amazon (`variation_theme` chọn per-listing, category chỉ cung cấp danh sách theme khả dụng) — cả 2
đều để quyết định này ở tay Seller, per-listing. Kết luận: `isVariantDefining` thuộc `ProductAttributeValue`
(mỗi entry Product tự đánh dấu), chỉ hợp lệ với template `SELECT` (`VARIANT_DEFINING_REQUIRES_SELECT` —
TEXT/NUMBER/BOOLEAN không có `AttributeOption` để tham chiếu `optionId`, không thể tham gia combination).
`required` (category) giữ nguyên ý nghĩa gốc, không đổi — thuần là completeness policy của Admin.

**Vì sao bỏ `AttributeScope` (GLOBAL/CATEGORY) — chỉ leaf (L3) mới assign, không kế thừa:** GLOBAL từng
tồn tại để tránh phải assign 1 attribute dùng chung vào từng category, nhưng nó tạo ra mâu thuẫn cấu trúc
— attribute GLOBAL không có `CategoryAttributeAssignment` nên không có chỗ lưu `isRequired`/`isFilterable`
riêng theo category (bị hardcode `false`). Cân nhắc thêm cơ chế kế thừa qua cây category (assign ở node
cha, leaf con thừa hưởng) nhưng bị loại vì: attribute có ý nghĩa dùng chung xuyên cả 1 nhánh L1 rất hiếm
trong thực tế (Storage/RAM của "Điện thoại" không liên quan "Laptop" dù cùng L1 "Electronics" — tham khảo
Google Product Taxonomy, phần lớn attribute có ý nghĩa ở đúng level leaf). Chốt: chỉ leaf (L3) được
`assignAttribute`/`updateAssignment`/`removeAssignment` — throw `ATTRIBUTE_ASSIGNMENT_REQUIRES_LEAF` nếu
cố assign ở L1/L2. Đánh đổi: attribute "gần như dùng chung" phải assign lặp lại ở từng leaf — bù lại mỗi
leaf tự cấu hình `isRequired`/`isFilterable` độc lập, và mô hình chỉ còn 1 cơ chế duy nhất, không có
exception nào (khớp pattern PIM chuẩn — Magento Attribute Set, Akeneo).

**API assignment — replace-all, không phải CRUD từng item:** `PUT /api/admin/categories/{id}/attributes`
nhận cả list, thay THẾ TOÀN BỘ assignment hiện có của category — khớp đúng cách `CategoryPersistenceAdapter`
vốn đã persist (xoá hết + insert lại toàn bộ mỗi lần save). Assign 1 attribute mới, sửa config 1 attribute,
hay gỡ 1 attribute đều quy về gọi lại đúng 1 endpoint này với list mong muốn cuối cùng (client tự
`GET /api/categories/{id}/attributes` trước, sửa trong list, rồi PUT lại) — không có 3 endpoint CRUD
riêng lẻ như trước.

**`isSearchable` — cùng nhóm curation với `isRequired`/`isFilterable`, KHÔNG phải per-attribute-template:**
dù search/browse ở scale lớn sẽ do 1 search-service riêng đảm nhiệm (catalog không kham nổi full-text
index ở quy mô lớn), quyết định "attribute này có nên đưa vào search index không" vẫn là quyết định
CURATION của Admin — cùng người, cùng chỗ cấu hình với `isRequired`/`isFilterable`, không phải quyết định
kỹ thuật catalog tự làm. Catalog chỉ đóng vai **system of record**: lưu + trả ra qua
`GetCategoryAttributes`, không tự dùng flag này để chạy query nào (giống hệt cách `isFilterable` đang tồn
tại — cũng chưa có nơi nào catalog tự dùng để lọc Product). Đặt ở `CategoryAttributeAssignment` (không
phải `AttributeTemplate`) vì đúng nguyên tắc: **hành vi theo NGỮ CẢNH SỬ DỤNG → category, định nghĩa NỘI
TẠI của khái niệm → template** — cùng 1 template "Color" có thể searchable ở category này nhưng không ở
category khác, tuỳ nhu cầu buyer từng ngành hàng. Việc phát event để 1 search-service tương lai đồng bộ
(VD khi Admin đổi `ReplaceCategoryAttributeAssignments`) chưa cần làm ngay — chưa có consumer thật, thêm
field trước (rẻ), thiết kế event sau khi search-service thực sự tồn tại để biết chính xác schema cần gì.

**Validate ĐÚNG FORMAT theo `inputType` cho NUMBER/BOOLEAN (`ATTRIBUTE_VALUE_FORMAT_INVALID`):** trước đây
`validateProductAttributes` chỉ validate riêng SELECT (khớp option ACTIVE), còn TEXT/NUMBER/BOOLEAN không
hề check format — Seller submit được `"large"` cho 1 attribute khai `inputType=NUMBER`, hay `"maybe"` cho
`BOOLEAN`. Đây không phải thiếu tính năng mới mà là lỗ hổng của tính năng ĐÃ khai báo (`inputType` tồn tại
để ép kiểu dữ liệu, không enforce thì khai ra vô nghĩa — sort/so sánh số sẽ vỡ nếu lẫn giá trị không phải
số). Thêm parse-check: `NUMBER` phải parse được `Double`, `BOOLEAN` phải là `"true"`/`"false"`
(case-insensitive) — `TEXT` vẫn free-form, không ràng buộc format.

**Validation rule đầy đủ (regex, min/max, length) — CÂN NHẮC RỒI HOÃN, chưa có nhu cầu cụ thể:** nếu làm,
nên đặt ở `AttributeTemplate` (đi cùng `inputType`), KHÔNG phải `CategoryAttributeAssignment` — khác với
`isRequired`/`isFilterable`/`isSearchable`. Lý do: range/regex trả lời câu hỏi "giá trị này có hợp lệ với
KHÁI NIỆM đó không" (định nghĩa nội tại), không phải "category này có dùng attribute ra sao" (hành vi theo
ngữ cảnh) — nếu để category override range sẽ nảy sinh câu hỏi khó "giá trị hợp lệ ở category A nhưng
category B lại không, vậy nó có hợp lệ 'tuyệt đối' không". So Akeneo/Magento (2 hệ thống PIM đã dùng làm
precedent xuyên suốt tài liệu này): validation rule (regex, min/max, giới hạn ký tự) đều cấu hình GLOBAL
trên attribute, không override theo Family/Attribute Set — khi range thực sự khác biệt lớn giữa các
category (VD pin điện thoại 1000-10000 mAh vs pin laptop 20000-100000 mAh), cách làm chuẩn là **tách thành
2 template riêng**, không phải giữ 1 template rồi override theo category. Hoãn vì chưa có category/nghiệp
vụ thật nào cần range/regex cụ thể — sẽ làm khi có yêu cầu rõ ràng.

**Vì sao Product bắt buộc thuộc category leaf (L3):** vì chỉ leaf mới có `CategoryAttributeAssignment`,
Product gán vào L1/L2 sẽ có tập attribute khả dụng rỗng — trạng thái vô nghĩa nên chặn từ gốc
(`CATEGORY_NOT_LEAF`), khớp UX thật của Shopee/Lazada/Tiki (seller luôn phải chọn đủ path xuống leaf).

**Đã cân nhắc và bác bỏ — custom attribute tự do (`Map<String,String>` hoặc tương đương) cho Product:**
so sánh với Amazon (Product Type schema cố định, không cho seller tự thêm field; phần tự do duy nhất là
text thuần — bullet points/description, không phải key-value có cấu trúc) — quyết định giữ nguyên
`description` cho nhu cầu "thông tin tự do", không thêm field mới. Lý do: custom attribute không
filter/search được nhất quán (không có template để so khớp), và tệ hơn cả việc không filter được — không
kiểm soát được tên key (seller A gõ "Xuất xứ", seller B gõ "Origin" cho cùng khái niệm).

**`ProductAttributeValue.values` (`List<String>`, không bao giờ rỗng, không giới hạn cardinality)** —
SELECT lưu `AttributeOptionId`, TEXT/NUMBER/BOOLEAN lưu raw value (không có option để tham chiếu). Persist
trong `product_attribute_value` với PK mở rộng `(product_id, template_id, value)` — xem `data.md` (cột tên
`value` nhưng nội dung là ID với SELECT, không đổi tên cột để tránh migration không cần thiết).

**`AddVariant`** load `AttributeTemplate` theo batch (`findAllByIds`, tránh N+1), so **thuần theo ID**
(`declared.values().contains(pair.optionId().getValue())`) — không còn bước decode `optionId → value`
nào cần thiết cho việc so sánh (chỉ còn tra template để xác nhận `optionId` thực sự thuộc template đó,
input validation thuần tuý). **Không có fallback nào cả** — nếu Product chưa khai entry nào cho templateId
đó, reject thẳng `VARIANT_OPTION_NOT_DECLARED_BY_PRODUCT` (đã bỏ hành vi cũ "mặc định mọi option ACTIVE"
— muốn dùng attribute nào để tách SKU, Product phải khai tường minh trước, không có mặc định ngầm).

**`isVariantDefining=true` bắt buộc xuất hiện trong MỌI combination của Product đó
(`VARIANT_MISSING_VARIANT_DEFINING_ATTRIBUTE`):** các attribute variant-defining tạo thành 1 ma trận
(Cartesian) — VD Product đánh dấu `color`+`storage` đều variant-defining nghĩa là SKU được tổ chức theo
ma trận Color × Storage, mỗi Variant là 1 điểm trong không gian đó, bắt buộc có ĐỦ mọi toạ độ (không chỉ
"ít nhất 1"). Cân nhắc "chỉ cần ít nhất 1 trong số các attribute variant-defining" nhưng bác bỏ: sẽ tái
hiện đúng lỗi ban đầu (Variant A chỉ khai `color`, Variant B chỉ khai `storage`, cả 2 "hợp lệ" song song vì
combination_hash khác nhau — SKU thiếu thông tin, không nhất quán). Khớp Shopify (mọi variant bắt buộc đủ
giá trị cho MỌI `product.options`, không cho thiếu 1 option), Amazon (mọi ASIN con phải khai đủ attribute
trong `variation_theme`), Shopee/Lazada (UI tự sinh ma trận đầy đủ Phân loại 1 × Phân loại 2, không có ô
nào chỉ điền 1 chiều). `AddVariant.validateCombinationAgainstProduct` đọc thẳng
`product.getAttributeValues()` lọc `isVariantDefining=true`, không cần load `Category`/`required` nữa —
`validateProductAttributes` đã đảm bảo cờ này chỉ tồn tại trên template SELECT nên không cần check lại.

**`isVariantDefining` khoá lại NGUYÊN TẬP sau khi Product có Variant đầu tiên
(`VARIANT_DEFINING_LOCKED_AFTER_VARIANT`):** đổi cờ giữa chừng (VD bật `color` thành variant-defining sau
khi đã có Variant không khai `color`) sẽ khiến Variant cũ/mới không còn nhất quán về trục bắt buộc — cùng
tinh thần "quyết định 1 lần, khoá lại" với `combination` bất biến của chính Variant.
`UpdateProduct.validateVariantConsistency` so **toàn bộ tập** `{templateId | isVariantDefining=true}` cũ
với tập submit mới bằng `Set.equals` — KHÔNG chỉ so từng entry trùng ở cả 2 bản, vì cách so entry-trùng sẽ
bỏ lọt trường hợp Seller thêm hẳn 1 attribute MỚI (chưa từng khai trước đó) với `isVariantDefining=true`:
entry mới này không nằm trong vòng lặp "so theo `previous`", sẽ lọt qua guard trong khi Variant cũ thực sự
đang "thiếu" đúng trục mới đó. Dùng chung 1 lần `variantRepository.findByProductId` với check
`ATTRIBUTE_VALUE_STILL_USED_BY_VARIANT` bên dưới, không query 2 lần.

**Trigger khoá là "đã có Variant", KHÔNG phải "đã publish":** ngay khi `AddVariant` chạy (dù Product còn
DRAFT), `VariantCreatedEvent` đã bắn sang inventory-service khởi tạo `Stock` — đã có hệ thống khác phụ
thuộc SKU đó trước cả khi buyer nhìn thấy Product. Khớp Amazon/Shopee/Lazada: khoá cấu trúc biến thể theo
việc ASIN/SKU con đã tồn tại, không quan tâm listing đang ẩn hay hiện. `name`/`description`/`warrantyInfo`
không bị ảnh hưởng bởi trigger này — luôn sửa được bất kể status hay đã có Variant hay chưa (chỉ chặn bởi
`PRODUCT_BLOCKED` khi Admin block, `Product.update()` đã tự guard).

**Lưu trữ `isVariantDefining` — bảng riêng `product_variant_defining_attribute`, KHÔNG nhét vào
`product_attribute_value`:** thử ban đầu thêm cột `is_variant_defining` ngay trên `product_attribute_value`
nhưng đó là bảng grain PER VALUE (PK `product_id, template_id, value`) — cờ này lại là thuộc tính của CẶP
`(product_id, template_id)`, nên bị lặp lại y hệt trên mọi row cùng template (VD "Màu sắc" 30 option → lặp
30 lần cho đúng 1 sự thật). Tách bảng riêng chỉ tồn tại row khi `true` (không cần cột boolean) — vừa đúng
chuẩn hoá, vừa rẻ hơn ở scale lớn cho attribute cardinality cao (xem `data.md`). Cân nhắc rồi bác bỏ việc
giữ nguyên đè lên `product_attribute_value` vì lo ngại thêm 1 query — nhưng query mới chỉ là index-seek
theo PK `product_id`, rẻ tương đương lý do đã dùng để BÁC BỎ denormalize `TEMPLATE_REQUIRED_BY_CATEGORY`
trước đó (chỉ nên denormalize khi query gốc thực sự đắt — ở đây thì không).

**`UpdateProduct` chặn thu hẹp `attributeValues` nếu Variant đang dùng giá trị bị gỡ
(`ATTRIBUTE_VALUE_STILL_USED_BY_VARIANT`):** Product khai tập giá trị con cho từng attribute, Variant chỉ
được chọn trong tập đó (`VARIANT_OPTION_NOT_DECLARED_BY_PRODUCT` ở trên) — hệ quả ngược lại là nếu Seller
sau đó gỡ bớt 1 giá trị khỏi Product mà 1 Variant đã tồn tại đang dùng đúng giá trị đó, dữ liệu sẽ lệch:
Variant "khai" 1 đặc tính mà Product không còn công nhận. So sánh Shopify (xoá option value đang có variant
phụ thuộc sẽ cảnh báo rõ "sẽ xoá luôn N variant", bắt Admin xác nhận cascade) và Amazon (không cho gỡ 1
variation value khỏi Product Type nếu còn ASIN dùng nó, phải xử lý ASIN trước) — cả 2 đều chặn/cảnh báo,
không cho âm thầm mồ côi. `UpdateProduct` chọn hướng **chặn** (khớp style guard sẵn có `OPTION_IN_USE`/
`TEMPLATE_REQUIRED_BY_CATEGORY`, không cascade tự động vì Variant là aggregate khác, tự ý mutate chéo aggregate
trong cùng transaction vi phạm boundary). Check load `variantRepository.findByProductId(...)` (danh sách
variant của 1 product — nhỏ, khác hẳn quy mô toàn bảng `variant_combination_item` nên không cần lo hiệu
năng như usageCount ở trên), diff `previous.values()` so với `submitted` theo từng `templateId`, giá trị nào
biến mất mà còn nằm trong `VariantAttributePair` của bất kỳ Variant nào (**không phân biệt Variant đang
ACTIVE hay INACTIVE** — khớp nguyên tắc `usageCount` không giảm khi deactivate, "đã từng dùng" là vĩnh viễn
cho mục đích guard) thì chặn. Seller muốn gỡ giá trị đó khỏi Product bắt buộc phải xử lý (deactivate) Variant
liên quan trước.

### Grandfather — miễn re-validate cho entry không đổi khi Update

`UpdateProduct` là **replace-all** (Seller phải gửi lại toàn bộ `attributeValues` mỗi lần, kể cả phần
không đổi) — nếu không có cơ chế này, 1 hành động không liên quan của Admin (deactivate 1 option/template
mà Product đang giữ) sẽ khiến Seller không update được Product nữa dù chỉ định đổi giá/mô tả. Tham khảo
Salesforce "inactive picklist values" (record cũ giữ nguyên giá trị đã inactive, chỉ chặn CHỌN MỚI giá
trị đó) — cùng kết luận với AWS IAM (policy tham chiếu resource đã xoá vẫn hoạt động, chỉ chặn tạo POLICY
MỚI tham chiếu sai) và Shopify (không re-validate lại phần không nằm trong diff).

`validateProductAttributes` nhận thêm `previouslyPersisted` (rỗng với Create — không có gì để grandfather):
- `ATTRIBUTE_NOT_IN_CATEGORY`/`TEMPLATE_INACTIVE` — grandfather ở cấp **templateId** (attribute đó đã tồn
  tại ở bản cũ hay chưa, không quan tâm giá trị cụ thể).
- `INVALID_ATTRIBUTE_VALUE` — grandfather ở cấp **TỪNG GIÁ TRỊ** trong list, không phải cả entry — nếu
  chỉ so cả entry, thêm 1 optionId mới vào list cũ sẽ vô tình bắt re-validate luôn cả các optionId cũ
  không hề đổi (VD Product có `storage: [128gb, 256gb]`, "256gb" đã inactive, Seller chỉ muốn thêm
  "512gb" — so theo entry sẽ chặn nhầm cả 256gb, so theo từng giá trị thì chỉ 512gb bị check, 128gb/256gb
  được miễn vì đã có sẵn).
- `REQUIRED_ATTRIBUTE_MISSING` — **KHÔNG grandfather** — đây là policy forward-looking (category đòi hỏi
  mới), không phải dữ liệu cũ bị lỗi thời, nên luôn phải thoả mãn theo cấu hình HIỆN TẠI.

### AttributeTemplate lifecycle — deactivate, không hard-delete

`AttributeTemplate` có `status: ACTIVE | INACTIVE`, cùng pattern soft-delete với `Brand`/`Category`/
`AttributeOption` — thay cho hard delete (vốn không dùng được: FK `ON DELETE RESTRICT` từ
`category_attribute_assignment`/`product_attribute_value`/`variant_combination_item` sẽ chặn ngay nếu đã
có dữ liệu tham chiếu).

**Copy vs reference — vì sao Product an toàn tuyệt đối trước deactivate, Variant thì không:**
- `ProductAttributeValue.values` (SELECT) lưu **`AttributeOptionId`** — tham chiếu bằng identity, KHỚP
  nguyên tắc "mọi quan hệ Category/Product/Variant → AttributeTemplate/AttributeOption đều là reference-
  by-ID" (không có ngoại lệ nào còn copy chuỗi nữa). Lý do đổi từ copy `value` string sang ID: Product là
  catalog listing ĐANG SỐNG (mutable, sửa được), không phải bản ghi giao dịch đã chốt (như Order) — nếu
  Admin sửa `displayValue` 1 option, Product hiển thị NÊN tự cập nhật theo, không đóng băng theo bản cũ;
  copy chuỗi cũng có rủi ro "drift" giữa các lần submit (facet search gộp sai bucket, giống lỗi
  "Đỏ"/"Do"/"Red" của Shopee). Deactivate option hay cả template sau khi Product đã tạo **vẫn không ảnh
  hưởng gì tới Product cũ** — resolve ID vẫn ra đúng row (không hard-delete), chỉ chặn submission MỚI
  (tạo/update Product) chọn giá trị/template đã inactive — và ngay cả submission cũ được gửi lại nguyên
  vẹn (do `UpdateProduct` là replace-all) cũng được "grandfather" miễn re-validate, xem mục dưới.
- `VariantAttributePair.optionId` là **tham chiếu thật** (FK `variant_combination_item.option_id →
  attribute_option.id`) — nên `AttributeOption` giữ 1 counter `usageCount: int`, bump bởi `AddVariant`
  (cùng transaction, bulk update trực tiếp qua `AttributeTemplateRepository.incrementOptionUsage` — KHÔNG
  đi qua `AttributeTemplate.save()`, vì save() xoá-hết-rồi-insert-lại TOÀN BỘ option của template, quá đắt
  cho việc chỉ bump 1 counter mỗi lần tạo Variant). `AttributeOption.deactivate()` tự chặn
  (`OPTION_IN_USE`) nếu `usageCount > 0` — **in-aggregate check thuần túy, không cần hỏi sang
  `VariantRepository` nữa** (trước đây là 1 query `existsByOptionId` full-scan `variant_combination_item`,
  bảng ghi liên tục + không có index trên `option_id` — Postgres không tự index cột FK). Stricter hơn
  Product vì Variant là SKU đang bán được thật.

**`activate()` — đối xứng `deactivate()`, cho cả Template lẫn Option, không cần guard:** bật lại không vi
phạm invariant nào (khác chiều deactivate). Endpoint: `POST .../attribute-templates/{id}/activate` và
`POST .../attribute-templates/{id}/options/{optionId}/activate`. Lý do cần có, không chỉ "cho đối xứng":
- **Template**: `name` có unique constraint trên MỌI row bất kể status — không có `activate()`, deactivate
  nhầm 1 template sẽ khiến `name` đó vĩnh viễn không dùng lại được (không hard-delete để giải phóng).
- **Option**: không bị kẹt tên (không có unique constraint trên `value`), nhưng tạo option MỚI thay vì bật
  lại option CŨ sẽ sinh 2 `AttributeOptionId` khác nhau cho cùng 1 khái niệm nghiệp vụ — phân mảnh identity,
  gây khó khi truy vấn/facet sau này (đúng loại lỗi "Đỏ"/"Do"/"Red" của Shopee được nhắc ở phần đầu tài liệu
  thiết kế) — nên ưu tiên bật lại đúng row cũ.

**Guard khi thêm/sửa Option trên Template đã `INACTIVE`:** `addOption()`/`updateOptionDisplayValue()` throw
`TEMPLATE_INACTIVE` nếu parent template không `ACTIVE` — quản lý option của 1 template đã "khai tử" là vô
nghĩa. `deactivateOption()`/`activateOption()` không bị chặn bởi trạng thái template (có thể bật/tắt option
độc lập bất kể template đang ACTIVE hay INACTIVE).

**Guard khi deactivate `AttributeTemplate` — tránh deadlock "required nhưng không dùng được":** nếu 1
template đang `required=true` ở 1 category mà bị deactivate, Seller sẽ không thể tạo Product hợp lệ trong
category đó nữa (buộc phải có giá trị cho 1 attribute không còn dùng được) — không có lối thoát cho tới khi
Admin can thiệp. Tham khảo PIM chuẩn (Akeneo/Salsify: chặn archive 1 Attribute nếu đang required trong bất
kỳ Family nào) — không đi theo Amazon (schema versioning, quá phức tạp so với quy mô) hay Shopee/Lazada
(phụ thuộc kỷ luật vận hành thủ công, dễ vỡ). Cụ thể:
- `validateTemplateDeactivatable` (`AttributeTemplateDomainService`) — throw `TEMPLATE_REQUIRED_BY_CATEGORY`
  nếu `categoryRepository.existsRequiredAssignmentByTemplateId(...)` true. **Không** chặn nếu chỉ assign
  nhưng `required=false` — trường hợp đó an toàn (Product bỏ qua bình thường, không deadlock).
- `AssignAttributeToCategory` chặn assign 1 template đã `INACTIVE` (`TEMPLATE_INACTIVE`).
- `validateProductAttributes` chặn submit giá trị cho template `INACTIVE` (áp dụng mọi `inputType`, không
  riêng SELECT) — đảm bảo deactivate có tác dụng thật kể cả với assignment không required.
- `GetCategoryAttributes` lọc bỏ assignment trỏ tới template `INACTIVE` khỏi response — Seller-facing UI
  không hiện field không dùng được nữa.

### Category lifecycle — soft toggle (điều hướng) khác hard-delete (dọn dữ liệu)

`Category` có **2 cơ chế tách biệt, không thay thế nhau**, so sánh với Magento/Shopify (category
enable/disable toggle) và Amazon (browse node retirement):

- **`DeactivateCategory`/`ActivateCategory`** — soft toggle, **không có guard nào** (khác hẳn AttributeOption/
  AttributeTemplate/Brand — deactivate category KHÔNG cần kiểm tra "đang có Product/children hay không").
  Lý do: đây thuần là toggle điều hướng/hiển thị, không phải khoá toàn vẹn dữ liệu — Product cũ tham chiếu
  category đã deactivate **không bị ảnh hưởng gì** (đọc bình thường, y hệt nguyên tắc Product/Brand/
  AttributeTemplate đã áp dụng trước đó). Tác dụng thật: (1) `GetCategoryTree` ẩn hẳn category INACTIVE
  khỏi cây — không cascade status xuống children, nhưng con của node đã ẩn tự nhiên "unreachable" (không
  còn ai link tới nó trong cây trả về), đúng hiệu ứng Magento; (2) `CreateProduct` chặn tạo Product MỚI
  dưới category INACTIVE (`CATEGORY_INACTIVE`) — Product cũ không bị đụng.
- **`DeleteCategory`** — hard delete, giữ nguyên guard `HAS_CHILDREN`/`HAS_PRODUCT_REFERENCE` (khớp FK
  `ON DELETE RESTRICT`) — chỉ dùng dọn category tạo nhầm/trùng lặp, chưa từng ai dùng. Không phải công cụ
  chính cho "ngừng dùng nhưng giữ lịch sử" — dùng `DeactivateCategory` cho mục đích đó.

> **`DeactivateBrand` đã fix theo đúng nguyên tắc trên** — bỏ guard `BRAND_IN_USE` (trước đây chặn ngược:
> không cho deactivate nếu CÓ Product dùng). Giờ deactivate Brand không guard gì cả, giống Category —
> Product cũ tham chiếu Brand đã deactivate vẫn resolve bình thường qua `GetProduct`.

### Product Lifecycle

`status` và `adminBlocked` là **2 trục độc lập** — không nén chung thành 1 enum (đúng bài học rút ra từ Stock aggregate ở inventory-service, xem `service/inventory-service/service.md`).

```
Trục 1 — status (seller điều khiển qua publish/unpublish):
        publish()              unpublish()
DRAFT ──────────► PUBLISHED ◄────────────► UNPUBLISHED

Trục 2 — adminBlocked (admin điều khiển qua block/unblock, độc lập với status):
false ──block()──► true ──unblock()──► false
```

Mọi guard write-operation (`update`, `updateCategory`, `publish`, `unpublish`, `addImage`, `removeImage`) đều gọi `guardNotBlocked()` trước — nếu `adminBlocked=true` thì reject bất kể `status` đang là gì. `block()`/`unblock()` tự nó **không đổi `status`**.

| Transition                 | Guard                                                                                             |
|----------------------------|---------------------------------------------------------------------------------------------------|
| `DRAFT → PUBLISHED`        | `variantRepository.existsActiveByProductId(productId)` — cross-AR query, cộng `guardNotBlocked()` |
| `adminBlocked: false→true` | Chỉ Admin (`BlockProduct`) — không có guard nào chặn, admin block được ở mọi status               |
| `adminBlocked: true→false` | Chỉ Admin (`UnblockProduct`) — Seller không thể tự gọi                                            |
| `UNPUBLISHED → PUBLISHED`  | Guard tương tự DRAFT                                                                              |

### Product delete — hard-delete, nhưng CHỈ khi còn DRAFT

Khác hẳn `AttributeTemplate`/`Category`/`Brand`/`AttributeOption` (đều chỉ soft-delete vĩnh viễn, không có
hard-delete nào) — Product **có** `DeleteProduct` (`DELETE /api/seller/products/{id}`), nhưng chỉ cho phép
khi `status = DRAFT` (`PRODUCT_NOT_DRAFT` nếu không). Lý do cho phép hard-delete ở đây trong khi từ chối ở
mọi entity khác: `DRAFT` nghĩa là **chưa từng publish**, mà storefront chỉ cho buyer thấy/mua Product
`PUBLISHED` — nên 1 Product DRAFT chắc chắn **chưa từng có Order nào** tham chiếu tới bất kỳ Variant nào
của nó, xoá cứng an toàn tuyệt đối về audit/toàn vẹn tham chiếu liên service (khác hẳn lý do vì sao
Category/AttributeTemplate không dám hard-delete — những entity đó có thể đã bị Product khác tham chiếu
kể cả khi Product đó chưa publish, còn ở đây object BỊ xoá chính là Product/Variant, không phải thứ được
tham chiếu). So Shopify/Amazon/Shopee/Lazada: cả 4 đều cho Seller xoá vĩnh viễn 1 draft/listing chưa từng
lên sàn, chỉ khoá vĩnh viễn (soft-only) sau khi đã từng publish/bán thật — khớp đúng ranh giới `DRAFT` ở
đây.

**Cascade xoá Variant:** vì Product và Variant là 2 Aggregate Root riêng biệt, cascade được orchestrate
tường minh ở `DeleteProduct` (gọi `variantRepository.deleteByProductId` trước, rồi `productRepository.delete`)
— không tự động cascade trong persistence adapter của Product (đúng nguyên tắc DDD: không cross-aggregate
mutate ngầm). Vì Product DRAFT chưa publish không đồng nghĩa Variant của nó chưa hề có tác dụng phụ nào —
`AddVariant` bắn `VariantCreatedEvent` ngay khi tạo bất kể Product đang DRAFT hay đã publish, và
inventory-service tạo `Stock` row ngay lúc đó (mô hình chuẩn thực tế: Seller set tồn kho TRƯỚC khi publish,
khớp Shopify/Amazon/Shopee/Lazada — Inventory gắn theo vòng đời SKU, không gắn theo trạng thái publish của
listing cha). `DeleteProduct` phát `VariantDeletedEvent` (`catalog.variant.deleted`) cho từng Variant vừa
xoá — inventory-service consume (`VariantDeletedConsumer` → `DeleteStock`) để hard-delete luôn `Stock` row
tương ứng, dọn sạch hoàn toàn thay vì để lại rác mồ côi (xem `service/inventory-service/service.md` §
Domain methods / Consumes). An toàn tuyệt đối vì `Stock` đó chắc chắn `reservedQty = 0` (chưa từng có
Order/reservation nào chạm tới, do Product chưa từng publish).

---

## Use Cases

### Admin

| Use Case                      | Command / Query                                                                                       | Endpoint                                                                 |
|-------------------------------|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Quản lý Brand                 | `CreateBrand`, `UpdateBrand`, `DeactivateBrand`, `ListActiveBrands`                                   | `POST/PUT/DELETE/GET /api/admin/brands`                                  |
| Quản lý Category tree         | `CreateCategory`, `UpdateCategory`, `DeleteCategory` (hard, guard HAS_CHILDREN/HAS_PRODUCT_REFERENCE), `DeactivateCategory`/`ActivateCategory` (soft toggle điều hướng, không guard) | `POST/PUT/DELETE /api/admin/categories`, `POST .../{id}/deactivate`, `POST .../{id}/activate` |
| Assign attribute vào Category | `ReplaceCategoryAttributeAssignments` (replace-all — assign/update/remove 1 đều gọi lại đây với list mới) | `PUT /api/admin/categories/{id}/attributes` |
| Quản lý AttributeTemplate     | `CreateAttributeTemplate`, `UpdateAttributeTemplate`, `DeactivateAttributeTemplate`                   | `POST/PUT/DELETE /api/admin/attribute-templates`                         |
| Quản lý AttributeOption       | `AddAttributeOption`, `UpdateAttributeOption`, `DeactivateAttributeOption`                            | `POST/PUT/DELETE /api/admin/attribute-templates/{id}/options/{optionId}` |
| Block / Unblock Product       | `BlockProduct`, `UnblockProduct`                                                                      | `POST /api/admin/products/{id}/block                                     |unblock` |

### Seller

| Use Case                   | Command / Query                                                          | Endpoint                                                          |
|----------------------------|--------------------------------------------------------------------------|-------------------------------------------------------------------|
| Tạo Product (DRAFT)        | `CreateProduct`                                                          | `POST /api/seller/products`                                       |
| Sửa Product                | `UpdateProduct`                                                          | `PUT /api/seller/products/{id}`                                   |
| Publish / Unpublish        | `PublishProduct`, `UnpublishProduct`                                     | `POST /api/seller/products/{id}/publish                           |unpublish` |
| Upload ảnh (presigned URL) | `GetProductImageUploadUrl`, `ConfirmProductImage`, `RemoveProductImage`  | `POST /api/seller/products/{id}/images/upload-url                 |confirm`, `DELETE` |
| Quản lý Variant            | `CreateVariant`, `UpdateVariant`, `ActivateVariant`, `DeactivateVariant` | `POST/PUT /api/seller/products/{id}/variants`, `POST .../activate |deactivate` |

### Guest / Customer (Read-only)

| Use Case                    | Query                   | Endpoint                              |
|-----------------------------|-------------------------|---------------------------------------|
| Xem category tree           | `GetCategoryTree`       | `GET /api/categories`                 |
| Xem attributes của category | `GetCategoryAttributes` | `GET /api/categories/{id}/attributes` |
| Xem danh sách brand         | `ListActiveBrands`      | `GET /api/brands`                     |
| Xem product detail          | `GetPublishedProduct`   | `GET /api/products/{id}`              |
| Xem variants của product    | `GetProductVariants`    | `GET /api/products/{id}/variants`     |

### Image Upload Flow

```
1. Seller → POST /seller/products/{id}/images/upload-url
2. catalog-service → MinIO: generate presigned PUT URL (TTL 5 phút)
3. Browser upload thẳng lên MinIO (bypass catalog-service)
4. Seller → POST /seller/products/{id}/images/confirm { objectKey }
5. catalog-service verify object tồn tại → append vào Product.images
```

### Flow: Seller Tạo Product

```
1. Seller chọn Category
2. System load: GLOBAL AttributeTemplates (luôn hiển thị) + CategoryAttributeAssignment của Category đó
3. Seller điền non-variant attributes (Brand, RAM, OS...)
4. Seller chọn variant-defining templates + options — VD: Color: [Đen, Trắng] × Size: [S, M, L]
5. System auto-generate Cartesian matrix → N Variant rows (2×3 = 6 ở VD trên)
6. Seller điền price, weight, barcode cho từng Variant (bulk-edit)
7. Upload ảnh (flow trên) → save → DRAFT
8. publish() → PUBLISHED → emit ProductPublishedEvent
```

---

## Business Rules

### AttributeTemplate
- `scope=GLOBAL`: áp dụng mọi Product, không cần CategoryAttributeAssignment.
- `scope=CATEGORY`: chỉ hiển thị khi Category đã assign.
- `AttributeOption` chỉ có thể soft-delete — không hard-delete nếu bất kỳ Variant nào đang reference.
- `inputType` và `name` không sửa sau khi có Product reference.

### Category
- Depth tối đa: Level 3. Node L3 không thể có con.
- Không hard-delete Category khi có Product reference.
- `CategoryAttributeAssignment` là Value Object — replace toàn bộ khi Admin sửa.
- Cùng `attributeTemplateId` không được assign 2 lần trong cùng Category.

### Product
- **Không có approval flow** — Seller tự publish, Admin chỉ force-block khi vi phạm chính sách (đã trust từ lúc Seller BC onboarding).
- Phải có ít nhất 1 Variant ACTIVE trước khi `publish()`.
- `publish()` ném exception nếu `status=BLOCKED`.
- `sellerId` và `categoryId` không thể thay đổi sau khi có Variant.

### Variant
- `VariantCombination` là **bất biến sau khi tạo** — Inventory/Cart/Order đã reference `skuId` này.
- `VariantCombination` phải unique trong cùng Product (constraint tại DB + domain guard).
- `VariantCombination` chỉ dùng `AttributeOption` của templates có `isVariantDefining=true` trong category.
- `originalPrice` nếu có: phải `> price`.

---

## Integration Contract

### Publishes (Kafka)

| Topic                           | Event                      | Partition Key | Consumers                             |
|---------------------------------|----------------------------|---------------|---------------------------------------|
| `catalog.product.published`     | `ProductPublishedEvent`    | `productId`   | `search-service`, `inventory-service` |
| `catalog.product.unpublished`   | `ProductUnpublishedEvent`  | `productId`   | `search-service`, `inventory-service` |
| `catalog.product.blocked`       | `ProductBlockedEvent`      | `productId`   | `search-service`, `inventory-service` |
| `catalog.product.unblocked`     | `ProductUnblockedEvent`    | `productId`   | `inventory-service`                   |
| `catalog.product.updated`       | `ProductUpdatedEvent`      | `productId`   | `search-service`                      |
| `catalog.variant.created`       | `VariantCreatedEvent`      | `skuId`       | `inventory-service`                   |
| `catalog.variant.price-changed` | `VariantPriceChangedEvent` | `skuId`       | `search-service`                      |
| `catalog.variant.activated`     | `VariantActivatedEvent`    | `skuId`       | `search-service`, `inventory-service` |
| `catalog.variant.deactivated`   | `VariantDeactivatedEvent`  | `skuId`       | `search-service`, `inventory-service` |
| `catalog.category.updated`      | `CategoryUpdatedEvent`     | `categoryId`  | `search-service`                      |

### Consumes (Kafka)

Không consume event từ BC nào. catalog-service là **upstream thuần túy**.

### Sync Calls

Không có outbound sync call. Inbound: `api-gateway` (`/web/**`, routing thuần — ADR-012) → `web-gateway` (route `/api/catalog/**`, `tokenRelay` + `saveSession`) → `catalog-service` qua REST.

`catalog-service` có `SecurityFilterChain` riêng permitAll 5 GET public (category tree, category attributes, brand list, product detail, product variants) — cần thiết vì Guest/Customer browse không có JWT. `web-gateway` cũng permitAll đúng 5 path này ở layer riêng, nếu không request Guest bị chặn 401 trước khi tới được `catalog-service`.

---

## Dependencies

| Dependency              | Lý do                                            |
|-------------------------|--------------------------------------------------|
| `common-domain`         | `AggregateRoot`, `DomainEvent`                   |
| `outbox-starter`        | Publish events reliable qua Outbox Pattern + CDC |
| `common-events`         | `EventEnvelope` — Kafka contract                 |
| `common-web`            | `ApiResponse`, `GlobalExceptionHandler`          |
| `observability-starter` | Tracing + structured logging                     |
| PostgreSQL (port 5436)  | Primary store                                    |
| Redis                   | L2 cache + pub/sub invalidation                  |
| MinIO                   | Object storage cho product/variant images        |

## Tài liệu liên quan

- [`cache.md`](cache.md) — chiến lược cache 2 tầng (Caffeine L1 + Redis L2), cache inventory, invalidation rules
- [`downstream-effects.md`](downstream-effects.md) — hành vi cụ thể của inventory/search/pricing/cart/order/reporting-service khi nhận event từ catalog-service, kèm open questions chưa chốt
