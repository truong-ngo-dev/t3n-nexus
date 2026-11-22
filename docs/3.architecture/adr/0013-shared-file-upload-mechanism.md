# ADR-0013: Cơ chế tải tệp lên dùng chung (xin chỗ, tải thẳng lên kho, xác nhận)

> **Trạng thái:** Accepted `2026-11-05` · **Ngày:** `2026-11-05` · **Phạm vi:** mọi dịch vụ có người dùng tải tệp (ảnh) lên; dùng đầu tiên ở C1a: Danh mục hàng, ảnh sản phẩm (feature 08)
>
> Bổ sung cho [ADR-0012](0012-parent-row-lock-for-child-operations.md) (không gọi dịch vụ ngoài khi giữ khóa dòng). Chuyển Accepted `2026-11-05` sau khi các phép kiểm ở mục "Cần kiểm" đạt (feature 08).

## Bối cảnh

Sẽ có nhiều nơi tải ảnh lên (ảnh sản phẩm, ảnh đại diện, logo cửa hàng, ảnh đánh giá). Mỗi nơi nếu tự viết sẽ lặp cùng một nhóm việc dễ sai: ép loại và dung lượng, chứng minh tệp được tải lên cho đúng đối tượng bởi đúng người, dọn tệp bỏ dở, không để tệp lớn đi qua dịch vụ. Hiện có hai nơi đã nói chuyện thẳng với MinIO theo hai kiểu khác nhau: `identity-service` đẩy nội dung qua dịch vụ (kiểm kiểu theo lời khai của client), catalog (mã cũ) dùng URL ký sẵn nhưng không ép loại, dung lượng và nhận khóa đối tượng từ client lúc xác nhận.

## Quyết định

1. **Luồng chung ba bước:** *xin chỗ* (dịch vụ kiểm điều kiện, sinh khóa đối tượng, ghi "chỗ đã cấp", trả form POST đã ký) → *tải thẳng* (trình duyệt gửi tệp tới kho đối tượng, không qua dịch vụ) → *xác nhận* (dịch vụ kiểm tệp thật, rồi gắn vào đối tượng nghiệp vụ).
2. **Ép bằng chính kho đối tượng:** form là **POST policy** ràng buộc khóa đúng một giá trị, `Content-Type` đúng loại đã khai, `content-length-range`, và hạn ngắn. Kiểu và dung lượng sai bị kho từ chối ngay, tệp không nằm lại.
3. **Không tin lời khai khi xác nhận:** dịch vụ `stat` đối tượng (tồn tại, dung lượng thật) và đọc vài byte đầu kiểm **chữ ký tệp** (JPEG, PNG, WebP), kiểu thật khớp kiểu khai. Sai thì xóa đối tượng.
4. **Bằng chứng "tải lên cho đúng đối tượng bởi đúng người"** là *chỗ đã cấp*: khóa đối tượng do dịch vụ sinh theo đối tượng, xác nhận nhận **mã chỗ** (không nhận khóa từ client) và đối chiếu người, đối tượng. Mọi trường hợp không hợp lệ cùng trả 404 (không lộ sự tồn tại). Chỗ dùng xong thì bị xóa, nên không dùng lại được.
5. **Hai pha cho xác nhận, vì ADR-0012:** *pha 1* `verify` gọi kho đối tượng, **không giữ khóa**; *pha 2* `consume` chỉ chạm cơ sở dữ liệu, chạy trong giao dịch của BC dưới khóa dòng cha. Nếu pha 2 thất bại (đủ giới hạn, sản phẩm đổi) thì đối tượng đã kiểm được xóa nỗ lực tốt nhất, phần sót do việc dọn xử lý.
6. **Hình thức: thư viện `upload-starter`** trong `libs` (như `outbox-starter`), cấu hình theo **mục đích** (kiểu cho phép, dung lượng, bucket, đọc công khai hay riêng, hạn của chỗ). Bảng `upload_slot` nằm trong **cơ sở dữ liệu của chính dịch vụ dùng**, nên `consume` cùng giao dịch với việc gắn ảnh. BC giữ: ai được xin, hạn mức nghiệp vụ (tối đa 9 ảnh), gắn vào aggregate, mã lỗi cho người dùng.
7. **Ảnh sản phẩm đọc công khai** (bucket có policy đọc công khai, URL ghép từ địa chỉ gốc cấu hình). Mục đích riêng tư (giấy tờ) cần cơ chế đọc khác, ngoài phạm vi ADR này.
8. **Chưa tách dịch vụ Quản lý tệp.** Ngưỡng tách: cần tệp riêng tư với quyền đọc theo người; cần xử lý (thumbnail, quét virus) hoặc một nơi duy nhất quản dung lượng, vòng đời; hoặc từ ba loại tệp trở lên với quy tắc khác nhau. Giao diện `issue` / `verify` / `consume` giữ đủ hẹp để chuyển từ "thư viện tại chỗ" sang "gọi dịch vụ" mà không đổi mã nghiệp vụ của BC; phần phải thiết kế lại lúc đó là `consume` hai pha có bù trừ.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Dịch vụ Quản lý tệp ngay từ C1a | `consume` thành lời gọi mạng ngay trong giao dịch giữ khóa dòng sản phẩm (trái ADR-0012) hoặc phải làm hai pha có bù trừ; thêm một dịch vụ để dựng, triển khai, chạy thật, chưa có loại tệp riêng tư nào cần nó |
| Đẩy nội dung qua dịch vụ (kiểu avatar hiện tại) | Băng thông và bộ nhớ dịch vụ; tệp lớn đi qua ứng dụng; kiểm loại chỉ theo lời khai của client |
| URL PUT ký sẵn | Không ép được dung lượng và `Content-Type` ở kho; tệp quá lớn hoặc không phải ảnh đã nằm trên kho rồi mới bị từ chối |
| Xác nhận nhận khóa đối tượng từ client (mã cũ) | Người bán gắn được khóa của sản phẩm khác, hoặc của người khác, vào sản phẩm của mình |
| Gọi kho đối tượng trong lúc giữ khóa dòng sản phẩm | Giữ khóa trong lúc chờ mạng; kho chậm làm mọi lệnh của sản phẩm xếp hàng |
| URL ký có hạn để đọc ảnh | Ảnh sản phẩm vốn công khai; URL ký làm hỏng bộ nhớ đệm của trình duyệt và CDN, thêm việc ký cho mỗi lượt xem |

## Hệ quả

- Thêm module `upload-starter` và một mẫu schema `upload-schema.sql` mà mỗi dịch vụ chép vào migration (như `outbox-schema.sql`); đổi mẫu thì dịch vụ dùng phải thêm migration.
- Mỗi dịch vụ dùng thư viện cần MinIO và khóa truy cập; thư viện tạo bucket khi khởi động nên tài khoản MinIO phải có quyền tạo bucket (dev: `minioadmin`; môi trường khác sẽ chia quyền).
- Form ký gắn với địa chỉ MinIO mà **trình duyệt** gọi; khi địa chỉ công khai khác địa chỉ nội bộ, phải cấu hình riêng. Trình duyệt tải thẳng lên MinIO cần CORS cho bucket: việc của feature giao diện (11), ghi `deferred.md`.
- Tệp mồ côi vẫn có thể phát sinh (xóa đối tượng lỗi sau khi bỏ ảnh); việc dọn xóa theo dòng chỗ, phần còn lại cần quy tắc vòng đời của bucket (ghi `deferred.md`).
- Avatar của `identity-service` chưa dùng cơ chế này; chuyển khi đến lượt.

## Cần kiểm

- [x] POST policy trên MinIO thật ép được kiểu và dung lượng: tệp quá cỡ và sai kiểu bị chính MinIO từ chối; hết hạn bị từ chối.
- [x] Tệp giả đuôi ảnh (nội dung chữ) bị `verify` từ chối và đối tượng bị xóa.
- [x] Gắn ảnh bằng chỗ của người khác, của sản phẩm khác, hết hạn, đã dùng: đều bị từ chối, không gắn gì.
- [x] 20 luồng xác nhận cùng một chỗ: đúng một ảnh. Sản phẩm 8 ảnh, nhiều chỗ khác nhau xác nhận cùng lúc: đúng một thành công, không có 10 ảnh.
- [x] `verify` không bao giờ chạy trong lúc giữ khóa dòng (test thứ tự gọi, phá thử đỏ).
- [x] Phá thử: bỏ kiểm chữ ký, bỏ kiểm đúng người, bỏ xóa chỗ khi `consume`, bỏ `FOR UPDATE` ở pha 2 thì test đỏ.

## Tham khảo

- ADR-0011, ADR-0012. `service/catalog-service/analysis/product.md` (CAP-CAT-06, `INV-CAT-34`), `0.overview.md` mục 11 (tách Quản lý tệp).
- `stage/c1a-listing/08-product-image/tech.md` T1.

Kết quả (feature 08, `2026-11-05`): `MinioObjectStoreTest` (8 ca, MinIO thật) và `ProductImageApiTest` (10 ca, Postgres và MinIO thật) đạt cả sáu phép kiểm; chạy thật trên ngăn xếp cách ly đạt 35/35 (KT-10). Phá thử 23 lỗi cố ý, cả 23 làm test đỏ, ca đối chứng không đổi gì ra xanh. Hai điều học được khi làm: MinIO không trả hai trường `key` và `Content-Type` trong form ký, thư viện thêm chúng vào `fields` để client gửi nguyên; hai yêu cầu xác nhận cùng một chỗ thì bên thua **không** được xóa đối tượng (bên thắng đang giữ chính nó), chỉ xóa khi lỗi là đủ ảnh.
