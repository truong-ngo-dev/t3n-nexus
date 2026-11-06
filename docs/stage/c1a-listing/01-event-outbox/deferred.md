# Feature 01: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi.

| Phần hoãn | Đang làm gì ở C1a | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Dọn hàng hộp thư đã chuyển | Hàng ở lại mãi sau khi chuyển; bảng chỉ tăng (`tech.md` T2) | Dung lượng bảng, truy vấn nhanh trên bảng nhỏ | Quy mô học tập chưa đau; việc định kỳ thuộc Lập lịch (roadmap C2b) | Cột `created_at` đã có chỉ mục để dọn theo tuổi |
| Báo tới người trực khi có thông điệp vào hàng chờ lỗi | Chỉ có log ERROR `EVENT_DEAD_LETTERED` và bộ đếm `events.dead_lettered` theo chủ đề (`tech.md` T6) | Lỗi nhận được biết ngay thay vì khi ai đó đọc log | Chưa có hạ tầng thông báo cho người trực | Bộ đếm và mã log là điểm nối để gắn cảnh báo |
| Xử lý lại thông điệp trong hàng chờ lỗi | Thủ công, bằng công cụ của Kafka; không có tác vụ tự xử lại (`tech.md` T6) | Phục hồi sau sự cố không cần thao tác tay | Handler đã khử trùng nên chạy lại tay an toàn; tác vụ tự xử lại cần quy tắc chọn lúc nào đủ an toàn | Hàng chờ lỗi `<dịch vụ>.dlq` (ADR-0010) giữ nguyên khóa và giá trị để phát lại |
| Giới hạn giữ nhật ký giao dịch khi connector dừng lâu | Chấp nhận cơ sở dữ liệu dev giữ nhật ký không dọn được khi khe sao chép đứng yên (`tech.md` T11) | Chặn đầy đĩa khi connector hỏng lâu | Dev chấp nhận rủi ro; chọn giới hạn cần số đo thật | Tham số giữ tối đa của khe sao chép ở Postgres |
| Đo độ trễ dưới tải | Mới đo 5 event đơn lẻ: 0,5 đến 2 giây ổn định, 11 đến 14 giây khởi động lạnh (`tech.md` T13) | Số thật về độ trễ và thông lượng của hộp thư | Cần chuỗi đầu-cuối có tải, thuộc feature 13 | Feature 13 |
| Chính sách trường nhạy cảm của `oauth2-service` | `app.outbox.sensitive-field-policy=off`: event `LoginOtpRequested` mang `token` (`tech.md` T4) | Không để bí mật đi qua bus | Gỡ cần đổi hợp đồng event đăng nhập; không thuộc phạm vi C1a | Thuộc tính trong `application.properties` của dịch vụ, kèm lý do |
