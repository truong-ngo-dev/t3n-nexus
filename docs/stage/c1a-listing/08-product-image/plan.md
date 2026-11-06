# Feature 08: kế hoạch

> **Trạng thái:** Đã làm `2026-11-06` · Nguồn: [`flow.md`](flow.md), [`tech.md`](tech.md) (Q1 đến Q11 đã chốt), ADR-0013 (Proposed). Người dùng đang cần xong catalog nhanh để làm giao diện nên mỗi bước chạy liền, rà ở cuối, không dừng chờ duyệt giữa chừng.

## Từ luồng đến giải pháp

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai |
|---|---|---|---|
| Xin chỗ tải lên (3.1) | `RequestImageUpload` gọi `UploadSlots.issue`; hạn mức chỗ đang chờ | `upload-starter`, `RequestImageUpload` | Đã làm: `RequestImageUpload` → `UploadSlots.issue`; `UploadSlotsTest`, `ImageUseCaseTest`, `ProductImageApiTest`, chạy thật KT-10 |
| Kho đối tượng ép loại, dung lượng | POST policy MinIO | `MinioObjectStore` | Đã làm: `MinioObjectStore.presignPost` (khóa, loại, dung lượng, hạn); `MinioObjectStoreTest`; MinIO tự từ chối ở KT-10 |
| Xác nhận pha 1: kiểm tệp (3.2) | `UploadSlots.verify` (stat, chữ ký tệp), ngoài khóa | `UploadSlots`, `FileSignature` | Đã làm: `UploadSlots.verify`, `FileSignature`; `FileSignatureTest`, `UploadSlotsTest` |
| Xác nhận pha 2: gắn ảnh (3.2) | Một giao dịch: `findOwnedForUpdate`, `consume`, `Product.addImage`, `recordChange` | `ConfirmImage`, `Product` | Đã làm: `ConfirmImage` hai pha (`TransactionTemplate`), `Product.addImage`; `ImageUseCaseTest` (thứ tự gọi), đua 20 luồng cùng chỗ và 5 chỗ khi 8 ảnh |
| Sắp xếp, bỏ ảnh (3.3) | Khóa dòng; `reorderImages`, `removeImage`; xóa đối tượng sau commit | `ReorderImages`, `RemoveImage` | Đã làm: `ReorderImages`, `RemoveImage`; bỏ ảnh xóa đối tượng sau commit |
| Đọc kèm ảnh, `NO_IMAGE` (3.4) | `ProductAssembler` thêm `images`; `MissingPublishConditions` | `ProductAssembler`, `MissingPublishConditions` | Đã làm: `ProductAssembler`, `ProductResponse.images`, `NO_IMAGE`; `MissingPublishConditionsTest` |
| Lưu ảnh và chỗ | `V3`: `upload_slot`, `product_image`; viết lại danh sách ảnh khi đổi | `V3`, `ProductPersistenceAdapter` | Đã làm: `V3__create_product_image.sql`, `ProductImageJpaEntity`, `ProductPersistenceAdapter.saveImages`; `ProductImageSchemaConstraintTest` |
| Dọn chỗ hết hạn | `UploadCleaner` định kỳ | `upload-starter` | Đã làm: `UploadCleaner` + `UploadSlots.cleanExpired`; `UploadSlotsTest`, `ProductImageApiTest.expiredSlotAndCleaning` |

## Điều kiện bắt đầu

Feature 07 đã đóng (`catalog_db` dev ở `V2`). **Không khởi động MinIO dev**; kiểm chứng bằng MinIO cách ly (Testcontainers `GenericContainer` và ngăn xếp `probe-`). **`catalog_db` dev chưa áp `V3`** (ứng dụng sẽ áp khi người dùng khởi động lần tới; chưa chạy dịch vụ trên nó trong feature này).

Test chạy được: `./mvnw -o -q -pl catalog-service -am test -Dtest='vn/t3nexus/catalog/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false` từ `services/` (module thư viện: `-pl libs/upload-starter -am`), `rm -rf catalog-service/target/classes/db` trước mỗi lần. Phá thử bằng script chạy qua `bash -c`, luôn kèm ca đối chứng không đổi gì.

## Các bước

### Bước 1. Rà kịch bản, đối chiếu mã cũ · Người

`flow.md` mục 4 với mục 6 (mỗi E có ít nhất một KT); mã cũ đã đọc (ba use case ảnh, `MinioObjectStorage`): chỗ lệch là mã cũ không kiểm loại và dung lượng, và nhận khóa đối tượng từ client; thiết kế mới theo tài liệu, viết lại. **Triển khai (`2026-11-05`):** xong; không ghi `_so-sanh-catalog.md` (mã cũ không hoàn lại, ghi lý do ở `tech.md` Q10).

### Bước 2. `upload-starter`: phần thuần và vòng đời chỗ · Agent

`UploadErrorCode`, `FileSignature`, `UploadProperties`, `UploadSlots` (issue, verify, consume, discard, urlOf, cleanExpired) kèm test với kho giả (KT-01, KT-02). Cổng `ObjectStore`, `UploadSlotStore` do Người viết trước.

### Bước 3. `upload-starter`: bộ cài thật · Người

`MinioObjectStore`, `JpaUploadSlotStore`, tự cấu hình, mẫu schema, `UploadCleaner`; test với MinIO và Postgres thật (Testcontainers): POST policy ép loại và dung lượng, tệp giả đuôi ảnh, dọn chỗ hết hạn (KT-09).

### Bước 4. Miền sản phẩm: ảnh · Agent

`ProductImage`, `Product.addImage/removeImage/reorderImages`, `NO_IMAGE`, mã lỗi (KT-03). Đổi tên `recordVariantChange` → `recordChange` (Người đã làm).

### Bước 5. `V3`, lưu trữ, gắn thư viện vào catalog · Người

`V3`, `ProductImageJpaEntity`, `ProductMapper`, adapter ghi lại danh sách ảnh; cấu hình `upload.purposes.product-image`, `@EntityScan` thêm gói thư viện; `CatalogLimitsProperties` thêm `max-images` (KT-05).

### Bước 6. Use case, API, lỗi, bảo mật · Agent + Người

`RequestImageUpload`, `ConfirmImage` (hai pha), `ReorderImages`, `RemoveImage`, bốn điểm cuối, `ProductResponse.images`, ánh xạ lỗi thư viện; test use case với kho giả và API với Postgres và MinIO thật (KT-04, KT-06, KT-08).

### Bước 7. Đua nhau, dọn, phá thử · Người

20 luồng cùng một chỗ, 8 ảnh + nhiều chỗ khác nhau, `version` đúng; phá thử theo danh sách ở `tech.md` T7 (KT-07). ADR-0013 chuyển Accepted nếu đạt.

### Bước 8. Chạy thật (KT-10) · Người

Ngăn xếp `probe-` thêm MinIO: tải bằng form ký thật, đọc URL công khai, tệp quá cỡ bị MinIO từ chối. Gỡ sạch sau khi xong.

### Bước 9. Đóng · Người, Agent

`deferred.md`; `service.md`, `data.md`, `api.yaml` chuyển nhãn và thêm ảnh; điền cột "Triển khai"; `00-features.md` đóng 08 kèm bài học.

## Kết quả (`2026-11-05`)

- **Test:** `upload-starter` 43 ca (35 đơn vị với kho giả, 8 với MinIO thật), catalog 498 ca (35 lớp) xanh, 0 lỗi (số cuối `2026-11-06`). `ProductImageApiTest` 10 ca trên Postgres và MinIO thật, gồm 3 ca đua nhau (20 luồng cùng một chỗ; 8 ảnh + 5 chỗ khác nhau; thêm, bỏ, sắp xếp lẫn lộn, lặp 15 lần liền không lệch).
- **Phá thử:** 23 lỗi cố ý, cả 23 đỏ, ca đối chứng không đổi gì xanh. Hai lần đầu của script không tìm thấy đoạn cần sửa vì tệp có kết thúc dòng CRLF (dùng biểu thức chính quy `\s*` cho đoạn nhiều dòng). Một lỗi sống sót ở lượt đầu (bỏ kiểm dung lượng thật): test dùng nội dung toàn số 0 nên chữ ký sai bắt trước; sửa test để chỉ dung lượng là lý do từ chối.
- **Chạy thật KT-10:** 35/35 trên ngăn xếp cách ly (Postgres, MinIO, ứng dụng, token ký thật); nhật ký không có `ERROR` hay 500; ngăn xếp gỡ sạch. Không chạm `infra-*`, `catalog_db` dev hay MinIO dev.
- **Bắt được khi rà diff và khi viết test:** (1) đặc tả của Người bảo xóa đối tượng mỗi khi pha 2 lỗi; đúng là chỉ khi đủ ảnh, vì lỗi ở `consume` nghĩa là một yêu cầu khác đang giữ chính đối tượng (xóa sẽ làm mất ảnh vừa gắn); (2) MinIO không trả `key` và `Content-Type` trong form ký, thư viện thêm vào `fields`; (3) cột `position` `SMALLINT` không khớp `int` của entity (`ddl-auto=validate`), đổi `INTEGER`; (4) test sắp xếp đua nhau chạy hai lệnh cùng thứ tự, lệnh sau là thao tác rỗng không tăng `version` (lỗi của test, không phải của hệ thống).
- **Chưa kiểm ở chạy thật:** form hết hạn bị MinIO từ chối (đã kiểm ở Testcontainers với hạn 1 giây), việc dọn định kỳ theo lịch (chỉ gọi trực tiếp `cleanExpired`), CORS cho trình duyệt (feature 11).

## Chỗ lệch đã biết

- Chưa gắn `@RateLimit` ở điểm cuối xin chỗ (catalog chưa có Redis); ghi `deferred.md`, giới hạn thật ở cổng truy cập (feature 10).
- `catalog_db` dev **chưa áp `V3`**: ứng dụng sẽ áp khi bạn khởi động lần tới (cần MinIO dev đang chạy để tạo bucket; thiếu MinIO thì chỉ cảnh báo, lần xin chỗ đầu báo 503).
- Mã cũ `//~` của ảnh còn lại (`SkuImage*`, `GetProduct...`) thuộc feature khác, không đụng.

## Bài học

- **Giao `agy` việc có đặc tả sai thì mã sai đúng như đặc tả:** lỗi xóa đối tượng nằm trong đặc tả của Người, agy làm đúng; chỉ rà diff và test đua nhau mới bắt. Đặc tả nên nêu "khi nào KHÔNG làm" cho mọi thao tác phá hủy.
- **Đồng thời hai agy trên hai worktree** (thư viện và miền catalog) chạy song song được vì tệp không giao nhau; phần phụ thuộc chéo (cổng `ObjectStore`, `UploadSlotStore`) do Người viết trước.
- **`src/test/resources/application.properties` che mất `application.properties` chính** (cùng tên trên classpath): không dùng để đặt cấu hình test; dùng `@DynamicPropertySource` hoặc tên khác.
- **Kho Maven offline:** thư viện MinIO phải ghim `okhttp-jvm 5.2.1` và `okio 3.9.0` như `identity-service`; không có `testcontainers-minio`, dùng `GenericContainer`.
