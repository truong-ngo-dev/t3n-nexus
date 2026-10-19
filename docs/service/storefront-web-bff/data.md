# Cổng ứng dụng Cửa hàng, kênh web: dữ liệu

> **Trạng thái:** DRAFT · **Cập nhật:** `2026-10-17`
>
> BFF **không có cơ sở dữ liệu quan hệ**. Toàn bộ trạng thái nằm ở Redis. Hệ quả của [`service.md`](service.md). Tên khóa và cookie đã chốt ở bước 4b của feature 06.

## 1. Khóa Redis

| Khóa | Giá trị | Hạn | Ai ghi | Ai đọc | Mục đích |
|---|---|---|---|---|---|
| `spring:session:*` (Spring Session Redis, cookie `STOREFRONT_SESSION`) | Phiên BFF: ủy quyền client (token truy cập, token làm mới, hạn), ngữ cảnh bảo mật, trạng thái OAuth2 đang chờ (`state`, PKCE) | `app.session.ttl` (bằng TTL của refresh token; mặc định 7 ngày, `dev` 3 phút), **trượt** theo hoạt động; đặt lại mỗi lần lưu token mới | BFF | BFF | Phiên của trình duyệt |
| `storefront:sid:<sid>` | Mã phiên BFF | `app.session.ttl` + `app.session.mapping-grace` (mặc định 5 phút); **gia hạn cùng lúc** với hạn phiên | `SessionMappingAuthenticationSuccessHandler` (đăng nhập, xác thực im lặng), bộ làm mới | Bộ nhận báo `{sid}`, đăng xuất | Từ `sid` tìm phiên BFF |
| `storefront:session:<mã phiên BFF>` | `sid` | Như trên | Cùng | Đăng xuất, dọn | Từ phiên BFF tìm `sid` |
| `wsticket:<ticket>` | `userId` | 10 giây, dùng một lần | `GET /bff/auth/ws-ticket` | `websocket-gateway` (đọc rồi xóa) | Bắt tay WebSocket không có cookie |
| `ratelimit:user:<mã tài khoản>` | Tập có thứ tự (cửa sổ trượt) các lượt gọi | Bằng khoảng thời gian của luật (60 giây) | `UserRateLimitFilter` | Cùng | Giới hạn người dùng đã đăng nhập |

## 2. Quy tắc

- **Một `sid` ứng với tối đa một phiên BFF** (`storefront:sid:<sid>` ghi đè; phiên BFF cũ bị xóa nếu khác mã).
- **Hạn ánh xạ không được ngắn hơn hạn phiên**, được phép dài hơn một chút (xóa phiên đã mất vẫn 200). Hai khóa của ánh xạ luôn cùng hạn.
- **Mọi đường kết thúc phiên BFF xóa cả hai khóa ánh xạ cùng phiên** (đăng xuất, `invalid_grant`, báo từ Xác thực).
- Khóa `storefront:oauth:*` và `webgw:oauth:*` (tên trước khi đổi sang `sid`) không còn được ghi; nếu thấy thì xóa tay. Khóa hiện tại có hạn ngay từ khi ghi (`app.session.ttl` cộng `app.session.mapping-grace`).
- Không lưu mật khẩu, mã đăng nhập thô hay mã CSRF ở Redis (mã CSRF chỉ là cookie).

## 3. Dung lượng và rủi ro

- Một phiên BFF mang token (vài KB). Quy mô theo số người dùng đang đăng nhập và hạn phiên; chưa đo (feature 08).
- Redis mất thì mất phiên BFF của mọi người dùng: họ đăng nhập lại (SSO nếu phiên Xác thực còn). Ánh xạ mất đi thì báo `{sid}` không xóa được phiên BFF, nhưng token truy cập ngắn hạn và mã làm mới đã bị xóa ở Xác thực nên an toàn.
- Redis dùng chung với giới hạn tần suất của `api-gateway` (`ratelimit:ip:`, `ratelimit:rule:`); chỉ khác tiền tố khóa.
