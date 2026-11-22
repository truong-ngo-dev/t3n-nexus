# Feature 8. Ứng dụng Người bán

> Chặng C1a (`2.roadmap/c1a-listing.md`, feature 8). Viết lại theo khung mới từ tài liệu cũ `stage/c1a-listing/11-seller-web/`
> (`flow.md`, `tech.md`, `plan.md`, `deferred.md`). Tài liệu cũ giữ làm lịch sử; từ nay làm theo thư mục này.

## 1. Feature này làm gì

Ứng dụng web Người bán (`seller-web`) chạy ở `seller.localhost`, đi qua `seller-web-bff` (đã có). Người dùng `USER` làm được bốn việc:
- nộp hồ sơ gian hàng và theo dõi kết quả duyệt;
- tạo sản phẩm có biến thể và giá;
- gắn ảnh;
- đăng sản phẩm.

Backend của cả bốn việc đã xong: hồ sơ ở feature 03 và 04 cũ, sản phẩm ở 07, ảnh ở 08, đăng ở 09.

## 2. Chia phần

Mỗi phần có `srs.md` (làm gì, quy tắc), `design.md` (màn hình, luồng bấm, API, mã đặt ở đâu), `test.md` (ca trọng yếu).

| #   | Phần                                   | Nội dung                                                            | Cần trước | Trạng thái                                                    |
|-----|----------------------------------------|---------------------------------------------------------------------|-----------|---------------------------------------------------------------|
| 0   | [Khung ứng dụng](0-app-shell/)         | Phiên, vai, trạng thái người bán, bố cục, đăng xuất                 |           | Code xong; cần đổi bố cục khi có khu sản phẩm                 |
| 1   | [Hồ sơ gian hàng](1-seller-profile/)   | Nộp, xem; sửa khi chờ duyệt hoặc đã duyệt; sửa và nộp lại khi bị từ chối | 0    | Có nộp, nộp lại, xem. Chưa có: hai chế độ sửa (cần backend), cách hiện lỗi đầy đủ, test thành phần, test qua trình duyệt |
| 2   | Sản phẩm và biến thể                   | Danh sách, tạo và sửa nháp, thuộc tính, trục, biến thể, giá, SKU    | 0, 1      | Chưa viết                                                     |
| 3   | Ảnh sản phẩm                           | Tải lên, tiến trình, thứ tự, bỏ ảnh                                 | 2         | Chưa viết                                                     |
| 4   | Đăng sản phẩm                          | Đăng, gỡ, đăng lại, xóa nháp; báo thiếu điều kiện gì                | 2, 3      | Chưa viết                                                     |

Phần 2 lớn (thuộc tính đổi theo danh mục, trục, biến thể); khi viết có thể tách thành hai thư mục con: thông tin sản phẩm và
biến thể.

**Đối chiếu với lát cũ.**

| Lát cũ (`11-seller-web/plan.md`) | Phần mới |
|----------------------------------|----------|
| 11a Khung                        | 0        |
| 11b Hồ sơ gian hàng              | 1        |
| 11c Sản phẩm và biến thể         | 2        |
| 11d Ảnh                          | 3        |
| 11e Đăng sản phẩm                | 4        |

## 3. Bài toán frontend của feature

Theo `1.requirement/6-ui-analysis.md`:
- **5.7 Biểu mẫu lớn, lỗi máy chủ gắn vào ô:** hồ sơ (phần 1), sản phẩm có thuộc tính đổi theo danh mục (phần 2).
- **5.2 Gửi trùng và thử lại an toàn:** nộp hồ sơ, xác nhận ảnh, đăng sản phẩm.
- **5.3 Lỗi từng phần:** trang sản phẩm có nhiều khối (thông tin, biến thể, ảnh); một khối lỗi thì khối khác vẫn dùng được.
- **5.5 Nhiều ứng dụng dùng chung nền:** dùng lại `@t3n/auth`, `@t3n/http`, `@t3n/model`.

## 4. Việc phía backend và thư viện phát hiện khi viết lại

| Việc                                                                                                                                           | Phần |
|------------------------------------------------------------------------------------------------------------------------------------------------|------|
| `ErrorInfo` của `@t3n/http` không giữ `traceId`, trong khi sàn chung (6-ui mục 6) đòi hiện mã truy vết cho lỗi hệ thống. Trùng việc ở feature 9 | 0    |
| (Đã sửa `2026-11-22`) Các lệnh gọi hồ sơ không bọc `requestTimeout()`, dù chú thích nói là có                                 | 1    |
| `seller-service`, bốn việc (`1-seller-profile/srs.md` mục 7):<br>- thêm `PUT /profile` cho sửa khi chờ duyệt;<br>- bỏ `PATCH legal-info`;<br>- `PATCH shop-info` chỉ mở khi đã duyệt và khóa tên gian hàng;<br>- cập nhật tài liệu service | 1 |
| `catalog-service/api.yaml` khai `delete` của `/api/seller/products/{id}` hai lần: một bản thụt lề sai nằm trong `patch`. Phải bỏ bản thừa          | 2, 4 |
