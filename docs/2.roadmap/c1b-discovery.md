# C1b. Khám phá

> Bản thử khung tài liệu chặng: phân tích nhanh để tách feature.

## 1. Mục tiêu

Khách vào Cửa hàng tìm, lọc và xem được sản phẩm người bán đã đăng. Quản trị khóa được tài khoản và chặn được sản phẩm vi
phạm. Người dùng xem được lịch sử đăng nhập của mình.

## 2. Phạm vi

| Làm                                                           | Không làm (chặng sau)                              |
|---------------------------------------------------------------|----------------------------------------------------|
| Duyệt theo danh mục, tìm văn bản, lọc, sắp xếp                | Còn hàng hay không trên trang sản phẩm (C2)        |
| Trang chi tiết sản phẩm: biến thể, giá, ảnh                   | Giỏ hàng, đặt hàng (C2)                            |
| Quản trị tìm tài khoản, khóa và mở khóa                       | Đo tìm kiếm ở vài triệu sản phẩm (C6)              |
| Quản trị chặn và bỏ chặn sản phẩm                             | Báo cho người dùng khi bị khóa, bị chặn sản phẩm   |
| Lịch sử đăng nhập của người dùng                              | Thiết bị, đăng xuất từ xa                          |

## 3. BC tham gia

`Search`, `Catalog`, `Authentication`, `Identity`, nhật ký kiểm toán.

## 4. Feature

| #  | Feature                             | Cần trước |
|----|-------------------------------------|-----------|
| 1  | Đưa sản phẩm đã đăng sang tìm kiếm  |           |
| 2  | Tìm và lọc sản phẩm                 | 1         |
| 3  | Chi tiết sản phẩm                   |           |
| 4  | Quản lý tài khoản: tìm, khóa, mở    |           |
| 5  | Chặn sản phẩm                       | 1         |
| 6  | Lịch sử đăng nhập                   |           |
| 7  | Ứng dụng Cửa hàng                   | 2, 3, 6   |
| 8  | Ứng dụng Quản trị (bổ sung)         | 4, 5      |
