# Mẫu thuộc tính — `AGG-CAT-02`

Một trong năm aggregate của Danh mục hàng (xem [`0.overview.md`](0.overview.md)). Mã BC `CAT`.

## 1. Định nghĩa

**Mẫu thuộc tính** là một thuộc tính **dùng chung toàn sàn** để mô tả hàng (Màu, Cỡ, Chất liệu, Xuất xứ, Dung lượng), kèm kiểu nhập và, với kiểu chọn, **danh sách giá trị chuẩn**. Mẫu được [danh mục lá](category.md) gán để dùng. Quản trị quản.

Vì sao có mẫu dùng chung: người mua muốn lọc "màu đỏ" qua mọi người bán và mọi danh mục. Điều đó chỉ làm được khi "Đỏ" là **một giá trị chuẩn duy nhất**, không phải chữ mỗi người bán gõ ("Đỏ", "đỏ tươi", "Red"). Phương án đã cân nhắc: định nghĩa thuộc tính ngay trong từng danh mục lá. Loại vì "Màu" xuất hiện ở hàng trăm danh mục, định nghĩa lại từng nơi dẫn tới lệch giá trị.

## 2. Thành phần

- **Tên** — mã kỹ thuật, duy nhất toàn sàn, đặt lúc tạo và không đổi; bắt buộc chữ thường không dấu, chữ số, gạch dưới, không có dấu cách (ví dụ `color`, `screen_size`) để dùng làm khóa ổn định cho dữ liệu sản phẩm và tìm kiếm; sai dạng thì từ chối, không tự sửa hộ. **Tên hiển thị** là chữ tự do cho người xem, sửa được; **gợi ý nhập** cho người bán.
- **Kiểu nhập** — một trong sáu, **không đổi sau khi tạo**:
  - **chữ** (tối đa 255 ký tự);
  - **số** (kèm đơn vị, ví dụ "GB", "cm"; tối đa 4 chữ số thập phân; chỉ kiểu số có đơn vị, và đơn vị không đổi vì đổi đơn vị là đổi nghĩa mọi số đã lưu);
  - **đúng/sai**;
  - **ngày** (chỉ năm, tháng và năm, hoặc ngày đầy đủ; phải là ngày có thật);
  - **chọn một**;
  - **chọn nhiều**.

  Đúng/sai và ngày đủ cho các thông số như "Có chống nước", "Ngày sản xuất"; thiếu chúng thì người bán phải gõ vào ô chữ và không lọc được.
- **Tùy chọn** (chỉ kiểu chọn) — danh sách giá trị chuẩn; mỗi tùy chọn có giá trị, tên hiển thị, thứ tự, đang dùng hoặc ngừng dùng. Giá trị là mã kỹ thuật không đổi: chữ thường không dấu, chữ số, gạch dưới hoặc gạch ngang, không có dấu cách (ví dụ `black`, `usb-c`, `x_large`); gạch ngang được phép vì nhiều giá trị thật có sẵn (`wi-fi`, `t-shirt`), còn tên mẫu thì không vì dùng làm khóa.
- **Đang dùng / ngừng dùng** — mẫu ngừng dùng không gán được vào danh mục mới.

**Chỉ thuộc tính kiểu chọn một mới làm được trục biến thể.** Mỗi biến thể nhận đúng một giá trị cho mỗi trục, và trục phải có tập giá trị rời rạc, chuẩn (`product.md`). Kiểu chọn nhiều, chữ, số, đúng/sai, ngày chỉ mô tả sản phẩm.

## 3. Vòng đời & hành vi

- Quản trị tạo mẫu, sửa tên hiển thị và gợi ý, thêm tùy chọn, ngừng dùng mẫu hoặc tùy chọn, bật lại, sắp xếp lại thứ tự tùy chọn.
- **Mẫu và tùy chọn không bao giờ bị xóa, chỉ ngừng dùng.** Sản phẩm, biến thể tham chiếu chúng bằng mã và cần đọc được lâu sau khi Quản trị thôi dùng; xóa chỉ thêm rủi ro mồ côi mà không thêm giá trị.
- **Mẫu kiểu chọn phải có ít nhất một tùy chọn đang dùng** thì mới gán được.
- **Giá trị tùy chọn duy nhất trong mẫu**, để một giá trị chuẩn thật sự là một. Vì giá trị đã bắt buộc đúng dạng chữ thường không dấu nên so thẳng, không cần chuẩn hóa thêm; `usb-c` và `usb_c` là hai giá trị khác nhau, người quản trị chịu trách nhiệm đặt nhất quán.
- **Ngừng dùng một tùy chọn hay mẫu không làm sản phẩm đang dùng nó mất giá trị**; chỉ chặn chọn mới từ nay. Sản phẩm đã lưu giữ nguyên giá trị khi sửa phần khác (`product.md`, `INV-CAT-32`). Mẫu "dùng được" khi đang dùng và, với kiểu chọn, còn ít nhất một tùy chọn đang dùng; thuộc tính bắt buộc chỉ ép người bán điền khi mẫu dùng được (nếu không, người bán bị kẹt trước một ô không còn hiện).
- **Kiểu nhập của mẫu đã được gán hoặc đã có giá trị không đổi** (`INV-CAT-12`): đổi chữ sang số làm giá trị cũ vô nghĩa.
- Đổi tên hiển thị của tùy chọn hay mẫu có hiệu lực ngay cho mọi sản phẩm đang dùng nó (giá trị lưu theo mã tùy chọn, hiển thị theo tên hiện hành).

## 4. Năng lực

Sáu câu của P3 (xem `category.md` mục 4).

### CAP-CAT-02 Quản mẫu thuộc tính và tùy chọn (stage: H4)

1. Quản trị.
2. Danh mục tự xử lý, không có bước duyệt: kiểm quyền (`INV-CAT-04`), tên duy nhất (`INV-CAT-10`), mẫu chọn có tùy chọn và giá trị duy nhất (`INV-CAT-11`), kiểu nhập không đổi (`INV-CAT-12`).
3. Phát `EVT-CAT-03` khi thay đổi làm đổi cách danh mục dùng mẫu (Tìm kiếm khi C1b vào); phát `EVT-CAT-04` cho Nhật ký kiểm toán.
4. Đường không suôn sẻ:
   - Tên mẫu trùng (kể cả khác dấu, khác chữ hoa): từ chối.
   - Mẫu chọn không có tùy chọn, hoặc tùy chọn trùng giá trị: từ chối.
   - Đổi kiểu nhập hay đơn vị của mẫu: từ chối (muốn khác thì tạo mẫu mới).
   - Thêm tùy chọn vào mẫu đã ngừng dùng, hoặc vào mẫu không phải kiểu chọn: từ chối.
   - Không phải Quản trị: từ chối.
5. Lịch sử: Nhật ký kiểm toán (`ATTRIBUTE_CREATED`, `ATTRIBUTE_UPDATED`, `ATTRIBUTE_DEACTIVATED`).
6. Chảy sang Tìm kiếm (C1b) và Nhật ký kiểm toán. Danh mục lá đọc mẫu qua gán thuộc tính (`RM-CAT-02`).

## 5. Bất biến (INV)

| ID | Bất biến | Lý do | Loại | Kiểm tra khi | Từ stage |
|---|---|---|---|---|---|
| `INV-CAT-10` | Tên mẫu thuộc tính đúng dạng (chữ thường không dấu, chữ số, gạch dưới) và duy nhất toàn sàn | Hai mẫu cùng tên làm người bán chọn nhầm và lọc không thống nhất | Liên tục | tạo, đổi tên | R10 |
| `INV-CAT-11` | Mẫu kiểu chọn có ít nhất một tùy chọn đang dùng khi được gán; giá trị tùy chọn đúng dạng (chữ thường không dấu, chữ số, gạch dưới hoặc gạch ngang) và duy nhất trong mẫu | Không có giá trị chuẩn thì không chọn được; trùng thì lọc đếm sai | Liên tục | thêm tùy chọn; gán mẫu. **Ngừng tùy chọn không bị kiểm:** ngừng tùy chọn cuối cùng được phép, mẫu thành không dùng được (mục 3) | mới |
| `INV-CAT-12` | Kiểu nhập và đơn vị của mẫu không đổi sau khi tạo; mẫu và tùy chọn không bị xóa, chỉ ngừng dùng | Đổi kiểu hay đơn vị làm giá trị cũ vô nghĩa; sản phẩm tham chiếu mẫu và tùy chọn bằng mã | Liên tục | sửa mẫu | mới |

## 6. Event

| Event | Khi nào | Nội dung chính | Bên nhận |
|---|---|---|---|
| `EVT-CAT-03` Danh mục thay đổi | Mẫu hay tùy chọn đổi làm ảnh hưởng thuộc tính được gán | Mã mẫu, phiên bản | Tìm kiếm (C1b) |
| `EVT-CAT-04` Hành động quản trị đã thực hiện | Quản trị tạo, sửa, ngừng mẫu | Người thực hiện, hành động `ATTRIBUTE_*`, mã mẫu, phiên bản | Nhật ký kiểm toán |

## 7. Chưa làm

| Năng lực | Trạng thái | Chặng dự kiến | Giá trị | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|---|
| Đơn vị quy đổi, kiểu ngày có giờ | Cắt tạm | Chưa dự kiến | Mô tả đa dạng hơn | Sáu kiểu nhập đủ cho demo | Kiểu nhập |
| Nhóm tùy chọn có phân cấp (hãng và dòng) | Cắt tạm | Chưa dự kiến | Chọn có quan hệ | Chưa có nhu cầu | Tùy chọn |
| Hợp nhất hai tùy chọn trùng nghĩa | Cắt tạm | Chưa dự kiến | Dọn giá trị lệch | Chưa có nhu cầu | Tùy chọn |

## 8. Câu hỏi cần trả lời

Không có. Dữ liệu khởi tạo: `Q-CAT-02` ở [`0.overview.md`](0.overview.md).
