# ADR-0014: Sự kiện trạng thái đầy đủ thay cho sự kiện nhỏ theo chuyển đổi (D6)

> **Trạng thái:** Accepted `2026-11-06` · **Ngày:** `2026-11-06` · **Phạm vi:** Danh mục hàng phát cho Tìm kiếm và Tồn kho (C1a phát, C1b và C2a nhận)
>
> Bổ sung cho [ADR-0008](0008-event-outbox-and-consumption.md) (hộp thư event, giao ít nhất một lần, khử trùng ở bên nhận). Thay các dòng sự kiện nhỏ của catalog trong `5. event-catalog.md` (D6).

## Bối cảnh

Thiết kế cũ phát một sự kiện cho mỗi chuyển đổi (đã đăng, đã gỡ, đã cập nhật, bị chặn, mở chặn, biến thể được tạo, đổi giá, bật, ngừng): tám chủ đề, cộng một bản chụp cho Tìm kiếm. Tồn kho phải nghe sáu trong tám chủ đề đó và tự ghép lại "đơn vị bán này bán được hay không" từ các cờ rải rác (`sellerActive`, `productPublished`, `adminBlocked`). Cách này đúng khi mọi thông điệp đến đủ và đúng thứ tự, nhưng:

- Hai chủ đề khác nhau không có thứ tự chung. `deactivated` có thể đến trước `published` của cùng sản phẩm; bên nhận phải tự chống đua nhau và tự khử trùng từng chủ đề.
- Thêm một điều kiện hiển thị (người bán mất duyệt, bị chặn) là thêm chủ đề hoặc cờ mới cho mọi bên nhận.
- Tồn kho nhận cả `price-changed`, thứ nó không cần.

## Quyết định

1. **Hai sự kiện, mỗi sự kiện mang trạng thái tuyệt đối.** Không dùng sự kiện nhỏ theo chuyển đổi.

   | | `EVT-CAT-01` Bản chụp sản phẩm | `EVT-CAT-02` Đơn vị bán được thay đổi |
   |---|---|---|
   | Chủ đề | `catalog.product.snapshot` | `catalog.sellable-unit.changed` |
   | Cho | Tìm kiếm (C1b) | Tồn kho (C2a) |
   | Nội dung | toàn bộ sản phẩm: mã, người bán, tiêu đề, mô tả, danh mục, thương hiệu, thuộc tính (kiểu nhập, trục), ảnh (**khóa đối tượng**, vị trí), biến thể (mã, tổ hợp, giá, hoạt động), `visible`, `version` | một biến thể: `variantId`, `productId`, `sellerId`, `active`, `visible`, `version`. **Không mang giá** |
   | Khóa thông điệp | mã sản phẩm | mã sản phẩm |

2. **Quy tắc phát** (cho cả hai sự kiện):
   - Sản phẩm **chưa từng đăng (nháp)**: không phát gì.
   - Sản phẩm **đang hiển thị**: mọi lệnh làm đổi sản phẩm hoặc biến thể phát `EVT-CAT-01`; lệnh làm đổi một biến thể (tạo, ngừng, bật lại) phát thêm `EVT-CAT-02` cho biến thể đó. Đổi giá, ảnh, tiêu đề chỉ phát `EVT-CAT-01`.
   - **Đăng, gỡ, đăng lại:** phát `EVT-CAT-01` và `EVT-CAT-02` cho **mọi** biến thể (cờ `visible` đổi).
   - Sản phẩm **đã gỡ**: sửa không phát; lần đăng lại phát lại toàn bộ nên bên nhận không lệch.
   - Hộp thư ghi **cùng giao dịch** với thay đổi (ADR-0008); khóa phân vùng là mã sản phẩm để thứ tự đúng theo sản phẩm.

3. **`version` là của sản phẩm** (số chung cho sản phẩm và biến thể, ADR-0012). Bên nhận lưu `version` đã nhận:
   - `EVT-CAT-01`: theo **sản phẩm**, ghi đè khi `version` lớn hơn hẳn.
   - `EVT-CAT-02`: theo **từng đơn vị bán được** (mã sản phẩm và mã biến thể), ghi đè khi lớn hơn hẳn. Một lệnh tăng `version` một lần và phát tối đa một thông điệp cho mỗi biến thể, nên (biến thể, `version`) là duy nhất; ba thông điệp cùng `version` của ba biến thể khi đăng không cản nhau.
   - `version` có khoảng nhảy (không phải mọi lệnh đều phát `EVT-CAT-02`) là bình thường; chỉ cần đơn điệu tăng.
   - Lưu `version` theo sản phẩm cho `EVT-CAT-02` là sai (biến thể đầu tiên cùng `version` làm các biến thể sau bị bỏ nhầm).

4. **Không có cờ "đã bị bỏ" ở C1a:** chỉ nháp chưa từng đăng mới xóa được và nháp không phát, nên chưa có ca nào cần báo "bỏ". Thêm khi có tính năng xóa sản phẩm đã đăng.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Giữ tám sự kiện nhỏ cạnh bản chụp | Bên nhận phải ghép trạng thái từ nhiều chủ đề không có thứ tự chung; thêm điều kiện hiển thị là thêm chủ đề |
| Một sự kiện duy nhất mang cả bản chụp cho mọi bên nhận | Tồn kho phải nhận toàn bộ sản phẩm (giá, ảnh, thuộc tính) cho một cờ hoạt động; mỗi lần đổi giá cũng đánh thức Tồn kho |
| `EVT-CAT-02` mang giá | Giá thuộc cách đọc đồng bộ hay bản sao chốt ở C2a (`service.md` mục 6); gắn giá vào trạng thái đơn vị bán được buộc hai việc đổi cùng nhau |
| Bên nhận so `version` theo sản phẩm cho `EVT-CAT-02` | Sai (xem quyết định 3) |
| `version` riêng cho từng biến thể | Biến thể không có `version` riêng (feature 07, Q6): mọi lệnh biến thể đã tuần tự qua dòng sản phẩm |

## Hệ quả

- `5. event-catalog.md`: tám dòng sự kiện nhỏ và `ProductSearchSnapshotEvent` được thay bằng hai dòng này. `inventory-service` hiện có mã nghe các chủ đề `catalog.variant.*`, `catalog.product.*` cũ và `3.technical/idempotency-layering.md` mô tả chúng; phần này viết lại ở C2a, khi đó nghe `catalog.sellable-unit.changed`. `search-service` đã định chỉ dùng bản chụp (Q2 của nó) nên chỉ đổi tên chủ đề.
- Bản chụp lớn (tối đa 50 biến thể, 9 ảnh): vài chục KB mỗi lần. Chấp nhận; nếu thành vấn đề thì gộp theo thời gian ở chỗ phát, không đổi hợp đồng.
- Feature 07 và 08 hiện không ghi hộp thư. Khi có sản phẩm đã đăng, mọi use case sản phẩm, biến thể, ảnh phải ghi sự kiện theo quy tắc ở quyết định 2 (feature 09).
- Bên nhận phải khử trùng và so `version` (ADR-0008); không cần thêm gì khác để chịu được nhận lặp hay sai thứ tự.

## Cần kiểm

- [x] Đăng sản phẩm 3 biến thể: ra đúng 1 `EVT-CAT-01` và 3 `EVT-CAT-02`, cùng khóa là mã sản phẩm, cùng `version`; nội dung đúng (có `visible`, không có giá ở `EVT-CAT-02`).
- [x] Nháp: tạo, sửa, thêm biến thể, thêm ảnh, xóa đều không ra thông điệp nào.
- [x] Sản phẩm đang hiển thị: đổi giá chỉ ra `EVT-CAT-01`; ngừng một biến thể ra cả `EVT-CAT-01` và `EVT-CAT-02` của biến thể đó.
- [x] Gỡ rồi đăng lại: gỡ ra thông điệp `visible=false`; sửa khi đã gỡ không ra gì; đăng lại ra lại toàn bộ.
- [x] `version` của các thông điệp cho cùng sản phẩm đơn điệu tăng; mọi thông điệp ghi cùng giao dịch (rollback thì không có).
- [x] Chạy thật trên Kafka và Connect cách ly: khóa, chủ đề, nội dung, số lượng đúng.

## Tham khảo

- ADR-0008, ADR-0012. `service/catalog-service/service.md` mục 5 và 7; `stage/c1a-listing/09-publish-product/tech.md`.

Kết quả (feature 09, `2026-11-06`): `ProductEventRecorderTest` (bảng quy tắc phát), `ProductPublishApiTest` (hộp thư thật: vòng đời đăng, sửa, gỡ, đăng lại; lệnh thất bại không ra gì) và chạy thật trên Postgres, Kafka, Connect (connector `catalog`) cách ly đạt 26/26 (KT-09): đúng 5 bản chụp và 3 sự kiện đơn vị cho một vòng đời, khóa là mã sản phẩm, `version` đơn điệu, đơn vị không có giá, nháp không ra gì, sửa khi đã gỡ không ra gì. Phá thử 21 lỗi cố ý, 20 đỏ, 1 lỗi tương đương (bỏ điều kiện `changedUnits` rỗng vì vòng lặp bên dưới đã lọc).
