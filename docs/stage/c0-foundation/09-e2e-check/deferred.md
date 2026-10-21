# Feature 09: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi (xem `flow.md` mục 1).

| Phần hoãn | Đang làm gì ở C0 | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Hết hạn phiên thật qua cổng (S5, INV-ATH-08) | S5 qua cổng xóa phiên BFF rồi đăng xuất (KT-12); phiên Xác thực hết hạn hoặc đã kết thúc chỉ kiểm ở mức use case và API (03/KT-03, KT-22; 02/KT-12, KT-20, KT-23) | Chứng minh hết hạn thật xảy ra và bị từ chối qua cổng và cookie thật | Hạn phiên 30 ngày không đợi được; rút hạn bằng cấu hình dev làm chuỗi dựng khác môi trường thật | Thêm cấu hình rút hạn phiên cho `up.sh` (ví dụ phiên 10 giây ở profile dev) và một test chờ rồi gọi lại qua cổng |
| Quét bí mật trên toàn bộ dữ liệu và log sau cả bộ (INV-ATH-02) | KT-23 và KT-24 kiểm log của hai dịch vụ cho yêu cầu đăng ký lỗi; 01/KT-11 kiểm cột mật khẩu chỉ chứa chuỗi băm | Bảo đảm không dấu vết mật khẩu nào của mọi test (đăng nhập, đăng xuất, làm mới) trong log, cơ sở dữ liệu, Redis | Cần danh sách các giá trị bí mật của cả bộ để tìm; mới có `TEST_PASSWORD` dùng chung nên quét được, nhưng chưa làm | Cuối `run.sh`, quét `services/logs/*.json`, bảng `user_accounts`, khóa Redis tìm `Passw0rdE2e` |
