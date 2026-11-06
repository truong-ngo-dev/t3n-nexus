# Thương hiệu — `AGG-CAT-03`

Một trong năm aggregate của Danh mục hàng (xem [`0.overview.md`](0.overview.md)). Mã BC `CAT`.

## 1. Định nghĩa

**Thương hiệu** là nhãn của sản phẩm (Nike, Samsung). Người mua dùng nó để nhận ra và lọc hàng. Danh sách thương hiệu do Quản trị giữ; người bán chọn từ danh sách.

**Thương hiệu là tùy chọn với sản phẩm.** Hàng nhà làm, hàng không nhãn bán rất nhiều; bắt buộc chọn thương hiệu sẽ buộc người bán gán nhãn sai. Phương án đã cân nhắc: bắt buộc có thương hiệu, kèm một thương hiệu "Không thương hiệu". Loại vì để trống tự nhiên hơn và không thêm một giá trị đặc biệt để lọc phải loại ra.

Ở C1a thương hiệu **tối giản**: tên và trạng thái dùng. Xác thực quyền sử dụng thương hiệu (giấy ủy quyền) là việc chưa làm.

## 2. Thành phần

- **Tên** — hiển thị nguyên dạng; duy nhất toàn sàn sau chuẩn hóa (bỏ khoảng trắng thừa, chữ thường, bỏ dấu).
- **Đang dùng / ngừng dùng** — thương hiệu ngừng dùng không chọn được cho sản phẩm mới.

## 3. Vòng đời & hành vi

- Quản trị tạo, đổi tên, ngừng dùng, bật lại. **Thương hiệu không bao giờ bị xóa, chỉ ngừng dùng**: sản phẩm tham chiếu nó bằng mã và cần đọc được lâu sau đó.
- Người bán **không** tự thêm thương hiệu: ở C1a nếu thương hiệu chưa có thì nhờ Quản trị thêm (xem Chưa làm).
- Sản phẩm đang dùng thương hiệu đã ngừng vẫn giữ nhãn đó, chỉ không chọn được cho sản phẩm khác.
- Đổi tên thương hiệu có hiệu lực ngay cho mọi sản phẩm đang dùng.

## 4. Năng lực

Sáu câu của P3 (xem `category.md` mục 4).

### CAP-CAT-03 Quản thương hiệu (stage: H4)

1. Quản trị.
2. Danh mục tự xử lý: kiểm quyền (`INV-CAT-04`), tên duy nhất (`INV-CAT-20`), thương hiệu ngừng dùng không chọn mới (`INV-CAT-21`).
3. Phát `EVT-CAT-04` cho Nhật ký kiểm toán.
4. Đường không suôn sẻ:
   - Tên trùng (kể cả khác dấu, khác chữ hoa): từ chối. Hai yêu cầu đồng thời: chỉ một thành công.
   - Không phải Quản trị: từ chối.
5. Lịch sử: Nhật ký kiểm toán (`BRAND_CREATED`, `BRAND_UPDATED`, `BRAND_DEACTIVATED`).
6. Chảy sang Nhật ký kiểm toán. Người bán đọc danh sách qua `RM-CAT-03`.

## 5. Bất biến (INV)

| ID | Bất biến | Lý do | Loại | Kiểm tra khi | Từ stage |
|---|---|---|---|---|---|
| `INV-CAT-20` | Tên thương hiệu duy nhất toàn sàn, so sánh sau chuẩn hóa | Chống trùng và mạo nhãn | Liên tục | tạo, đổi tên | R10 |
| `INV-CAT-21` | Thương hiệu ngừng dùng không được chọn cho sản phẩm mới; thương hiệu không bị xóa, chỉ ngừng dùng | Quản trị muốn dừng một nhãn; sản phẩm tham chiếu thương hiệu bằng mã | Tiên quyết | gắn thương hiệu cho sản phẩm | mới |

## 6. Event

| Event | Khi nào | Nội dung chính | Bên nhận |
|---|---|---|---|
| `EVT-CAT-04` Hành động quản trị đã thực hiện | Quản trị tạo, sửa, ngừng thương hiệu | Người thực hiện, hành động `BRAND_*`, mã thương hiệu, phiên bản | Nhật ký kiểm toán |

## 7. Chưa làm

| Năng lực | Trạng thái | Chặng dự kiến | Giá trị | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|---|
| Người bán đề xuất thương hiệu mới | Cắt tạm | Chưa dự kiến | Bớt việc cho Quản trị | Chưa có nhu cầu | Hành động mới với bước duyệt |
| Xác thực quyền sử dụng thương hiệu (giấy ủy quyền) | Cắt tạm | Chưa dự kiến | Chống hàng nhái | Cần tệp riêng tư và quy trình xét | Hồ sơ thương hiệu của người bán |
| Logo, mô tả thương hiệu | Cắt tạm | Chưa dự kiến | Trình bày | Chưa có nhu cầu | Thương hiệu |

## 8. Câu hỏi cần trả lời

Không có. Dữ liệu khởi tạo: `Q-CAT-02` ở [`0.overview.md`](0.overview.md).
