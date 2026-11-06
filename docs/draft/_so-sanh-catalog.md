# Đối chiếu `analysis/` Danh mục hàng với mã `catalog-service` (file tạm, bản 2)

> **Trạng thái:** file tạm `2026-10-23`, theo `work-process.md` mục 8: `analysis/` viết **trước**, rồi mới đối chiếu mã. Thay bản 1 (đối chiếu với mô hình nháp cũ). Dùng để lập danh sách việc sửa; xong việc thì bỏ file này. **Bản 3 (`2026-11-01`) thêm mục 7 đến 10 ở cuối: đối chiếu bổ sung và phương án triển khai feature 05.**
> Tài liệu chính thức không nhắc mã cũ. Dòng "chưa kiểm" là chỗ tôi chưa đọc đủ để khẳng định.
> **Phạm vi đã đọc:** miền (`Category`, `AttributeTemplate`, `Product`, `Variant`, `VariantCombinationPolicy`, `ProductAttributeValue`, `AttributeTemplateDomainService`), các use case `CreateProduct`, `PublishProduct`, `AddVariant`, `DeleteCategory`, `ReplaceCategoryAttributeAssignments`, `UpdateProduct`, `ConfirmProductImageUpload`, migration, danh sách use case, ràng buộc ở `presentation`. Chưa đọc: lớp persistence, cache, controller chi tiết, `inventory-service`.

Ký hiệu: **Khớp** · **Khác** (hai bên làm khác, cần chọn) · **Mã thiếu** · **Mã thêm** (mã có, `analysis/` chưa có).

## 1. Hiện trạng mã (tóm tắt)

224 tệp Java, kiến trúc DDD/hexagonal đúng quy ước; 19 migration (thiếu V12); kiểm thử chỉ mức miền. Hạ tầng đã nối: hộp thư event (`outbox`), Redis, Kafka, MinIO. Mã đi **xa hơn C1a**: chặn sản phẩm, cấu hình khám phá, bộ nhớ đệm hai tầng, phát lại bản chụp tìm kiếm.

## 2. Đối chiếu theo aggregate

### 2.1. Danh mục — `AGG-CAT-01`

| Điểm | Mã | `analysis/` | Đánh giá |
|---|---|---|---|
| Cây ba cấp (`INV-CAT-01`) | `CategoryLevel` L1 đến L3, tạo cấp thứ tư báo `MAX_DEPTH_EXCEEDED` | Ba cấp | **Khớp** |
| Lá | **Chỉ cấp ba (L3)** nhận sản phẩm và gán thuộc tính (`CATEGORY_NOT_LEAF` khi `level != L3`) | "Lá là danh mục không có con" | **Khác (E1)**: danh mục cấp một hoặc hai chưa có con vẫn là "lá" theo phân tích nhưng mã không cho |
| Tên duy nhất trong cùng cha (`INV-CAT-02`) | Kiểm ở tầng ứng dụng, **không có ràng buộc cơ sở dữ liệu** (V18 ghi lý do: `parent_id` rỗng ở gốc); không bỏ dấu | Duy nhất sau chuẩn hóa (bỏ dấu, chữ thường) | **Mã thiếu**: đua đồng thời tạo trùng; chưa chuẩn hóa bỏ dấu |
| Thứ tự | `sortOrder`, `ReorderCategories` | Có | **Khớp** |
| Ngừng dùng | `deactivate()` **chỉ ẩn khỏi cây và chặn sản phẩm mới**, không đụng sản phẩm cũ, không cascade | Sản phẩm trong danh mục ngừng dùng **hết hiển thị** (`INV-CAT-95`) | **Khác (E7)** |
| "Dùng được" | Mình **và mọi tổ tiên** đang dùng (`hasInactiveAncestor` khi tạo sản phẩm); cha đang đóng vẫn tạo được con | Chỉ nói "đang dùng" | **Mã thêm (E7)**: quy tắc tổ tiên chưa có trong phân tích |
| Gán thuộc tính | `replaceAssignments` thay toàn bộ danh sách; chỉ L3; mỗi mẫu một lần; mẫu phải tồn tại và đang dùng | Như vậy, nhưng bỏ gán thuộc tính đã có sản phẩm dùng bị cấm (`INV-CAT-98`) | **Khác (E5)**: mã **cho** bỏ gán (sản phẩm cũ giữ giá trị, "chỉ giữ hoặc bỏ hẳn, không thêm") |
| Xóa | `DeleteCategory`: không còn con, không còn sản phẩm tham chiếu (race với `CreateProduct` được chấp nhận) | Như vậy (`INV-CAT-05`, `98`) | **Khớp**; không có khóa ngăn đua |
| Ràng buộc riêng theo danh mục khi gán | `AttributeConstraints` (min, max, độ dài, số lựa chọn tối đa, ngày) | "Ràng buộc riêng nếu có" | **Khớp** về ý; chi tiết sẽ vào `service.md` |
| Ảnh đại diện (`imageUrl`), `slug` | Có | Ảnh danh mục: cắt tạm | **Mã thêm (E10)** |
| Cấu hình khám phá (lọc, sắp xếp, trọng số) | `Discovery`, `FilterConfig`, `SortConfig` | C1b | **Mã thêm**, thuộc C1b |
| Cây lưu | Bảng bao đóng `category_closure` | Không nói | **Mã thêm**, hợp lý |

### 2.2. Mẫu thuộc tính — `AGG-CAT-02`

| Điểm | Mã | `analysis/` | Đánh giá |
|---|---|---|---|
| Kiểu nhập | **Sáu kiểu**: chọn một, chọn nhiều, chữ, số, **đúng/sai**, **ngày** | Bốn kiểu; đúng/sai, ngày là cắt tạm | **Khác (E2)** |
| Đơn vị | Chỉ kiểu số có đơn vị, bất biến | Có (kiểu số) | **Khớp** |
| Tên duy nhất (`INV-CAT-10`) | `UNIQUE (name)` đúng chữ, không bỏ dấu | Sau chuẩn hóa | **Mã thiếu** một phần |
| Tùy chọn duy nhất trong mẫu (`INV-CAT-11`) | Chỉ mục `(template_id, LOWER(value))` | Có | **Khớp** (chưa bỏ dấu) |
| Mẫu chọn cần tùy chọn | `isUsable()`: ACTIVE và (kiểu chọn thì còn ít nhất một tùy chọn đang dùng) | Mẫu chọn có ít nhất một tùy chọn khi gán | **Khớp**; mã thêm định nghĩa "dùng được" dùng khi kiểm bắt buộc |
| Kiểu nhập bất biến | `inputType` `final` | `INV-CAT-12` | **Khớp** |
| Ngừng dùng thay xóa | **Không có use case xóa** mẫu, tùy chọn | "Xóa chỉ khi chưa dùng" | **Khác (E6)**: mã chỉ ngừng dùng |
| Đổi tên hiển thị | Áp ngay mọi nơi (giá trị lưu bằng mã tùy chọn) | Có | **Khớp** |
| Sắp xếp tùy chọn | `reorderOptions` | Có thứ tự | **Khớp** |
| Giới hạn mặc định | Chữ tối đa 255 ký tự; số tối đa 4 chữ số thập phân; ngày `yyyy`, `yyyy-MM`, `yyyy-MM-dd` | Không nói | **Mã thêm**, chi tiết cho `data.md` |

### 2.3. Thương hiệu — `AGG-CAT-03`

| Điểm | Mã | `analysis/` | Đánh giá |
|---|---|---|---|
| Bắt buộc hay tùy chọn | **Bắt buộc**: `brandId` `@NotBlank`, `publish` đọc `brandId.getValue()` | Tùy chọn (D4 đã chốt) | **Khác**: phải sửa mã |
| Tên duy nhất | `UNIQUE (LOWER(name))`, không bỏ dấu | Sau chuẩn hóa | **Khớp** một phần |
| Slug | Do người gọi nhập (`@NotBlank slug`) | Không nói | **Mã thêm** |
| Xóa | Không có use case xóa | Xóa khi chưa dùng | **Khác (E6)** |
| Chọn thương hiệu đã ngừng | Chặn khi tạo sản phẩm; sản phẩm cũ giữ | `INV-CAT-21` | **Khớp** |

### 2.4. Sản phẩm — `AGG-CAT-04`

| Điểm | Mã | `analysis/` | Đánh giá |
|---|---|---|---|
| Trạng thái | `DRAFT`, `PUBLISHED`, `UNPUBLISHED` + cờ `adminBlocked` riêng | Ba trạng thái; chặn là điều kiện riêng (C1b) | **Khớp** |
| Tiêu đề, mô tả | Chỉ `@NotBlank` cho tên; **không giới hạn độ dài**, không kiểm chữ thuần | 5 đến 200; 5.000; chữ thuần (`INV-CAT-31`) | **Mã thiếu** |
| Danh mục của sản phẩm | Chọn lúc tạo; **`update` không có danh mục**, không đổi được | Đổi được khi Nháp, khóa sau lần đăng đầu | **Khác (E8)** |
| Giá trị thuộc tính (`INV-CAT-32`) | `validateProductAttributes`: thuộc tính phải thuộc danh mục; kiểu đúng; tùy chọn đang dùng; ràng buộc theo danh mục | Có | **Khớp** |
| Giá trị đã lưu được giữ nguyên | **Miễn kiểm lại** giá trị giống hệt bản cũ (mẫu hay tùy chọn bị tắt sau đó, thuộc tính bị gỡ khỏi danh mục) | Không nói | **Mã thêm (E4)**: quy tắc nghiệp vụ tốt, người bán không bị kẹt khi sửa phần không liên quan |
| Thuộc tính bắt buộc | Kiểm **lúc tạo và sửa**, và **chỉ khi mẫu còn dùng được**; không kiểm lúc đăng | Kiểm lúc đăng (`INV-CAT-91`); nháp thiếu được | **Khác (E9)**; nuance "chỉ khi mẫu còn dùng được" là **Mã thêm (E4)** |
| Trục biến thể | Sản phẩm đánh dấu `isVariantDefining`; **chỉ kiểu chọn một** (`VARIANT_DEFINING_REQUIRES_SELECT`) | "Kiểu chọn (một hoặc nhiều)" | **Phân tích sai (E3)**: mỗi biến thể nhận đúng một giá trị trục nên chỉ chọn một |
| Tập giá trị của trục | Giá trị khai ở sản phẩm là tập; biến thể phải thuộc tập (`VARIANT_OPTION_NOT_DECLARED_BY_PRODUCT`) | Có (`INV-CAT-94`) | **Khớp** |
| Tối đa 2 trục | **Không giới hạn số trục** | Tối đa 2 (giả định, `Q-CAT-01`) | **Mã thiếu** |
| Khóa trục khi đã có biến thể; không rút giá trị đang dùng | `VARIANT_DEFINING_LOCKED_AFTER_VARIANT`, `ATTRIBUTE_VALUE_STILL_USED_BY_VARIANT` | `INV-CAT-33` | **Khớp** |
| Ảnh | Tải qua URL ký sẵn của MinIO rồi xác nhận; **không thấy giới hạn số ảnh, loại, dung lượng**; không có sắp xếp thứ tự (chưa kiểm) | 9 ảnh, loại, dung lượng, thứ tự, ≥1 ảnh khi đăng | **Mã thiếu** |
| Ảnh phải thuộc chính sản phẩm | Khóa đối tượng chứa mã sản phẩm | `INV-CAT-34` | **Khớp** (chưa kiểm khi xác nhận) |
| Điều kiện đăng (`INV-CAT-91`) | `PublishProduct`: có **ít nhất một biến thể hoạt động**, thương hiệu tồn tại, không bị chặn | Thêm: người bán đã duyệt, danh mục còn dùng, thuộc tính bắt buộc đủ, ≥1 ảnh | **Mã thiếu** (và không báo đủ điều kiện còn thiếu một lượt) |
| Hiển thị | `isPubliclyVisible = PUBLISHED && !adminBlocked` | Thêm: người bán còn được duyệt, danh mục dùng được | **Mã thiếu** hai điều kiện |
| Xóa | Chỉ `DRAFT` (`markDeleted`) | `INV-CAT-35` | **Khớp** |
| Chủ sản phẩm | `assertOwnedBy(sellerId)`; trả `NOT_PRODUCT_OWNER` | Trả như thể không tồn tại | **Khác nhỏ**: mã lộ sự tồn tại của sản phẩm người khác |
| Phiên bản | Bản chụp tìm kiếm mang `searchVersion`; `AddVariant` khóa dòng sản phẩm (`findByIdForUpdate`); chưa thấy khóa lạc quan khi sửa sản phẩm | `INV-CAT-36`, hai lần sửa đồng thời không ghi đè âm thầm | **Chưa kiểm** khóa lạc quan |
| Bảo hành | `WarrantyInfo` (tháng, loại, phạm vi) | Cắt tạm | **Mã thêm (E10)** |

### 2.5. Biến thể — `AGG-CAT-05`

| Điểm | Mã | `analysis/` | Đánh giá |
|---|---|---|---|
| Aggregate riêng | `Variant` có `VariantRepository`, khóa dòng sản phẩm khi thêm biến thể | Có (D5) | **Khớp**; tổ hợp duy nhất ép bằng `UNIQUE (product_id, combination_hash)` |
| Tổ hợp bất biến | Trường `final` | `INV-CAT-41` | **Khớp** |
| Giá | `long`, `price > 0` (kiểm miền và ràng buộc cơ sở dữ liệu); **không có trần** | Số nguyên đồng, >0, ≤ 1 tỷ | **Mã thiếu trần** |
| SKU | `skuCode` tự đặt; **không giới hạn độ dài, không duy nhất theo người bán** | 1 đến 50 ký tự, duy nhất theo người bán | **Mã thiếu** |
| Tối đa 50 biến thể | Không giới hạn | `INV-CAT-93` | **Mã thiếu** |
| Biến thể mặc định cho sản phẩm không trục | Không thấy xử lý riêng: tổ hợp rỗng có thể tạo (chưa kiểm) | `INV-CAT-44` | **Chưa kiểm** |
| Xóa biến thể | **Không có use case xóa** (xóa sản phẩm nháp xóa theo) | Xóa được khi sản phẩm chưa từng đăng | **Khác (E6)** |
| Bật, tắt | `ActivateVariant`, `DeactivateVariant` | Có | **Khớp** |
| Giá gốc | Cột `original_price` ở cơ sở dữ liệu nhưng miền không có trường | Không | **Mã thêm** (cột thừa, chưa dùng) |
| Ảnh riêng biến thể | `SkuImage` | Cắt tạm | **Mã thêm (E10)** |

### 2.6. Quy tắc xuyên aggregate (`INV-CAT-91..98`)

| Quy tắc | Mã | Đánh giá |
|---|---|---|
| `91` Điều kiện đăng | Một phần (xem 2.4) | **Mã thiếu** |
| `92` Tổ hợp duy nhất | Ràng buộc duy nhất ở cơ sở dữ liệu cộng `existsByProductIdAndCombinationHash` | **Khớp** |
| `93` Tối đa 50 biến thể | Không | **Mã thiếu** |
| `94` Giá trị trục thuộc tập đã chọn, đủ mọi trục | `VariantCombinationPolicy` | **Khớp** |
| `95` Hiển thị | Thiếu hai điều kiện | **Mã thiếu** |
| `96` SKU duy nhất theo người bán | Không | **Mã thiếu** |
| `97` Sản phẩm chỉ ở lá; danh mục có sản phẩm không thêm con | Có chiều thứ nhất (L3 và đang dùng). Chiều thứ hai: chưa thấy; ở L3 không thêm con được (sâu tối đa) nên chiều này chỉ cần với phương án lá = không có con (E1) | **Khớp** với mã nếu chọn E1 theo mã |
| `98` Đã dùng thì không xóa | Danh mục: có. Mẫu, tùy chọn, thương hiệu: **không có xóa nào** | **Khác (E6)** |

### 2.7. Sự kiện

| Điểm | Mã | `analysis/` | Đánh giá |
|---|---|---|---|
| Cơ chế phát | `OutboxEventStore.store`, Debezium đẩy lên Kafka | Hộp thư event cùng giao dịch | **Khớp** (chưa kiểm "cùng giao dịch" ở mọi use case: `PublishProduct` phát sau khi lưu) |
| Bản chụp trạng thái | `ProductSearchSnapshotEvent`: toàn bộ trạng thái, `searchVersion`, khóa theo mã sản phẩm; có `adminBlocked`, thuộc tính (`filterable`, `sortable`, `searchWeight`), biến thể (giá, hoạt động, tổ hợp) | `EVT-CAT-01` (trạng thái đầy đủ kèm cờ hiển thị và phiên bản) | **Khớp mạnh**; mã mang `adminBlocked` thay vì cờ hiển thị đã tính, và cần thêm các điều kiện hiển thị mới |
| Sự kiện nhỏ | `ProductPublished`, `Unpublished`, `Updated`, `Blocked`, `Unblocked`, `Deleted`, `Variant{Created, PriceChanged, Activated, Deactivated, Deleted}`, `CategoryUpdated` cạnh bản chụp | `EVT-CAT-02` đơn vị bán được tạo, đổi trạng thái, bỏ (không mang giá); bản chụp `EVT-CAT-01` | **Khác (D6)**: `inventory-service` nghe tám chủ đề nhỏ; chốt ở khâu thiết kế (đã định) |
| Sự kiện danh mục | `CategoryUpdatedEvent` | `EVT-CAT-03` | **Khớp** |
| Sự kiện kiểm toán | Không có | `EVT-CAT-04` | **Mã thiếu** |
| Sự kiện đầu vào | Không nhận sự kiện nào | `EVT-SEL-01`, `EVT-SEL-02` (bản sao trạng thái người bán) | **Mã thiếu** |

## 3. Hạ tầng và chất lượng (giữ từ bản 1)

| Điểm | Mã | Đánh giá |
|---|---|---|
| Danh tính, vai | API người bán nhận `X-Seller-Id`, chưa kiểm vai (trạng thái tạm có chủ đích, chưa có phân hệ người bán) | **Chờ thay** bằng danh tính từ token cộng chú thích kiểm vai (quyết định `2026-10-22`) |
| Mã định danh | ULID | **Khác ADR-0001 (UUID v7)**; chi tiết ở khâu triển khai (D3) |
| Bộ nhớ đệm hai tầng | Có | **Mã thêm**, thuộc C1b, C5 |
| `DevToolController` | Trong mã chính, chưa thấy khóa theo profile | **Cần kiểm** |
| Kiểm thử | Mức miền; không test API, tích hợp, đồng thời | **Mã thiếu**: chưa chứng minh các bất biến đồng thời (tổ hợp duy nhất, tên duy nhất) |
| Migration | 19 tệp, nhiều lần sửa lại cấu trúc, thiếu V12 | Gộp thành bản gốc sạch (D8) |
| Mã bất biến trong chú thích mã | Dùng `INV-CAT-04x` đến `9x`, `AGG-CAT-0x` của tài liệu cũ | Đánh số lại theo `analysis/` (D9) |

## 4. Điểm cần quyết (E1 đến E10)

**Đã chốt `2026-10-23`** và đã sửa `analysis/` tương ứng. Kết quả: E1–E6, E8 theo mã (đã sửa phân tích); E9 theo phân tích (thuộc tính bắt buộc kiểm lúc đăng, mã cần sửa); **E7 theo mã** (ngừng dùng danh mục chỉ đóng nhận sản phẩm mới, sản phẩm đã đăng vẫn hiển thị; thêm quy tắc "dùng được" = mình và mọi tổ tiên); **E10 cắt tạm cả ba** (bảo hành, ảnh danh mục, ảnh biến thể, mã giữ nguyên, không đưa vào phạm vi C1a).

Phát sinh khi đối chiếu `analysis/` mới với mã. Mỗi điểm cho hai hướng: **sửa phân tích** (theo mã) hoặc **sửa mã** (theo phân tích).

| # | Câu hỏi | Mã | Phân tích | Đề xuất |
|---|---|---|---|---|
| E1 | "Lá" là gì | Chỉ cấp ba | Mọi danh mục không có con | **Theo mã**: sản phẩm và gán thuộc tính chỉ ở cấp ba. Khớp cây ba cấp cố định (D7), đơn giản hơn, bỏ chiều "thêm con vào danh mục có sản phẩm" của `INV-CAT-97` |
| E2 | Kiểu nhập của mẫu thuộc tính | Sáu (thêm đúng/sai, ngày) | Bốn | **Theo mã**: giữ sáu; đúng/sai và ngày hữu ích cho thông số (Bảo hành, Ngày sản xuất) |
| E3 | Trục biến thể là kiểu chọn nào | Chỉ chọn một | Chọn một hoặc nhiều | **Theo mã**: chọn một (phân tích sai: mỗi biến thể nhận đúng một giá trị trục) |
| E4 | Giá trị đã lưu có phải kiểm lại khi sửa phần không liên quan | Miễn kiểm lại; bắt buộc chỉ ép khi mẫu còn dùng được | Không nói | **Theo mã**: bổ sung vào `analysis/` (lý do nghiệp vụ: người bán không bị kẹt vì Quản trị tắt mẫu hay tùy chọn sau khi sản phẩm tạo) |
| E5 | Bỏ gán thuộc tính đã có sản phẩm dùng | Cho; sản phẩm giữ giá trị | Cấm (`INV-CAT-98`) | **Theo mã**: cho, sản phẩm cũ giữ giá trị, chỉ giữ hoặc bỏ hẳn |
| E6 | Xóa mẫu, tùy chọn, thương hiệu; xóa biến thể nháp | Không có xóa nào (chỉ ngừng dùng); biến thể xóa theo sản phẩm nháp | Xóa khi chưa dùng | **Theo mã**: bỏ "xóa" khỏi `analysis/` cho ba thứ trên và biến thể; chỉ danh mục và sản phẩm nháp xóa được |
| E7 | Danh mục ngừng dùng có ẩn sản phẩm cũ không; tổ tiên tắt | Không ẩn sản phẩm cũ; "dùng được" = mình và mọi tổ tiên | Ẩn (`INV-CAT-95`); chưa nói tổ tiên | **Theo phân tích** cho phần ẩn (ngừng dùng nghĩa là sàn không còn muốn bán nhóm đó), **bổ sung** quy tắc tổ tiên vào `analysis/`; sửa mã. Cần bạn xác nhận vì ngừng một danh mục cha sẽ ẩn cả nhánh |
| E8 | Đổi danh mục của sản phẩm Nháp | Không có | Có | **Theo mã**: bỏ khỏi `analysis/` (di chuyển là cắt tạm hoàn toàn) |
| E9 | Thuộc tính bắt buộc kiểm lúc nào | Lúc tạo, sửa | Lúc đăng | **Theo phân tích**: lúc đăng (nháp soạn từng bước); sửa mã. Vẫn nên giữ kiểm định dạng ngay khi nhập |
| E10 | Bảo hành, ảnh danh mục, ảnh riêng biến thể | Đã có mã | Cắt tạm | **Bảo hành** nhận vào Sản phẩm (nghiệp vụ hợp lý, mã có sẵn); **ảnh danh mục, ảnh biến thể** giữ cắt tạm, mã giữ nguyên nhưng không đưa vào phạm vi C1a |

## 5. Danh sách việc sửa mã (sau khi chốt)

| Ưu tiên | Việc | Nguồn |
|---|---|---|
| 1 | Bỏ `X-Seller-Id`, lấy danh tính từ token; chú thích kiểm vai `ADMIN` cho `/api/admin/**` và kiểm người bán đã duyệt cho `/api/seller/**` | 3 |
| 1 | Nhận `EVT-SEL-01`, `EVT-SEL-02`, dựng bản sao trạng thái người bán (mặc định chưa duyệt) | 2.7 |
| 1 | Điều kiện đăng đầy đủ và báo đủ điều kiện còn thiếu; điều kiện hiển thị mới | 2.4, 2.6 |
| 1 | Phát `EVT-CAT-04` (hành động quản trị) cho mọi thao tác Quản trị | 2.7 |
| 2 | Thương hiệu tùy chọn | 2.3 |
| 2 | Giới hạn: tiêu đề, mô tả, số ảnh, loại và dung lượng ảnh, trần giá, độ dài SKU, tối đa 50 biến thể, tối đa 2 trục | 2.4, 2.5 |
| 2 | Ràng buộc duy nhất: tên danh mục trong cha (cơ sở dữ liệu), tên mẫu, tên thương hiệu sau chuẩn hóa bỏ dấu, SKU theo người bán | 2.1 đến 2.5 |
| 2 | Chốt mã định danh (D3) và sự kiện nhỏ so với bản chụp (D6) | 3, 2.7 |
| 2 | Test tích hợp và đồng thời (Testcontainers đã khai báo, chưa dùng) | 3 |
| 3 | Kiểm thuộc tính bắt buộc lúc đăng thay vì lúc tạo, sửa (E9); chặn đăng lại sản phẩm đã gỡ khi danh mục không dùng được (E7) | 2.4 |
| 3 | Sửa lộ sự tồn tại của sản phẩm người khác (trả như không tồn tại) | 2.4 |
| 3 | Khóa `DevToolController` theo profile; gộp migration; đánh số bất biến (D8, D9) | 3 |
| 3 | Gỡ cột `original_price` thừa | 2.5 |

## 6. Việc sửa `analysis/` (sau khi chốt E1 đến E10)

| Việc | File |
|---|---|
| Lá = cấp ba; bỏ chiều thêm con vào danh mục có sản phẩm | `category.md`, `0.overview.md` (`INV-CAT-97`) |
| Kiểu nhập sáu loại | `attribute-template.md` |
| Trục chỉ kiểu chọn một | `attribute-template.md`, `product.md` (`INV-CAT-33`) |
| Giữ nguyên giá trị đã lưu; bắt buộc chỉ ép khi mẫu còn dùng được | `product.md`, `0.overview.md` (`INV-CAT-91`) |
| Bỏ gán thuộc tính đã dùng được phép; bỏ "xóa" mẫu, tùy chọn, thương hiệu, biến thể | `category.md`, `attribute-template.md`, `brand.md`, `variant.md`, `0.overview.md` (`INV-CAT-98`) |
| Quy tắc tổ tiên của "dùng được"; cha đóng vẫn tạo con | `category.md`, `0.overview.md` (`INV-CAT-95`, `97`) |
| Bỏ đổi danh mục của sản phẩm Nháp | `product.md` |
| Thêm bảo hành vào Sản phẩm | `product.md`, `0.overview.md` (bỏ khỏi Chưa làm) |

---

# Bản 3 (`2026-11-01`): phương án triển khai, mở đầu bằng feature 05

> Bản 2 ở trên đã đọc miền và use case nhưng **chưa đọc** lưu trữ, bộ nhớ đệm, controller, cấu hình, migration, test. Bản 3 đọc nốt phần đó cho phạm vi danh mục, mẫu thuộc tính, thương hiệu (khoảng 110 trong 245 tệp nguồn), rồi đặt phạm vi đó vào bức tranh cả dịch vụ để không làm lệch phần còn lại. Tài liệu phân tích cũ vẫn còn ở `docs/service/catalog-service/old/` (`analysis.md`, `service.md`, `data.md`, `api.yaml`, `cache.md`, `downstream-effects.md`); dùng làm lịch sử quyết định (T-01 đến T-10, C-01 đến C-07, TQ-xx), không làm nguồn đúng.

## 7. Đối chiếu bổ sung cho phạm vi feature 05

| # | Điểm | Mã | `analysis/` và `service.md` mới | Hành động |
|---|---|---|---|---|
| 7.1 | Mã định danh | `String` ULID (`ULIDGenerator`), cột `VARCHAR(26)`, mọi `*Id` bọc `String` | UUID v7, kiểu `uuid`, cổng `IdGenerator` (đã có ở `common-domain`, 4 service khác đang dùng) | Đổi toàn bộ kiểu mã của miền và lưu trữ |
| 7.2 | Tên duy nhất | Danh mục: chỉ kiểm ở ứng dụng bằng `equalsIgnoreCase` trên **toàn bộ anh em** (không chỉ mục); mẫu: `UNIQUE(name)` đúng chữ; thương hiệu: `UNIQUE(LOWER(name))`; tùy chọn: `UNIQUE(template, LOWER(value))`. Không bỏ dấu | Khóa tên chuẩn hóa (bỏ dấu, chữ thường), chỉ mục duy nhất làm chốt cuối cho cả bốn (INV-CAT-02, 10, 11, 20) | Thêm cột khóa tên; chỉ mục duy nhất, riêng danh mục cấp một cần chỉ mục từng phần (`parent_id` rỗng); hàm chuẩn hóa dùng `StringUtils.removeAccent` |
| 7.3 | Cây lưu | `parent_id` + `level` + bảng bao đóng `category_closure` (ba lệnh SQL thuần để chèn, hai lớp JPA); chỉ dùng để tìm tổ tiên cho "dùng được" | Cây cố định ba cấp, cha không đổi | Xem câu hỏi A bên dưới |
| 7.4 | Slug, ảnh đại diện, thương hiệu có slug bắt buộc | Có ở danh mục (`slug`, `image_url`) và thương hiệu (`slug`, `@NotBlank`, bất biến, duy nhất) | Chỉ tên và trạng thái; đường dẫn thân thiện, ảnh danh mục: cắt tạm | Bỏ khỏi miền, lưu trữ, API, test |
| 7.5 | Cấu hình khám phá | `Discovery`, `FilterConfig`, `SortConfig` nằm trong dòng gán thuộc tính (jsonb) | C1b, "thêm vào gán thuộc tính" | Bỏ ở C1a. `AttributeConstraints` (ràng buộc riêng theo danh mục) giữ vì analysis có; bộ kiểm giá trị `AttributeConstraintChecker` thuộc về kiểm giá trị sản phẩm nên đi cùng feature 07 |
| 7.6 | Gán thuộc tính | Thay toàn bộ danh sách trong một lần; mẫu **mới thêm** phải dùng được, mẫu đã gán nay ngừng dùng vẫn giữ và sửa được; trùng mẫu trong yêu cầu: lỗi; chỉ cấp ba | Cùng ý (`service.md`: "thay toàn bộ danh sách một lần") | Giữ nguyên hành vi; chỉnh `flow.md` của feature 05 cho khớp (đã sửa) |
| 7.7 | Event | Chỉ `CategoryUpdatedEvent` (mã danh mục, không phiên bản, `catalog.category.updated`) qua bộ xử lý hộp thư. Mẫu, tùy chọn, thương hiệu **không** phát gì. Không có `EVT-CAT-04` | `EVT-CAT-03` (mã, phiên bản) cho danh mục và mẫu; `EVT-CAT-04` cho mọi thao tác quản trị | Cơ chế (sự kiện miền, `EventDispatcher`, bộ xử lý ghi hộp thư) giữ nguyên; thêm phiên bản, thêm sự kiện cho mẫu, thêm bộ xử lý kiểm toán như feature 04 |
| 7.8 | Phiên bản | Chỉ `AttributeTemplate` có `@Version`; danh mục và thương hiệu không | Mọi aggregate có `version` tăng mỗi thay đổi | Thêm cho danh mục, thương hiệu; `EVT-CAT-03` mang phiên bản |
| 7.9 | Vai và đăng nhập | `SecurityConfig`: `permitAll` cho đọc cây, thuộc tính, thương hiệu, sản phẩm; mọi đường quản trị chỉ cần đăng nhập, **không kiểm vai** (ghi chú chờ phân quyền) | Quản trị ghi: `ADMIN`; đọc cây, thuộc tính, thương hiệu: đã đăng nhập (`RM-CAT-01` đến `03`) | Kiểm vai theo Q2 của `flow.md`; đọc phải đăng nhập (xem câu hỏi C) |
| 7.10 | Bộ nhớ đệm | Hai tầng (Caffeine + Redis + pub/sub) cho cây, thuộc tính, sản phẩm; `@CacheEvict` trong khoảng 25 use case; có sửa lỗi tuần tự hóa từ `2026-09-16` | "Chưa cần Redis ở C1a (bộ nhớ đệm là C1b, C5)" (`service.md` mục 8) | Bỏ khỏi dịch vụ ở C1a (cấu hình, gói `cache`, chú thích, phụ thuộc Redis và Caffeine). Làm lại khi có số đo tải |
| 7.11 | Xung đột đua nhau | `PersistenceConflictExceptionHandler`: mọi `DataIntegrityViolation` thành 409 một thông điệp chung, không mã lỗi | Mỗi vi phạm một mã lỗi riêng (tên trùng, ...) | Ánh xạ tên ràng buộc sang mã lỗi; giữ 409 |
| 7.12 | Xóa danh mục | `DeleteCategory` hỏi bảng sản phẩm qua `ProductJpaRepository` ngay trong bộ chuyển đổi lưu trữ của danh mục; khóa ngoại cha `RESTRICT` bắt đua nhau thêm con | Không có con, chưa từng có sản phẩm (INV-CAT-05, 98) | Khi bảng sản phẩm chưa có (xem 8), chỉ kiểm con; thêm kiểm sản phẩm ở feature 07 và ghi vào `deferred.md` |
| 7.13 | Mã lỗi và trạng thái HTTP | Mã số (`20201`...) và `422` cho quy tắc nghiệp vụ (quá cấp, không phải lá, ràng buộc sai), `409` cho trùng/có con | Seller dùng `409` cho trạng thái, `400` cho trường; mã chữ theo tên hằng | Chốt ở `tech.md` (nghiêng về thống nhất với seller) |
| 7.14 | Kiểm thử | Sáu lớp miền liên quan (`CategoryTest`, `AttributeTemplateTest`, `BrandTest`, ...) cộng `CreateAttributeTemplateTest`, `BrandNameUniqueTest`; không có test API, hộp thư, đồng thời, migration | R10, R12 phải kiểm trên cơ sở dữ liệu thật | Giữ và chỉnh test miền; viết mới test use case, API, đua nhau, hộp thư, kiểm toán, migration (Testcontainers) |
| 7.15 | Hộp thư event | Bảng `outbox_events` trong V1 theo hình cũ; thư viện `outbox-starter` đã đổi hình bảng (có `event_id`, khóa duy nhất) | Hình bảng chung của thư viện | Bản gốc sạch lấy hình mới từ `outbox-schema.sql`, không giữ hình cũ |
| 7.16 | `DevToolController` | Lệnh tải ảnh mẫu, không khóa theo profile | | Thuộc nhóm ảnh, vào lại ở feature 08 (hoặc bỏ) |

## 8. Cả dịch vụ: mỗi khu vực mã đi đâu

| Khu vực mã | Cỡ | Số phận | Feature |
|---|---|---|---|
| Danh mục, mẫu thuộc tính, thương hiệu (miền, use case, lưu trữ, API) | ≈ 110 tệp | **Giữ làm nền, chỉnh theo bảng 7** | 05 |
| Sản phẩm, biến thể (miền, use case, lưu trữ, API, tám bộ sự kiện nhỏ, quy tắc kiểm giá trị thuộc tính) | ≈ 85 tệp | Chưa chạm ở 05. Vào lại ở 07, 08 sau đối chiếu với `product.md`, `variant.md` (còn các lệch E4, E8, E9 và thiếu giới hạn ở bảng 2.4 đến 2.6) | 07, 08 |
| Bản chụp tìm kiếm, phát lại (`search_sync`, `SearchReplayController`) | ≈ 6 tệp | Ý tưởng giữ (`EVT-CAT-01` là bản chụp đầy đủ kèm phiên bản); vào lại cùng sản phẩm | 07 |
| Kho đối tượng ảnh (`MinioConfig`, `MinioObjectStorage`), `DevToolController` | ≈ 6 tệp | Vào lại cùng ảnh sản phẩm | 08 |
| Bộ nhớ đệm hai tầng | ≈ 5 tệp + chú thích rải | Bỏ ở C1a | C1b, C5 |
| Nhận event người bán (bản sao trạng thái) | chưa có | Viết mới | 09 |
| `SecurityConfig`, `PersistenceConflictExceptionHandler`, `IdGeneratorConfig`, `EventDispatcherConfig`, `JpaConfig`, `application.properties` | 6 tệp | Viết lại hoặc chỉnh | 05 |

**Điểm chạm làm 05 không thể "chỉ một phần":** bản gốc sạch đổi **kiểu mã cho mọi bảng** (D3, D8) và hình bảng hộp thư; cấu hình `ddl-auto=validate` buộc thực thể JPA khớp từng cột. Nghĩa là mọi thực thể sản phẩm và biến thể còn trong cây mã sẽ không khởi động được nếu bảng của chúng không có hoặc khác kiểu. Hai cách:

| | **Cách P: tạm đưa phần sản phẩm ra khỏi cây mã** | **Cách K: giữ cả dịch vụ biên dịch được** |
|---|---|---|
| Việc ở 05 | Gỡ sản phẩm, biến thể, `search_sync`, ảnh, bộ nhớ đệm khỏi cây (còn nguyên trong git); bản gốc sạch chỉ có bảng của 05 và hộp thư; sản phẩm thêm bảng bằng `V2` ở feature 07 | Đổi kiểu mã cho sản phẩm, biến thể (≈ 85 tệp, cơ học) và viết bảng sản phẩm, biến thể, ảnh vào bản gốc sạch **ngay** |
| Ưu | 05 nhỏ, kiểm chứng được trọn; bảng sản phẩm quyết khi làm 07 với `product.md` trong tay; không phải dựng Redis, MinIO để chạy thật | Không mất thứ gì khỏi cây |
| Nhược | Phải lấy lại mã từ git ở 07, 08 (`git show` theo mã commit; nếu bạn commit trước khi làm 07 thì lấy từ commit trước đó) | Quyết trước hình bảng sản phẩm khi `product.md` chưa được đối chiếu hết (E4, E8, E9 còn lệch); phải giữ Redis và MinIO sống để dịch vụ khởi động, mã sản phẩm chưa kiểm vẫn nằm trong cây |
| Lịch sử migration | `V1` (cấu trúc), `V2` (sản phẩm, biến thể), `V3` (ảnh): mỗi bản đúng hình cuối, không `ALTER` hay `DROP` chắp vá, đúng tinh thần D8 | `V1` duy nhất |

**Nghiêng về cách P.** Lý do chính: mã sản phẩm hiện kỹ về trạng thái nhưng còn lệch `analysis/` ở nhiều chỗ; đổi kiểu mã cơ học trên đó rồi vẫn phải sửa lại ở 07 là làm hai lần, và buộc quyết hình bảng sản phẩm sớm.

## 9. Phương án triển khai feature 05 (theo cách P)

1. **Dọn nền:** gỡ khu vực ngoài 05 khỏi cây (bảng 8); bỏ bộ nhớ đệm, Redis, Caffeine, MinIO, ULID khỏi `pom.xml` và cấu hình; thêm `audit-events`, `common-utils`.
2. **Bản gốc sạch `V1`** cho cấu trúc 05 và hộp thư (hình mới): `brand`, `attribute_template`, `attribute_option`, `category`, `category_attribute_assignment` (và `category_closure` nếu giữ); khóa `uuid`; cột khóa tên; chỉ mục duy nhất; `version`. Cơ sở dữ liệu dev cũ có lịch sử `V1` đến `V19` nên Flyway từ chối bản mới: **chạy thật dùng ngăn xếp cách ly `probe-` như feature 04**, cơ sở dữ liệu dev của catalog chỉ phải dựng lại khi bạn muốn chạy nó; tôi không chạm.
3. **Miền:** đổi kiểu mã sang UUID v7 qua `IdGenerator`; bỏ slug, ảnh, khám phá; thêm khóa tên, `version`, người thực hiện; sự kiện `EVT-CAT-03` có phiên bản; thêm sự kiện kiểm toán cho mọi thao tác (theo mẫu feature 04).
4. **Use case:** bỏ `@CacheEvict`; thêm người thực hiện từ token, kiểm vai; ghi hộp thư và kiểm toán cùng giao dịch; đọc cây hai loại (cho người bán, cho quản trị).
5. **Lưu trữ và API:** ánh xạ ràng buộc sang mã lỗi; `SecurityConfig` có vai và bắt đăng nhập khi đọc; tách tên đường dẫn như analysis (`/api/admin/...`).
6. **Kiểm thử theo thứ tự feature 04:** test miền đã có (chỉnh) → test viết mới từ kịch bản S9, R10, R12 → mã → đột biến → chạy thật (Postgres, Kafka, connector cách ly) → đóng.
7. **Tài liệu:** `data.md` và `api.yaml` của catalog viết ở feature này theo `service.md`; `service.md` sửa mục 4 (quyền) và chuyển "Dự kiến" thành "Đã làm" cho phần của 05.

**Đã chốt `2026-11-01`:** bỏ bảng bao đóng (A); cách P nhưng **vô hiệu hóa bằng comment, không xóa tệp** (B). Cách làm: mỗi dòng của tệp sản phẩm, biến thể, bản chụp tìm kiếm, kho ảnh, bộ nhớ đệm, `DevToolController` và test của chúng được thêm tiền tố `//~ ` bằng một script (dấu `~` để phân biệt với chú thích có sẵn). Tệp còn nguyên chỗ cũ, Java biên dịch được vì tệp chỉ còn chú thích. Hoàn lại chính xác bằng script ngược xóa đúng tiền tố `//~ `, lúc nào làm lại ở feature 07, 08. Script nằm ở thư mục tạm của phiên, chạy được lại, và ghi danh sách tệp đã comment vào `plan.md` của feature 05.

## 10. Việc cần bạn chốt

| # | Câu hỏi | Nghiêng về |
|---|---|---|
| A | Bảng bao đóng `category_closure`: giữ hay bỏ. Cây cố định ba cấp, cha không đổi, nên tổ tiên của một nút là tối đa hai bước theo `parent_id` (một truy vấn có thể lấy) | **Bỏ**: bớt một bảng, ba lệnh SQL thuần, hai lớp JPA, và một chỗ có thể lệch dữ liệu. Chỉ giữ nếu bạn muốn truy vấn "mọi con cháu" bằng một câu ở các chặng sau |
| B | Cách P hay cách K ở bảng 8 | **P** |
| C | Đọc cây, thuộc tính, thương hiệu: bắt đăng nhập như `analysis` hay giữ công khai như mã hiện tại (Guest duyệt hàng) | **Bắt đăng nhập ở C1a** (Guest duyệt hàng là C1b trở đi, khi có Tìm kiếm); mở công khai là một thay đổi nhỏ về sau |
| D | Hai câu đã nêu ở `flow.md`: hành động kiểm toán cho xóa và bật lại (Q4); ngừng tùy chọn cuối cùng của mẫu đã gán (Q6) | Như `flow.md` |

**Đã chốt `2026-11-01`:** C (đọc phải đăng nhập), D (Q4: thêm `CATEGORY_DELETED`; Q6: cho phép ngừng tùy chọn cuối, mã đã làm đúng vậy), Q2, Q3.

## 11. Bước 2 của feature 05: đối chiếu dữ liệu và API (`2026-11-01`)

Nguồn đã đọc: `old/data.md`, `old/api.yaml` (liệt kê đường), 24 bản ghi yêu cầu, phản hồi trong `presentation/*/model`, ba controller, các use case đọc (`GetCategoryTree`, `GetCategoryAttributes`, `ListAssignableAttributeTemplates`, `ListBrands`, `SearchBrandsForSeller`, `ListAdminBrands`, `ListAttributeTemplates`). `old/data.md` ghi mã là `uuid` nhưng migration thật là `VARCHAR(26)` (ULID): tài liệu cũ đã lệch mã.

| # | Điểm | Mã | Tài liệu mới | Quyết định |
|---|---|---|---|---|
| 11.1 | Đường dẫn danh mục, mẫu, thương hiệu | `/api/admin/...` cho ghi, `/api/categories`, `/api/brands` cho đọc | Cùng tiền tố admin | **Giữ nguyên đường dẫn** (không đổi gì thừa) |
| 11.2 | "Ngừng dùng" | Danh mục: `POST .../deactivate`. Mẫu, tùy chọn, thương hiệu: `DELETE` (nhưng thực chất chỉ ngừng dùng) | Mẫu, tùy chọn, thương hiệu **không bao giờ bị xóa** (INV-CAT-12, 21) | **Theo tài liệu:** `POST .../deactivate` cho cả bốn; `DELETE` chỉ còn ở danh mục. Một `DELETE` mà không xóa gây hiểu lầm |
| 11.3 | Kết quả của lệnh ghi | Tạo trả chuỗi mã; sửa, ngừng, bật trả `null`; thương hiệu trả `{id,name,slug}` | Phiên bản sau thay đổi cần cho `EVT-CAT-03`, kiểm toán và giao diện | **Theo tài liệu:** mọi lệnh ghi trả tài nguyên sau thay đổi kèm `version` (như seller); tạo trả 201 |
| 11.4 | Thân yêu cầu gán thuộc tính | Mảng ở gốc `[ {templateId,required,displayOrder,constraints,discovery} ]` | Thay toàn bộ danh sách | **Đổi** thành đối tượng `{ "items": [...] }` (dễ mở rộng, như các thân khác); bỏ `discovery` |
| 11.5 | Cây cho người dùng | Có `slug`, `imageUrl`; ẩn danh mục `INACTIVE` cùng cả nhánh con (con không có đường vào) | Không slug, ảnh; chỉ nhánh dùng được | Giữ hành vi ẩn; bỏ `slug`, `imageUrl` |
| 11.6 | Cây quản trị | Mọi trạng thái, có `status`, `slug`, `imageUrl` | Cần thấy cả cờ "dùng được" và `sortOrder`, `version` | Thêm `sortOrder`, `usable`, `version`; bỏ `slug`, `imageUrl` |
| 11.7 | Thương hiệu: danh sách đọc | Hai đường: `/api/brands` (công khai, có `slug`, phân trang) và `/api/seller/brands` (rút gọn, tối đa 20, không phân trang) | Một danh sách `RM-CAT-03` cho người dùng đã đăng nhập | **Gộp** thành `GET /api/brands?keyword&page&size` trả `{items:[{id,name}], total}`; bỏ `/api/seller/brands` (cùng nội dung, `size` đã giới hạn) |
| 11.8 | Thương hiệu: `slug` | Bắt buộc trong yêu cầu và phản hồi | Chỉ tên và trạng thái | Bỏ |
| 11.9 | `GET /api/admin/categories/{id}/assignable-templates` (droplist mẫu chưa gán) | Có | Không có trong `RM-CAT-*` | **Chưa mang vào C1a** (là tiện ích giao diện; giao diện Vận hành dùng `GET /api/admin/attribute-templates?status=ACTIVE` cộng danh sách đã gán). Ghi vào việc của feature 12, không mất |
| 11.10 | Phân trang | `page` từ 0, `size` mặc định 20; **không có trần `size`** | Trần phải có (nợ kỹ thuật TD-06 của tài liệu cũ) | `size` mặc định 20, **tối đa 50**; vượt trần thì cắt về 50 |
| 11.11 | Ràng buộc riêng khi gán (`constraints`) | Tám trường theo kiểu nhập: `TEXT` `maxLength`; `NUMBER` `min`, `max`, `integerOnly`; `MULTI_SELECT` `maxSelections`; `DATE` `inputPrecision`, `minDate`, `maxDate` (cho phép mốc tương đối như `now-30d`); `SINGLE_SELECT`, `BOOLEAN` không có | `analysis` chỉ nói "ràng buộc riêng của danh mục nếu có (ví dụ khoảng giá trị số)", **không nêu bộ trường** | **Mã có mà tài liệu chưa có: cần người chốt** (xem cuối mục). Tạm viết `data.md`, `api.yaml` theo mã, đánh dấu chờ xác nhận |
| 11.12 | Mô tả `AttributeTemplate.name` | "machine key bất biến" (`old/data.md`) | `name` duy nhất sau chuẩn hóa (INV-CAT-10), đổi được? | `analysis` không nói `name` bất biến, chỉ `inputType`, `unit` bất biến (INV-CAT-12). Mã hiện không có lệnh đổi `name` | Giữ: `name` đặt lúc tạo, **không có lệnh đổi** (chỉ đổi `displayName`, `hint`); ghi vào `service.md` |
| 11.13 | `AttributeTemplate`: `scope` | Đã bỏ ở migration cũ | Không có | Không có cột |
| 11.14 | Đường đọc cho quản trị | Danh sách mẫu (lọc `keyword`, `inputType`, `status`), chi tiết mẫu kèm mọi tùy chọn và `usable`, danh sách và chi tiết thương hiệu | Có cho quản lý | Giữ |

**Điểm cần người chốt (11.11):** bộ ràng buộc riêng theo danh mục. Ba hướng: (a) **giữ đúng như mã** (tám trường ở trên, kể cả mốc ngày tương đối), bổ sung vào `category.md`; (b) giữ phần số, chữ, chọn nhiều và bỏ phần ngày (`inputPrecision`, `minDate`, `maxDate`); (c) chỉ khoảng số `min`, `max` đúng ví dụ của `analysis`, phần khác thêm khi có nhu cầu. Nghiêng về (a): mã và test đã có, mô hình dữ liệu (`jsonb`) mở sẵn nên bỏ bớt không đơn giản hơn là bao nhiêu, và feature 07 cần bộ kiểm giá trị ngay.

## 12. Bước 4 của feature 05: mẫu thuộc tính và tùy chọn (`2026-11-01`)

Đối chiếu mã `AttributeTemplate`, `AttributeOption`, mười một use case, lưu trữ, controller với `analysis/attribute-template.md` và `tech.md`.

| # | Điểm | Mã cũ | Tài liệu | Quyết định |
|---|---|---|---|---|
| 12.1 | Hành vi miền (kiểu và đơn vị bất biến, tùy chọn xếp cuối, sắp xếp đủ danh sách, `isUsable`, sửa nhãn bất kể trạng thái) | Có đủ | Khớp | Giữ nguyên hành vi, chỉnh chữ ký (thêm ngữ cảnh thay đổi) |
| 12.2 | Kiểm định dạng mặc định theo kiểu (chữ 255, số 4 chữ số thập phân, ngày rút gọn, đúng/sai) trong mẫu | Có (`isValidRawValue`) | Có (mục 2) | Giữ trong mẫu; bộ gọi nó (`AttributeTemplateDomainService`, kiểm giá trị sản phẩm) thuộc feature 07 nên đã comment |
| 12.3 | Trùng tên mẫu, trùng giá trị tùy chọn | So `existsByName` (nguyên văn) và `equalsIgnoreCase` | Tên mẫu và giá trị là mã kỹ thuật bắt buộc đúng dạng (chốt `2026-11-02`), nên so thẳng | Không dùng khóa tên cho mẫu và tùy chọn (`NameKey` chỉ còn cho thương hiệu, danh mục và tên hiển thị); kiểm sớm ở use case, ràng buộc duy nhất ở cơ sở dữ liệu là chốt cuối. Mã cũ chưa từng thực thi dạng `snake_case` dù tài liệu cũ ghi |
| 12.15 | Dạng của tên mẫu và giá trị tùy chọn | Chỉ `@NotBlank` (tài liệu cũ ghi `snake_case`, không thực thi) | Tên: `^[a-z0-9]+(_[a-z0-9]+)*$`; giá trị: `^[a-z0-9]+([_-][a-z0-9]+)*$`; tối đa 100 | Từ chối (400 `CATALOG_FIELD_INVALID`), không tự sửa; `CHECK` ở cơ sở dữ liệu làm chốt cuối. Gạch ngang chỉ cho giá trị vì tên mẫu làm khóa dữ liệu |
| 12.4 | Ngừng, bật lại cái đã ở trạng thái đó | Cho phép lặp (không lỗi) | 409 `CATALOG_INVALID_STATE`, không event (Q5) | Theo tài liệu, áp cho cả tùy chọn |
| 12.5 | Vai quản trị | Không kiểm | `ADMIN` ở lệnh ghi (Q2) | Kiểm trong use case; đọc quản trị cũng cần `ADMIN` (đường `/api/admin/...`) |
| 12.6 | Phiên bản | `@Version` + sao chép bằng phản xạ vào trường của lớp cha | Ngầm, bắt đầu 0, event `version() + 1` | Mẫu có trường `version` riêng như hồ sơ người bán; ánh xạ qua hàm khởi tạo, không phản xạ |
| 12.7 | Event, kiểm toán, hộp thư | Chỉ ghi thẳng vào kho | `AttributeTemplateChanged`, `ATTRIBUTE_*`, `EVT-CAT-03` | Một loại event có `change`; kiểm toán chạy trước hộp thư (`getOrder` 50 < 100) |
| 12.8 | Mã lỗi | Số (`20101`...), 422 cho vi phạm trạng thái | Mã chữ là tên hằng; 409 vi phạm quy tắc hoặc trạng thái, 400 dữ liệu sai | Theo tài liệu. Đơn vị sai chỗ, gợi ý quá dài thành `CATALOG_FIELD_INVALID` nêu tên trường |
| 12.9 | Độ dài và rỗng của tên, tên hiển thị, giá trị tùy chọn | Không kiểm ở miền | Cột `VARCHAR(100)`... | Kiểm ở miền, 400 `CATALOG_FIELD_INVALID` |
| 12.10 | Đường ngừng dùng | `DELETE` | `POST .../deactivate` (bước 2) | Theo bước 2, cho cả mẫu và tùy chọn |
| 12.11 | Kết quả lệnh ghi | Chuỗi mã hoặc `null` | Tài nguyên kèm `version` (bước 2) | Trả `TemplateView`; lệnh tạo và thêm tùy chọn trả 201 |
| 12.12 | `size` danh sách | Trần 100 | Trần 50 | 50 |
| 12.13 | Thêm tùy chọn khi mẫu ngừng, hoặc mẫu không phải kiểu chọn | Từ chối (422) | Từ chối (409) | Giữ, đổi mã |
| 12.14 | Ngừng tùy chọn cuối của mẫu | Cho phép | Cho phép (Q6) | Giữ; có test |

**Mã có mà tài liệu chưa có:** không có điểm mới cần hỏi.

**Lệch kế hoạch có chủ đích:** chuyển kiểu mã sang UUID chỉ ở mẫu; danh mục và thương hiệu giữ chuỗi cho đến bước 5, 6, biên nối bằng phép đổi chuỗi sang UUID tạm trong mã danh mục (gỡ ở bước 6). Use case `ListAssignableAttributeTemplates` (đường `assignable-templates`) bỏ như quyết định ở bước 2.

## 13. Bước 5 của feature 05: thương hiệu (`2026-11-03`)

Đối chiếu mã `Brand`, tám use case, lưu trữ, controller, `BrandTest`, `BrandNameUniqueTest` với `analysis/brand.md` và `tech.md`.

| # | Điểm | Mã cũ | Tài liệu | Quyết định |
|---|---|---|---|---|
| 13.1 | Hành vi miền (tạo, đổi tên, ngừng, bật lại; không xóa; đổi tên được cả khi đã ngừng) | Có đủ | Khớp | Giữ hành vi, chỉnh chữ ký (thêm ngữ cảnh thay đổi) |
| 13.2 | Slug | Có: bất biến, nhập tay, đúng dạng `[a-z0-9]+(-[a-z0-9]+)*`, duy nhất; mục đích ghi trong mã là một phần URL | Không có slug (bước 2) | Bỏ slug. Thương hiệu được sản phẩm tham chiếu bằng mã, C1a chưa có URL thân thiện. Đã cân nhắc lại với người dùng (`2026-11-03`): giữ khóa tên, mã nhập tay bất biến chỉ dành cho dữ liệu làm khóa trong dữ liệu (tên mẫu, giá trị tùy chọn). Cần URL thì thêm slug sinh từ tên ở giai đoạn có giao diện cửa hàng |
| 13.3 | Chống trùng tên | Chỉ mục `LOWER(name)` và kiểm `existsByNameIgnoreCase`; khác dấu hay khoảng trắng lọt qua | Sau chuẩn hóa (INV-CAT-20) | Khóa tên `NameKey` (`name_key`); kiểm sớm ở use case, ràng buộc duy nhất ở cơ sở dữ liệu là chốt cuối |
| 13.4 | Ngừng, bật lại thứ đã ở trạng thái đó | Cho phép lặp (không lỗi) | 409 `CATALOG_INVALID_STATE`, không event (Q5) | Theo tài liệu |
| 13.5 | Vai quản trị | Không kiểm | `ADMIN` ở lệnh ghi (Q2) | Kiểm trong use case; đọc quản trị cũng cần `ADMIN` (đường `/api/admin/...`) |
| 13.6 | Phiên bản | Không có (không `@Version`) | Ngầm, bắt đầu 0, event `version() + 1` (ADR-0011) | Trường `version` riêng trong aggregate; `CONCURRENT_UPDATE` khi hai người sửa cùng lúc |
| 13.7 | Event, kiểm toán | Chỉ ghi nhật ký ứng dụng | `BrandChanged`, `BRAND_CREATED`, `_UPDATED`, `_DEACTIVATED`; không phát `EVT-CAT-03` | Một loại event có `change` (`CREATED`, `RENAMED`, `DEACTIVATED`, `ACTIVATED`); chỉ handler kiểm toán, không hộp thư |
| 13.8 | Mã lỗi | Số `20001` đến `20005`, 422 cho thương hiệu ngừng | Mã chữ là tên hằng; 409 vi phạm quy tắc, 400 dữ liệu sai | `BRAND_NOT_FOUND` 404, `BRAND_NAME_TAKEN` 409; bỏ `BRAND_SLUG_*`. `BRAND_INACTIVE` (chọn thương hiệu ngừng cho sản phẩm mới, INV-CAT-21) thuộc feature 07 và làm cùng sản phẩm |
| 13.9 | Đường dẫn | `GET /api/brands` và `GET /api/seller/brands` (hai danh sách), `DELETE` để ngừng | Một `GET /api/brands` cho mọi người đã đăng nhập, `POST .../deactivate` | Gộp hai danh sách thành một; `size` trần 50 (cũ 100; droplist người bán cũ cắt 20) |
| 13.10 | Tìm theo tên | `LOWER(name) LIKE` (phân biệt dấu) | Không phân biệt hoa thường, dấu | Tìm trên `name_key` với từ khóa đã chuẩn hóa |
| 13.11 | Kết quả lệnh ghi | `id` hoặc rỗng | Tài nguyên kèm `version` | Trả `BrandView`; lệnh tạo trả 201 |
| 13.12 | Kiểm nội dung tên | `@NotBlank` ở request, không độ dài | Cột `VARCHAR(100)` | Kiểm ở miền (rỗng, quá 100): 400 `CATALOG_FIELD_INVALID` |
| 13.13 | Mã định danh | ULID chuỗi | UUID v7 (ADR-0001) | `BrandId` bọc `UUID` |
| 13.14 | Dọn dữ liệu trùng tên ở migration cũ | Có (đổi tên bản trùng) | Bản gốc sạch (D8) | Không cần |

**Mã có mà tài liệu chưa có:** không có điểm mới cần hỏi.

**Ảnh hưởng ngoài thương hiệu:** mã sản phẩm đã comment (`GetProduct`, `PublishProduct`, `PublishProductSearchSnapshot`) vẫn gọi `BrandRepository.findById` và `BrandErrorCode.BRAND_NOT_FOUND`; hai thứ này còn nguyên tên. Khi làm lại sản phẩm (feature 07) chỉ cần đổi kiểu mã sang UUID.

## 14. Bước 6 của feature 05: cây danh mục và gán thuộc tính (`2026-11-03`)

Đối chiếu mã `Category`, `CategoryLevel`, `CategoryAttributeAssignment`, `AttributeConstraints`, mười hai use case, lưu trữ, adapter, controller, `CategoryTest` với `analysis/category.md` và `tech.md`.

| # | Điểm | Mã cũ | Tài liệu | Quyết định |
|---|---|---|---|---|
| 14.1 | Cây cố định ba cấp, chỉ cấp ba là lá, cha đóng vẫn tạo con, xóa chỉ kiểm con, thay toàn bộ danh sách gán | Có đủ | Khớp | Giữ hành vi, chỉnh chữ ký (thêm ngữ cảnh thay đổi) |
| 14.2 | Slug, ảnh đại diện, cấu hình khám phá (`Discovery`, `FilterConfig`, `SortConfig`) | Có | Không có slug (bước 2); ảnh đại diện "cắt tạm"; khám phá thuộc C1b | Bỏ cả ba. Dòng gán chỉ còn `required`, `displayOrder`, `constraints` |
| 14.3 | Bảng bao đóng để tìm tổ tiên | Có (`category_closure`) | Cây tối đa ba cấp, tổ tiên là cha và ông | Bỏ bảng. `hasInactiveAncestor` đi lên theo khóa chính, tối đa hai lần tra; cây đọc dựng từ một lần `findAll` |
| 14.4 | Chống trùng tên trong cùng cha | Tải mọi anh em rồi `equalsIgnoreCase`; không có ràng buộc ở cơ sở dữ liệu | Sau chuẩn hóa (INV-CAT-02) | Khóa tên `NameKey` (`name_key`); kiểm sớm ở use case, hai chỉ mục duy nhất từng phần (cấp một và có cha) làm chốt cuối |
| 14.5 | Sửa danh mục | `update(name, imageUrl)`, sinh lại slug | Chỉ đổi tên | `rename(name)`; làm được cả khi đang ngừng dùng |
| 14.6 | Ngừng, bật lại thứ đã ở trạng thái đó | Cho phép lặp (không lỗi) | 409 `CATALOG_INVALID_STATE`, không event (Q5) | Theo tài liệu |
| 14.7 | Vai quản trị | Không kiểm | `ADMIN` ở lệnh ghi (Q2) | Kiểm trong use case; đọc quản trị cũng cần `ADMIN`; hai đường đọc công khai chỉ cần đăng nhập |
| 14.8 | Phiên bản, event, kiểm toán, hộp thư | `CategoryUpdatedEvent` chỉ mang mã; không phiên bản, không kiểm toán | `CategoryChanged`, `CATEGORY_*`, `EVT-CAT-03` | Một loại event có `change` (`CREATED`, `RENAMED`, `REORDERED`, `ASSIGNMENTS_REPLACED`, `DEACTIVATED`, `ACTIVATED`, `DELETED`); kiểm toán chạy trước hộp thư (`getOrder` 50 < 100); `@Version` ở dòng danh mục, đổi danh sách gán cũng tăng phiên bản |
| 14.9 | Sắp xếp | Ghi và phát event cho mọi anh em, kể cả cái không đổi chỗ | Danh sách đủ mọi anh em, mỗi cái đúng một lần | Chỉ ghi và phát event cho cái đổi chỗ; trả đủ danh sách theo thứ tự mới; cha không tồn tại thì 404 |
| 14.10 | Vị trí khi tạo | Số anh em hiện có | Xếp cuối | Lớn nhất cộng một (không trùng sau khi xóa bớt) |
| 14.11 | Xóa | Kiểm còn con; phát event rồi xóa | Chỉ kiểm con (Q7); ghi `CATEGORY_DELETED` kèm tên và cha | Theo tài liệu; khóa ngoài cha `RESTRICT` là chốt cuối khi đua nhau với tạo con; dòng gán đi theo nhờ `ON DELETE CASCADE` |
| 14.12 | Mẫu khi thay danh sách gán | Chỉ mẫu mới thêm phải dùng được; mẫu cũ đã ngừng giữ được | Khớp | Giữ. Mẫu không tồn tại thì 404 `ATTRIBUTE_TEMPLATE_NOT_FOUND`; trùng mẫu trong danh sách thì 409 `ATTRIBUTE_ASSIGNMENT_DUPLICATED` |
| 14.13 | `constraints` của dòng gán | Mẫu kiểm định dạng chung theo kiểu (`isValidRawValue`); ràng buộc theo ngành hàng khai báo ở dòng gán (tám trường theo kiểu nhập, kiểm hình khi gán); bộ áp ràng buộc lên giá trị sản phẩm khi đăng là `AttributeConstraintChecker` | "Ràng buộc riêng của danh mục nếu có (ví dụ khoảng giá trị số)"; người dùng nhắc: ràng buộc chung chỉ kiểm định dạng, quy tắc cụ thể khai báo khi tạo danh mục | Khớp với mã. **Chốt `2026-11-03`:** giữ `constraints` như mã (bỏ `discovery`); mẫu kiểm định dạng chung, dòng gán giữ quy tắc cụ thể, kiểm hình khi gán; bộ áp lên sản phẩm kiểm khi đăng thuộc feature 07 (mã đã comment). Đã sửa `data.md`, `api.yaml` bỏ chữ "chưa chốt" |
| 14.14 | Mã lỗi | Số `202xx`, 422 cho vi phạm quy tắc | Mã chữ là tên hằng; 409 vi phạm quy tắc hoặc trạng thái, 400 dữ liệu sai | `CATEGORY_NOT_FOUND` 404; `CATEGORY_MAX_DEPTH_EXCEEDED`, `CATEGORY_NOT_LEAF`, `CATEGORY_NAME_TAKEN`, `CATEGORY_HAS_CHILDREN`, `CATEGORY_INVALID_ORDER`, `ATTRIBUTE_ASSIGNMENT_DUPLICATED` 409; `ATTRIBUTE_CONSTRAINTS_INVALID` 400. Bỏ `INVALID_DISCOVERY`, `INVALID_RANGE_FILTER`; `HAS_PRODUCT_REFERENCE` và `CATEGORY_INACTIVE` (chọn danh mục ngừng cho sản phẩm mới) thuộc feature 07 |
| 14.15 | Mã định danh, tên cột | ULID chuỗi; cột `is_required` | UUID v7 (ADR-0001); cột `required` | `CategoryId` bọc `UUID`; gỡ các chỗ đổi chuỗi-UUID tạm ở bước 4 và cả thư viện ULID |
| 14.16 | Đường dẫn và phản hồi | `PUT .../attributes` nhận mảng trần; hầu hết lệnh ghi trả rỗng; cây công khai có slug, ảnh | `PUT .../attributes` nhận `{items}` trả danh sách gán; lệnh ghi trả danh mục kèm `version`; cây công khai chỉ `id`, `name`, `level`, `children` | Theo tài liệu. Cây quản trị thêm `sortOrder`, `status`, `usable`, `version` |

**Mã có mà tài liệu chưa có:** không có điểm mới cần hỏi.

**Ảnh hưởng ngoài danh mục:** mã sản phẩm đã comment còn dùng `CategoryRepository.findById`, `hasInactiveAncestor` và `CategoryErrorCode`; ba thứ này còn tên cũ (đổi tên mã lỗi `CATEGORY_NOT_LEAF`, `CATEGORY_INACTIVE` khi làm lại sản phẩm ở feature 07).

## 15. Bước 2 của feature 07: sản phẩm và biến thể (`2026-11-05`)

Đối chiếu mã `Product`, `ProductAttributeValue`, `Variant`, `VariantCombination`, `VariantCombinationPolicy`, `AttributeTemplateDomainService.validateProductAttributes`, `AttributeConstraintChecker`, các use case `CreateProduct`, `UpdateProduct`, `AddVariant`, `UpdateVariant`, `DeactivateVariant`, `ActivateVariant`, lưu trữ, hai controller và ba lớp test (`ProductTest`, `VariantTest`, `VariantCombinationPolicyTest`) với `analysis/product.md`, `variant.md`, `flow.md` và `tech.md` của feature 07. Phần ảnh, đăng, gỡ, chặn, xóa, bản chụp tìm kiếm, bộ nhớ đệm, sự kiện **không** thuộc 07, không đối chiếu ở đây.

**Cột "Đề xuất" chờ người chốt** (chỗ lệch ghi "theo mã" hay "theo tài liệu"). Chỗ khớp: hoàn lại và chỉnh tối thiểu.

| # | Điểm | Mã cũ | Tài liệu | Đề xuất |
|---|---|---|---|---|
| 15.1 | Trục biến thể | Cờ `isVariantDefining` trên từng giá trị thuộc tính; trục là **tập con** thuộc tính; không giới hạn số trục; chỉ kiểu chọn một (`VARIANT_DEFINING_REQUIRES_SELECT`) | Trục khai riêng (`PUT .../axes`), tối đa 2, chọn một, tập giá trị riêng (`INV-CAT-33`) | **Cờ như mã cũ, `2026-11-05` (người dùng chốt).** Một danh sách `attributes`, dòng có `axis: true` là trục; thêm giới hạn 2 trục và chỉ chọn một. Bỏ ý bảng trục riêng của bản đầu `tech.md` |
| 15.2 | Khóa trục khi đã có biến thể | `VARIANT_DEFINING_LOCKED_AFTER_VARIANT`: chặn đổi **nguyên tập** cờ; chặn gỡ giá trị đang dùng (`ATTRIBUTE_VALUE_STILL_USED_BY_VARIANT`) | Danh sách trục giữ nguyên, được thêm bớt giá trị trừ giá trị đang dùng | Khớp. Hoàn lại hành vi (hàm kiểm trong `replaceAttributes`); mã lỗi đổi thành `PRODUCT_AXES_LOCKED`, `PRODUCT_AXIS_VALUE_IN_USE`; kiểm ở ứng dụng dưới khóa dòng, không khóa ngoại |
| 15.3 | Giới hạn số trục, số biến thể | **Không có** (không kiểm 2 trục, không kiểm 50 biến thể) | Tối đa 2 trục, 50 biến thể (`INV-CAT-93`, `Q-CAT-01`) | **Theo tài liệu.** Thêm `ProductLimits`; đếm 50 dưới khóa dòng (ADR-0012) |
| 15.4 | Khóa dòng | `findByIdForUpdate` đã có ở `UpdateProduct`, `AddVariant`; `UpdateVariant`, `DeactivateVariant`, `ActivateVariant` đọc `findById` **không khóa** | Mọi lệnh biến thể và trục khóa dòng (ADR-0012) | **Theo tài liệu.** Cả bốn lệnh biến thể khóa; `UpdateProduct` có `attributes` (có thể đổi trục) cũng khóa, không có thì khóa lạc quan (`PATCH`) |
| 15.5 | Phiên bản | Không có `version` ở sản phẩm và biến thể | `version` sản phẩm dùng chung, mọi lệnh biến thể tăng (`INV-CAT-36`, ADR-0012) | **Theo tài liệu.** `recordVariantChange` làm dòng sản phẩm "bẩn" để `@Version` tăng |
| 15.6 | Tính duy nhất tổ hợp | `combination_hash` SHA-256 của `templateId:optionId` sắp theo mã mẫu; chỉ mục duy nhất `(product_id, combination_hash)`; kiểm sớm `existsBy…` | Không phụ thuộc thứ tự trục (`INV-CAT-92`) | Khớp ý. **Đổi cách tính:** khóa tổ hợp là các mã tùy chọn sắp theo mã mẫu, nối dấu phẩy (đọc được, tối đa 73 ký tự), thay băm. Lý do: UUID thay ULID, khóa ngắn nên không cần băm; tổ hợp rỗng = khóa rỗng |
| 15.7 | Tổ hợp vào bảng con | `variant_combination_item` có, không khóa ngoại tới giá trị trục (kiểm ở `VariantCombinationPolicy`) | `INV-CAT-94`: giá trị nằm trong tập trục | **Giữ kiểm ở miền, không thêm khóa ngoại** (người dùng chốt `2026-11-05`): khóa dòng đã loại đua nhau, và không có khóa ngoại thì thay danh sách thuộc tính được xóa-rồi-ghi-lại |
| 15.8 | `VariantCombinationPolicy` | Kiểm: không trùng trục, mỗi cặp thuộc trục, mẫu và tùy chọn tồn tại và đang dùng, giá trị nằm trong tập sản phẩm khai, đủ mọi trục | Khớp (`INV-CAT-94`; "đang dùng lúc tạo" của `INV-CAT-33`) | Khớp. Hoàn lại; đổi nguồn trục sang `axes` của sản phẩm, mã lỗi `VARIANT_COMBINATION_INVALID` (400) thay các lỗi 422 rời rạc |
| 15.9 | Kiểm giá trị thuộc tính | `validateProductAttributes` (dịch vụ miền): thuộc tính thuộc danh mục (giữ nếu đã có), mẫu dùng được, kiểu, tùy chọn đang dùng, ràng buộc riêng; giá trị **đã lưu** miễn kiểm từng giá trị; bắt buộc chỉ ép khi mẫu dùng được | Khớp (`INV-CAT-32`, grandfathering) | Khớp. Hoàn lại hành vi; tách thành `AttributeValueChecker` thuần (nhận mẫu và dòng gán, không gọi kho) để kiểm đơn vị; gộp `AttributeConstraintChecker` và `AttributeTemplate.isValidRawValue` |
| 15.10 | Kiểu lưu giá trị | Một bảng `product_attribute_value(product_id, template_id, value)` khóa nhiều giá trị, cột chuỗi dùng chung cho tùy chọn lẫn giá trị rời | Hai bảng (Q5) | **Theo tài liệu**, chỉnh theo cờ trục: hai bảng cha-con `product_attribute` (cờ `axis`, giá trị rời) và `product_attribute_option`. Chốt `2026-11-05` |
| 15.11 | Giá niêm yết | `long`, `> 0`; không trần | 1 đến 1.000.000.000, nguyên (`INV-CAT-40`, `43`) | **Theo tài liệu.** Thêm trần; JSON số có phần thập phân là 400 |
| 15.12 | SKU | `sku_code VARCHAR(100)` không duy nhất, không kiểm độ dài | 1-50 ký tự, duy nhất theo người bán (`INV-CAT-96`) | **Theo tài liệu.** `sku` 1-50; `uq_variant_seller_sku` từng phần; thêm `seller_id` sao ở biến thể |
| 15.13 | Ngừng, bật lại biến thể đã ở trạng thái đó | Lặp không lỗi (`return`) | 409 `CATALOG_INVALID_STATE` (như 05) | **Theo tài liệu** |
| 15.14 | Biến thể mặc định | Không có khái niệm riêng; tổ hợp rỗng được khi sản phẩm không có trục (suy từ chính sách) | Tổ hợp rỗng tạo khi đặt giá lần đầu (`INV-CAT-44`) | Khớp. Khóa tổ hợp rỗng nên một sản phẩm chỉ có một biến thể mặc định nhờ cùng ràng buộc |
| 15.15 | Quyền sở hữu | `X-Seller-Id` do cổng chuyển; `assertOwnedBy` trả 403 `NOT_PRODUCT_OWNER` | Chủ lấy từ token (vai `USER`); sản phẩm người khác như không tồn tại (404) | **Theo tài liệu.** Bỏ header; `requireSeller()`; 404 không lộ sự tồn tại |
| 15.16 | Biến thể thuộc sản phẩm khác | Trả `PRODUCT_NOT_FOUND` (cùng mã với sản phẩm) | `VARIANT_NOT_FOUND` | **Theo tài liệu** |
| 15.17 | Sửa sản phẩm | `PUT` thay toàn bộ (tên, mô tả, bảo hành, mọi thuộc tính) | `PATCH`: vắng giữ nguyên; `attributes` thay toàn bộ (Q7) | **Theo tài liệu** |
| 15.18 | Tên trường | `name`, `skuCode`, `attributeValues`, `isVariantDefining` | `title`, `sku`, `attributes`; trục khai riêng | **Theo tài liệu** (`service.md`, `api.yaml` chỉnh theo) |
| 15.19 | Danh mục, thương hiệu khi tạo | Danh mục tồn tại, lá, dùng được; thương hiệu **bắt buộc** và đang dùng | Danh mục lá dùng được (`INV-CAT-97`); thương hiệu tùy chọn (E2) | **Theo tài liệu.** Thương hiệu rỗng được; mã lỗi `PRODUCT_CATEGORY_NOT_USABLE`, `PRODUCT_BRAND_NOT_USABLE` (409) thay `CATEGORY_INACTIVE`, `BRAND_INACTIVE` (đã hẹn ở 13.8, 14.14) |
| 15.20 | Bảo hành | `WarrantyInfo` (tháng, loại, phạm vi) | Chưa làm (E10 cắt tạm) | Giữ `//~`, không đưa vào 07 |
| 15.21 | Cân nặng, kích thước, mã vạch, giá gốc của biến thể | Cột ở lược đồ cũ | Chưa làm (`variant.md` mục 7) | Không đưa vào `V2` |
| 15.22 | Mã lỗi | Số `203xx`, nhiều 422 | Mã chữ, 400 dữ liệu sai, 409 vi phạm quy tắc, 404 | Bảng mã ở `tech.md` T2 thay toàn bộ |
| 15.23 | Mã định danh | ULID chuỗi, `sellerId` chuỗi | UUID v7; `sellerId` UUID từ token | `ProductId`, `VariantId` bọc `UUID`; `sellerId` lấy từ `sub` |
| 15.24 | Event, bộ nhớ đệm, bản chụp tìm kiếm | Phát event, `@CacheEvict`, `PublishProductSearchSnapshot` ở mọi lệnh | 07 không phát event (Q2) | Gỡ khỏi use case hoàn lại; giữ `//~` cho 09, C1b |
| 15.25 | Test cũ | `ProductTest`, `VariantTest`, `VariantCombinationPolicyTest` (350 dòng) theo mô hình cờ trục | KT-01, KT-02 | Viết lại theo mô hình mới; dùng làm danh sách ca tham khảo, không chép |

**Hoàn lại hay viết lại.** `VariantCombinationPolicy` (kiểm đủ ý, đổi nguồn trục), `AttributeConstraintChecker` (nguyên), kiểm giá trị cũ miễn kiểm (nguyên ý): **hoàn lại và chỉnh**. `Product` (mất cờ trục, thêm `axes`, `version`, bỏ ảnh, bảo hành, chặn) và `Variant` (thêm `sellerId`, bỏ ảnh): **viết lại phần lớn**. Lưu trữ và controller: **viết lại** vì khác lược đồ, khác xác thực.

**Mã có mà tài liệu chưa có:** không có điểm mới cần hỏi.

**Đã chốt `2026-11-05`:** 15.1 và 15.7 (cờ trục, bỏ khóa ngoại). **Còn chờ người chốt:** 15.6 (khóa tổ hợp đọc được thay băm), 15.9 (tách `AttributeValueChecker` thuần), 15.19 (thương hiệu tùy chọn). Còn lại theo tài liệu hoặc khớp.
