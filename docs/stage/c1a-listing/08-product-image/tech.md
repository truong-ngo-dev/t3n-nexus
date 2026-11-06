# Feature 08: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho mọi BC đã có: [ADR-0001](../../../global/2.architecture/adr/0001-entity-id-uuid-v7.md) (UUID v7), [ADR-0003](../../../global/2.architecture/adr/0003-login-session-and-identity-contract.md) (hợp đồng danh tính), [ADR-0011](../../../global/2.architecture/adr/0011-optimistic-lock-and-storage-uniqueness.md) (khóa lạc quan, ràng buộc ở lưu trữ), [ADR-0012](../../../global/2.architecture/adr/0012-parent-row-lock-for-child-operations.md) (khóa dòng sản phẩm). Quyết định riêng của feature này: [ADR-0013](../../../global/2.architecture/adr/0013-shared-file-upload-mechanism.md) (Proposed `2026-11-05`). Nghiệp vụ: `catalog-service/analysis/product.md` (CAP-CAT-06, INV-CAT-34).

## Câu hỏi đã chốt

| # | Câu hỏi | Quyết định | Chốt |
|---|---|---|---|
| Q1 | Tải ảnh lên thế nào | **Xin chỗ → tải thẳng lên kho đối tượng bằng form ký sẵn → xác nhận.** Dịch vụ không nhận nội dung ảnh | `2026-11-05` (theo `service.md`; người dùng đồng ý) |
| Q2 | Ép loại và dung lượng | **POST policy** của MinIO: khóa đúng một giá trị, `Content-Type` đúng loại đã khai, `content-length-range` từ 1 đến giới hạn. Lúc xác nhận kiểm lại: có tồn tại, dung lượng thật, chữ ký tệp (không tin kiểu do client khai) | `2026-11-05` (người dùng đồng ý đề xuất) |
| Q3 | "Ảnh được tải lên cho chính sản phẩm này" | Khóa đối tượng **do dịch vụ sinh** (`products/{mã sản phẩm}/{mã chỗ}`) và ghi lại "chỗ đã cấp" (ai, sản phẩm nào). Xác nhận nhận **mã chỗ**, không nhận khóa từ client | `2026-11-05` |
| Q4 | Người xem ảnh | Bucket ảnh sản phẩm **đọc công khai**; dịch vụ chỉ lưu khóa, URL ghép từ địa chỉ gốc cấu hình. Không URL ký để đọc | `2026-11-05` (người dùng đồng ý đề xuất) |
| Q5 | Cơ chế chung | **Thư viện `upload-starter`** trong `libs`, cấu hình theo **mục đích** (`product-image`...), `claim` cùng giao dịch của BC. Chưa tách dịch vụ Quản lý tệp; ngưỡng tách ghi ở ADR-0013 | `2026-11-05` (người dùng đồng ý) |
| Q6 | Chống đua | Mọi lệnh ảnh khóa dòng sản phẩm (ADR-0012); gọi kho đối tượng ở **pha 1, ngoài khóa** (ADR-0012 mục "Hệ quả": không gọi dịch vụ ngoài khi giữ khóa) | `2026-11-05` |
| Q7 | Ảnh có tăng `version` sản phẩm, có phát event | **Tăng `version`** (một ảnh là một thay đổi của sản phẩm, INV-CAT-36). **Không phát event** (`EVT-CAT-01` thuộc 09); không ghi kiểm toán (hành động của người bán) | `2026-11-05` (theo mẫu 07) |
| Q8 | Giới hạn | Tối đa 9 ảnh, JPEG/PNG/WebP, 5 MB mỗi ảnh, chỗ hiệu lực 5 phút; tất cả cấu hình được (T2, T3). `Q-CAT-01` | `2026-11-05` (theo bảng phân tích) |
| Q9 | Tần suất | **Chưa gắn `@RateLimit`** (catalog chưa có thư viện giới hạn tần suất và Redis). Chặn bằng hạn mức chỗ đang chờ mỗi sản phẩm (`UPLOAD_PENDING_LIMIT`); giới hạn tần suất thật ở cổng truy cập (feature 10), ghi `deferred.md` | `2026-11-05` |
| Q10 | Đối chiếu mã cũ | Theo quy tắc của feature 05, 07: ghi `docs/draft/_so-sanh-catalog.md` (mục 16 trở đi). Mã cũ đang `//~` (`MinioConfig`, `MinioProperties`, `MinioObjectStorage`, ba use case ảnh, `ProductImage`) **viết lại theo thiết kế này**, không hoàn lại nguyên văn (mã cũ không kiểm kiểu, dung lượng, không đối chiếu khóa client gửi lên) | `2026-11-05` |
| Q11 | Dữ liệu hạ tầng dev | Không khởi động MinIO dev (`infra-minio-1`, đang dừng). Kiểm chứng bằng MinIO cách ly trong Testcontainers và ngăn xếp `probe-` | `2026-11-05` |

## T1. Cơ chế chung: `upload-starter` · Dự kiến

Module mới `services/libs/upload-starter`, khai trong `libs/pom.xml`; tự cấu hình qua `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (cùng cách `outbox-starter`). Gói `vn.t3nexus.lib.upload`. Phụ thuộc: `io.minio:minio:9.0.1` (đã có trong kho Maven offline, `identity-service` dùng), `rate-limiter-starter` không cần (BC tự gắn `@RateLimit`).

**Giao diện cho BC gọi:**

| Hàm | Việc |
|---|---|
| `issue(IssueRequest(purpose, accountId, targetId, contentType, size))` → `Issued(slotId, objectKey, url, fields, expiresAt, maxSize)` | Kiểm kiểu và dung lượng theo mục đích; ghi chỗ; ký form |
| `verify(slotId, purpose, accountId, targetId)` → `Verified` | **Pha 1, gọi kho đối tượng, không chạm dòng nghiệp vụ.** Kiểm chỗ có, đúng người, đúng đối tượng, còn hạn; `stat` đối tượng; đọc đầu tệp kiểm chữ ký. Sai thì xóa đối tượng và chỗ |
| `consume(Verified)` → `Claimed(objectKey, contentType, size)` | **Pha 2, chỉ cơ sở dữ liệu**, chạy trong giao dịch của BC: xóa dòng chỗ. Dòng đã bị ai xóa thì báo như không có chỗ |
| `discard(objectKey)` | Xóa đối tượng (sau khi bỏ ảnh, hoặc dọn đối tượng đã kiểm mà pha 2 thất bại); lỗi chỉ ghi log |
| `urlOf(purpose, objectKey)` | Ghép URL công khai (nếu mục đích công khai) |

Mã lỗi của thư viện (ném `UploadException(code)`; BC ánh xạ thành phản hồi qua bộ xử lý lỗi chung, trạng thái do thư viện quy định): `UPLOAD_TYPE_NOT_ALLOWED` 400, `UPLOAD_SIZE_INVALID` 400, `UPLOAD_SLOT_NOT_FOUND` 404, `UPLOAD_SLOT_EXPIRED` 409, `UPLOAD_PENDING_LIMIT` 409, `UPLOAD_NOT_COMPLETED` 422, `UPLOAD_CONTENT_INVALID` 422, `UPLOAD_STORAGE_UNAVAILABLE` 503.

**Cấu hình theo mục đích** (`upload.purposes.<tên>.*`): `bucket`, `public-read`, `public-base-url`, `allowed-types`, `max-size`, `ttl-seconds`, `max-pending` (số chỗ chờ tối đa mỗi cặp người và đối tượng). Kết nối: `app.minio.endpoint`, `access-key`, `secret-key` (cùng tên cấu hình với `identity-service`). Khi khởi động: tạo bucket nếu chưa có, đặt policy đọc công khai cho mục đích công khai (như `MinioConfig` cũ).

**Bảng `upload_slot`** (mẫu schema `libs/upload-starter/src/main/resources/db/upload-schema.sql`, mỗi dịch vụ chép vào migration của mình, như `outbox-schema.sql`): `id` (UUID v7, là mã chỗ), `purpose` (40), `account_id`, `target_id`, `object_key`, `content_type`, `declared_size`, `expires_at`, `created_at`. Ràng buộc: `pk_upload_slot`, `uq_upload_slot_object_key`, `ck_upload_slot_size (declared_size > 0)`, `idx_upload_slot_target (target_id)`, `idx_upload_slot_expires (expires_at)`. Chỗ đã dùng thì bị **xóa** (không có cột trạng thái).

**Kiểm chữ ký** (hàm thuần `FileSignature`): JPEG bắt đầu `FF D8 FF`; PNG `89 50 4E 47 0D 0A 1A 0A`; WebP `RIFF` ở byte 0 đến 3 và `WEBP` ở byte 8 đến 11. Kiểu thật phải khớp kiểu đã khai lúc xin chỗ.

**Việc dọn:** `@Scheduled` trong starter xóa các chỗ hết hạn quá một khoảng ân hạn cùng đối tượng của chúng, từng đợt, an toàn khi nhiều bản chạy (xóa lặp vô hại). Khoảng chạy cấu hình được. Tệp mồ côi do `discard` lỗi chỉ được dọn nếu còn dòng chỗ; phần còn lại cần quy tắc vòng đời của bucket (ghi `deferred.md`).

Phương án đã loại: dịch vụ Quản lý tệp ngay (gọi mạng trong lúc giữ khóa sản phẩm, hoặc hai pha có bù trừ; xem ADR-0013); PUT ký sẵn (không ép được dung lượng ở MinIO, tệp lớn đã nằm trên kho rồi mới bị từ chối); client gửi khóa đối tượng lúc xác nhận (mã cũ: gắn được khóa của người khác).

## T2. Dữ liệu: migration `V3` · Dự kiến

`V3__create_product_image.sql` thêm bảng; không sửa `V1`, `V2`. Quy ước như `data.md`.

| Bảng | Cột chính | Ràng buộc có tên |
|---|---|---|
| `upload_slot` | như T1 | như T1 (chép từ mẫu schema) |
| `product_image` | `id`, `product_id`, `object_key` (200), `content_type` (20), `size_bytes` (`BIGINT`), `position` (`SMALLINT`, từ 0), `created_at` | `pk_product_image`; `fk_product_image_product` → `product` (`CASCADE`, cùng aggregate); `uq_product_image_position (product_id, position)`; `uq_product_image_object_key (object_key)`; `ck_product_image_position (position >= 0)`; `ck_product_image_size (size_bytes > 0)`; `idx_product_image_product (product_id)` |

Tối đa 9 ảnh không có CHECK (đếm theo hàng): giữ bằng khóa dòng sản phẩm (ADR-0012), cùng cách tối đa 50 biến thể. `fk_variant_product` ở `V2` là `RESTRICT`; `fk_product_image_product` là `CASCADE` vì ảnh cùng aggregate với sản phẩm (xóa nháp ở 09 xóa biến thể tường minh, ảnh theo sản phẩm).

Sắp xếp và bỏ ảnh viết lại **toàn bộ** danh sách ảnh của sản phẩm (xóa hàng loạt rồi chèn lại, chỉ khi khác bản đã lưu), cùng cách `replaceAttributes` ở 07, tránh va ràng buộc `uq_product_image_position` khi hoán đổi hai ảnh.

## T3. Miền · Dự kiến

- `Product` có thêm danh sách `ProductImage` (`id`, `objectKey`, `contentType`, `sizeBytes`, `position`). Hành vi: `addImage(image, limits)` thêm ở cuối, từ chối nếu đã đủ 9 (`IMAGE_LIMIT_REACHED`); `removeImage(imageId)` (`IMAGE_NOT_FOUND`); `reorderImages(ids)` (`IMAGE_ORDER_INVALID` nếu không nêu đúng mọi ảnh một lần). Vị trí do miền gán, luôn liền nhau từ 0.
- `recordVariantChange` đổi tên thành `recordChange` (dùng cho biến thể và ảnh; hành vi không đổi).
- `MissingPublishConditions` thêm `NO_IMAGE` (không có ảnh nào).
- `ProductLimits` thêm `maxImages` (mặc định 9, cấu hình `catalog.limits.max-images`; chỉ có thể siết xuống, không CHECK ở lưu trữ vì đếm hàng).
- Mã lỗi mới `ProductErrorCode`: `IMAGE_LIMIT_REACHED` 409, `IMAGE_NOT_FOUND` 404, `IMAGE_ORDER_INVALID` 409.

## T4. Use case và khóa · Dự kiến

| Use case | Việc | Khóa |
|---|---|---|
| `RequestImageUpload` | `requireSeller`; đọc sản phẩm của mình; đếm ảnh; gọi `issue` | đọc thường (kiểm sớm) |
| `ConfirmImage` | `requireSeller`; **pha 1** `verify` (không giao dịch, không khóa); **pha 2** một giao dịch: `findOwnedForUpdate`, kiểm đủ 9, `consume`, `addImage`, `recordChange`, lưu. Pha 2 thất bại sau pha 1 thì `discard` đối tượng (best-effort) | khóa dòng ở pha 2 |
| `ReorderImages` | `requireSeller`; khóa; `reorderImages`; chỉ `recordChange` và lưu nếu thứ tự đổi | khóa dòng |
| `RemoveImage` | `requireSeller`; khóa; `removeImage`; `recordChange`; lưu; sau commit `discard` | khóa dòng |
| `GetMyProduct`, `ListMyProducts` | thêm `images` (URL qua `urlOf`) | không khóa |

Quy tắc chung (như 07): chủ lấy từ token; sản phẩm của người khác coi như không có (404); khóa lạc quan (`@Version`) cho mọi lệnh ngoài ADR-0012; mọi lệnh ảnh bắt buộc khóa dòng để `version` tăng tuần tự và đếm 9 chính xác.

## T5. API và bảo mật · Dự kiến

| Điểm cuối | Vai | Phản hồi |
|---|---|---|
| `POST /api/seller/products/{id}/images/upload-slots` `{contentType, size}` | `USER` | 201 `{slotId, url, fields, expiresAt, maxSize}` |
| `POST /api/seller/products/{id}/images` `{slotId}` | `USER` | 201 ảnh `{id, url, position, contentType, size}` |
| `PUT /api/seller/products/{id}/images/order` `{imageIds: [...]}` | `USER` | 200 danh sách ảnh theo thứ tự mới |
| `DELETE /api/seller/products/{id}/images/{imageId}` | `USER` | 200 danh sách ảnh còn lại |

Không có `permitAll`; vai kiểm trong use case (`CatalogActor.requireSeller`), như 07. Nội dung bỏ qua mọi trường ngoài danh sách trên. `ProductResponse` thêm `images`; `MissingCondition` có mã `NO_IMAGE`. `SellerBodyReader` đọc `slotId`, `contentType`, `size`, `imageIds`.

## T6. Dấu vết kiểm toán và hộp thư · Dự kiến

Không ghi hộp thư, không ghi kiểm toán (Q7). `version` sản phẩm tăng để 09 dùng.

## T7. Kiểm chứng · Dự kiến

Test viết từ `flow.md` mục 4 trước khi viết mã; sau mỗi bước, phá thử (đột biến) và thấy test đỏ. Mức: đơn vị (`FileSignature`, vòng đời chỗ với kho giả, miền, use case với kho giả), Testcontainers (migration, API, đua nhau, rollback, dọn; MinIO bằng `GenericContainer` với ảnh `quay.io/minio/minio` đã có trên máy, vì kho Maven chưa có mô-đun `testcontainers-minio`), chạy thật (ngăn xếp `probe-` thêm MinIO). Danh sách KT-01 đến KT-10 ở `flow.md` mục 6.

Đột biến bắt buộc: bỏ kiểm chữ ký tệp; bỏ kiểm kiểu thật khớp kiểu khai; bỏ kiểm chỗ đúng người; bỏ kiểm chỗ đúng sản phẩm; bỏ kiểm hạn; bỏ xóa chỗ khi `consume` (xác nhận hai lần ra hai ảnh); bỏ `FOR UPDATE` ở `ConfirmImage` (ca vượt 9 đỏ); gọi `verify` trong lúc giữ khóa (test nhận biết thứ tự gọi, KT-04, đỏ); bỏ `recordChange` (ca `version` đỏ); bỏ đếm 9; bỏ kiểm `reorder` đủ mọi ảnh; mô tả URL công khai sai tiền tố; bỏ từng ràng buộc `V3`.

## T8. Thứ tự làm · Dự kiến

1. Rà kịch bản (`flow.md` mục 4 với mục 6) và đối chiếu mã cũ (`_so-sanh-catalog.md` mục 16).
2. `upload-starter`: phần thuần (`FileSignature`, cấu hình mục đích, dựng khóa), cổng kho đối tượng, vòng đời chỗ với kho giả (KT-01, KT-02). **Giao `agy` theo khung.**
3. `upload-starter`: bộ cài MinIO thật, mẫu schema, việc dọn, tự cấu hình; test với MinIO Testcontainers.
4. Miền sản phẩm: ảnh, `recordChange`, `NO_IMAGE`, giới hạn (KT-03). **Giao `agy`.**
5. `V3`, lưu trữ ảnh, bộ cài kho (KT-05).
6. Use case, API, bộ xử lý lỗi, bảo mật (KT-04, KT-06, KT-08).
7. Đua nhau và dọn trên Postgres và MinIO thật (KT-07, KT-09); phá thử.
8. Chạy thật (KT-10).
9. Đóng: `deferred.md`, `service.md`, `data.md`, `api.yaml` (chuyển nhãn), `00-features.md`; đổi ADR-0013 sang Accepted nếu các phép kiểm đạt.
