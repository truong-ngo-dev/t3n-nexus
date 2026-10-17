-- Gỡ mô hình phiên cũ (feature 03): phiên đăng nhập nay nằm ở login_sessions và client_sessions (V13). Mọi mã đăng nhập, token và đăng xuất
-- đã chuyển sang mô hình mới, nên bảng oauth_sessions (và các chỉ mục, ràng buộc của nó) không còn ai đọc hay ghi.
DROP TABLE IF EXISTS oauth_sessions;
