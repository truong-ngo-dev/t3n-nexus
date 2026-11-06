# Feature 04: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi.

| Phần hoãn | Đang làm gì ở C1a | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| `audit-service` nhận và lưu bản ghi kiểm toán của quyết định (S8 phía nhận) | Phía phát đã chạy thật: mỗi quyết định ra đúng một bản `AuditActionRecorded` ở `audit.action.recorded` với đúng hành động, quản trị, đối tượng và dữ kiện (có lý do khi từ chối, không số căn cước); bản ghi đọc lại được bằng `AuditRecordReader` trong test. Hợp đồng hai phía đã kiểm riêng ở feature 02 | Thấy bản ghi nằm trong nhật ký tra cứu được ngay sau khi duyệt hoặc từ chối | Cần dựng thêm `audit-service` và cơ sở dữ liệu của nó trong chạy thật, tốn bộ nhớ trên máy hiện tại; không thêm mã ở phía phát | Feature 13 (kiểm đầu-cuối): nộp, duyệt, rồi tra nhật ký qua API của `audit-service`; kịch bản nhóm B (`e2e-seller-f04.sh b`) đã có hạ tầng Kafka và Connect |
| Nhiều quyết định đến cùng lúc qua Kafka | Chạy thật qua Kafka chỉ tuần tự (một duyệt, một từ chối). Đồng thời đã kiểm trên Postgres thật: 20 quản trị cùng duyệt một hồ sơ cho đúng một 200, và test Maven (duyệt đua với từ chối, với chủ sửa) | Biết chắc khi nhiều hồ sơ được quyết định song song thì mỗi quyết định ra đúng một thông điệp kết quả và một bản ghi kiểm toán, không lẫn khóa | Cơ chế chuyển hộp thư và khóa thông điệp không đổi so với feature 03; thêm ca tốn thêm thời gian chạy Kafka trên máy thiếu bộ nhớ | Feature 13, hoặc thêm một mục đếm vào `e2e-seller-f04.sh` (ví dụ 10 hồ sơ duyệt song song rồi đếm 10 thông điệp) khi máy đủ bộ nhớ |
