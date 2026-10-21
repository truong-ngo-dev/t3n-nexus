# Feature 01: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi; năng lực nghiệp vụ chưa làm của BC nằm ở mục "Chưa làm" của `analysis.md`.

| Phần hoãn                                | Đang làm gì ở C0                            | Giá trị của phần hoãn               | Lý do xếp sau                                           | Điểm nối                                       |
|------------------------------------------|---------------------------------------------|-------------------------------------|---------------------------------------------------------|------------------------------------------------|
| Kiểm mật khẩu quá phổ biến hoặc đã bị lộ | Chỉ kiểm theo luật mật khẩu ở `analysis.md` | Chặn mật khẩu yếu thật sự           | Cần danh sách dữ liệu bên ngoài; không cần để chạy được | Một bước kiểm thêm trong kiểm đầu vào (bước 3) |
| Chống bot (CAPTCHA) | Chỉ giới hạn tần suất theo địa chỉ mạng ở cổng | Chặn đăng ký hàng loạt bằng công cụ | Giới hạn theo địa chỉ mạng đủ cho C0; cần dịch vụ ngoài; làm sau cùng của chặng | Bước kiểm bằng chứng người thật, tùy chọn và mặc định tắt, đứng trước giới hạn tần suất và mọi xử lý; thêm được trường bằng chứng tùy chọn vào đầu vào đăng ký mà không phá hợp đồng cũ (xem `analysis.md` mục 8) |
| Đo NFR của đăng ký (tải duy trì, trần thử, 3 triệu tài khoản) | Chỉ có phép đo tính đúng khi đồng thời; các dòng đo tải còn nhãn Chỉ thiết kế (`nfr.md`) | Số đo thật về P99, trần theo máy, tra trùng ở 3 triệu dòng | Trần máy đã có từ bộ đo nền (feature 08, `2026-10-20`); còn thiếu chính phép đo ứng dụng | Chạy phép đo ở `nfr.md` (dùng điều kiện container như 08), rồi đổi nhãn |
