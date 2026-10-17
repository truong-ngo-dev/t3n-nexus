# Feature 04: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt kê những thứ chưa làm nằm ngoài phạm vi: API tạo, sửa tài khoản nội bộ,
đổi mật khẩu, bắt đổi mật khẩu lần đầu (C1b).

| Phần hoãn | Đang làm gì ở C0 | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| UUID v7 từ hàm có sẵn của Postgres | Script tự dựng UUID v7 bằng biểu thức SQL vì Postgres 16 chưa có `uuidv7()` (`tech.md` T5) | Script ngắn hơn, không phụ thuộc một biểu thức tự viết | Dự án chạy Postgres 16; nâng bản không thuộc feature này | Khi nâng Postgres lên 18: thay biểu thức bằng `uuidv7()`; KT-07 vẫn kiểm kết quả |
| Nhiều miền nội bộ trong script | Script nhận **một** miền (mặc định `t3nexus.com.vn`), phải là một trong các miền của `INTERNAL_EMAIL_DOMAINS` (`tech.md` T3) | Email Quản trị thuộc miền nội bộ nào cũng được kiểm theo đúng cấu hình của dịch vụ | Chỉ cần một tài khoản Quản trị ở một miền; danh sách trong script dễ lệch với cấu hình hơn một giá trị | Khi có nhiều tài khoản nội bộ cần seed (C1b): đọc miền từ cùng nguồn cấu hình với dịch vụ |
