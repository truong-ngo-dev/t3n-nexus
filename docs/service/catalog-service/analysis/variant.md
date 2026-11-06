# Biến thể — `AGG-CAT-05`

Một trong năm aggregate của Danh mục hàng (xem [`0.overview.md`](0.overview.md)). Mã BC `CAT`.

## 1. Định nghĩa

**Biến thể** là một lựa chọn cụ thể của [sản phẩm](product.md) (áo X, màu đỏ, cỡ M): **đơn vị thực sự được bán**. Tồn kho, giỏ hàng, đơn hàng đều tham chiếu *biến thể*, không tham chiếu sản phẩm. Biến thể có **tổ hợp** giá trị các trục, **giá niêm yết**, và một mã **SKU** tùy chọn do người bán tự đặt.

**Vì sao biến thể là aggregate riêng, không nằm trong Sản phẩm.** Người bán đổi giá, bật, tắt nhiều biến thể thường xuyên, và thường đồng thời với việc sửa mô tả hay ảnh. Nếu biến thể nằm trong ranh giới sản phẩm thì hai việc độc lập này tranh nhau cùng một khóa. Cái giá phải trả: các quy tắc cắt ngang sản phẩm và biến thể (đủ điều kiện đăng, tổ hợp duy nhất, tối đa 50 biến thể) không do một aggregate tự bảo đảm mà được ép ở hành động và bằng ràng buộc duy nhất (`0.overview.md` mục 7). Chấp nhận vì các quy tắc đó ít và rõ.

Biến thể có mã **bền**: một khi các BC khác đã tham chiếu thì nó không đổi nghĩa và không biến mất khỏi lịch sử.

## 2. Thành phần

- **Sản phẩm** — mã sản phẩm chứa nó; không đổi.
- **Tổ hợp** — với mỗi trục của sản phẩm, đúng một giá trị trong tập sản phẩm đã chọn (`INV-CAT-94`). **Bất biến sau khi tạo** (`INV-CAT-41`): đổi tổ hợp là đổi nghĩa của thứ mà đơn hàng đã trỏ tới; muốn khác thì tạo biến thể mới và ngừng biến thể cũ. Hai biến thể của cùng sản phẩm không có cùng tổ hợp, không phụ thuộc thứ tự các trục (`INV-CAT-92`).
- **Giá niêm yết** — số nguyên đồng (VND), lớn hơn 0, không quá 1 tỷ (`INV-CAT-40`). Đổi được bất cứ lúc nào; đổi giá của sản phẩm đang hiển thị có hiệu lực ngay.
- **SKU** — mã người bán tự đặt, tùy chọn, 1 đến 50 ký tự, duy nhất trong cùng người bán qua mọi sản phẩm của họ (`INV-CAT-96`). Để người bán đối chiếu với kho riêng của họ.
- **Trạng thái** — Hoạt động hay Ngừng. Biến thể ngừng không bán được, không được tính vào điều kiện đăng.

**Sản phẩm không có trục** có đúng một **biến thể mặc định** với tổ hợp rỗng (`INV-CAT-44`). Biến thể mặc định được tạo khi người bán đặt giá lần đầu cho sản phẩm không trục; từ đó không khai trục được nữa (`INV-CAT-33`). Phương án đã cân nhắc: cho khai trục sau và chuyển biến thể mặc định thành biến thể có tổ hợp. Loại vì phải đổi nghĩa một biến thể đã có mã, trái với tính bất biến.

## 3. Vòng đời & hành vi

- Người bán thêm biến thể cho sản phẩm của mình khi sản phẩm ở bất kỳ trạng thái nào (Nháp, Đã đăng, Đã gỡ). Thêm biến thể cho sản phẩm đã đăng làm biến thể đó bán được ngay nếu giá hợp lệ.
- **Biến thể không bao giờ bị xóa riêng lẻ, chỉ ngừng** (`INV-CAT-42`): đơn và tồn kho tham chiếu nó bằng mã. Biến thể chỉ mất khi xóa cả sản phẩm Nháp chưa từng đăng. Phương án đã cân nhắc: cho xóa biến thể của sản phẩm chưa từng đăng. Loại vì ngừng cho cùng kết quả (không bán, không tính vào điều kiện đăng) mà không thêm một đường xóa cần bảo vệ.
- Ngừng biến thể cuối cùng đang hoạt động của sản phẩm Đã đăng: sản phẩm vẫn Đã đăng nhưng người bán thấy cảnh báo không còn gì để bán; đăng lại sẽ bị từ chối cho tới khi có biến thể hoạt động (`INV-CAT-91`).
- Bật lại biến thể đã ngừng được, miễn giá hợp lệ.

## 4. Năng lực

Sáu câu của P3 (xem `category.md` mục 4).

### CAP-CAT-05 Quản biến thể và giá niêm yết (stage: H5)

1. Chủ sản phẩm.
2. Danh mục tự xử lý: kiểm chủ sản phẩm (`INV-CAT-30`), tổ hợp thuộc tập đã chọn (`INV-CAT-94`), tổ hợp duy nhất (`INV-CAT-92`), tối đa 50 biến thể (`INV-CAT-93`), giá (`INV-CAT-40`), SKU (`INV-CAT-43`, `INV-CAT-96`), tổ hợp không đổi (`INV-CAT-41`), không xóa riêng lẻ (`INV-CAT-42`).
3. Chủ sản phẩm thấy kết quả ngay. Nếu sản phẩm hiển thị thì phát `EVT-CAT-01`; tạo, ngừng, bật lại biến thể phát `EVT-CAT-02`.
4. Đường không suôn sẻ:
   - Tổ hợp trùng trong sản phẩm (kể cả hai yêu cầu cùng lúc): chỉ một thành công, bên sau bị từ chối.
   - Giá không hợp lệ (không dương, quá trần, không nguyên): từ chối.
   - SKU trùng trong cùng người bán: từ chối.
   - Giá trị trục ngoài tập đã chọn, hoặc thiếu trục: từ chối.
   - Sửa tổ hợp của biến thể đã có: từ chối.
   - Thêm biến thể thứ 51: từ chối.
   - Xóa riêng một biến thể: không có thao tác này, dùng ngừng.
   - Sửa biến thể của sản phẩm người khác: từ chối như thể không tồn tại.
5. Chưa có lịch sử giá ở C1a (mục 7).
6. Chảy sang Tồn kho (`EVT-CAT-02`, C2a) và Tìm kiếm (`EVT-CAT-01`, C1b). Giỏ hàng, Khuyến mãi, Định phí đọc giá niêm yết qua giao diện (C2a).

## 5. Bất biến (INV)

| ID | Bất biến | Lý do | Loại | Kiểm tra khi | Từ stage |
|---|---|---|---|---|---|
| `INV-CAT-40` | Giá là số nguyên đồng, lớn hơn 0, không quá 1 tỷ | Tiền không âm, không phân số; trần chặn lỗi nhập | Tiên quyết | đặt, đổi giá | R9 |
| `INV-CAT-41` | Tổ hợp của biến thể không đổi sau khi tạo | Biến thể là đơn vị mà đơn hàng trỏ tới; đổi nghĩa làm sai đơn cũ | Liên tục | sửa biến thể | R9 |
| `INV-CAT-42` | Biến thể không bị xóa riêng lẻ, chỉ ngừng; chỉ mất khi xóa cả sản phẩm Nháp | Đơn và tồn kho tham chiếu biến thể | Liên tục | mọi thao tác trên biến thể | mới |
| `INV-CAT-43` | SKU nếu có thì dài 1 đến 50 ký tự | Chặn mã rỗng hoặc quá dài | Tiên quyết | đặt SKU | mới |
| `INV-CAT-44` | Sản phẩm không trục có đúng một biến thể mặc định, tổ hợp rỗng | Mọi thứ phía sau luôn trỏ vào một biến thể | Liên tục | tạo biến thể | mới |

Quy tắc xuyên aggregate liên quan biến thể: `INV-CAT-91` (điều kiện đăng), `92` (tổ hợp duy nhất), `93` (tối đa 50), `94` (giá trị trục), `96` (SKU duy nhất theo người bán), ở [`0.overview.md`](0.overview.md) mục 7.

## 6. Event

| Event | Khi nào | Nội dung chính | Bên nhận |
|---|---|---|---|
| `EVT-CAT-02` Đơn vị bán được được tạo, đổi trạng thái, bỏ | Biến thể được tạo, ngừng, bật lại; hoặc sản phẩm Nháp bị xóa kèm biến thể (bỏ) | Mã biến thể, mã sản phẩm, mã người bán, hoạt động hay không; **không mang giá** | Tồn kho (C2a) |
| `EVT-CAT-01` Sản phẩm công khai thay đổi | Biến thể hay giá đổi khi sản phẩm hiển thị | Trạng thái đầy đủ của sản phẩm kèm biến thể và giá | Tìm kiếm (C1b) |

Đổi giá không phát `EVT-CAT-02`: tồn kho không cần biết giá.

## 7. Chưa làm

| Năng lực | Trạng thái | Chặng dự kiến | Giá trị | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|---|
| Lịch sử giá | Cắt tạm | Chưa dự kiến | Biết giá đổi thế nào, chống giá ảo | Chưa có nhu cầu | Biến thể |
| Ảnh riêng cho biến thể | Cắt tạm | Chưa dự kiến | Mỗi màu có ảnh riêng | Ảnh chung cho sản phẩm đủ | Biến thể |
| Kích thước, cân nặng của biến thể | Chưa có ở root | C3a | Tính phí vận chuyển | Chưa có Định phí và Giao nhận | Biến thể |
| Giá theo bậc số lượng, giá sỉ | Ngoài | — | — | Ngoài phạm vi | — |
| Nhiều loại tiền | Ngoài | — | — | Ngoài phạm vi | — |

## 8. Câu hỏi cần trả lời

`Q-CAT-01` (giới hạn: số biến thể, trục) ở [`0.overview.md`](0.overview.md).
