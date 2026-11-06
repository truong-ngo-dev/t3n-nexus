# Feature 09: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi (chặn và mở chặn sản phẩm, người mua xem sản phẩm hiển thị, bên nhận sự kiện ở Tìm kiếm và Tồn kho).

| Phần hoãn | Đang làm gì ở C1a | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Phát lại hàng loạt khi người bán mất trạng thái duyệt (một điều kiện của `INV-CAT-95`) | `visible` tính bằng hàm `isPubliclyVisible` nhưng ở C1a trạng thái "Được duyệt" không thu hồi được nên không có chuyện sản phẩm hết hiển thị do người bán | Sản phẩm của người bán bị khóa hết hiển thị đúng lúc ở Tìm kiếm và Tồn kho | `seller-service` chỉ thêm trạng thái "Bị khóa" ở C1b; không có sự kiện nào để phản ứng | C1b: nhận sự kiện khóa người bán, phát `EVT-CAT-01` và `EVT-CAT-02` (`visible=false`) cho mọi sản phẩm đang hiển thị của người đó, theo từng đợt |
| Nạp bản sao trạng thái cho người bán đã duyệt trước khi catalog có consumer | Script tay `infra/seed/catalog-seller-state.sql` theo mã tài khoản; không tự phát lại sự kiện từ `seller-service` | Môi trường dev có sẵn dữ liệu cũ không phải duyệt lại | Phát lại sự kiện cần công cụ ở `seller-service` hoặc quay lại offset Kafka, vượt phạm vi | Khi cần cho môi trường thật: lệnh quản trị phát lại `SellerProfileApproved` của `seller-service` |
| Gộp bản chụp theo thời gian khi sản phẩm đổi dồn dập | Mỗi lệnh sửa sản phẩm đang hiển thị ghi một bản chụp đầy đủ (tối đa vài chục KB) | Giảm tải hộp thư, Kafka và bên nhận khi người bán sửa hàng loạt | Chưa có số đo; hợp đồng không đổi khi gộp (bên nhận ghi đè theo `version`) | Gộp ở chỗ phát (gom nhiều thay đổi của một sản phẩm trong cửa sổ ngắn) khi có số liệu |
| Quy tắc "sản phẩm đã đăng phải còn biến thể hoạt động" | Không có: sản phẩm đã đăng hết biến thể hoạt động vẫn hiển thị (người mua thấy hết hàng) | Không bán được thứ không có đơn vị bán | `analysis` không có quy tắc này; chưa rõ hành vi mong muốn khi người mua đã thấy trang | C1b: chốt cùng người mua xem sản phẩm hiển thị (`RM-CAT-05`) |
