# Feature 07: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho mọi BC đã có: [ADR-0001](../../../global/2.architecture/adr/0001-entity-id-uuid-v7.md) (UUID v7), [ADR-0003](../../../global/2.architecture/adr/0003-login-session-and-identity-contract.md) (hợp đồng danh tính), [ADR-0011](../../../global/2.architecture/adr/0011-optimistic-lock-and-storage-uniqueness.md) (ràng buộc duy nhất ở lưu trữ, khóa lạc quan chạy ngầm). **Feature này cần ADR mới:** [ADR-0012](../../../global/2.architecture/adr/0012-parent-row-lock-for-child-operations.md) (khóa dòng cha, đang Proposed; chuyển Accepted sau khi KT-06 đạt). Mẫu cấu trúc, kiểu mã, kiểm toán, lỗi ràng buộc: feature 05 (`05-category-structure/tech.md`).

## Câu hỏi cần chốt trước khi viết `plan.md`

| # | Câu hỏi | Quyết định | Chốt |
|---|---|---|---|
| Q1 | Kiểm "người bán đã duyệt" (`INV-CAT-30`) ở 07? | **Không.** 07 kiểm vai `USER` và quyền chủ sản phẩm; nửa "đã duyệt" làm cùng bản sao trạng thái người bán ở 09. Ghi vào `deferred.md` | `2026-11-04` (người dùng đồng ý) |
| Q2 | 07 có phát event, có `version`? | **Có `version`, không phát event.** `EVT-CAT-01`, `EVT-CAT-02` thuộc 09 (gắn `D6`). Không ghi hộp thư, không ghi kiểm toán (hành động của người bán, không phải quản trị) | `2026-11-04` (người dùng đồng ý) |
| Q3 | Giới hạn `Q-CAT-01` | Tiêu đề 5-200, mô tả 5.000, tối đa 2 trục, tối đa 50 biến thể, giá 1 đến 1.000.000.000, SKU 1-50; cấu hình được (T2). Giới hạn ảnh để 08 | `2026-11-04` (theo bảng phân tích); `2026-11-05` người dùng không còn thắc mắc |
| Q4 | Cơ chế chống đua cho quy tắc cắt ngang sản phẩm và biến thể | **Khóa dòng sản phẩm** (ADR-0012) cho lệnh biến thể và lệnh sửa danh sách thuộc tính (có thể đổi trục); khóa lạc quan cho lệnh còn lại | `2026-11-04` (người dùng đồng ý) |
| Q5 | Lưu giá trị thuộc tính thế nào | Hai bảng cha-con: `product_attribute` (một dòng mỗi cặp sản phẩm và mẫu; cờ trục; giá trị rời dạng **chuỗi chuẩn hóa** cho chữ, số, đúng/sai, ngày) và `product_attribute_option` (mỗi tùy chọn đã chọn một dòng, theo **mã tùy chọn**) (T1) | `2026-11-05` (người dùng đồng ý giữ hai bảng; cờ trục làm hai bảng thành cha-con, xem Q9) |
| Q9 | Trục biến thể là khái niệm riêng hay cờ trên thuộc tính | **Cờ trên thuộc tính** (`axis`): một danh sách `attributes`, dòng nào `axis: true` là trục (chọn một, tối đa 2); tập giá trị của trục chính là giá trị của thuộc tính đó. Không bảng trục riêng, không `PUT .../axes`, không khóa ngoại từ biến thể tới giá trị trục; tổ hợp nằm trong tập trục và không rút giá trị đang dùng do ứng dụng kiểm dưới khóa dòng sản phẩm | `2026-11-05` (người dùng đồng ý chuyển sang cờ, bỏ khóa ngoại) |
| Q6 | Biến thể có `version` riêng không | **Không.** Mọi lệnh biến thể tuần tự qua dòng sản phẩm nên `version` sản phẩm đủ; biến thể giữ `seller_id` để ràng buộc SKU | `2026-11-05` (theo đề xuất, người dùng không còn thắc mắc) |
| Q7 | Lệnh sửa sản phẩm: `PATCH` hay `PUT` | **`PATCH`** (ADR-0011 mục 4): trường vắng giữ nguyên; nếu gửi `attributes` thì thay **toàn bộ** danh sách giá trị | `2026-11-05` (theo đề xuất, người dùng không còn thắc mắc) |
| Q8 | Đối chiếu mã cũ | Theo quy tắc của feature 05: ghi `docs/draft/_so-sanh-catalog.md` (mục 15 trở đi), chỗ khớp thì hoàn lại và chỉnh tối thiểu, chỗ lệch ghi "theo mã" hay "theo tài liệu" | `2026-11-05` (theo đề xuất, người dùng không còn thắc mắc) |

## T1. Dữ liệu: migration `V2` · Dự kiến

`V2__create_product_variant.sql` thêm bảng; không sửa `V1`. Quy ước như `data.md`: khóa `UUID` v7, thời điểm `TIMESTAMPTZ`, trạng thái `VARCHAR(20)` kèm `CHECK`, ràng buộc có tên. **Không có khóa ngoại từ sản phẩm sang danh mục, thương hiệu, mẫu** (tham chiếu giữa aggregate bằng mã, kiểm ở ứng dụng; `data.md` mục 11).

| Bảng | Cột chính | Ràng buộc có tên |
|---|---|---|
| `product` | `id`, `seller_id` (chủ), `category_id`, `brand_id` (rỗng được), `title` (200), `description` (`TEXT`, rỗng được), `status`, `version`, `created_at`, `updated_at` | `pk_product`; `ck_product_status` (`DRAFT`, `PUBLISHED`, `UNPUBLISHED`; 07 chỉ ghi `DRAFT`); `ck_product_title_length` (5-200 ký tự); `ck_product_description_length` (≤ 5.000); `idx_product_seller` `(seller_id, created_at DESC, id)` |
| `product_attribute` | `product_id`, `template_id`, `axis` (`BOOLEAN`), `value` (`VARCHAR(255)`, rỗng được: giá trị rời của chữ, số, đúng/sai, ngày) | `pk_product_attribute (product_id, template_id)`; `fk_pa_product` → `product` (`CASCADE`, cùng aggregate); `ck_product_attribute_axis_no_value (NOT axis OR value IS NULL)` (trục là kiểu chọn, không có giá trị rời) |
| `product_attribute_option` | `product_id`, `template_id`, `option_id` | `pk` cả ba cột; `fk_pao_attribute (product_id, template_id)` → `product_attribute` (`CASCADE`) |
| `variant` | `id`, `product_id`, `seller_id` (sao từ sản phẩm, bất biến), `combination_key`, `price` (`BIGINT`), `sku` (50, rỗng được), `status`, `created_at`, `updated_at` | `pk_variant`; `fk_variant_product` → `product` (`RESTRICT`: xóa nháp ở 09 xóa biến thể tường minh trước); `uq_variant_product_combination (product_id, combination_key)`; `uq_variant_seller_sku (seller_id, sku) WHERE sku IS NOT NULL`; `ck_variant_price` (1 đến 1.000.000.000); `ck_variant_sku_length` (1-50); `ck_variant_status` (`ACTIVE`, `INACTIVE`); `idx_variant_product (product_id)` |
| `variant_combination_item` | `variant_id`, `template_id`, `option_id` | `pk (variant_id, template_id)`; `fk_vci_variant` → `variant` (`CASCADE`). **Không có khóa ngoại tới giá trị trục:** `INV-CAT-94` (tổ hợp nằm trong tập trục) và "không rút giá trị trục đang được biến thể dùng" (`INV-CAT-33`) do ứng dụng kiểm dưới khóa dòng sản phẩm (ADR-0012); thay toàn bộ danh sách thuộc tính là xóa rồi ghi lại các dòng của sản phẩm đó |

**Khóa tổ hợp** (`combination_key`): các mã tùy chọn của tổ hợp sắp theo **mã mẫu** tăng dần, nối bằng dấu phẩy (tối đa 73 ký tự). Sắp theo mã mẫu nên không phụ thuộc thứ tự trục khai (`INV-CAT-92`). Biến thể mặc định có khóa rỗng, nên một sản phẩm chỉ có một biến thể mặc định nhờ cùng ràng buộc.

**Giá trị thuộc tính.** Mỗi thuộc tính của sản phẩm một dòng ở `product_attribute`, cờ `axis` nói nó có là trục không (cờ là của cả cặp sản phẩm và mẫu nên cần dòng cha). Chọn một, chọn nhiều: mỗi tùy chọn đã chọn một dòng ở `product_attribute_option` (lưu **mã tùy chọn**, không lưu chữ, nên đổi tên hiển thị có hiệu lực ngay); với thuộc tính làm trục, các dòng này chính là tập giá trị của trục. Các kiểu còn lại ghi vào cột `value` của dòng cha. Chữ: nguyên văn đã cắt khoảng trắng; số: `BigDecimal` dạng chuỗi chuẩn (`toPlainString`, bỏ số 0 thừa, tối đa 4 chữ số thập phân); đúng/sai: `true` hoặc `false`; ngày: `yyyy`, `yyyy-MM` hoặc `yyyy-MM-dd` theo độ chính xác. Tìm kiếm và lọc theo giá trị là việc của Tìm kiếm (C1b, chỉ mục riêng), nên Danh mục không cần cột có kiểu.

**Phương án đã loại.**
- *Cột `value` chuỗi dùng chung cho cả tùy chọn lẫn giá trị rời* (mã cũ): chọn nhiều phải gộp khóa chính cả `value`; không phân biệt "mã tùy chọn" với "chữ trùng mã".
- *Bảng trục riêng (`product_axis`, `product_axis_value`) và khóa ngoại từ biến thể tới giá trị trục:* thêm hai bảng, một điểm cuối, một use case, hai nơi giữ cùng một danh sách giá trị (kéo theo câu hỏi thuộc tính bắt buộc đã làm trục có phải điền lại), để cơ sở dữ liệu ép thêm điều khóa dòng sản phẩm đã bảo đảm. Chọn cờ (Q9).
- *Khóa ngoại từ biến thể tới tùy chọn của sản phẩm:* buộc thay danh sách thuộc tính phải ghi theo kiểu so sánh cũ mới (không xóa-rồi-ghi-lại được); khóa dòng đã đủ.
- *JSONB một cột cho mọi giá trị thuộc tính:* khó bắt tính toàn vẹn (tùy chọn tồn tại), khó sửa từng phần.
- *Giữ cột `search_version` của mã cũ:* thay bằng `version` sản phẩm (ADR-0012 quyết định 4).
- *`ON DELETE CASCADE` từ sản phẩm xuống biến thể:* xóa nháp ở 09 không nên lặng lẽ xóa biến thể kéo theo; ở 09 xóa tường minh theo thứ tự.

## T2. Miền · Dự kiến

Theo mẫu feature 05: `domain/<aggregate>`, `Id` đóng gói UUID, `CatalogActor`. **Hành vi của sản phẩm nhận `Instant now`, không nhận `ChangeContext`:** 07 không phát event nên không cần `actor` và `eventId`; 09 thêm cùng event sẽ đổi chữ ký. `VariantUsage.of(biến thể của sản phẩm)` (thuần) cho biết có biến thể nào, có biến thể hoạt động không và giá trị trục nào đang được dùng, kể cả biến thể đã ngừng; lệnh sửa `attributes` và đọc chi tiết nạp biến thể qua `VariantRepository.findByProductId` (không cần cổng đọc riêng). **Hành vi của biến thể cũng nhận `Instant now`**, và lệnh biến thể chung một bước đầu (`VariantLocator`: kiểm vai, đọc khóa dòng sản phẩm của chính chủ). `PATCH` dùng `Patch<T>` (vắng, giá trị, `null`) cục bộ ở `application/product`. Use case chưa gắn `@Service` ở bước 4 (chưa có cài đặt lưu trữ); gắn ở bước 6 cùng bộ chuyển đổi. `CatalogActor` (thêm `requireSeller()`: vai `USER`), lỗi `CatalogErrorCode` hoặc mã riêng theo aggregate, `CatalogFieldException(field)` cho 400.

- **`Product`** (aggregate sản phẩm): `id`, `sellerId`, `categoryId`, `brandId`, `title`, `description`, `status`, `attributes` (giá trị thuộc tính; mỗi dòng có cờ `axis`), `version` (`Long`, `null` khi mới), thời điểm. Hành vi: `create` (kiểm tiêu đề, mô tả; trạng thái `DRAFT`, `version` null); `updateDetails(title?, description?, brandId?)`; `replaceAttributes(values, checker, hasVariants, usedAxisValues)` (thay toàn bộ; giá trị **không đổi** so với đã lưu được miễn kiểm; kiểm trục `INV-CAT-33`: tối đa 2, kiểu chọn một; khi đã có biến thể thì tập mẫu làm trục giữ nguyên và không rút giá trị đang được biến thể dùng, kể cả biến thể ngừng); `recordVariantChange(ctx)` (làm dòng sản phẩm "bẩn" để `@Version` tăng, và cập nhật `updatedAt`); `ownedBy(actor)`.
- **`Variant`** (aggregate biến thể): `id`, `productId`, `sellerId`, `combination` (bất biến), `price`, `sku`, `status`, thời điểm. Hành vi: `create`; `changePrice`; `changeSku`; `deactivate`; `activate` (lặp: `CATALOG_INVALID_STATE`). Không `version` (Q6).
- **Chính sách giá trị thuộc tính** (`AttributeValueChecker`, từ `AttributeConstraintChecker` cũ, phục hồi và chỉnh): đưa vào danh sách mẫu được gán (kèm `required`, `constraints`) và các tùy chọn, kiểm từng giá trị mới hoặc đổi theo kiểu nhập, tùy chọn đang dùng, `maxLength`, `min`, `max`, `integerOnly`, `maxSelections`, `inputPrecision`, `minDate`, `maxDate` (mốc tương đối `now`, `now-30d`, `now+1y`, `now-6m` do hàm này diễn giải; feature 05 lưu nguyên chuỗi). Giá trị đã lưu và không đổi bỏ qua (kiểu chọn nhiều: kiểm từng tùy chọn mới). Thuộc tính bắt buộc **không** kiểm ở đây vì nháp được soạn từng bước: `MissingPublishConditions` báo thiếu (dòng làm trục tính là đã điền). Lỗi `ProductAttributeException` nêu tên kỹ thuật của mẫu.
- **Chính sách tổ hợp** (`VariantCombinationPolicy`, phục hồi): kiểm tổ hợp có đúng một giá trị cho mỗi trục (thuộc tính `axis`), mỗi giá trị nằm trong tập giá trị của thuộc tính đó; tính khóa tổ hợp.
- **Giới hạn** (`ProductLimits`): `TITLE_MIN` 5, `TITLE_MAX` 200, `DESCRIPTION_MAX` 5.000, `MAX_AXES` 2, `MAX_VARIANTS` 50, `PRICE_MAX` 1.000.000.000, `SKU_MAX` 50, ràng buộc hằng số mặc định; ghi đè qua `catalog.limits.*` trong cấu hình, kiểm lúc khởi động: **chỉ siết được, không nới quá ràng buộc `V2`** (tiêu đề 5-200, mô tả 5.000, giá tối đa 1 tỷ, SKU 50, tối đa 2 trục; số biến thể tối đa nới được vì không có ràng buộc ở cơ sở dữ liệu). Giá trị sai làm dịch vụ không khởi động.
- **Giá:** số nguyên đồng (`long`), kiểm trong khoảng; kiểu chuyển từ JSON phải nguyên (số có phần thập phân là 400).
- **Mã lỗi** (409 trừ ghi chú): `PRODUCT_NOT_FOUND` (404, gồm sản phẩm của người khác), `PRODUCT_CATEGORY_NOT_USABLE`, `PRODUCT_BRAND_NOT_USABLE`, `PRODUCT_AXES_LOCKED`, `PRODUCT_AXIS_INVALID` (400), `PRODUCT_AXIS_VALUE_IN_USE`, `PRODUCT_ATTRIBUTE_INVALID` (400, kèm tên thuộc tính), `VARIANT_NOT_FOUND` (404), `VARIANT_COMBINATION_INVALID` (400), `VARIANT_COMBINATION_TAKEN`, `VARIANT_LIMIT_REACHED`, `VARIANT_SKU_TAKEN`; dùng lại `CATALOG_FIELD_INVALID`, `CATALOG_INVALID_STATE`, `CATALOG_ROLE_NOT_ALLOWED`, `CONCURRENT_UPDATE`. Chốt tên và trạng thái ở `data.md`, `api.yaml` (bước 2 của `plan.md`).

**Phương án đã loại.** *Một aggregate duy nhất chứa biến thể:* lý do tách (`variant.md` mục 1). *Domain event cho thay đổi sản phẩm ở 07:* không có bên nhận; 09 thêm cùng event trạng thái đầy đủ (`D6`).

## T3. Use case và khóa · Dự kiến

Lệnh ghi theo mẫu feature 05: `requireSeller` → kiểm chủ (đọc sản phẩm theo mã và chủ; không khớp: `PRODUCT_NOT_FOUND`) → kiểm sớm qua kho → lưu bằng `saveAndFlush`. Adapter ghi `@Transactional(MANDATORY)`.

| Use case | Đọc sản phẩm | Ghi |
|---|---|---|
| `CreateProduct` | không (tạo) | sản phẩm + giá trị thuộc tính |
| `UpdateProduct` | có `attributes`: `findOwnedForUpdate` (**khóa dòng**, vì có thể đổi trục); không có: `findOwned` (thường, không khóa) | `PATCH`: trường vắng giữ nguyên; `attributes` thay toàn bộ |
| `AddVariant` | `findOwnedForUpdate` (**khóa dòng**) | biến thể + `recordVariantChange` |
| `UpdateVariant` (giá, SKU) | `findOwnedForUpdate` | biến thể + `recordVariantChange` |
| `DeactivateVariant`, `ActivateVariant` | `findOwnedForUpdate` | biến thể + `recordVariantChange` |
| `GetMyProduct`, `ListMyProducts` | thường | không ghi |

- **Đọc cấu trúc cùng BC:** `CreateProduct`, `UpdateProduct` đọc danh mục (là lá, dùng được: chính nó và mọi tổ tiên đang dùng, `hasInactiveAncestor` của kho danh mục), thương hiệu, và mẫu của danh mục qua kho của feature 05 (chỉ đọc, không khóa).
- **Thứ tự trong lệnh biến thể:** khóa dòng sản phẩm → kiểm chủ → (với `AddVariant`) đếm biến thể và kiểm quy tắc → ghi biến thể → `recordVariantChange` → `saveAndFlush` cả hai. Một dòng sản phẩm được khóa mỗi giao dịch, nên không bế tắc (ADR-0012 quyết định 6).
- **Mẫu mới thêm vào danh mục (INV-CAT-97, 91):** kiểm "mẫu dùng được" lúc tạo hoặc đổi giá trị; sản phẩm đã có giá trị của mẫu về sau ngừng dùng được miễn kiểm (`INV-CAT-32`).
- **Điều kiện còn thiếu để đăng** (`RM-CAT-04`): hàm thuần `MissingPublishConditions` trong miền sản phẩm tính từ sản phẩm, biến thể và các mẫu bắt buộc của danh mục. 07 trả hai điều kiện (`REQUIRED_ATTRIBUTE_MISSING` kèm tên thuộc tính, `NO_ACTIVE_VARIANT`); 08 thêm ảnh, 09 thêm người bán chưa duyệt và danh mục không dùng được.
- **Danh sách:** `ListMyProducts` phân trang `(page, size ≤ 50)`, mới nhất trước, lọc theo trạng thái; đọc từ bảng, không qua miền; kèm số biến thể và khoảng giá cho trang danh sách.

**Phương án đã loại.** *Khóa lạc quan ép tăng cho lệnh biến thể:* ADR-0012. *Một lệnh "thay toàn bộ sản phẩm kèm biến thể":* đổi giá một biến thể sẽ tranh khóa với mọi thứ khác và API lớn khó dùng.

## T4. Dấu vết kiểm toán và hộp thư · Dự kiến

**Không có.** Hành động của người bán không phải quản trị nên không ghi `AuditActionRecorded` (`product.md` mục 6); `EVT-CAT-01`, `EVT-CAT-02` thuộc 09 (Q2). Test KT-07 xác nhận không có hàng `outbox_events` do 07 sinh ra. `AuditConfig` không thêm hành động.

## T5. API và bảo mật · Dự kiến

- Tất cả đường cần đăng nhập, `SecurityConfig` của feature 05 không đổi (`/api/**` bắt đăng nhập, đường khác từ chối). Vai kiểm trong use case: `CatalogActor.requireSeller()` (vai `USER`), 403 `CATALOG_ROLE_NOT_ALLOWED` kể cả với `ADMIN`. Chủ luôn lấy từ token.
- Đường (chi tiết trong `api.yaml`): `POST /api/seller/products`; `GET /api/seller/products` (phân trang, `?status=`); `GET /api/seller/products/{id}`; `PATCH /api/seller/products/{id}` (thông số kèm cờ `axis`, không có đường riêng cho trục); `POST /api/seller/products/{id}/variants`; `PATCH /api/seller/products/{id}/variants/{variantId}` (giá, SKU); `POST …/variants/{variantId}/deactivate`, `…/activate`. Không `DELETE`, không đường sửa tổ hợp.
- Mã sản phẩm hoặc biến thể không phải UUID: 400 (bộ xử lý lỗi chung); biến thể của sản phẩm khác: `VARIANT_NOT_FOUND`.
- Bộ xử lý lỗi ràng buộc (`PersistenceConflictExceptionHandler`) thêm ánh xạ: `uq_variant_product_combination` → `VARIANT_COMBINATION_TAKEN`, `uq_variant_seller_sku` → `VARIANT_SKU_TAKEN`; ràng buộc khác → 500 (để lộ).
- `GET` danh sách và chi tiết không lộ `sellerId` của người khác (chỉ trả sản phẩm của chính người gọi).

## T6. Kiểm chứng · Dự kiến

Test viết từ `flow.md` mục 4 trước khi viết mã; sau mỗi bước, phá thử (đột biến) và thấy test đỏ. Mức: đơn vị (miền, use case với kho giả), Testcontainers (migration, API, đua nhau, rollback), chạy thật (ngăn xếp cách ly). Danh sách KT-01 đến KT-09 ở `flow.md` mục 6.

Đột biến bắt buộc: bỏ `requireSeller`; bỏ kiểm chủ (đọc sản phẩm người khác); bỏ `FOR UPDATE` (ca vượt 50 đỏ); bỏ `recordVariantChange` (ca `version` đỏ); bỏ từng ràng buộc `V2`; bỏ lần kiểm giá trị trong tập trục; bỏ lần kiểm "không rút giá trị trục đang được biến thể dùng"; sửa `attributes` không khóa dòng (ca đổi trục đua với thêm biến thể đỏ); bỏ miễn kiểm giá trị đã lưu (ca "Quản trị tắt tùy chọn rồi người bán sửa tiêu đề" đỏ); bỏ lần đếm 50; làm mô tả `<script>` bị từ chối hay bị thay đổi.

Không viết lại mô tả quy tắc trong test: dựng từ hàm của miền và API (như feature 06).

## T7. Thứ tự làm · Dự kiến

1. Rà kịch bản test (đối chiếu E1 đến E23 với KT).
2. Đối chiếu mã cũ (Q8); `data.md`, `api.yaml`, chi tiết `service.md`.
3. Nền: hoàn lại thư mục sản phẩm và biến thể khỏi `//~` nhưng chỉ phần của 07 (miền, use case, lưu trữ, API); phần ảnh, bản chụp tìm kiếm, bộ nhớ đệm, sự kiện giữ `//~`; `V2`; `CatalogActor.requireSeller`.
4. Sản phẩm và giá trị thuộc tính (KT-01, KT-03 một phần).
5. Biến thể kèm khóa dòng, và kiểm trục khi sửa thuộc tính (KT-02, KT-03).
6. Lưu trữ, API, bộ xử lý lỗi, bảo mật (KT-04, KT-05, KT-07, KT-08).
7. Đua nhau trên Postgres thật (KT-06); ADR-0012 chuyển Accepted.
8. Chạy thật (KT-09).
9. Đóng: `deferred.md` (nửa "đã duyệt" của `INV-CAT-30` → 09; event → 09), `service.md`, `00-features.md`, `INV-CAT-98` nửa sản phẩm (thêm `hasProductReference` và lần kiểm trong `DeleteCategory`, ghi ở `05/deferred.md`).
