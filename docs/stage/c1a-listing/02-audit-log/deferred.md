# Feature 02: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi.

| Phần hoãn | Đang làm gì ở C1a | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Kiểm token và vai `ADMIN` của API tra cứu (`INV-AUD-06`, KT-13) | API không kiểm token và không kiểm vai; dịch vụ chỉ chạy trong mạng dev và chưa có route ở cổng truy cập (`tech.md` T8) | Chỉ Quản trị đọc được nhật ký | Người chốt (`2026-10-24`) làm phân quyền một lần cho các dịch vụ khi có mô-đun phân quyền | Use case, kho và bộ nhận không biết người gọi; thêm cấu hình bảo mật và chú thích ở lớp trình bày, rồi viết KT-13 |
| Chuỗi băm / bằng chứng toàn vẹn ngoài cơ sở dữ liệu | Bất biến bằng quyền tài khoản và trigger (ADR-0009); không chặn được người quản trị cơ sở dữ liệu cố ý gỡ | Phát hiện giả mạo cả từ người có quyền quản trị | Chưa có yêu cầu pháp lý hay sự cố giả mạo | Bản ghi có thể thêm cột băm mà không đổi quyết định của ADR-0009 |
| Hết hạn và dọn bản ghi (`INV-AUD-08`) | Bản ghi giữ mãi (`Q-AUD-01` chốt: không hết hạn ở C1a); bảng chỉ tăng | Dung lượng | Quy mô học tập vài trăm bản ghi mỗi ngày | Cột `occurred_at` đã có chỉ mục; phân vùng theo thời gian nếu cần |
| Cảnh báo khi bản ghi vào hàng chờ lỗi hoặc khóa dữ kiện bị bỏ | Chỉ log (`EVENT_DEAD_LETTERED`, `AUDIT_DETAILS_KEY_DROPPED`) và bộ đếm Micrometer (`audit.entries.recorded`, `audit.entries.duplicate`, `audit.details.keys_dropped`) | Biết ngay khi nhật ký thiếu | Chưa có hạ tầng thông báo cho người trực | Bộ đếm và mã log là điểm nối |
| Đo ở quy mô lớn hơn 100 nghìn dòng và dưới tải | Đã đo 100.356 dòng: trang 13 đến 25 ms, `Index Scan` cả năm dạng truy vấn (`tech.md` T12) | Biết ngưỡng cần phân vùng hay đổi kho | Khối lượng dự kiến chỉ vài trăm bản ghi mỗi ngày | Feature 13 (kiểm đầu-cuối) |
| Khởi động container `postgres-audit` của compose dev | Đã khai báo và kiểm cú pháp compose; cơ chế tạo hai tài khoản đã chạy thật trên Postgres của stack cô lập, chưa qua container dev | Bảo đảm môi trường dev dùng được ngay | Người rà chạy khi cần dev; tránh ghi hạ tầng dev trong lúc đóng | Chạy `docker compose up postgres-audit` rồi khởi động `audit-service` |
