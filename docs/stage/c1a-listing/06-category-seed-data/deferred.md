# Feature 06: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi.

| Phần hoãn | Đang làm gì ở C1a | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| `EVT-CAT-03` cho dữ liệu mẫu | Script ghi thẳng bảng, không phát event và không ghi kiểm toán (dữ liệu khởi tạo thuộc môi trường, như tài khoản Quản trị) | Tìm kiếm (C1b) nhận đủ thông điệp cho mọi danh mục và mẫu, kể cả bộ mẫu, mà không cần cách dựng riêng | C1a chưa có bên nhận `EVT-CAT-03`; sinh event và kiểm toán cho dữ liệu khởi tạo đòi nạp qua miền hoặc chép logic hộp thư vào SQL, trái cách làm "script chạy tay" đã chốt | C1b: Tìm kiếm dựng chỉ mục ban đầu **từ cơ sở dữ liệu của Danh mục hàng** (bản chụp trạng thái hiện tại), rồi mới nghe `EVT-CAT-03` từ đó về sau; không trông chờ event cho dữ liệu cũ |
