# ADR-0005: Bảo vệ yêu cầu từ trình duyệt và cookie mã thiết bị

> **Trạng thái:** Proposed · **Ngày:** `2026-10-13` (rà lại `2026-10-14`) · **Phạm vi:** `oauth2-service`, `storefront-web-bff` (BFF) và các ứng dụng web

## Bối cảnh

Trình duyệt giữ hai loại cookie ở hai nơi: cookie đăng nhập của máy chủ xác thực và cookie phiên của BFF. Hiện CSRF đang tắt ở cả hai
(`oauth2-service` hai chuỗi lọc, `storefront-web-bff` hai chuỗi lọc), client `storefront-web` seed `require-proof-key=false`. Rủi ro là một trang
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
- **CSRF ở BFF (đã làm ở feature 06, `2026-10-18`):** cookie `XSRF-TOKEN` **không `HttpOnly`** (JavaScript phải đọc, Angular có bộ chặn sẵn), `Path=/`, `SameSite=Lax`, không `Max-Age` (cookie phiên của trình duyệt, cùng loại với cookie phiên BFF nên **không cần gia hạn trượt**, cơ chế (A) cũ bỏ); cookie phiên BFF vẫn `HttpOnly`. Gửi hai lần (cookie cộng header `X-XSRF-TOKEN`) với **mã thô, không XOR** (JavaScript gửi lại nguyên giá trị cookie; XOR chống BREACH cần mã che mỗi lần, không hợp). Frontend gọi BFF bằng đường tương đối (bộ chặn của Angular bỏ qua URL khác miền) và gửi lại header cho mọi yêu cầu ghi, kể cả `POST /bff/auth/logout`. Một bộ lọc buộc phát cookie ở mọi yêu cầu (WebFlux chỉ phát khi mã được dùng), nên `GET` đầu tiên luôn kèm cookie. Mã **xoay khi đăng nhập thành công** (kể cả xác thực im lặng) và **xóa khi đăng xuất**. Thiếu hoặc sai mã: 403 `CSRF_TOKEN_INVALID` (phân biệt với 403 `FORBIDDEN` thiếu quyền), phiên không bị hủy. **Cơ chế để người đang dùng không bị 403 vì mất mã** là (C): phía client gặp `CSRF_TOKEN_INVALID` thì gọi một `GET` nhẹ (`/bff/auth/session`) để sinh lại mã rồi thử lại **đúng một lần**; mất mã CSRF không đồng nghĩa phải đăng nhập lại, phiên vẫn sống. Phiên đã mất mà cookie CSRF còn khớp thì yêu cầu ghi nhận 401, không phải 403. `/bff/internal/**` ở chuỗi riêng, không CSRF (xác thực bằng token dịch vụ). Kiểm: `CsrfTest` (cổng, Redis thật) và chạy thật qua ba dịch vụ và Chrome (`06-access-gateway/tech.md`, T4 và mục bước 11).
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
- Cookie CSRF của máy chủ xác thực và của BFF không có tiền tố `__Host-`: cân nhắc khi có nhiều miền con (ứng dụng cùng miền gốc), vì kiểu "so hai
  giá trị, không lưu" yếu nếu kẻ tấn công ghi đè được cookie từ miền con khác. Sau ADR-0006 mỗi ứng dụng một host với cookie theo máy (không `Domain`), nên host khác không ghi đè được cookie của host này; vẫn chưa cần `__Host-` ở C0, xem lại khi chạy trên miền thật có nhiều miền con (ghi ở `06-access-gateway/deferred.md`).
- Cookie `DEVICE_KEY` sống lâu hơn cookie phiên; xóa cookie hoặc ẩn danh là thiết bị mới.

## Tham khảo

- `docs/service/oauth2-service/analysis/login-session.md` mục 7 và mục mã thiết bị; ADR-0003.
