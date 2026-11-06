# Feature 07: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định; **Agent** viết phần thường, người rà), và cách biết bước đã xong. Quy tắc giống feature 01 đến 06: mỗi bước chỉ làm và kiểm đúng phạm vi của nó; test viết từ `flow.md` mục 4 và 6 trước mã; phá thử sau mỗi bước; không commit.

Feature lớn nhất của C1a: một migration, hai aggregate, chín use case, một cơ chế khóa mới (ADR-0012). Chia làm chín bước để mỗi bước đỏ hay xanh được riêng.

## Từ luồng đến giải pháp

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai |
|---|---|---|---|
| Dựng người thực hiện, kiểm vai `USER` | `CatalogActor.requireSeller()`, chủ lấy từ token | `CatalogActor`, mọi use case | Đã làm: `CatalogActor.requireSeller`; mọi use case sản phẩm và biến thể gọi đầu tiên; `CatalogActorTest`, `ProductUseCaseTest`, `VariantUseCaseTest` |
| Kiểm tiêu đề, mô tả, giới hạn | `Product.create`, `ProductLimits` đọc `catalog.limits.*` | `Product`, `ProductLimits` | Đã làm: `Product`, `ProductLimits` (`catalog.limits.*`, chỉ siết được qua CHECK ở V2); `ProductTest`, `ProductLimitsTest` |
| Đọc danh mục lá dùng được, thương hiệu | Kho của feature 05, chỉ đọc, không khóa (`tech.md` T3) | `CreateProduct`, `UpdateProduct` | Đã làm: kho feature 05 chỉ đọc; `CreateProduct`, `UpdateProduct` (thương hiệu không bắt buộc, đổi sang thương hiệu cũ không kiểm lại) |
| Kiểm giá trị thuộc tính, miễn kiểm giá trị cũ | `AttributeValueChecker` (phục hồi từ `AttributeConstraintChecker`), `Product.replaceAttributes` | `Product`, `AttributeValueChecker` | Đã làm: `AttributeValueChecker` hàm thuần, `Product.replaceAttributes`; `AttributeValueCheckerTest` 22 ca, 12 phá thử bắt hết |
| Lưu giá trị thuộc tính và cờ trục | Hai bảng cha-con `product_attribute` (cờ `axis`, giá trị rời), `product_attribute_option` (Q5, Q9) | `V2`, adapter | Đã làm: `V2__create_product_variant.sql`, `ProductAttributeJpaEntity`, `ProductAttributeOptionJpaEntity`; `ProductSchemaConstraintTest` |
| Đánh dấu trục | Cờ `axis` trên thuộc tính; `Product.replaceAttributes` kiểm `INV-CAT-33` (tối đa 2, chọn một, khóa khi đã có biến thể); sửa `attributes` khóa dòng (Q9) | `Product`, `UpdateProduct` | Đã làm: cờ `axis`; `Product.replaceAttributes` kiểm tối đa 2, chọn một, khóa tập mẫu và giá trị đang dùng; `UpdateProduct` khóa dòng khi gửi `attributes` |
| Thêm biến thể, kiểm tổ hợp, đếm 50 | `VariantCombinationPolicy`, khóa dòng, `recordVariantChange` (ADR-0012) | `AddVariant`, `Variant`, `Product` | Đã làm: `AddVariant`, `VariantCombinationPolicy`, `Product.recordVariantChange`; `SellerConcurrencyTest` (49→50, 20 tổ hợp, `version` +20) |
| Đổi giá, SKU, ngừng, bật biến thể | Cùng đường khóa dòng, tăng `version` sản phẩm | `UpdateVariant`, `DeactivateVariant`, `ActivateVariant` | Đã làm: `UpdateVariant`, `DeactivateVariant`, `ActivateVariant` qua `VariantLocator` (khóa dòng); `version` +1 mỗi lệnh thành công |
| Quy tắc duy nhất (tổ hợp, SKU theo người bán) | Ràng buộc có tên ở `V2` + ánh xạ lỗi (ADR-0011) | `V2`, `PersistenceConflictExceptionHandler` | Đã làm: `uq_variant_product_combination`, `uq_variant_seller_sku` + hai mã lỗi ở `PersistenceConflictExceptionHandler`; chạy thật: 1 thắng, 19 lần `409` |
| Đọc sản phẩm của tôi kèm điều kiện còn thiếu | `MissingPublishConditions` (hàm thuần), danh sách đọc thẳng bảng | `GetMyProduct`, `ListMyProducts` | Đã làm: `MissingPublishConditions`, `GetMyProduct`, `ListMyProducts`; `NO_ACTIVE_VARIANT` có rồi hết khi có biến thể (chạy thật) |
| Chống đua | `findOwnedForUpdate` cho lệnh biến thể và lệnh sửa `attributes`; `@Version` cho lệnh còn lại | `ProductRepository`, adapter | Đã làm: `ProductRepository.findOwnedForUpdate` (`PESSIMISTIC_WRITE`) + `@Version`; ADR-0012 Accepted; bỏ `FOR UPDATE` thì 5 ca đỏ |

## Điều kiện bắt đầu

Đã chốt (`tech.md` Q1 đến Q8, `2026-11-05`): Q1, Q2, Q4 người dùng đồng ý `2026-11-04`; Q5 giữ hai bảng `2026-11-05`; Q3, Q6, Q7, Q8 theo đề xuất, người dùng không còn thắc mắc. Feature 05 đã đóng (cấu trúc danh mục, `V1`, API quản trị); feature 06 đã đóng (bộ mẫu không bắt buộc cho 07, test tự tạo dữ liệu). Cần Docker chạy được cho Testcontainers.

**Ghi hạ tầng dev:** không có. Mọi kiểm chứng chạy trên Postgres cách ly (Testcontainers; ngăn xếp `probe-` cho bước chạy thật). **Không khởi động `catalog-service` trên `catalog_db` dev trong feature này**: ứng dụng sẽ áp `V2` lên cơ sở dữ liệu dev mà người dùng chưa cho phép. Về sau, người dùng cho phép (`2026-11-05`) và `catalog_db` đã được xóa, dựng lại ở bước 9.

**Test chạy được:** `./mvnw -o -q -pl catalog-service -am test -Dtest='vn/t3nexus/catalog/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false` từ `services/`, `rm -rf catalog-service/target/classes/db` trước mỗi lần. Không chạy bốn lớp test cũ của `oauth2-service` nối cơ sở dữ liệu dev.

## Các bước

### Bước 1. Rà kịch bản test · Người

Đối chiếu E1 đến E24 (`flow.md` mục 4; E24 thêm `2026-11-05` khi chuyển sang mô hình cờ trục, Q9) với KT-01 đến KT-09 (mục 6): mỗi đường không suôn sẻ phải có ít nhất một KT chứng minh.

| E | KT chứng minh | E | KT chứng minh |
|---|---|---|---|
| E1 | KT-03, KT-05 | E13 | KT-02, KT-05 |
| E2 | KT-03, KT-05 | E14 | KT-04, KT-06, KT-08 |
| E3 | KT-01, KT-05 | E15 | KT-05 (không có đường) |
| E4 | KT-01, KT-05 (Quản trị tắt tùy chọn qua API của 05, người bán sửa tiêu đề) | E16 | KT-02, KT-05 |
| E5 | KT-01 | E17 | KT-06 |
| E6 | KT-01, KT-05 (ký tự `<`, `&` lưu nguyên) | E18 | KT-06 |
| E7 | KT-03, KT-05, KT-09 | E19 | KT-03, KT-05, KT-09 |
| E8 | KT-01, KT-05 | E20 | KT-05 |
| E9 | KT-01, KT-05, KT-06 (đua với thêm biến thể) | E21 | KT-05 |
| E10 | KT-02, KT-05 | E22 | KT-07 |
| E11 | KT-04, KT-06, KT-08 | E23 | KT-05 |
| E12 | KT-06, KT-09 | E24 | KT-01, KT-05 |

Xong khi: người đồng ý bảng (E1 đến E23 đã đồng ý; E24 và E9 đổi theo Q9).

### Bước 2. Đối chiếu mã cũ, tài liệu chi tiết · Người

(1) Đối chiếu mã sản phẩm và biến thể đang `//~` với `analysis/product.md`, `variant.md` theo quy tắc feature 05: ghi `docs/draft/_so-sanh-catalog.md` mục 15 trở đi (chỗ khớp: hoàn lại và chỉnh tối thiểu; chỗ lệch: ghi "theo mã" hay "theo tài liệu", chờ người chốt). Tài liệu chính thức không nhắc mã cũ. (2) Sau khi chốt, theo thứ tự `analysis` → `service.md` → `data.md` → `api.yaml`: `service.md` chi tiết 07 (nhãn Dự kiến), `data.md` bảng `V2` và ràng buộc, `api.yaml` các điểm cuối người bán và mã lỗi (`tech.md` T2, T5).

Xong khi: bảng so sánh được người đồng ý; `data.md`, `api.yaml`, `service.md` khớp `tech.md`.

**Triển khai (`2026-11-05`):** xong phần tài liệu, chờ người chốt ba điểm của bảng so sánh.
- Bảng đối chiếu: `docs/draft/_so-sanh-catalog.md` mục 15 (25 dòng). Người chốt `2026-11-05`: trục là **cờ trên thuộc tính** như mã cũ (không bảng trục riêng, không `PUT .../axes`), và **bỏ khóa ngoại** từ tổ hợp biến thể tới giá trị trục. Hệ quả đã sửa: `tech.md` (Q9, T1 đến T3, T5, T6, T7), `flow.md` (mục 2, 3.1, 3.2, E8, E9, E24, KT-01, KT-06), ADR-0012 (quyết định 1 đến 3, phương án đã loại, mục "Cần kiểm"), `analysis/product.md` (trục là thuộc tính được đánh dấu; bắt buộc đã làm trục coi như đã điền), `service.md`, `data.md` mục 10 (bảng `V2`), `api.yaml` (sáu đường `/api/seller/products`, nhãn Dự kiến).
- Q5 đổi hình nhưng vẫn hai bảng: cha `product_attribute` (cờ `axis`, giá trị rời) và con `product_attribute_option`.
- **Còn chờ người chốt:** 15.6 (khóa tổ hợp đọc được thay băm), 15.9 (tách `AttributeValueChecker` thuần), 15.19 (thương hiệu tùy chọn). Một chi tiết tôi tự đặt trong `api.yaml`: `PATCH` biến thể với `sku: null` thì bỏ SKU (vắng thì giữ nguyên).
- Sửa kèm: `api.yaml` có ba chỗ mô tả chứa dấu hai chấm làm hỏng YAML (một chỗ của feature 05, hai chỗ mới); đã đưa sang khối chữ, đã kiểm bằng `js-yaml` (sáu đường người bán, 0 tham chiếu hỏng).

### Bước 3. Nền: hoàn lại mã cũ, `V2`, `requireSeller` · Agent (theo khung), Người rà

Việc: hoàn lại khỏi `//~` **chỉ phần của 07** (miền, use case, lưu trữ, API sản phẩm và biến thể); ảnh, bản chụp tìm kiếm, bộ nhớ đệm, sự kiện giữ `//~`; viết `V2__create_product_variant.sql` theo `tech.md` T1; thêm `CatalogActor.requireSeller()`; `ProductLimits` và `catalog.limits.*`. Dự án biên dịch được, `V1` không đổi.

Xong khi: biên dịch xanh; test hiện có của catalog vẫn xanh; `V2` áp được trên Postgres cách ly (test của bước 6 chứng minh sâu hơn).

**Triển khai (`2026-11-05`):** xong phần nền; chưa hoàn lại mã sản phẩm và biến thể.
- Làm: `V2__create_product_variant.sql` (năm bảng theo `data.md` mục 10); `CatalogActor.requireSeller()` và `isSeller()`; `ProductLimits` (miền) cùng `CatalogLimitsProperties`, `CatalogLimitsConfig` (`catalog.limits.*`); `ProductId`, `VariantId` (UUID), `ProductStatus`, `VariantStatus`; `ProductErrorCode`, `VariantErrorCode` (bảy và năm mã theo `tech.md` T2). Sáu tệp trên thay bản cũ đang `//~`.
- Chưa hoàn lại: `Product`, `Variant`, `VariantCombinationPolicy`, kho, use case, lưu trữ, API; làm ở bước 4 đến 6 cùng test viết trước, vì các lớp này viết lại phần lớn (so sánh mục 15). Các tệp cũ còn `//~` đến lúc đó.
- Test (mười ca mới, viết trước mã): `CatalogActorTest` (3), `ProductLimitsTest` (4), `CatalogLimitsConfigTest` (3). Sửa `CatalogSchemaConstraintTest` từ "đúng V1" thành "đúng V1 và V2" kèm năm bảng mới.
- Kết quả: **336 test của catalog xanh** (326 cũ + 10 mới; không chạy bốn lớp test cũ của `oauth2-service`). Phá thử ba lỗi (`isSeller` luôn đúng, trần trục 3, bỏ trần SKU): cả ba làm test đỏ, đã khôi phục.
- Hệ quả thiết kế đã ghi: giới hạn cấu hình chỉ siết được, không nới quá ràng buộc `V2` (`tech.md` T2).
- Chưa kiểm `V2` sâu (từng ràng buộc có hiệu lực và phá thử): KT-04, KT-08 ở bước 6. `V2` đã áp ở `catalog_db` dev khi dựng lại cơ sở dữ liệu (`2026-11-05`, bước 9).

### Bước 4. Sản phẩm và giá trị thuộc tính · Người (chính sách), Agent (test, mã thường)

Việc: test KT-01 và phần KT-03 trước; rồi `Product` (`create`, `updateDetails`, `replaceAttributes` kèm kiểm trục, `recordVariantChange`), `AttributeValueChecker` (miễn kiểm giá trị cũ; thuộc tính bắt buộc đã làm trục tính là đã điền), `CreateProduct`, `UpdateProduct` (có `attributes` thì khóa dòng), `GetMyProduct`, `ListMyProducts`, `MissingPublishConditions` (hai điều kiện của 07).

Xong khi: KT-01 và phần KT-03 của sản phẩm xanh; phá thử (`tech.md` T6: bỏ `requireSeller`, bỏ kiểm chủ, bỏ miễn kiểm giá trị đã lưu, mô tả `<script>`) làm test đỏ.

**Triển khai (`2026-11-05`):** xong miền và use case; chưa gắn vào ứng dụng vì chưa có lưu trữ.
- Miền: `Product` (tạo, đổi tiêu đề, mô tả, thương hiệu, `replaceAttributes` kèm khóa trục, `recordVariantChange`), `ProductAttribute`, `AttributeInput`, `AttributeValueChecker` (hàm thuần; `AttributeConstraintChecker` hoàn lại nguyên văn), `MissingPublishConditions`, `VariantUsage` và cổng `VariantUsageReader`, `ProductRepository` (`findOwned`, `findOwnedForUpdate`). Use case: `CreateProduct`, `UpdateProduct`, `GetMyProduct`, `ListMyProducts`, `ProductView`, `AttributeView`, `Patch`.
- Lệch kế hoạch: (1) use case chưa có `@Service`, gắn ở bước 6 cùng bộ chuyển đổi lưu trữ (nếu gắn sớm, ứng dụng không khởi động vì thiếu kho); (2) hành vi của sản phẩm nhận `Instant now`, không nhận `ChangeContext` (07 không phát event); (3) `ProductView` chưa có biến thể, thêm ở bước 5; (4) thuộc tính bắt buộc không kiểm lúc tạo hay sửa (nháp soạn từng bước), `MissingPublishConditions` báo thiếu; (5) đã xóa mã cũ bị thay: `ProductAttributeValue`, `GetSellerProduct`, `ListSellerProducts`.
- Test viết trước mã: `AttributeValueCheckerTest` (22), `ProductTest` (13), `MissingPublishConditionsTest` (5), `ProductUseCaseTest` (17, kho giả ghi lại lần đọc thường hay khóa dòng). Cả 57 ca xanh ngay lần đầu, nên phá thử.
- Phá thử **12/12** làm test đỏ, tệp khôi phục đúng (**đã chạy lại đúng cách `2026-11-05`**: lần đầu script gọi `./mvnw` qua `cmd.exe` nên không chạy được và mọi lần đều báo đỏ giả; lần chạy lại có ca đối chứng không đổi gì ra xanh, 11 lỗi chạy được đều đỏ, lỗi `recordVariantChange` chạy riêng cũng đỏ): bỏ `requireSeller`; `UpdateProduct` luôn đọc không khóa; `ownedBy` luôn đúng; bỏ miễn kiểm giá trị đã lưu; bỏ yêu cầu thuộc danh mục và dùng được; bỏ trần số trục; bỏ kiểm rút giá trị trục đang dùng; bỏ khóa tập mẫu làm trục; mô tả bị bỏ ký tự `<`; ép cả mẫu không dùng được; thương hiệu cũ đã ngừng vẫn bị kiểm; `recordVariantChange` không đổi gì.
- Số test: **351 xanh, 0 lỗi** (23 lớp, đọc từ báo cáo mới). Các số 326 (feature 06) và 336 (bước 3) báo trước đây cộng cả báo cáo cũ còn sót của các lớp test đã bị xóa; số thật trước bước 4 là 294.
- Đã chốt (`2026-11-05`, người dùng): `Patch<T>` có ở hai BC (Người bán, Danh mục), **giữ bản sao cục bộ**, không nâng lên `libs`; người dùng sẽ làm thư viện theo dõi trường thay đổi riêng sau, khi đó thay cả hai bản một lần.

### Bước 5. Biến thể kèm khóa dòng · Người (khóa), Agent (phần còn lại)

Việc: test KT-02 và phần KT-03 trước; rồi `Variant`, `VariantCombinationPolicy` (đọc trục từ các thuộc tính `axis` của sản phẩm), `AddVariant`, `UpdateVariant`, `DeactivateVariant`, `ActivateVariant`; kho có `findOwnedForUpdate`.

Xong khi: KT-02 và KT-03 xanh; phá thử (bỏ kiểm giá trị trong tập trục, bỏ đếm 50, bỏ kiểm "không rút giá trị trục đang dùng", lệnh biến thể gọi đường không khóa) làm test đỏ.

**Triển khai (`2026-11-05`):** xong miền và use case biến thể; chưa gắn vào ứng dụng (như bước 4).
- Miền: `Variant` (tạo, đổi giá, đổi hoặc bỏ SKU, ngừng, bật lại; khóa tổ hợp `keyOf` không phụ thuộc thứ tự cặp), `VariantAttributePair`, `VariantCombinationPolicy` (đọc trục từ các thuộc tính `axis` của sản phẩm), `VariantRepository`. Use case: `AddVariant`, `UpdateVariant`, `DeactivateVariant`, `ActivateVariant`, `VariantView`; bước đầu chung `VariantLocator` (kiểm vai, đọc khóa dòng sản phẩm của chính chủ). `ProductView` nay có `variants`.
- Lệch kế hoạch: bỏ cổng `VariantUsageReader` của bước 4; `VariantUsage.of(biến thể)` tính thẳng từ `VariantRepository.findByProductId` (tối đa 50 dòng), `UpdateProduct` và `GetMyProduct` nhận `VariantRepository`. Đã xóa mã cũ bị thay: `VariantCombination` và bốn use case biến thể cũ; phần lưu trữ cũ (`infrastructure`) chưa đụng, viết lại ở bước 6.
- Thứ tự trong `AddVariant`: kiểm vai và khóa dòng, đếm (kể cả biến thể đã ngừng), kiểm tổ hợp, dựng biến thể (kiểm giá và SKU), kiểm sớm tổ hợp trùng, SKU trùng, ghi biến thể, `recordVariantChange`, ghi sản phẩm.
- Test viết trước mã: `VariantTest` (10), `VariantCombinationPolicyTest` (9), `VariantUseCaseTest` (15), `VariantUsageTest` (4). Cả ca xanh ngay lần đầu, nên phá thử **15/15** lỗi làm test đỏ (chạy lại đúng cách `2026-11-05` như ghi ở bước 4, có ca đối chứng): lệnh biến thể đọc không khóa; bỏ kiểm vai; bỏ trần số biến thể; `AddVariant` không tăng phiên bản; bỏ kiểm tổ hợp trùng; bỏ kiểm SKU trùng; khóa tổ hợp phụ thuộc thứ tự; bỏ kiểm giá trị trong tập trục; bỏ kiểm thiếu trục; đổi SKU không loại chính nó; ngừng lặp không lỗi; bỏ trần giá; `VariantUsage` bỏ qua biến thể đã ngừng; `UpdateVariant` không đổi gì vẫn tăng phiên bản; bỏ kiểm trục tồn tại.
- Số test: **389 xanh, 0 lỗi** (27 lớp, đọc từ báo cáo mới).
- Chưa chứng minh được ở mức này: tổ hợp trùng và SKU trùng khi đua nhau (ràng buộc cơ sở dữ liệu), vượt 50 khi đua nhau: KT-06 ở bước 7.

### Bước 6. Lưu trữ, API, bộ xử lý lỗi, bảo mật · Agent (theo khung), Người rà

Việc: JPA entity và adapter (`@Transactional(MANDATORY)`, `saveAndFlush`); controller `/api/seller/products` (`tech.md` T5); ánh xạ `uq_variant_product_combination` và `uq_variant_seller_sku` ở `PersistenceConflictExceptionHandler`; kiểm KT-04, KT-05, KT-07, KT-08.

Xong khi: KT-04, KT-05, KT-07, KT-08 xanh trên Postgres cách ly; phá thử bỏ từng ràng buộc `V2` làm test tương ứng đỏ; không có `permitAll`, không có đường xóa hay sửa tổ hợp.

**Triển khai (`2026-11-05`):** xong; ứng dụng khởi động với `V2`, `ddl-auto=validate` khớp, mọi use case sản phẩm và biến thể đã gắn `@Service`.
- Lưu trữ: `ProductJpaEntity` (`@Version`), `ProductAttributeJpaEntity`, `ProductAttributeOptionJpaEntity`, `VariantJpaEntity` (không phiên bản), `VariantCombinationItemJpaEntity`, các kho JPA, bộ chuyển đổi, `ProductPersistenceAdapter`, `VariantPersistenceAdapter` (`saveAndFlush`, `@Transactional(MANDATORY)`; `findOwnedForUpdate` dùng `PESSIMISTIC_WRITE` = `SELECT … FOR UPDATE`). Thay thuộc tính: so với bản đã lưu, khác mới xóa rồi chèn lại (xóa bằng một câu lệnh và dọn ngữ cảnh lưu trữ để chèn lại cùng khóa không bị gộp nhầm). Ghi cặp tổ hợp chỉ khi biến thể mới. Danh sách đọc thẳng bảng, nối biến thể để lấy số lượng và khoảng giá.
- Trình bày: `ProductController` (4 đường), `VariantController` (4 đường), `SellerBodyReader` (đọc `JsonNode`: phân biệt vắng / giá trị / null, giá phải là số nguyên, mã phải là UUID; trường chủ, trạng thái, `version` bị bỏ qua), `ProductResponse`. `SecurityConfig` không đổi; `PersistenceConflictExceptionHandler` thêm `uq_variant_product_combination`, `uq_variant_seller_sku`.
- Hai điều chỉnh miền: `Product.recordVariantChange` làm `updatedAt` tăng nghiêm ngặt (cùng một vi giây hoặc đồng hồ cố định vẫn làm dòng đổi thật, để `@Version` luôn tăng); `Patch<T>` giữ bản sao cục bộ (đã chốt).
- Test (viết **sau** mã vì phần lớn là cầu nối; phá thử bù): `ProductApiTest` (16), `VariantApiTest` (14), `ProductSchemaConstraintTest` (13), `SellerRollbackTest` (2), thêm một ca vào `PersistenceConflictExceptionHandlerTest`; `CatalogApiTestBase`, `CatalogSchemaConstraintTest` dọn thêm năm bảng mới. Cấu trúc test dựng bằng chính API quản trị của feature 05. Hai kỳ vọng sai của tôi sửa khi chạy lần đầu: cận trên tiêu đề và SKU bị kiểu cột `VARCHAR` chặn trước ràng buộc `CHECK` (nên cận dưới mới là việc của `CHECK`), và hộp thư không rỗng sẵn vì API quản trị đã ghi kiểm toán khi dựng cấu trúc (so trước và sau thay vì so với 0).
- Phá thử **15/16** làm test đỏ, một lỗi sống sót là **lỗi tương đương** (chạy lại đúng cách `2026-11-05`, có ca đối chứng): bỏ `WHERE sku IS NOT NULL` của chỉ mục SKU không đổi hành vi vì Postgres coi mọi `NULL` là khác nhau trong chỉ mục duy nhất; mệnh đề giữ lại để nói rõ ý và khớp `data.md`. Các lỗi đỏ: bỏ từng ràng buộc `V2` (tổ hợp duy nhất, SKU duy nhất, SKU không còn từng phần, giá, trục không giá trị rời, khóa ngoại biến thể thành CASCADE, độ dài tiêu đề); bỏ ánh xạ SKU trùng; giá thập phân được nhận; đọc sản phẩm không lọc theo chủ; không ghi tùy chọn thuộc tính; luôn bỏ qua ghi lại thuộc tính; không ghi cặp tổ hợp; danh sách cũ nhất trước; danh sách lấy sản phẩm mọi người bán; sửa thuộc tính đọc không khóa.
- Số test: **435 xanh, 0 lỗi** (31 lớp, đọc từ báo cáo mới).
- Chưa chứng minh (chờ bước 7): hai yêu cầu cùng lúc cho tổ hợp trùng, SKU trùng giữa hai sản phẩm, vượt 50, `version` tăng đúng số lệnh; ca duy nhất ở mức này là khóa dòng có mặt trong adapter, nhưng chỉ KT-06 chứng minh nó chặn được đua nhau. KT-07 hoàn tác chỉ thử với lỗi ép ở lần ghi sản phẩm (sau khi ghi biến thể).

### Bước 7. Đua nhau và ADR-0012 · Người

Việc: KT-06 trên Postgres thật (20 luồng: vượt 50, `version` đúng 20, cùng tổ hợp, cùng SKU hai sản phẩm, sửa tiêu đề đua với thêm biến thể, đổi trục đua với thêm biến thể). Phá thử: bỏ `FOR UPDATE` làm ca vượt 50 đỏ; bỏ `recordVariantChange` làm ca `version` đỏ. Khi đạt, ADR-0012 chuyển Accepted (đổi README ADR cùng lúc).

Xong khi: KT-06 xanh, hai lần phá thử đều đỏ; ADR-0012 Accepted.

**Triển khai (`2026-11-05`):** xong; ADR-0012 chuyển **Accepted**.
- `SellerConcurrencyTest` (8 ca, Postgres thật, 20 luồng mỗi ca, không luồng nào 5xx): sản phẩm có 49 biến thể thêm 20 tổ hợp khác nhau → đúng một 201, 19 `VARIANT_LIMIT_REACHED`, 50 biến thể, `version` 50; sản phẩm trống thêm 20 tổ hợp → cả 20 thành công, `version` đúng 20; cùng tổ hợp → một 201, 19 `VARIANT_COMBINATION_TAKEN`; cùng SKU ở hai sản phẩm của một người bán → một 201, 19 `VARIANT_SKU_TAKEN`; cùng SKU ở hai người bán → cả hai thành công; 20 lần đổi giá → `version` +20; 20 sửa tiêu đề đua 20 thêm biến thể → đủ 20 biến thể, `version` bằng số lệnh thành công, bên sửa chỉ gặp 409 `CONCURRENT_UPDATE`; 12 cặp (bỏ giá trị trục, thêm biến thể dùng giá trị đó) → mỗi cặp hoặc sửa trục thắng và biến thể bị `VARIANT_COMBINATION_INVALID`, hoặc biến thể thắng và sửa trục bị `PRODUCT_AXIS_VALUE_IN_USE`, không bao giờ có biến thể ngoài tập trục.
- Phá thử **7/7** (có ca đối chứng không đổi gì ra xanh), số ca đỏ trên tổng 8: bỏ `FOR UPDATE` 5; `AddVariant` bỏ `recordVariantChange` 4; `UpdateVariant` bỏ `recordVariantChange` 1; bỏ `@Version` 8; `recordVariantChange` không tăng nghiêm ngặt 5; bỏ đếm 50 1; `UpdateProduct` đọc không khóa khi sửa thuộc tính 1. Hai lỗi theo ADR (bỏ `FOR UPDATE` làm ca vượt 50 đỏ; bỏ `recordVariantChange` làm ca `version` đỏ) đều đạt.
- Ca `axisChangeRacesWithAddingVariant` phụ thuộc lịch chạy luồng: bỏ khóa dòng ở `UpdateProduct` chỉ làm đỏ ca này (1 ca) và chưa chắc ở mọi lần chạy; khóa dòng do `FOR UPDATE` ở kho (đỏ 5 ca khi bỏ) mới là chứng cứ chắc.
- Số test: **443 xanh, 0 lỗi** (32 lớp).

### Bước 8. Chạy thật (KT-09) · Người

Ngăn xếp cách ly (như feature 06: Postgres, ứng dụng, máy chủ khóa thử, token ký thật; không Kafka; không chạm `infra-*`): tạo sản phẩm với thuộc tính và trục, 20 yêu cầu đồng thời thêm biến thể, đọc lại; vai khác 403; sản phẩm người khác 404. Gỡ sạch sau khi xong.

Xong khi: KT-09 đạt; ngăn xếp gỡ sạch.

**Triển khai (`2026-11-05`):** KT-09 đạt **30/30**; ngăn xếp gỡ sạch. Dựng `probe-catalog` (chỉ Postgres 16, máy chủ khóa thử, jar dựng lại từ mã hiện tại; Flyway áp V1 và V2 trên cơ sở dữ liệu trống; không Kafka, không chạm `infra-*` hay `catalog_db` dev). Kịch bản `kt09-product.js` (ở file tạm, không vào repo), token ký thật, cấu trúc dựng bằng chính API quản trị.
- **Xác thực, vai:** không token 401; token ký sai khóa 401; `ADMIN` tạo, đọc, thêm biến thể đều 403 `CATALOG_ROLE_NOT_ALLOWED`.
- **Tạo sản phẩm:** 201, bỏ qua `status`, `sellerId`, `version` gửi lên (luôn `DRAFT`, version 0, chủ lấy từ token, kiểm ở cơ sở dữ liệu); `<`, `&`, `<script>` lưu nguyên; `NO_ACTIVE_VARIANT` có trước và hết sau khi có biến thể.
- **20 yêu cầu đồng thời, 20 tổ hợp khác nhau** (5 màu × 4 cỡ, hai trục): cả 20 là 201, không 5xx; đọc lại có 20 biến thể, `version` 20; cơ sở dữ liệu 20 khóa tổ hợp khác nhau, 40 dòng tổ hợp.
- **Đua nhau:** cùng một tổ hợp 20 luồng: đúng 1 lần 201, 19 lần `VARIANT_COMBINATION_TAKEN`, `version` 1; cùng SKU trên hai sản phẩm của một người bán 20 luồng: đúng 1 lần 201, 19 lần `VARIANT_SKU_TAKEN`.
- **Người khác:** đọc, sửa, thêm biến thể, ngừng biến thể của sản phẩm người khác đều 404, cùng nội dung với mã không tồn tại, dữ liệu không đổi; biến thể gắn sai sản phẩm 404.
- **Quy tắc:** giá trị ngoài tập trục 400 `VARIANT_COMBINATION_INVALID`; ngừng lần hai 409 `CATALOG_INVALID_STATE` và `version` chỉ tăng 20 → 21 (lần lỗi không tăng); `DELETE` biến thể không có đường; bỏ giá trị trục đang dùng 409 `PRODUCT_AXIS_VALUE_IN_USE`; không có hàng hộp thư nào của sản phẩm hay biến thể.
- Nhật ký ứng dụng không có `ERROR`, không có 500; 22 lần `POST …/variants` 201 khớp 20 + 1 + 1 của kịch bản.
- Không phát hiện lỗi của hệ thống. `catalog_db` dev đã xóa và dựng lại `2026-11-05` theo yêu cầu của người dùng: Flyway áp V1, V2, nạp `infra/seed/catalog.sql` (10/13/35/22/57), bảng sản phẩm và biến thể trống.
- Chưa kiểm ở chạy thật: Kafka và Connect (feature 07 không phát event); 50 biến thể và biên 49 → 50 chỉ ở Testcontainers (`SellerConcurrencyTest`).

### Bước 9. Đóng · Người, Agent

Điền cột "Triển khai"; `deferred.md` (nửa "đã duyệt" của `INV-CAT-30` và event `EVT-CAT-01`, `EVT-CAT-02` sang feature 09; giới hạn ảnh sang 08); `INV-CAT-98` nửa sản phẩm: thêm `hasProductReference` ở kho sản phẩm và lần kiểm trong `DeleteCategory` (ghi ở `05/deferred.md`) kèm test; `service.md` chuyển phần của 07 sang Đã làm; `data.md`, `api.yaml` đối chiếu lần cuối; `00-features.md` đóng feature 07 kèm bài học.

Xong khi: cả bộ test catalog xanh; tài liệu khớp mã; mục "Chỗ lệch đã biết" ghi rõ điều chưa làm.

**Triển khai (`2026-11-05`):** xong.
- **`INV-CAT-98` nửa sản phẩm:** `ProductRepository.existsByCategory` (không đặt `hasProductReference` ở kho danh mục như `05/deferred.md` ghi trước đó: kho sản phẩm là nơi biết sản phẩm), `DeleteCategory` kiểm sau lần kiểm con, mã lỗi mới `CATEGORY_HAS_PRODUCTS` (409). Không có khóa ngoại giữa hai aggregate; đua giữa xóa danh mục và tạo sản phẩm vào nó là rủi ro chấp nhận được (`service.md`). Mã do `agy` viết theo đặc tả, Claude rà diff, siết một assertion yếu (kiểm danh mục còn trong cây), chạy lại. Phá thử 5/5 đỏ (bỏ kiểm; đảo thứ tự kiểm; kho luôn `false`; kho luôn `true`; sai mã lỗi), ca đối chứng xanh. Dòng "đã từng có sản phẩm" đã bỏ khỏi `05/deferred.md` vì đã làm.
- **Tài liệu:** `deferred.md` của 07 chỉ có một dòng (nửa "người bán đã duyệt" của `INV-CAT-30` → 09); ảnh, đăng/gỡ/xóa nháp và hai event nằm ngoài phạm vi 07 nên không ghi. `service.md`, `data.md`, `api.yaml` chuyển phần của 07 sang Đã làm (aggregate Sản phẩm: Đã làm phần thông tin, thuộc tính, trục; Dự kiến phần ảnh, đăng, event); `api.yaml` kiểm lại bằng `js-yaml`; cột "Triển khai" của bảng ở đầu tệp đã điền.
- **Test:** **446 xanh, 0 lỗi** (32 lớp).

**Chỗ lệch đã biết / chưa làm:** `Patch<T>` giữ bản sao cục bộ theo quyết định của người dùng (không phải lệch); ca `axisChangeRacesWithAddingVariant` phụ thuộc lịch luồng; không chạy thật Kafka/Connect (07 không phát event).

## Bài học

- **Phá thử bằng script trên Windows:** `spawnSync('./mvnw', {shell:true})` chạy qua `cmd.exe` và lỗi im lặng, mọi lần đều báo "đỏ" giả. Phải gọi `bash -c` và luôn có ca đối chứng không đổi gì (phải ra xanh).
- **Báo cáo surefire cũ:** lớp test đã xóa để lại tệp `.txt` cũ làm sai tổng; xóa báo cáo trước khi chạy và chỉ đếm báo cáo mới.
- **Lỗi "value too long" đến trước CHECK:** giới hạn trên của cột `VARCHAR` chặn trước ràng buộc CHECK; test ràng buộc phải kỳ vọng đúng lỗi nào bắt trước.
- **Chỉ mục duy nhất có điều kiện:** `WHERE sku IS NOT NULL` không đổi hành vi ở Postgres (NULL luôn khác nhau); đó là lỗi tương đương khi phá thử, không phải lỗ hổng test.
- **Hạ tầng bước 8:** tắt tiến trình bằng PowerShell theo mẫu dòng lệnh có thể tắt luôn chính tiến trình đang chạy lệnh; lọc theo tên tệp cụ thể và chạy phần gỡ container ở lệnh riêng.
- **`agy`:** một lượt giao việc 8 tệp tốn ~870 giây, 442k token vào (phần lớn đọc lại ngữ cảnh), kết thúc `ERROR` do mạng đứt nhưng đã ghi đủ tệp; luôn kiểm diff chứ đừng tin trạng thái. Ghi sớm: viết đặc tả nhỏ hơn (ít tệp mỗi lượt) sẽ nhanh hơn.
