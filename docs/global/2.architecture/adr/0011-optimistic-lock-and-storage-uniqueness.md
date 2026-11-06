# ADR-0011: Khóa lạc quan chạy ngầm và tính duy nhất chốt ở lưu trữ

> **Trạng thái:** Accepted · **Ngày:** `2026-10-26` · **Chấp nhận:** `2026-10-27` · **Phạm vi:** BC có aggregate sửa được và quy tắc duy nhất giữa nhiều aggregate (C1a: Người bán; Danh mục hàng dùng lại ở feature 05)
>
> Chấp nhận sau khi feature 03 chạy thật: KT-06 đến KT-08 trên Postgres thật bằng Testcontainers, phá thử bỏ từng ràng buộc, và đua nhau 20 luồng trên Postgres thật ở stack cô lập (KT-13). Kết quả ở mục "Cần kiểm".

## Bối cảnh

Hồ sơ người bán (feature 03) là aggregate sửa được, kèm ba quy tắc duy nhất: mỗi tài khoản một hồ sơ (INV-SEL-01), tên gian hàng duy nhất toàn sàn (INV-SEL-04), mỗi chủ thể pháp lý một hồ sơ hiệu lực (INV-SEL-09). Hai người có thể đua nhau cùng tên, cùng khóa định danh; một người bấm hai lần hoặc sửa từ hai thiết bị. Phép "kiểm rồi ghi" trong use case không đủ: hai yêu cầu cùng qua phép kiểm rồi cùng ghi. Cần một quyết định chung chứ không để mỗi BC tự chọn (Danh mục hàng có quy tắc duy nhất tương tự: tên danh mục, thương hiệu).

## Quyết định

1. **Ràng buộc ở lưu trữ là chốt cuối của mọi quy tắc duy nhất.** Mỗi quy tắc có một ràng buộc duy nhất **có tên** trong cơ sở dữ liệu (`uq_<bảng>_<ý nghĩa>`). Quy tắc "chỉ tính hồ sơ hiệu lực" dùng chỉ mục duy nhất **một phần** (`WHERE status <> 'REJECTED'`), không dùng cột phụ.
2. **Use case vẫn kiểm sớm** qua kho (dịch vụ miền hỏi "đã dùng chưa") để trả thông báo rõ và tránh lỗi cơ sở dữ liệu ở đường thường. Phép kiểm sớm **không** thay ràng buộc.
3. **Vi phạm ràng buộc làm rollback cả giao dịch**, không bắt rồi thử tiếp trong use case. Ghi bằng `saveAndFlush` để vi phạm nổ ngay trong giao dịch (kể cả hàng hộp thư event của cùng giao dịch cũng rollback, R12). Lớp trình bày đổi ngoại lệ thành mã lỗi nghiệp vụ **theo tên ràng buộc**; ràng buộc không biết thì lỗi hệ thống 500, không đoán.
4. **Khóa lạc quan chạy ngầm.** Mỗi aggregate sửa được có cột `version` ánh xạ `@Version`, tăng mỗi thay đổi nội dung. Người dùng sửa dữ liệu của chính mình **không gửi và không thấy** phiên bản. Hai giao dịch đua nhau trên cùng một hàng: giao dịch sau thất bại và trả 409 `CONCURRENT_UPDATE` ("thử lại"). Lệnh sửa là một phần (`PATCH`: trường vắng giữ nguyên) nên hai sửa vào hai trường khác nhau đều được giữ.
5. **Phiên bản chỉ có nghĩa nghiệp vụ ở quyết định của người khác lên dữ liệu đã xem.** Khi một bên thứ ba quyết định dựa trên bản mình đã xem (quản trị duyệt hồ sơ, INV-SEL-06), lệnh quyết định gửi kèm phiên bản đã xem và lệch thì 409 `STALE_VERSION`. Dùng cùng cột; nếu sau này có thay đổi kỹ thuật làm tăng phiên bản mà nội dung không đổi thì tách cột `revision` nghiệp vụ.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Chỉ kiểm sớm trong use case | Hai yêu cầu cùng qua phép kiểm rồi cùng ghi: trùng tên, hai hồ sơ cùng khóa định danh |
| Bắt `DataIntegrityViolationException` trong use case rồi thử tiếp | Postgres đánh dấu giao dịch hỏng sau lỗi; thử tiếp trong cùng giao dịch lỗi theo (đã gặp ở feature 02). Phải rollback cả use case |
| Khóa bi quan `SELECT ... FOR UPDATE` | Không khóa được hàng "chưa có" (nộp lần đầu, tên chưa dùng); thêm thời gian giữ khóa |
| Khóa tư vấn theo tên (`pg_advisory_xact_lock`) | Thêm một cơ chế khi ràng buộc đã đủ; phải nhớ khóa ở mọi đường ghi |
| Bắt người dùng gửi `expectedVersion` ở mọi lệnh sửa | Người bán sửa dữ liệu tự khai của chính mình không có nhu cầu nghiệp vụ biết phiên bản; giao diện phải giữ số; `PATCH` một phần đã tránh đa số mất dữ liệu |
| Cột phụ `is_active` kèm duy nhất trên `(identity_key, is_active)` | Phải đồng bộ cột phụ với `status` ở mọi chuyển đổi; chỉ mục một phần diễn đạt đúng quy tắc trực tiếp |

## Hệ quả

- Mỗi BC có quy tắc duy nhất phải đặt tên ràng buộc đúng quy ước và có bảng ánh xạ tên sang mã lỗi; thêm ràng buộc mà quên ánh xạ thì ra 500 (có chủ đích, để lộ).
- Đường thường vẫn trả thông báo rõ nhờ phép kiểm sớm; đường đua nhau trả cùng mã lỗi nhưng sau khi rollback.
- `CONCURRENT_UPDATE` là lỗi tạm: giao diện được phép thử lại; bên gọi hệ thống nhớ không thử lại vô hạn.
- Ràng buộc nằm trong migration nên đổi quy tắc duy nhất là đổi migration, không chỉ mã.

## Cần kiểm

- [x] 20 luồng đồng thời cùng nộp (cùng chủ; khác chủ cùng tên; khác chủ cùng khóa định danh): đúng một thành công, lỗi còn lại đúng mã (KT-07).
- [x] Hai sửa đồng thời cùng một hồ sơ: đúng một thành công, một `CONCURRENT_UPDATE` (KT-08).
- [x] Chỉ mục một phần: khóa trùng với hồ sơ bị từ chối được, với hồ sơ hiệu lực bị chặn (KT-06).
- [x] Rollback cả hàng hộp thư khi vi phạm (KT-07, KT-09).
- [x] Phá thử: bỏ từng ràng buộc, bỏ `@Version`, bỏ điều kiện `WHERE` của chỉ mục: mỗi lần có test đỏ (bước 4 của feature 03, bảy cách).
- [x] Trên cơ sở dữ liệu thật ngoài Testcontainers (KT-13, bước 7): cả ba cuộc đua 20 luồng cho đúng một hồ sơ và một hàng hộp thư, các luồng còn lại đúng mã (`SELLER_PROFILE_EXISTS`, `SHOP_NAME_TAKEN`, `IDENTITY_ALREADY_REGISTERED`).

## Tham khảo

- `stage/c1a-listing/03-seller-profile-submit/tech.md` T3, T4. ADR-0001 (UUID v7), ADR-0008 (hộp thư cùng giao dịch).
- `service/seller-service/analysis.md` INV-SEL-01, 04, 06, 09.
