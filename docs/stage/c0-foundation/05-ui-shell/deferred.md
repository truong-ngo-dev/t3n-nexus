# Feature 05: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau.

| Phần hoãn | Đang làm gì ở C0 | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Kiểm khung nhìn điện thoại (KT-21 đã bỏ khỏi `flow.md`) | Cửa hàng là web đáp ứng (`5-ui-analysis.md` mục 2) và dùng bố cục co giãn của Angular Material, nhưng không có test nào kiểm không cuộn ngang hay nút bấm được ở màn hẹp | Bắt lỗi bố cục trên điện thoại ngay khi xảy ra | Người dùng chọn làm khi bàn lại phần giao diện; khung C0 chỉ có bốn màn đơn giản | Khi làm giao diện thật (Trang chủ, danh mục): thêm test Playwright với khung nhìn hẹp, dùng lại số KT-21 |
| `?error` thật từ Spring ở màn Đăng nhập (KT-14) | Bộ thật chỉ mở thẳng `/login?error` qua cổng và kiểm màn hiển thị | Bắt lỗi nếu BFF/Spring đổi cách trả `?error` | Cần dựng callback OAuth hỏng, quá nặng cho khung C0 | Khi có ca đăng nhập lỗi thật ở feature sau: tạo callback hỏng và kiểm đường sinh `?error` |
| Hết phiên BFF thật (KT-12) | Giả lập bằng cách xóa mọi khóa `spring:session:*` ở Redis | Kiểm đúng đường hết hạn phiên | Cách xóa chỉ đúng khi Redis dev không có phiên của người khác | Khi có Redis riêng cho test hoặc cơ chế hết hạn thật: xóa đúng khóa của phiên test |
| Thao tác thật gặp 401 (KT-16) | Gọi API bằng `HttpClient` thật lấy qua `ng.getComponent` (chỉ bản dev) | Kiểm bộ chặn 401 qua thao tác người dùng | Chưa trang nào gọi API | Khi có trang đầu tiên gọi API: đổi ca sang thao tác trên trang |
