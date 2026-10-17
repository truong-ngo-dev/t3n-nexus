# Feature 03: phần hoãn

Chỉ ghi phần **đã có chỗ trong phạm vi của feature** nhưng được làm ở mức đơn giản hoặc để phần sâu hơn lại sau. Không liệt
kê những thứ chưa làm nằm ngoài phạm vi: đăng xuất từ xa, danh sách thiết bị (C3c); kết thúc phiên do khóa tài khoản và đổi mật khẩu (C1b);
phía BFF của đăng xuất (feature 05, 06).

| Phần hoãn | Đang làm gì ở C0 | Giá trị của phần hoãn | Lý do xếp sau | Điểm nối |
|---|---|---|---|---|
| Báo ứng dụng bằng Back-Channel Logout chuẩn OIDC (`logout_token`) và thử lại khi lỗi | Một lời gọi nội bộ tự chế cho mỗi phiên ứng dụng, thân `{"sid": ...}`, cố gắng hết sức, không thử lại (`tech.md` T7) | Dùng được thư viện chuẩn ở BFF (`OidcSessionRegistry`); báo được nhiều ứng dụng; không sót phiên BFF khi lỗi tạm thời | Chưa có bên nhận chuẩn; mã làm mới đã bị xóa và mã truy cập hết hạn ngắn đã đủ làm lưới an toàn; thử lại cần hộp thư event (C1a) | Khi BFF bật Back-Channel Logout (feature 06 trở đi) và có hộp thư event |
| Token service account cho lời gọi báo BFF | Mint token `client_credentials` mới mỗi lần báo; mỗi lần để lại một hàng `oauth2_authorization` không bao giờ bị xóa (`tech.md` T10) | Không có hàng tích dần theo số lần đăng xuất | Tần suất đăng xuất thấp ở C0; cách sửa (cache token hoặc dọn theo hạn) ăn nhập với job dọn đã hoãn ở feature 02 | Job dọn bản ghi quá hạn (Lập lịch, C2b), hoặc cache token ở `WebGatewayRevocationClient` |
| Kết thúc phiên theo mỗi `id_token_hint` khi mất cookie đăng nhập | Không có cookie thì không kết thúc gì (chốt tạm, `tech.md` T5); phiên và mã làm mới còn tới khi hết hạn | Người dùng mất cookie rồi bấm đăng xuất thì mã làm mới và phiên không còn sót | Hint không chứng minh người gọi đang cầm phiên (ai giữ ID token cũ cũng kết thúc được đúng phiên đó); chưa có yêu cầu thực tế | Nếu cần, đổi quyết định ở `EndLoginSession` (một điều kiện) |
| `auth_time` trong ID token | Không phát (sổ phiên rỗng làm Spring không sinh; chưa nơi nào đọc) (`tech.md` T8) | Hỗ trợ `max_age` và xác thực lại | Chưa có luồng nào cần | Khi có nơi đọc: bộ tùy biến token đặt từ `login_sessions.issued_at` |
| Tên ứng dụng công khai trong phiên ứng dụng và event | `client_sessions.client_id` và `ClientSessionIssued` mang mã nội bộ của client đã đăng ký (`tech.md` T10) | Định danh hiển thị được "đăng nhập ở ứng dụng nào" | Chỉ Định danh (C3c) cần | Khi làm danh sách thiết bị: ghi hoặc tra `client_id` công khai |
