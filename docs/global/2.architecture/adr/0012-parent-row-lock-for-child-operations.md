# ADR-0012: Khóa dòng cha cho thao tác trên thực thể con có quy tắc chung

> **Trạng thái:** Accepted `2026-11-05` · **Ngày:** `2026-11-04` · **Phạm vi:** BC có hai aggregate cùng một cha logic và quy tắc nghiệp vụ cắt ngang cả hai (C1a: Danh mục hàng, sản phẩm và biến thể, feature 07)
>
> Bổ sung cho [ADR-0011](0011-optimistic-lock-and-storage-uniqueness.md), không thay nó. Chuyển Accepted `2026-11-05` sau khi các phép kiểm ở mục "Cần kiểm" đạt (`SellerConcurrencyTest`, feature 07).

## Bối cảnh

Sản phẩm và biến thể là hai aggregate riêng (đổi giá và bật, tắt biến thể không được tranh khóa với sửa mô tả, ảnh). Nhưng ba quy tắc cắt ngang cả hai:

- tối đa 50 biến thể cho một sản phẩm (`INV-CAT-93`);
- tổ hợp biến thể duy nhất trong sản phẩm (`INV-CAT-92`);
- mọi thay đổi biến thể tăng phiên bản của sản phẩm, tuần tự không nhảy (`INV-CAT-36`).

ADR-0011 chốt mọi quy tắc duy nhất ở ràng buộc lưu trữ và khóa lạc quan trên một dòng. Ở đây chúng không đủ:

- Quy tắc "tối đa 50" là phép **đếm**, không phải tính duy nhất: hai yêu cầu cùng đọc thấy 49 biến thể rồi cùng thêm sẽ ra 51 mà không ràng buộc nào bắt được.
- Thêm biến thể không đổi dòng sản phẩm, nên `@Version` của sản phẩm không tự tăng và hai lần thêm không xung đột với nhau.
- Ép tăng phiên bản (`OPTIMISTIC_FORCE_INCREMENT`) làm đúng một bên thắng nhưng bên kia nhận 409. Người bán thêm hàng chục biến thể liên tiếp (giao diện gửi song song) sẽ gặp 409 liên tục.

## Quyết định

1. **Lệnh nào chạm biến thể, hoặc sửa danh sách thuộc tính (có thể đổi trục), thì khóa dòng sản phẩm trước** bằng `SELECT … FOR UPDATE`, trong giao dịch của use case, rồi mới đếm, kiểm quy tắc, ghi, và tăng phiên bản sản phẩm. Lệnh đến sau **chờ** lệnh trước xong, không bị từ chối.
2. **Dòng cha đã tồn tại** là điều kiện dùng khóa này (tạo biến thể, đổi giá, bật tắt biến thể, sửa thuộc tính đều có sản phẩm sẵn). Khóa này không dùng cho "hàng chưa có": nơi đó ADR-0011 vẫn áp dụng (ràng buộc duy nhất).
3. **Lệnh không chạm biến thể và danh sách thuộc tính** (sửa tiêu đề, mô tả, thương hiệu) vẫn dùng khóa lạc quan của ADR-0011. Trục là thuộc tính được đánh dấu, không có bảng riêng và không có khóa ngoại từ biến thể tới giá trị trục; việc "tổ hợp nằm trong tập trục" và "không rút giá trị trục đang được biến thể dùng" do ứng dụng kiểm **dưới cùng khóa dòng** này, nên lệnh thêm biến thể và lệnh đổi trục không thể đua nhau. Nếu nó đua với lệnh biến thể, bên đọc phiên bản cũ nhận 409 `CONCURRENT_UPDATE` khi lưu, vì lệnh biến thể đã tăng phiên bản sản phẩm.
4. **Phiên bản sản phẩm là một số chung** cho cả sản phẩm và biến thể: lệnh biến thể gọi một hành vi của miền sản phẩm (`recordVariantChange`) để làm dòng sản phẩm "bẩn", nên `@Version` tăng đúng một lần. Biến thể không có `version` riêng vì mọi lệnh biến thể đã tuần tự qua dòng sản phẩm.
5. **Quy tắc duy nhất giữa các sản phẩm vẫn là ràng buộc lưu trữ** (SKU duy nhất theo người bán): khóa dòng sản phẩm không bao được hai sản phẩm khác nhau của cùng một người bán. Mục "tổ hợp duy nhất" cũng giữ thêm ràng buộc `(sản phẩm, khóa tổ hợp)` làm chốt cuối.
6. **Thứ tự lấy khóa:** chỉ khóa một dòng sản phẩm mỗi giao dịch, nên không có bế tắc giữa các lệnh biến thể. Không khóa dòng danh mục, mẫu hay thương hiệu (chỉ đọc).

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| `OPTIMISTIC_FORCE_INCREMENT` trên sản phẩm | Đúng (một bên thắng) nhưng bên kia nhận 409 và phải thử lại; thêm hàng loạt biến thể song song thành trải nghiệm xấu |
| Gộp biến thể vào aggregate sản phẩm (một khóa lạc quan) | Đổi giá một biến thể tranh khóa với sửa mô tả và ảnh; lý do tách aggregate (`variant.md` mục 1) mất |
| Khóa ngoại từ biến thể tới giá trị trục để cơ sở dữ liệu ép "tổ hợp nằm trong tập trục" | Cần bảng trục riêng hoặc ghi giá trị thuộc tính theo kiểu so sánh cũ mới; khóa dòng đã loại đua nhau nên khóa ngoại chỉ là chốt thừa (`stage/c1a-listing/07-product-variant/tech.md` T1) |
| Ràng buộc kiểm tra đếm ở cơ sở dữ liệu (trigger đếm 50) | Thêm logic nghiệp vụ vào cơ sở dữ liệu, khó thử và khó đọc; khóa dòng ở ứng dụng đủ |
| Khóa tư vấn (`pg_advisory_xact_lock`) theo mã sản phẩm | Thêm một cơ chế khi dòng sản phẩm sẵn có để khóa, và khóa dòng còn đi cùng việc đọc trạng thái mới nhất |
| Tăng `version` sản phẩm bằng câu `UPDATE` riêng (native) | Tách khỏi `@Version` của JPA nên hai cơ chế dễ lệch nhau; làm sản phẩm "bẩn" qua miền giữ một cơ chế |

## Hệ quả

- Kho sản phẩm có thêm đường đọc "khóa để ghi" (`findByIdForUpdate`). Lệnh biến thể và lệnh trục chỉ dùng đường này; lệnh khác dùng đường thường. Quên gọi đường khóa là lỗi lập trình, nên kiểm bằng test đua nhau chứ không bằng đọc mã.
- Lệnh biến thể của cùng một sản phẩm chạy **tuần tự**, không song song. Chấp nhận: thao tác ghi biến thể của một sản phẩm vốn ít và ngắn; sản phẩm khác nhau không chờ nhau.
- Thời gian giữ khóa là thời gian giao dịch; không gọi dịch vụ ngoài khi đang giữ khóa.
- Phiên bản sản phẩm tăng khi thêm biến thể làm các lệnh sửa lạc quan đang dở nhận 409: đúng ý định (INV-CAT-36), không phải lỗi.

## Cần kiểm

- [x] 20 luồng cùng thêm biến thể khác tổ hợp vào sản phẩm đang có 49: đúng một thành công, 19 luồng nhận `VARIANT_LIMIT_REACHED`; không có 51 biến thể.
- [x] 20 luồng thêm biến thể khác tổ hợp vào sản phẩm trống: cả 20 thành công, `version` sản phẩm tăng đúng 20, không nhảy, không trùng.
- [x] 20 luồng cùng thêm một tổ hợp: đúng một thành công, 19 nhận `VARIANT_COMBINATION_TAKEN`.
- [x] Đổi trục (bỏ một giá trị trục) đua với thêm biến thể dùng chính giá trị đó: kết quả luôn nhất quán, hoặc biến thể được thêm rồi đổi trục bị từ chối `PRODUCT_AXIS_VALUE_IN_USE`, hoặc đổi trục xong rồi thêm biến thể bị từ chối `VARIANT_COMBINATION_INVALID`; không bao giờ có biến thể mang giá trị ngoài tập trục.
- [x] Sửa tiêu đề đua với thêm biến thể: bên sửa nhận `CONCURRENT_UPDATE` khi đọc phiên bản cũ; kết quả cuối không mất biến thể.
- [x] Hai sản phẩm khác nhau của cùng người bán thêm cùng SKU: đúng một thành công (ràng buộc lưu trữ làm chốt).
- [x] Phá thử: bỏ `FOR UPDATE` thì ca "vượt 50" đỏ; bỏ `recordVariantChange` thì ca `version` đỏ.

Kết quả: `SellerConcurrencyTest` (8 ca, 20 luồng, Postgres thật) đạt cả sáu phép kiểm; phá thử có ca đối chứng: bỏ `FOR UPDATE` làm 5 ca đỏ (gồm ca vượt 50 và ca `version`), bỏ `recordVariantChange` làm 4 ca đỏ.

## Tham khảo

- ADR-0011. `service/catalog-service/analysis/product.md` (`INV-CAT-36`), `variant.md` (`INV-CAT-92`, `INV-CAT-93`).
- `stage/c1a-listing/07-product-variant/tech.md` T3.
