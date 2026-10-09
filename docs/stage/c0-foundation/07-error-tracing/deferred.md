# Feature 07: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi (ELK ở roadmap C2a, bộ lọc của cổng truy cập ở feature 06).

| Phần hoãn | Đang làm gì ở C0 | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Quét bí mật ở console dạng chữ | Chỉ quét ở file JSON bằng `SecretScrubbingCustomizer`; console dạng chữ không được quét | Token lọt vào `message` cũng không hiện trên console của dev | Quy ước "không đưa bí mật vào log" là lớp phòng thủ chính; console chỉ dev đọc, dữ liệu gom lên ELK đã được quét | Thêm lớp quét cho bộ mã hóa chữ của console, dùng chung `SecretPatterns` |
| Che theo tên trường và cắt độ dài trong `message`, `error.message`, `error.stack_trace` | Chỉ che ba mẫu: `Bearer …`, JWT, mã băm bcrypt. `password=…` nằm trong `message` không bị che | Lưới an toàn phủ cả trường hợp code lỡ ghi cặp tên nhạy cảm vào `message` | Code hiện không đưa nội dung yêu cầu vào `message`; mẫu rộng dễ che nhầm | Dùng mẫu che theo tên của `BodySanitizer` (`namedValuePattern`) trong `SecretScrubbingCustomizer` |
