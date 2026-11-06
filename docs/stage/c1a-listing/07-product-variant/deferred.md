# Feature 07: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi (ảnh: feature 08; đăng, gỡ, xóa nháp, điều kiện đăng đầy đủ, cờ hiển thị và hai event `EVT-CAT-01`, `EVT-CAT-02`: feature 09).

| Phần hoãn | Đang làm gì ở C1a | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Nửa "người bán đã được duyệt" của điều kiện tạo sản phẩm (`INV-CAT-30`) | Tạo, sửa và thêm biến thể kiểm vai `USER` và quyền chủ sản phẩm (chủ lấy từ token); tài khoản `USER` nào cũng tạo được nháp, kể cả người bán chưa duyệt | Người bán chưa được duyệt, hoặc đã bị từ chối, không có nháp trong hệ thống | Cần bản sao trạng thái người bán (`seller_state`), nhận từ kết quả duyệt của `seller-service`; bản sao thuộc feature 09 cùng với điều kiện đăng. Nháp chưa hiển thị cho ai, nên chưa gây hại | Feature 09: `CreateProduct` đọc bản sao qua cổng riêng và từ chối khi chưa duyệt; thêm ca test "người bán chưa duyệt tạo → bị từ chối" |
