# Danh mục — `AGG-CAT-01`

Một trong năm aggregate của Danh mục hàng (xem [`0.overview.md`](0.overview.md)). Mã BC `CAT`.

## 1. Định nghĩa

**Danh mục** là một nút của cây phân loại hàng trên sàn. Cây giúp người mua duyệt theo nhóm và cho phép nền tảng quy định **thuộc tính nào áp cho nhóm hàng nào** (áo có Cỡ, điện thoại có Dung lượng). Danh mục do Quản trị quản; người bán chỉ chọn.

Vì sao cấu trúc do nền tảng giữ, không để người bán tự đặt: nếu mỗi người bán tự đặt nhóm và thuộc tính ("Màu", "màu sắc", "Color") thì không lọc, không so sánh được giữa các người bán.

## 2. Thành phần

- **Tên** — hiển thị nguyên dạng; so sánh trùng sau chuẩn hóa (bỏ khoảng trắng thừa, chữ thường, bỏ dấu tiếng Việt).
- **Cha** — danh mục cấp trên; danh mục cấp một không có cha.
- **Cấp** — một, hai hoặc ba. **Cây cố định ba cấp** (ví dụ Thời trang → Áo nam → Áo thun). Phương án đã cân nhắc: cây sâu tùy ý (bốn cấp trở lên). Loại vì ba cấp đủ cho sàn mô phỏng, sâu hơn làm giao diện chọn và lọc phức tạp mà không thêm bài học.
- **Thứ tự** — vị trí giữa các danh mục cùng cha; Quản trị sắp xếp để giao diện hiển thị đúng ý.
- **Đang dùng / ngừng dùng** — danh mục ngừng dùng không nhận sản phẩm mới.
- **Lá** — danh mục **cấp ba**. **Chỉ lá mới có sản phẩm và có thuộc tính được gán.** Danh mục cấp một hoặc hai chưa có con vẫn không nhận sản phẩm: cây cố định ba cấp nên cấp một, hai luôn là nhóm, cấp ba luôn là nơi đặt hàng.
- **Gán thuộc tính** — mỗi dòng gán một [mẫu thuộc tính](attribute-template.md) cho một danh mục lá, kèm: bắt buộc hay không, thứ tự hiển thị, và ràng buộc riêng của danh mục nếu có (ví dụ khoảng giá trị số). Một mẫu chỉ gán một lần cho một danh mục.

Lý do thuộc tính chỉ gán ở lá: gán ở nút giữa buộc phải kế thừa xuống, làm khó cả quy tắc lẫn lọc. Lý do thuộc tính là **mẫu dùng chung**, không định nghĩa riêng trong từng danh mục: "Màu" hay "Xuất xứ" dùng ở hàng trăm danh mục; định nghĩa lại ở từng nơi dẫn tới lệch giá trị và không lọc được xuyên danh mục. Gán thuộc tính nằm trong Danh mục vì nó là *cách danh mục này dùng mẫu* (bắt buộc hay không có thể khác nhau giữa các danh mục).

## 3. Vòng đời & hành vi

- Quản trị tạo danh mục (gốc hoặc dưới một cha), đổi tên, sắp xếp lại thứ tự, ngừng dùng, bật dùng lại, xóa.
- **Danh mục cha đang đóng vẫn tạo được con**: Quản trị chuẩn bị trước cả nhánh rồi mở sau.
- **Lá không có con** (cấp ba là cấp cuối), nên sản phẩm luôn ở nút lá; chia nhỏ một lá là tạo lá mới và chuyển sản phẩm, mà di chuyển là việc chưa làm (mục 7).
- **Ngừng dùng thay vì xóa** khi danh mục đã có sản phẩm: sản phẩm không được mồ côi. Xóa chỉ khi không có con và chưa từng có sản phẩm.
- **Ngừng dùng = đóng nhận sản phẩm mới và ẩn khỏi cây duyệt; sản phẩm đã đăng vẫn hiển thị.** Danh mục **dùng được** khi chính nó **và mọi tổ tiên** đang dùng (`INV-CAT-97`): tắt một cha thì cả nhánh không nhận sản phẩm mới. Sản phẩm đã gỡ trong danh mục không dùng được không đăng lại được (đăng lại cũng là đưa hàng mới lên sàn, `INV-CAT-91`). Người bán thấy cảnh báo trên sản phẩm của họ nằm trong danh mục đã ngừng dùng. Phương án đã cân nhắc: ẩn mọi sản phẩm trong danh mục ngừng dùng. Loại vì di chuyển và sao chép sản phẩm đều là việc chưa làm, nên người bán bị kẹt không có lối đưa hàng trở lại sàn, và tắt một danh mục cha sẽ ẩn cả nhánh làm mất doanh số của nhiều người bán không có lỗi. Hàng vi phạm đã có công cụ riêng là chặn sản phẩm (C1b). Cách Tìm kiếm xử lý danh mục ngừng dùng là điểm nối ở C1b.
- **Gán thuộc tính**: thêm, bỏ, đổi bắt buộc và thứ tự. Thuộc tính mới *bắt buộc* chỉ kiểm lúc đăng (`INV-CAT-91`): sản phẩm đã đăng thiếu giá trị đó vẫn hiển thị; chỉ khi đăng lại mới phải đủ. **Bỏ gán một thuộc tính đã có sản phẩm dùng được phép**: sản phẩm cũ giữ giá trị đã điền, khi sửa chỉ giữ hoặc bỏ hẳn, không thêm hay đổi (`product.md`). Phương án đã cân nhắc: cấm bỏ gán khi đã có sản phẩm dùng. Loại vì Quản trị gán nhầm một thuộc tính thì sau sản phẩm đầu tiên điền, không gỡ được nữa mà không ngừng dùng cả mẫu (ảnh hưởng mọi danh mục khác) hoặc cả danh mục. Giá trị cũ không biến mất, chỉ không còn được hỏi và không còn cấu hình lọc.
- Ai cũng có thể xem cây (người bán, Quản trị); chỉ Quản trị sửa.

## 4. Năng lực

Mỗi năng lực trả lời sáu câu của P3: (1) ai khởi tạo, (2) ai xử lý và theo quy trình nào, (3) ai được báo, (4) đường không suôn sẻ, (5) ai xem lại được lịch sử, (6) dữ liệu chảy sang đâu.

### CAP-CAT-01 Quản danh mục và gán thuộc tính (stage: H4)

1. Quản trị.
2. Danh mục tự xử lý, không có bước duyệt: kiểm quyền (`INV-CAT-04`), độ sâu (`INV-CAT-01`), tên duy nhất trong cùng cha (`INV-CAT-02`), gán thuộc tính ở lá và mẫu đang dùng (`INV-CAT-03`), điều kiện xóa (`INV-CAT-05`, `INV-CAT-98`).
3. Phát `EVT-CAT-03` (cây hay thuộc tính được gán đổi) cho Tìm kiếm khi C1b vào; phát `EVT-CAT-04` cho Nhật ký kiểm toán cùng lúc với thay đổi.
4. Đường không suôn sẻ:
   - Tên trùng trong cùng cha (kể cả khác dấu, khác chữ hoa): từ chối. Hai yêu cầu đồng thời: chỉ một thành công.
   - Tạo cấp thứ tư: từ chối.
   - Gán thuộc tính vào danh mục không phải lá (cấp ba), hoặc gán mẫu đã ngừng dùng, hoặc gán trùng: từ chối.
   - Xóa danh mục có con hoặc đã có sản phẩm: từ chối, gợi ý ngừng dùng.
   - Không phải Quản trị: từ chối.
5. Lịch sử thay đổi: Nhật ký kiểm toán (`CATEGORY_CREATED`, `CATEGORY_UPDATED`, `CATEGORY_DEACTIVATED`, `CATEGORY_DELETED`).
6. Chảy sang Tìm kiếm (`EVT-CAT-03`, C1b) và Nhật ký kiểm toán (`EVT-CAT-04`). Sản phẩm đọc cây qua `RM-CAT-01`, `RM-CAT-02`.

## 5. Bất biến (INV)

| ID | Bất biến | Lý do | Loại | Kiểm tra khi | Từ stage |
|---|---|---|---|---|---|
| `INV-CAT-01` | Cây có tối đa ba cấp; chỉ danh mục cấp ba là lá | Đủ cho sàn mô phỏng; sâu hơn làm giao diện chọn và lọc phức tạp | Liên tục | tạo danh mục | mới |
| `INV-CAT-02` | Tên danh mục duy nhất trong cùng cha, so sánh sau chuẩn hóa | Chống trùng và nhầm lẫn khi chọn | Liên tục | tạo, đổi tên | R10 |
| `INV-CAT-03` | Thuộc tính chỉ gán ở danh mục lá; mẫu được gán phải đang dùng; một mẫu chỉ gán một lần cho một danh mục | Thuộc tính ở nút giữa phải kế thừa, làm khó quy tắc và lọc | Tiên quyết | `CAP-CAT-01` | mới |
| `INV-CAT-04` | Chỉ Quản trị quản danh mục, mẫu thuộc tính, thương hiệu | Cấu trúc dùng chung của sàn | Tiên quyết | `CAP-CAT-01`, `02`, `03` | R6 |
| `INV-CAT-05` | Danh mục có con thì không bị xóa | Con không được mồ côi | Tiên quyết | xóa danh mục | R10 |

Quy tắc xuyên aggregate liên quan danh mục: `INV-CAT-91` (điều kiện đăng), `INV-CAT-97` (sản phẩm chỉ ở lá dùng được), `INV-CAT-98` (đã dùng không xóa), ở [`0.overview.md`](0.overview.md) mục 7.

## 6. Event

| Event | Khi nào | Nội dung chính | Bên nhận |
|---|---|---|---|
| `EVT-CAT-03` Danh mục thay đổi | Mọi thay đổi cây hay gán thuộc tính | Mã danh mục, phiên bản | Tìm kiếm (C1b) |
| `EVT-CAT-04` Hành động quản trị đã thực hiện | Quản trị tạo, sửa, ngừng, xóa danh mục | Người thực hiện, hành động `CATEGORY_CREATED` / `CATEGORY_UPDATED` / `CATEGORY_DEACTIVATED` / `CATEGORY_DELETED`, mã danh mục, phiên bản | Nhật ký kiểm toán |

Đổi tên, sắp xếp, gán, bỏ gán thuộc tính và bật lại dùng đều ghi là `CATEGORY_UPDATED`. Xóa có hành động riêng vì sau khi xóa không còn chỗ nào khác để tra danh mục đó từng tồn tại.

## 7. Chưa làm

| Năng lực | Trạng thái | Chặng dự kiến | Giá trị | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|---|
| Di chuyển sản phẩm sang danh mục khác | Cắt tạm | Chưa dự kiến | Xử lý khi danh mục ngừng dùng hoặc chia nhỏ | Chưa có nhu cầu | Hành động của Sản phẩm |
| Di chuyển hoặc gộp danh mục trong cây | Cắt tạm | Chưa dự kiến | Tổ chức lại cây | Cây ổn định ở demo | Danh mục |
| Cấu hình khám phá theo danh mục (thuộc tính nào lọc được, sắp xếp) | Chưa có ở root | C1b | Tìm và lọc đúng ý | Cần Tìm kiếm | Gán thuộc tính |
| Nhóm thuộc tính dùng chung giữa các danh mục | Cắt tạm | Chưa dự kiến | Gán một lần cho nhiều danh mục | Gán từng danh mục đủ cho demo | Gán thuộc tính |
| Ảnh đại diện của danh mục | Cắt tạm | Chưa dự kiến | Trang chủ trực quan | Chưa có nhu cầu | Danh mục |

## 8. Câu hỏi cần trả lời

Không có câu hỏi riêng của aggregate này. Dữ liệu khởi tạo (bộ danh mục mẫu): `Q-CAT-02` ở [`0.overview.md`](0.overview.md).
