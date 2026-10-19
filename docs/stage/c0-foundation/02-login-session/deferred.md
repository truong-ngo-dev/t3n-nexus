# Feature 02: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi; năng lực nghiệp vụ chưa làm của BC nằm ở mục "Chưa làm" của `analysis/0.overview.md`.

| Phần hoãn | Đang làm gì ở C0 | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Job dọn phiên quá hạn và `oauth2_authorization` mồ côi | Chỉ có truy vấn phiên quá hạn theo lô (`findExpired`) ở kho; chưa viết use case dọn và chưa có ai gọi định kỳ | Dung lượng bảng, vệ sinh dữ liệu | Việc dọn chạy qua Lập lịch (C2b); dòng quá hạn chỉ tốn chỗ vì hạn đã kiểm ở điểm cấp token (ADR-0003 mục 7) | Lập lịch gửi lệnh định kỳ, service nhận và gọi use case dọn |
| Đo NFR của đăng nhập và làm mới token (tải duy trì, điểm gãy, hành vi `oauth2_authorization` dưới tải) | Chỉ có phép đo tính đúng; các dòng đo tải còn nhãn Chỉ thiết kế (`nfr.md`) | Số đo thật về P99, trần theo máy, hiệu quả tra theo khóa chính | Cần trần của máy từ bộ đo nền (feature 08) | Chạy phép đo ở bước 14, rồi đổi nhãn |
| Chống bot (CAPTCHA) | Chỉ chừa chỗ ở bước "kiểm trước khi xử lý", mặc định tắt | Chặn dò mật khẩu bằng công cụ | Giới hạn số lần thử và địa chỉ mạng đủ cho C0; cần dịch vụ ngoài | `analysis/0.overview.md` mục 10 |
| Client đăng ký cho ứng dụng Cửa hàng và ứng dụng Vận hành | Chỉ có `storefront-web-bff` (BFF của Người dùng) và `postman-dev` (chỉ dev); đăng nhập tài khoản Quản trị chạy qua client dev | Đăng nhập thật của hai ứng dụng này, kiểm S4 và KT-09 qua đúng client | Cách nối BFF cho hai ứng dụng chưa chốt, đăng ký trước sẽ phải sửa lại | Khi chốt nối BFF (feature 05/06), thêm client vào `RegisteredClientSeeder` |
| Thư viện dùng chung để dịch vụ nhận token kiểm token (`security-starter`) | Chỉ chuỗi `/api/**` của `oauth2-service` tự dựng `JwtDecoder` với độ lệch đồng hồ 5 giây (`KeyConfiguration`); quy ước ở `tech.md` T8 | Dịch vụ nhận token không phải tự nhớ cấu hình (dung sai 5 giây, kiểm `iss`, `aud`, đọc `sid`, `role`) | Chưa có dịch vụ nào khác nhận token; làm sớm dễ phải sửa khi dịch vụ đầu tiên cần | Khi có dịch vụ nhận token thứ hai, hoặc ở feature 06 nếu cổng truy cập kiểm token |
