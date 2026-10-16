# ADR-0005: Bảo vệ yêu cầu từ trình duyệt và cookie mã thiết bị

> **Trạng thái:** Proposed · **Ngày:** `2026-10-13` (rà lại `2026-10-14`) · **Phạm vi:** `oauth2-service`, `web-gateway` (BFF) và các ứng dụng web

## Bối cảnh

Trình duyệt giữ hai loại cookie ở hai nơi: cookie đăng nhập của máy chủ xác thực và cookie phiên của BFF. Hiện CSRF đang tắt ở cả hai
(`oauth2-service` hai chuỗi lọc, `web-gateway` hai chuỗi lọc), client `web-gateway` seed `require-proof-key=false`. Rủi ro là một trang
độc lợi dụng cookie của người dùng đã đăng nhập; các biện pháp này không chống được cookie bị lộ hay trình duyệt bị chiếm
(ngoài phạm vi, `login-session.md` mục 7).

Mã thiết bị (cookie) được cấp trước khi nhập mật khẩu để cả đăng nhập thất bại cũng mang được mã (`login-session.md`, mục mã thiết bị).

## Quyết định

- **CSRF ở máy chủ xác thực (đã làm, bước 9b.2):** bật cho chuỗi dùng cookie có form do máy chủ dựng (đăng nhập, mã một lần); miễn
  `/register` (API JSON do BFF gọi, không có cookie) và `/oauth2/token` (xác thực bằng client, không bằng cookie). Mã nằm trong cookie
  `XSRF-TOKEN` (`CookieCsrfTokenRepository`, `HttpOnly` vì không có JavaScript nào cần đọc, `SameSite=Lax`, hạn 30 ngày, máy chủ không
  lưu gì) và trong ô ẩn `_csrf` của form; so khớp hai giá trị. Không xoay mã khi đăng nhập để biểu mẫu mở lâu hoặc nhấn Back không bị 403.
  Đăng xuất OIDC không cần mã CSRF riêng vì `id_token_hint` là bắt buộc (do Spring Authorization Server) và kẻ tấn công không có
  ID token của nạn nhân.
- **CSRF ở BFF (chưa làm):** cookie `XSRF-TOKEN` **không `HttpOnly`** (JavaScript phải đọc, Angular có bộ chặn sẵn); cookie phiên BFF vẫn
  `HttpOnly`. Frontend gọi BFF bằng đường tương đối (bộ chặn của Angular bỏ qua URL khác miền) và gửi lại header `X-XSRF-TOKEN` cho mọi
  yêu cầu ghi; cần một `GET` đầu để sinh cookie. `POST /logout` của BFF nhận CSRF theo cấu hình chung của chuỗi. **Cơ chế để người đang
  dùng không bị 403 vì mất mã** (bàn kỹ thuật khi làm BFF): (A) gia hạn trượt cookie CSRF theo hoạt động, hạn không ngắn hơn cookie phiên;
  (C) phía client tự phục hồi: gặp 403 do CSRF (máy chủ trả mã lỗi ổn định, ví dụ `CSRF_TOKEN_INVALID`, phân biệt với 403 thiếu quyền)
  thì gọi một `GET` nhẹ để sinh lại mã rồi thử lại **đúng một lần**. Mất mã CSRF không đồng nghĩa phải đăng nhập lại: phiên vẫn sống.
- **Mobile:** API gọi bằng header (không cookie tự gắn) nên không cần CSRF, miễn cổng di động không đặt cookie phiên cho ứng dụng. Trang
  đăng nhập dùng chung (mở bằng trình duyệt hệ thống) vẫn có mã `_csrf`. Client di động là client công khai nên bắt buộc PKCE.
- **Cookie phiên (`OAUTH2_SESSION`):** đặt tường minh `HttpOnly`, `Secure`, `SameSite=Lax`.
- **OAuth2:** bật PKCE (`ClientSettings.requireProofKey(true)` cho client BFF). **`state`:** Spring Authorization Server 7.0.5 không có cài đặt bắt buộc `state` (bộ kiểm tra mặc định chỉ có redirect URI và scope), nên máy chủ xác thực từ chối yêu cầu cấp mã thiếu `state` bằng bộ kiểm tra tùy biến (`setAuthenticationValidator` trên provider); `state` do BFF sinh và kiểm khi nhận mã ở callback (Spring Security OAuth2 Client làm sẵn), nên `state` sai do BFF từ chối, không phải máy chủ xác thực.
- **Cookie mã thiết bị:** tên `DEVICE_KEY`; `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`; hạn 12 tháng, gia hạn mỗi lần đăng nhập; giá trị
  ngẫu nhiên 256 bit; cơ sở dữ liệu chỉ lưu bản băm; cookie mất thì lần đăng nhập sau là thiết bị mới. Ứng dụng di động tự sinh mã khi cài.
- Làm ngay trong đợt này vì chi phí thấp; test: thiếu hoặc sai mã CSRF thì 403, `/oauth2/token` vẫn gọi được không cần mã.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Để CSRF tắt, dựa vào `SameSite` | `SameSite=Lax` không bao phủ mọi tình huống (ví dụ yêu cầu GET đổi trạng thái, trình duyệt cũ); bật CSRF rẻ |
| Làm CSRF sau | Bật muộn phải sửa lại từng màn hình và lời gọi đã viết |
| Vân tay trình duyệt làm mã thiết bị | Do client tính, không đáng tin (`device.md` mục 9) |

## Lý do

- Vệ sinh cơ bản trước trang độc, chi phí thấp, hầu hết là cấu hình.
- Bản băm của mã thiết bị bảo vệ trước việc lộ cơ sở dữ liệu; cookie không có quyền gì tự thân (ai lấy được vẫn cần mật khẩu).

## Hệ quả

- Frontend phải gửi `X-XSRF-TOKEN` cho mọi yêu cầu thay đổi dữ liệu qua BFF (một interceptor ở lớp gọi API) và xử lý 403 do CSRF bằng
  một lần thử lại sau khi sinh lại mã.
- Cookie CSRF của máy chủ xác thực không có tiền tố `__Host-`: cân nhắc khi có nhiều miền con (ứng dụng cùng miền gốc), vì kiểu "so hai
  giá trị, không lưu" yếu nếu kẻ tấn công ghi đè được cookie từ miền con khác. Không cần ở C0 (một nguồn gốc).
- Cookie `DEVICE_KEY` sống lâu hơn cookie phiên; xóa cookie hoặc ẩn danh là thiết bị mới.

## Tham khảo

- `docs/service/oauth2-service/analysis/login-session.md` mục 7 và mục mã thiết bị; ADR-0003.
