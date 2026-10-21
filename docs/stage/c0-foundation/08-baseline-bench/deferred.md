# Feature 08: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi (đo qua cổng, đo mở rộng nhiều thể hiện, đo theo tỷ lệ, bộ đệm, tìm kiếm, mặt hàng nóng:
xem `flow.md` mục 1; các phép đo ứng dụng của feature 01, 02, 07: nằm ở `deferred.md` và `plan.md` của từng feature).

| Phần hoãn | Đang làm gì ở C0 | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Đo ở gói điện High performance | Đo ở gói Balanced có cắm sạc, vì đổi gói điện là cài đặt hệ thống của người dùng (`tech.md` T5) | Trần cao nhất của máy, bỏ nhiễu do bộ điều tốc | Chưa có yêu cầu đổi cài đặt; số hiện tại là trần ở điều kiện làm việc thường ngày | Đặt gói High performance rồi chạy lại ba script trong `bench/` (khoảng 40 phút cả ba), đối chiếu `tech.md` T5 đến T7 |
| Điểm bão hòa thật của băm và ghi | Băm đo tới 16 luồng (bằng số luồng logic), ghi tới 64 kết nối; trần ghi là mức cao nhất đã đo | Biết có tăng thêm khi vượt các mức đó hay không | Với băm, vượt số luồng logic thường không tăng; với ghi, CPU của Postgres đã chạm hạn mức | Thêm bậc 24 đến 32 luồng cho `BcryptBench`, bậc 96 kết nối (đặt `max_connections` cao hơn) cho `JdbcWriteBench` |
| Biến thể luồng ảo của điểm cuối rỗng | Đo cấu hình mặc định (luồng nền tảng của Tomcat), giống dịch vụ thật | Biết luồng ảo đổi trần đọc bao nhiêu | Dịch vụ thật chưa bật luồng ảo; "một biến đổi một lần" nên đo riêng | Thêm `--spring.threads.virtual.enabled=true` vào lệnh chạy khung trong `bench/run-empty.sh`, so với `tech.md` T6 |
| Ghi ở quy mô dữ liệu lớn | Bảng nhỏ (khoảng 330 nghìn dòng sau mỗi mức), nằm trọn trong bộ nhớ đệm | Trần ghi khi chỉ mục lớn hơn bộ nhớ đệm (3 triệu tài khoản, NFR 4.3) | Nạp 3 triệu dòng thuộc phép đo ứng dụng của đăng ký (feature 01 bước 11); làm ở đó mới khớp dữ liệu thật | `JdbcWriteBench` với bảng đã nạp sẵn dữ liệu lớn, so với `tech.md` T7 |
