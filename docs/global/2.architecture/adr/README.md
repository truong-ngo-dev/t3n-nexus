# Architecture Decision Records

Mỗi ADR là một quyết định kỹ thuật đúng cho cả BC hoặc mọi BC. **Không sửa** ADR đã chấp nhận: khi quyết định đổi thì
tạo ADR mới và đánh dấu ADR cũ là Superseded. Quyết định chỉ đúng cho một feature nằm ở `tech.md` của feature đó. Tên
file: `NNNN-<slug>.md`.

| # | Tiêu đề | Trạng thái |
|---|---|---|
| 0001 | Định dạng mã định danh thực thể | Accepted |
| 0002 | Băm mật khẩu | Accepted |
| 0003 | Phiên đăng nhập hai tầng, hợp đồng danh tính và làm mới token | Accepted |
| 0004 | Mã một lần | Proposed |
| 0005 | Bảo vệ yêu cầu từ trình duyệt và cookie mã thiết bị | Accepted |
| 0006 | Nguồn gốc của các ứng dụng và định tuyến theo `Host` | Accepted |
| 0007 | Cấu trúc workspace giao diện `frontends/` | Accepted |
| 0008 | Hộp thư event và nhận event | Accepted |
| 0009 | Bản ghi kiểm toán bất biến ở lưu trữ | Accepted |
| 0010 | Một hàng chờ lỗi cho mỗi dịch vụ nhận | Accepted (thay tên chủ đề DLQ của ADR-0008) |
| 0011 | Khóa lạc quan chạy ngầm và tính duy nhất chốt ở lưu trữ | Accepted |
| 0012 | Khóa dòng cha cho thao tác trên thực thể con có quy tắc chung | Accepted |
| 0013 | Cơ chế tải tệp lên dùng chung (xin chỗ, tải thẳng lên kho, xác nhận) | Accepted |
| 0014 | Sự kiện trạng thái đầy đủ thay cho sự kiện nhỏ theo chuyển đổi (D6) | Accepted |
