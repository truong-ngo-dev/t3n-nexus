# C1a. Niêm yết

> Bản thử khung tài liệu chặng: phân tích nhanh để tách feature.

## 1. Mục tiêu

Người bán nộp hồ sơ và được duyệt, rồi đăng được sản phẩm có biến thể, giá, ảnh. Hành động của quản trị tra lại được.

## 2. Phạm vi

| Làm                                                   | Không làm (chặng sau)                          |
|-------------------------------------------------------|------------------------------------------------|
| Hồ sơ người bán: nộp, sửa, nộp lại, duyệt, từ chối    | Tìm, lọc, xem sản phẩm cho khách (C1b)         |
| Danh mục, thuộc tính, thương hiệu                     | Khóa tài khoản, chặn sản phẩm (C1b)            |
| Sản phẩm, biến thể, giá, ảnh; đăng và gỡ              | Thông báo kết quả duyệt                        |
| Nhật ký hành động quản trị                            | Giấy tờ đính kèm hồ sơ                         |

## 3. BC tham gia

`Seller`, `Catalog`, nhật ký kiểm toán.

## 4. Feature

| #  | Feature                   | Cần trước  | Bản chia cũ | Trạng thái                                   |
|----|---------------------------|------------|-------------|----------------------------------------------|
| 1  | Nền sự kiện giữa các BC   |            | 01          | Xong                                         |
| 2  | Nhật ký quản trị          | 1          | 02          | Xong                                         |
| 3  | Hồ sơ người bán           |            | 03          | Xong                                         |
| 4  | Duyệt người bán           | 2, 3       | 04          | Xong                                         |
| 5  | Danh mục                  | 2          | 05, 06      | Xong                                         |
| 6  | Sản phẩm                  | 5          | 07, 08      | Xong                                         |
| 7  | Đăng sản phẩm             | 4, 6       | 09          | Xong                                         |
| 8  | Ứng dụng Người bán        | 3, 7       | 10, 11      | Đang làm: xong khung, dở hồ sơ, chưa có sản phẩm |
| 9  | Ứng dụng Quản trị         | 4, 5       | 10, 12      | Đang làm: tài liệu ở `7.feature/c1a-listing/12-operation-ui/` |

**Về bản chia cũ.** C1a đã làm theo bản chia 13 feature ở `stage/c1a-listing/00-features.md`. Bảng trên là bản chia lại theo
khung mới, chỉ để so; tài liệu và thư mục của từng feature giữ số cũ. Vì vậy thư mục của feature 9 tên là `09-operation-ui`.

Hai chỗ lệch giữa hai bản:
- **Cổng BFF.** Bản cũ có feature riêng (10) cho cổng của Người bán và Quản trị. Ở bản mới, mỗi cổng đi cùng ứng dụng của nó,
  là feature 8 và 9.
- **Kiểm đầu-cuối.** Bản cũ có feature riêng (13) để kiểm đầu-cuối cả chặng. Bản mới không tách: mỗi feature kiểm luồng của mình
  ở `test.md`, phần chạy thật. Việc chạy lại cả chuỗi làm một lần khi đóng chặng, không thành một feature.
