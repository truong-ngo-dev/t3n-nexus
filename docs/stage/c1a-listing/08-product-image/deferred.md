# Feature 08: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi (bỏ ảnh cuối của sản phẩm đã đăng và `EVT-CAT-01` mang ảnh: feature 09; phục vụ ảnh cho người mua: C1b; chuyển avatar của `identity-service` sang cơ chế chung; thumbnail, quét virus).

| Phần hoãn | Đang làm gì ở C1a | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Giới hạn tần suất xin chỗ tải lên | Chỉ chặn bằng hạn mức chỗ còn hiệu lực mỗi sản phẩm (`max-pending`, mặc định 9); chỗ hết hạn sau 5 phút | Chống người dùng xin chỗ liên tục để làm đầy kho hoặc làm chậm dịch vụ | Catalog chưa có thư viện giới hạn tần suất và Redis; cổng truy cập (feature 10) là nơi đặt giới hạn theo người dùng cho mọi dịch vụ | Feature 10: giới hạn ở cổng; hoặc gắn `@RateLimit` của `rate-limiter-starter` vào `RequestImageUpload` khi catalog có Redis |
| Dọn tệp mồ côi ngoài dòng chỗ | `UploadCleaner` xóa chỗ hết hạn cùng đối tượng của chúng; bỏ ảnh xóa đối tượng nỗ lực tốt nhất | Tệp mồ côi (xóa đối tượng lỗi sau khi bỏ ảnh, hoặc đối tượng mà dòng chỗ không còn) không tốn kho mãi | Cần quy tắc vòng đời của bucket (xóa theo tuổi), thuộc vận hành kho; tệp mồ côi hiếm và nhỏ | Quy tắc vòng đời trên bucket `product-images`, hoặc một lần quét đối chiếu `product_image` với kho |
| CORS cho bucket để trình duyệt tải thẳng lên | Server cấp form ký và đã kiểm chứng bằng HTTP trực tiếp; không đặt CORS | Giao diện Người bán tải ảnh từ trình duyệt được | Cấu hình CORS của MinIO gắn với địa chỉ giao diện, chưa có ở C1a | Feature 11: đặt CORS trên bucket cùng địa chỉ giao diện; `app.minio.public-endpoint` cho địa chỉ mà trình duyệt gọi |
