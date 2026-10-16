# Feature 02: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho cả BC hoặc mọi BC nằm ở ADR (ADR-0003 phiên và token, ADR-0004 mã
> một lần, ADR-0005 bảo vệ yêu cầu từ trình duyệt); file này ghi tham số và chi tiết riêng của feature.

## T1. TTL token và hạn phiên (chốt `2026-10-13`, bước 1 của kế hoạch)

- **Quyết định:** chính sách theo **vai của tài khoản**, không theo ứng dụng (nguồn: `analysis/login-session.md` mục 3.4). Đọc từ cấu hình, nạp
  lúc khởi động.

| Tham số | Vai Người dùng | Vai nội bộ | Môi trường dev (mọi vai) |
|---|---|---|---|
| TTL access token | 15 phút | 15 phút | 1 phút |
| TTL refresh token | 7 ngày | 30 phút | 3 phút |
| Hạn phiên | 30 ngày | 10 giờ | 10 phút |
| TTL mã ủy quyền | 5 phút | 5 phút | 5 phút |

- **Ràng buộc kiểm khi khởi động:** TTL refresh token ≥ 2 lần TTL access token; hạn phiên > TTL refresh token. Vi phạm thì từ chối khởi động. Cả ba cột thỏa.
- **Cắt hạn:** hạn mỗi token = nhỏ hơn của (bây giờ + TTL của loại token, hạn phiên) (ADR-0003 mục 3).
- **Lý do:** số theo thông lệ (mặc định Keycloak), chưa dựa dữ liệu dự án. TTL access 15 phút giữ vì đây là điểm cần đo: tải làm mới ước tính khoảng
  22 lần/giây thường ngày và 220 lần/giây lúc flash sale (phiên đồng thời ÷ 900 giây, NFR 3.3; T2), chỉnh theo số đo ở bước 14.
- **Loại:** đặt TTL 30 phút cho vai Người dùng (giảm một nửa tải làm mới, nhưng tăng cửa sổ lộ token); chính sách theo ứng dụng (phiên người dùng
  dùng chung nhiều ứng dụng nên không có hạn phiên rõ ràng).

## T2. Tải và dữ liệu của đăng nhập (chốt `2026-10-13`, bước 1)

- **Quyết định:** lấy số của `6-non-function-requirement.md` mục 3.3, 3.4 làm nguồn chung; điều chỉnh hai dòng cho khớp quyết định đã chốt:

| Đại lượng | Số thiết kế | Nhãn | Cách có |
|---|---|---|---|
| Đăng nhập/giây | ~5 thường ngày đỉnh; ~700 flash sale | Suy ra; Giả định | NFR 3.3 |
| Làm mới token/giây | ~22 thường ngày; ~220 flash sale | Suy ra | Phiên đồng thời ÷ TTL access 15 phút (T1) |
| Lịch sử đăng nhập | ~60 triệu dòng (~18 GB), giữ 12 tháng | Giả định | 165.000 dòng/ngày × 365 × ~300 byte/dòng |
| Phiên người dùng đang sống | ≤ ~1,5 triệu | Giả định | Hạn phiên Người dùng 30 ngày, không hạn không hoạt động, nên phiên sống tới 30 ngày; cận trên bằng số thiết bị (NFR 3.4) |
| Phiên ứng dụng đang sống | ≤ ~2 triệu | Giả định | Phiên người dùng × ~1,3 ứng dụng mỗi phiên |

- **Hệ quả:** bảng phiên và `oauth2_authorization` có cỡ hàng triệu dòng, nên chỉ mục đã chốt (ADR-0003, `_viec-con-lai.md` B3) phải đủ ở quy mô này; đo ở bước 14. Lịch sử đăng nhập cân nhắc phân vùng theo tháng khi số đo đòi.
- **Lý do điều chỉnh:** NFR cũ ghi giữ 90 ngày (15 triệu dòng) trong khi ta chốt 12 tháng; và ghi phiên ≤ 400.000 theo 24 giờ trong khi phiên sống tới 30 ngày.
- **Loại:** ước tính 430 nghìn dòng lịch sử mỗi ngày (dựng trên 5 đăng nhập/giây trung bình): cao hơn nhiều so với số suy từ NFR.

## T3. Giới hạn số lần thử đăng nhập (chốt `2026-10-13`, bước 1)

- **Quyết định:** luật nhóm 2 (`rate-limiting-layers.md`), đếm **mọi lần thử** (cả lần đúng), cửa sổ trượt qua Redis, áp như nhau cho mọi email kể cả không tồn tại.

| Khóa đếm | Mức | Cửa sổ | Mục đích |
|---|---|---|---|
| (email, địa chỉ mạng) | 5 lần | 5 phút | Gõ nhầm vài lần vẫn đăng nhập lại được; một địa chỉ không dò được mật khẩu của một email |
| Riêng email | 15 lần | 15 phút | Chặn dò phân tán từ nhiều địa chỉ |

- **Vượt mức:** HTTP 429 kèm `Retry-After`, không kiểm mật khẩu, dựng lại trang đăng nhập với thông báo "thử lại sau khoảng N phút". **Redis lỗi thì mở cửa** và ghi cảnh báo.
- **Điều quyết khi cài (`2026-10-16`, bước 10):**
  - `Retry-After` là **độ dài cửa sổ** của khóa bị vượt (300 hoặc 900 giây): bộ đếm của thư viện chỉ trả đúng hoặc sai, không trả thời gian còn lại, nên đây là cận trên.
  - Kiểm theo cặp trước; **chỉ khi cặp chưa bị chặn mới đếm khóa riêng email**, để một địa chỉ bị chặn không làm cạn mức của email với người dùng thật ở địa chỉ khác (lần bị từ chối cũng không được ghi thêm vào cửa sổ).
  - Email chuẩn hóa như lúc xác thực; giá trị sai định dạng hoặc thiếu vẫn bị đếm (mỗi lần thử vẫn tốn một lần băm). Khóa trong Redis dùng bản băm SHA-256, không chứa email hay địa chỉ mạng.
  - Địa chỉ mạng lấy qua `IpAddressExtractor` (tiêu đề chuyển tiếp). Chừng nào cổng truy cập chưa ghi đè tiêu đề này (feature 06), client giả được tiêu đề để né mức theo cặp; mức riêng email vẫn chặn.
  - Cấu hình `app.login.rate-limit.{pair-attempts, pair-window, email-attempts, email-window}`; mức theo email thấp hơn mức theo cặp thì không khởi động.
- **Cấu hình:** mức và cửa sổ đọc từ thuộc tính, nạp lúc khởi động, kiểm hợp lệ (mức > 0, cửa sổ > 0); dev có thể hạ thấp để test. Độc lập với giới hạn theo địa chỉ mạng ở cổng (feature 06).
- **Hệ quả đã chấp nhận:** mức theo email cho phép khoảng 1.440 lần thử mỗi ngày mỗi email; hàng rào chính cùng bcrypt (ADR-0002) và luật mật khẩu, vì chưa có khóa tạm tài khoản (B16). Trong lúc bị bắn phân tán, chủ email có thể bị chặn tối đa 15 phút (không khóa tài khoản).
- **Lý do:** con số theo thông lệ, chưa đo; chỉnh sau bước 14.

## T4. Khóa ký token và xoay khóa (chốt `2026-10-13`, bước 1)

- **Hiện trạng đã đọc trên mã:** khóa lưu ở bảng `rsa_key_pairs` (khóa riêng mã hóa bằng bí mật cấu hình); mọi khóa trong bảng được công bố qua JWKS và khóa mới nhất dùng để ký. Còn thiếu: `id` khóa lấy từ cấu hình `jwt.key.id` (hằng) nên khóa thứ hai bị `ON CONFLICT (id) DO NOTHING` bỏ qua im lặng; cột `created` kiểu `DATE` nên thứ tự khóa cùng ngày không xác định; không có chỗ nào gọi xoay (sự kiện sinh khóa chỉ phát khi bảng rỗng); bí mật mã hóa có giá trị mặc định trong `application.properties`; mỗi lần ký JWT truy vấn bảng hai lần.
- **Quyết định:**
  1. Giữ **RS256, RSA 2048**.
  2. **Mã khóa (`kid`) sinh theo từng cặp** (UUID v7, ADR-0001); bỏ cấu hình `jwt.key.id`; đổi `created` thành `TIMESTAMPTZ` (migration).
  3. **Ký bằng khóa mới nhất đã được công bố đủ lâu** (mục 8); JWKS công bố mọi khóa còn trong bảng.
  4. **Giữ khóa cũ trong JWKS tối thiểu 24 giờ sau khi ngừng ký** (lớn hơn TTL của JWT dài nhất là ID token ≤ 30 phút, cộng thời gian lưu đệm JWKS ở bên nhận). Bảng không có trạng thái "đã ngừng ký": khóa cũ nằm lại tới khi dọn (dọn khóa cũ thuộc nhóm job hoãn tới C2b).
  5. **Chu kỳ xoay 90 ngày.** Ở C0 chỉ có thao tác xoay chạy tay (một use case, một lệnh); tự động hóa hoãn tới C2b cùng Lập lịch.
  6. **Khóa riêng mã hóa bằng bí mật cấu hình, không có giá trị mặc định**; thiếu thì từ chối khởi động.
  7. **Lộ khóa riêng:** xoay ngay bằng tay và gỡ khóa lộ khỏi bảng (mọi token ký bằng nó bị từ chối); bản chụp (mục 8) giữ khóa tối đa 5 phút nên cần khởi động lại để dứt điểm.
  8. **Giữ khóa trong bộ nhớ**, không truy vấn mỗi lần ký (trước đây khoảng 4 truy vấn mỗi lần cấp token, ước tính ~3.700 truy vấn/giây lúc flash sale, chưa đo). `JwtTokenCustomizer` và nguồn JWKS cùng đọc một bản chụp đã giải mã.
- **Tham số của bản chụp:**

| Tham số | Giá trị | Lý do |
|---|---|---|
| Làm mới bản chụp (TTL) | 5 phút | Khóa đổi rất hiếm; mỗi bản đọc bảng một lần mỗi 5 phút |
| Độ trễ trước khi khóa mới bắt đầu ký | 15 phút | Lớn hơn TTL bản chụp (5) cộng thời gian lưu đệm JWKS của bên nhận (Spring mặc định khoảng 5 phút, **chưa kiểm ở dự án**), cộng dư |
| Làm mới khi gặp `kid` lạ | Tối đa một lần mỗi 30 giây | Bắt khóa mới ngay khi cần, không để `kid` bậy gây bão CSDL |

- **Ràng buộc kiểm khi khởi động:** độ trễ kích hoạt > TTL bản chụp + thời gian lưu đệm phía nhận (hằng số cấu hình).
- **Sinh khóa lúc khởi động khi bảng rỗng:** chặn hai bản service cùng sinh bằng khóa tư vấn (advisory lock) hoặc kiểm lại trước khi lưu.
- **Loại:** xóa hàng `rsa_key_pairs` rồi khởi động lại để xoay (làm token ký bằng khóa cũ mất hiệu lực ngay, không có giai đoạn chồng khóa); truy vấn bảng mỗi lần ký; ES256 (không có lý do đổi ở C0).
- **Lý do:** token tự mô tả, các service tự kiểm chữ ký bằng JWKS (ADR-0003); xoay không gián đoạn nhờ chồng khóa và `kid` riêng mỗi cặp.

## T5. Hệ số băm, hạn ID token, hạn cookie đăng nhập (chốt `2026-10-13`, bước 1)

- **Hệ số băm:** giữ **10** (ADR-0002); chỉnh theo trần băm đo ở feature 08. Băm lại sau đăng nhập đúng khi hệ số lưu cũ hơn (KT-07).
- **TTL của ID token:** **bằng TTL access token** (15 phút; dev 1 phút), cắt theo hạn phiên như access token. ID token chỉ BFF dùng lúc đăng nhập để lấy claim, nên không cần sống lâu hơn; mặc định cố định 30 phút của thư viện không dùng.
- **Cookie đăng nhập của Spring (`OAUTH2_SESSION`):** cookie **bền**, `Max-Age` = **30 ngày** (hạn phiên lớn nhất), session Spring mặc định chỉ 30 phút không hoạt động nên được nới đúng bằng hạn phiên lúc đăng nhập thành công (session vô danh giữ 30 phút). Hạn thật của từng phiên do cửa kiểm hạn của ta quyết (vai nội bộ 10 giờ vẫn bị từ chối sau 10 giờ dù cookie còn). Cờ `HttpOnly`, `Secure`, `SameSite=Lax` (ADR-0005).
- **Lý do cookie bền:** đăng nhập chung phải sống qua việc đóng và mở lại trình duyệt, nếu không người dùng phải nhập lại mật khẩu mỗi lần mở. Đánh đổi chấp nhận: máy dùng chung có cookie sống tới 30 ngày; giảm thiểu bằng nhập lại mật khẩu cho hành động nhạy cảm và đăng xuất từ xa (`analysis/login-session.md` mục 7).
- **Loại:** cookie phiên (hết khi đóng trình duyệt): an toàn hơn trên máy dùng chung nhưng làm hỏng trải nghiệm đăng nhập chung.

**Cấu hình T1 trong mã (bước 9a.3):** các số ở bảng trên nằm ở `application.properties` (`app.session.user.*`, `app.session.internal.*`) và `application-dev.properties` (profile dev: 1 phút, 3 phút, 10 phút), nạp qua `SessionLifetimeProperties`; `SessionLifetimePolicy` từ chối cấu hình vi phạm hai ràng buộc nên ứng dụng không khởi động. Claim trong token: `sub` (mã tài khoản), `role`, `sid`, `exp`.

## T6. Điều rút ra khi nối Spring ở bước 9a (chốt `2026-10-14`)

Ba điều mà test đơn vị không thấy, chỉ lộ ra khi chạy toàn bộ ngữ cảnh qua HTTP thật (`LoginFlowIntegrationTest`):

1. **Spring chép `details` của yêu cầu vào token đã xác thực nếu kết quả của bộ xác thực không có `details`** (`ProviderManager.copyDetails`), rồi token được lưu vào session và JSON của bản ghi ủy quyền. Bản đầu của `LoginRequestDetails` mang mã thiết bị thô, nên mã thô đã suýt được lưu (kèm lỗi 500 khi đọc lại vì Jackson không biết kiểu). **Quyết định (chốt `2026-10-14`, sửa lại sau khi rà):** giữ nguyên hành vi mặc định của Spring, không giả `details`. `LoginRequestDetails` kế thừa `WebAuthenticationDetails` và chỉ mang giá trị an toàn để lưu: địa chỉ mạng, trình duyệt và **bản băm** mã thiết bị (băm ngay lúc dựng, ở `authenticationDetailsSource`), cộng `remoteAddress` và `sessionId` của lớp cha (chấp nhận: đã có ở `SPRING_SESSION`). Kiểu này đăng ký với Jackson (mixin và danh sách kiểu được phép) và `Serializable` để lưu được. `AuthenticateWithPassword.Command` nhận bản băm, nên mã thô không vào use case. Test: mã thô không có ở `SPRING_SESSION_ATTRIBUTES`, `oauth2_authorization.attributes`, token, `login_sessions` (`LoginFlowIntegrationTest`); provider qua `ProviderManager` có `details` là bản băm (`PasswordAuthenticationProviderTest`).

2. **Spring Security 7 lấy `auth_time` của ID token từ `FactorGrantedAuthority`** (yếu tố mật khẩu kèm thời điểm), không từ `details`. `DaoAuthenticationProvider` tự thêm; bộ xác thực của ta phải tự thêm (`FACTOR_PASSWORD`, `issuedAt` = đồng hồ của ứng dụng). Thiếu thì ID token không cấp được.
3. **Lệnh ghi (upsert gốc) cần giao dịch.** Kho phiên tự bảo đảm giao dịch cho `save` và `delete` (`@Transactional`, tham gia giao dịch của điểm móc nếu có), thay vì trông vào việc từng use case tự mở giao dịch.

Hai điều có sẵn từ trước (không do feature này) được ghi lại để sửa ở bước 9b (đã sửa, xem T7): client `web-gateway` được seed bí mật `{noop}secret` trong khi bean `PasswordEncoder` là `BCryptPasswordEncoder` (không hiểu `{noop}`), nên không client nào xác thực được ở `/oauth2/token` (test tự đặt bản băm BCrypt); `server.servlet.session.timeout` vẫn là 24 giờ trong khi chốt là 30 ngày (T5).

## T7. Điều rút ra khi làm bước 9b (chốt `2026-10-14`)

1. **PKCE của `web-gateway` tạm tắt cho tới feature 06.** Spring chỉ tự thêm PKCE cho client công khai; `web-gateway` là client bí mật kiểu reactive và chưa gửi `code_challenge`, nên bật ở máy chủ sẽ làm hỏng đăng nhập qua cổng web. Bật bằng `WEB_GATEWAY_REQUIRE_PKCE=true` (`app.clients.web-gateway.require-proof-key`, mặc định `false`); test bật sẵn. Client `postman-dev` luôn bắt buộc PKCE. Lệch ADR-0005 có chủ đích, bật lại khi làm feature 06.
2. **Mã CSRF của biểu mẫu đăng nhập nằm trong cookie riêng `XSRF-TOKEN` (`CookieCsrfTokenRepository`, `HttpOnly`, `SameSite=Lax`, 30 ngày) và không xoay khi đăng nhập**, để session hết hạn hoặc nhấn Back không làm biểu mẫu đang mở mất hiệu lực: người dùng không thấy lỗi. Kho mặc định theo session thì mã chết cùng session. Thiếu hoặc sai mã (kể cả khi đã xóa cả cookie) ra 403, kèm `dispatcherTypeMatchers(ERROR).permitAll()` ở chuỗi mặc định: theo tài liệu Spring, CSRF sai gọi `AccessDeniedHandlerImpl` (`sendError(403)`), container chuyển sang `/error`; không cho phép thì người chưa đăng nhập gửi `Accept: text/html` bị entry point đẩy về `/login` và 403 thành 302. Test kiểm: cookie riêng, form còn hợp lệ khi session mất, form còn hợp lệ sau đăng nhập, thiếu/giả mã thì 403.
3. **`state` bị kiểm trước cả khi chuyển sang đăng nhập**: yêu cầu cấp mã thiếu `state` hoặc thiếu `code_challenge` được trả về `redirect_uri` đã đăng ký kèm `invalid_request` ngay ở lần gọi đầu.
4. **`RegisteredClientSeeder` chạy lúc khởi động** thay vì sửa bằng migration, để bí mật nằm ngoài mã nguồn; ngoài profile `dev` mà thiếu `WEB_GATEWAY_CLIENT_SECRET` thì dịch vụ không khởi động. Bí mật của client nội bộ (`INTERNAL_CLIENT_SECRET`) được băm cùng cách. Client cho ứng dụng Cửa hàng và Vận hành chưa đăng ký vì cách nối BFF của hai ứng dụng này chưa chốt (xem `deferred.md`).
5. **Ba test cũ dùng cơ sở dữ liệu cấu hình sẵn** (`UserAccountApiTest`, `RequestLoggingIntegrationTest`, `UserAccountConcurrencyTest`) không có cấu hình Testcontainers; chưa chạy ở bước này để không đụng hạ tầng cục bộ. Cần chuyển sang `PostgresContainerHolder` trước khi tin chúng.

## T8. Độ lệch đồng hồ khi kiểm token (chốt `2026-10-16`)

Bên nhận token chấp nhận lệch đồng hồ **5 giây** khi kiểm `exp` và `nbf` (cấu hình `app.security.jwt.clock-skew`, mặc định 5s). Mặc định của Spring là 60 giây làm token còn dùng được tới 60 giây sau hạn phiên, trái với quy tắc "token không sống lâu hơn phiên". Các máy đồng bộ NTP lệch dưới 1 giây nên 5 giây đủ dư. Quy ước cho mọi dịch vụ nhận token; nơi nào tự dựng bộ giải mã phải đặt `JwtTimestampValidator` với giá trị này. Đã áp dụng cho chuỗi `/api/**` của `oauth2-service` (`KeyConfiguration`).
