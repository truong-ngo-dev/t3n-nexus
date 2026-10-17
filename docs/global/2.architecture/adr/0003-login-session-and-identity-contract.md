# ADR-0003: Phiên đăng nhập hai tầng, hợp đồng danh tính và làm mới token

> **Trạng thái:** Proposed · **Ngày:** `2026-10-13` (rà lại `2026-10-14` sau khi dựng miền) · **Phạm vi:** Xác thực (`oauth2-service`) và mọi bên nhận mã đăng nhập
>
> Chuyển Accepted sau khi cài và đo ở feature 02. Hai chỗ còn mở ghi ở mục "Còn mở".

## Bối cảnh

Nhiều ứng dụng (Cửa hàng, Người bán, Vận hành, Shipper) dùng chung một máy chủ xác thực để đăng nhập một lần. Ứng dụng web đi qua
BFF: token nằm ở phía máy chủ, trình duyệt chỉ có cookie phiên (`analysis/login-session.md` mục 7). Máy chủ xác thực dùng Spring
Authorization Server (bản ghi ủy quyền `oauth2_authorization`) và Spring Session JDBC (bảng `SPRING_SESSION`; cookie đăng nhập đặt tên `OAUTH2_SESSION`, ADR-0005).

Cần bảo đảm: hạn phiên cố định và không kéo dài; đăng xuất, khóa tài khoản, đổi mật khẩu cắt được cả hai tầng phiên; không có nơi
nào để lộ giá trị cookie đăng nhập; làm mới token chịu được tải (ước tính 50 đến 200 lần mỗi giây, `_viec-con-lai.md` B3, chưa đo).

Các sự kiện về thư viện đã kiểm trên mã (Spring Session JDBC 4.0.3, Spring Authorization Server 7.0.5):
- Spring Session lưu và xóa trong giao dịch riêng (`REQUIRES_NEW`); `PRIMARY_ID` không lấy được qua API công khai, chỉ có `SESSION_ID`
  (là giá trị cookie, đổi khi chống chiếm phiên).
- `JdbcOAuth2AuthorizationService` dùng `JdbcOperations`, tham gia giao dịch của ta; tra theo token là so cột `TEXT` (quét bảng
  nếu không có chỉ mục); bộ sinh refresh token mặc định lấy TTL cố định từ client; bộ sinh mã ủy quyền chạy khi `authorization = null`.
- Làm mới cập nhật cùng bản ghi tại chỗ; token cũ bị từ chối như token rác, không có phát hiện dùng lại.

## Quyết định

**1. Bảng của ta là nguồn sự thật; Spring chỉ giữ cookie và ngữ cảnh bảo mật.**
- `login_sessions` (phiên người dùng, gốc): mã UUID v7 (ADR-0001), `idp_session_id` giữ `SESSION_ID` của Spring, một hạn phiên cố định.
  Mã phiên của ta ghi vào thuộc tính session Spring; khi `session.getId()` khác `idp_session_id` thì cập nhật.
- `client_sessions` (phiên ứng dụng): mã `id` UUID v7 chính là `sid`; `authorization_id` trỏ tới `oauth2_authorization` và **đổi được**.

**2. Hạn phiên cố định, kiểm ở điểm cấp token, không có bộ lọc chung.**
- Kiểm ở `/oauth2/authorize` (cấp mã: đọc phiên theo mã trong thuộc tính session; không còn hoặc hạn đã qua thì từ chối) và ở
  `/oauth2/token` khi làm mới (dùng hạn phiên mang theo bản ghi ủy quyền). Trang dùng cookie nhưng không cấp token không cần cửa.
- Cơ sở dữ liệu lỗi lúc đọc phiên thì từ chối. Session Spring mặc định chỉ sống 30 phút không hoạt động (Spring Session JDBC bỏ qua `server.servlet.session.timeout`): session vô danh giữ mặc định đó, còn khi mở phiên thành công thì hạn của session được nới đúng bằng hạn phiên của người đó (`LoginSessionAuthenticationSuccessHandler`).

**3. Cắt hạn token theo hạn phiên.**
- Access token và ID token: `OAuth2TokenCustomizer<JwtEncodingContext>`, `exp = min(bây giờ + TTL, hạn phiên)`.
- Refresh token: `OAuth2TokenGenerator<OAuth2RefreshToken>` riêng, vì bộ mặc định dùng TTL cố định. Hạn còn lại không dương
  thì `invalid_grant`. Mã ủy quyền giữ TTL 5 phút.
- TTL theo loại tài khoản (cấu hình, kiểm lúc nạp): refresh TTL ≥ 2 lần access TTL; hạn phiên > refresh TTL (bảng ở `login-session.md` 3.4).
- Thuộc tính trên `oauth2_authorization`, ghi lúc cấp mã (lớp `SessionBinding`): mã phiên người dùng, mã phiên ứng dụng (`sid`), hạn phiên (bất biến), vai; không mang mã thiết bị (đọc từ phiên khi cần)
  Hạn phiên mang theo bản ghi nên điểm kiểm ở `/oauth2/token` không cần đọc bảng phiên; điểm móc (mục 9) vẫn nạp phiên theo `sid` để gọi `refreshClientSession` (một truy vấn theo khóa).
- **Mã phiên ứng dụng (`sid`) chọn lúc cấp mã**, vì JWT được ký trước khi điểm móc chạy: phiên người dùng đã có phiên ứng dụng cho client đó thì dùng lại mã đó, chưa có thì sinh trước một mã mới (UUID v7); mã được ghi vào thuộc tính của bản ghi ủy quyền và bộ tùy biến JWT đặt `sid` từ đó.

**4. Hợp đồng danh tính: một claim phiên là `sid`.**
- Claim: mã tài khoản, vai, `sid` (mã phiên ứng dụng của ta), `exp`. Không thêm claim mã phiên người dùng: tra được từ `sid` bằng một
  lần đọc khóa chính ở thao tác hiếm (đăng xuất từ xa trừ phiên đang gọi); thêm claim tốn khoảng 60 byte mỗi yêu cầu.
- **`sid` của ta khác `sid` mặc định của Spring Authorization Server** (băm mã session HTTP, chỉ ở ID token). Ta cung cấp bean `SessionRegistry` không lưu gì để Spring không sinh `sid` và không kiểm `sid` lúc đăng xuất, vì Spring phải đoán session khi đổi mã (yêu cầu không có cookie) và chọn nhầm khi một người dùng có nhiều thiết bị (đã thử, feature 03 `tech.md` T2). Hint đăng xuất do ta ràng buộc với cookie bằng `idp_session_id`.
- `SESSION_ID` của Spring chỉ nằm ở cookie và cột `idp_session_id`; **không vào token, event, log**. `authorization_id` chỉ ở cơ sở dữ liệu.
  Rà log che `Cookie`, `Set-Cookie`.

**5. Ba hành vi của phiên đăng nhập theo tình huống** (`open`, `issueClientSession`, `refreshClientSession`).
- `open` (đăng nhập mật khẩu): chỉ tạo phiên người dùng (chưa có bản ghi ủy quyền). `issueClientSession` (đổi mã lấy token, tra theo `clientId`): chưa có phiên ứng dụng cho (phiên người dùng, client) thì tạo; đã có (xác thực im lặng) thì giữ mã `sid` và đổi `authorization_id` sang bản ghi mới,
  xóa bản ghi cũ trong cùng giao dịch. `refreshClientSession` (làm mới, tra theo `sid`): kiểm phiên và `sid`, không tạo, không đổi liên kết (Spring cập nhật cùng bản ghi tại chỗ). Phiên ứng dụng của ta không bị xóa khi xác thực im lặng; chỉ mất khi phiên người dùng kết thúc hoặc quá hạn.
- Dùng lại mã ủy quyền đã dùng: thư viện từ chối và vô hiệu token, ta không làm gì thêm. Không phát hiện dùng lại refresh token.

**6. Mở phiên người dùng một lần, ở điểm cuối chuỗi xác thực** (bộ xử lý thành công). Địa chỉ mạng, trình duyệt, cookie mã thiết bị lấy
thẳng từ yêu cầu của trình duyệt. Sau này xác thực nhiều lớp chèn bước trước điểm này, không sửa phiên.

**7. Xóa và dọn.**
- Một giao dịch xóa phiên của ta (phiên ứng dụng xóa theo) và `oauth2_authorization` qua service Spring AS; sau commit gọi
  `sessionRepository.deleteById(idp_session_id)` theo kiểu cố gắng hết sức, rồi báo BFF xóa mã theo `sid`. Cửa kiểm hạn (mục 2) là lưới an toàn.
  Vì `LoginSession.end` xóa các phiên ứng dụng khỏi aggregate, use case phải **lấy trước** danh sách `authorization_id` và `sid` của các phiên ứng dụng (để xóa bản ghi ủy quyền và báo BFF) rồi mới gọi `end`.
- Không đụng bảng Spring bằng SQL. Dọn phiên quá hạn và `oauth2_authorization` mồ côi (không phiên ứng dụng nào trỏ tới và cũ hơn
  TTL mã ủy quyền) theo lô, thành use case riêng chạy được bằng một lệnh. **Việc dọn chạy qua Lập lịch và hoãn tới C2b** (Lập lịch
  chưa có sớm hơn; việc dọn không cần callback). Trong lúc chờ, dòng quá hạn chỉ tốn dung lượng: mọi truy vấn phiên (kiểm hạn, "phiên
  đang sống của tài khoản") phải **lọc theo hạn**, không dựa vào việc dòng quá hạn đã bị dọn (INV-ATH-08).

**8. Làm mới token tra theo khóa chính, không chỉ mục trên giá trị token.**
- BFF gửi thêm `sid` ở `/oauth2/token`. Xác thực đọc phiên ứng dụng theo khóa chính `sid`, lấy `authorization_id`, đọc bản ghi
  ủy quyền theo khóa chính, rồi so refresh token bằng phép so thời gian hằng. `sid` sai thì không khớp và bị từ chối.
- Cần tùy biến điểm tra authorization theo token. Mọi kiểu tra cứu khác (access token, ID token, `state`, mã thiết bị, kiểu không
  xác định) trả không tìm thấy; chặn `/oauth2/revoke`, `/oauth2/introspect`. `fillfactor` thấp cho bảng để cập nhật tại chỗ rẻ.

**9. Điểm móc lúc token được cấp** (`save()` của lớp bọc `SessionBoundAuthorizationService`, use case `RecordClientSessionIssued`, cùng giao dịch với bản ghi ủy quyền) gọi `issueClientSession` hoặc `refreshClientSession` của aggregate (chọn theo việc phiên ứng dụng `sid` đã trỏ tới đúng bản ghi ủy quyền này chưa), và aggregate phát `ClientSessionIssued`. Với `issueClientSession`, điểm móc truyền `sid` đã chọn lúc cấp mã (mục 3) làm mã phiên ứng dụng mới, **so `sid` aggregate trả về với `sid` đã nằm trong token và quay lui giao dịch nếu khác** (đua nhau), rồi xóa bản ghi ủy quyền cũ nếu kết quả báo có đổi liên kết. **Không thử lại khi vi phạm ràng buộc duy nhất**: bên thua đã ký token mang `sid` riêng nên không thể hòa giải với phiên của bên thắng, chỉ có thể thất bại và đăng nhập lại. Chỉ có một nơi phát: khi bản ghi có access token chưa bị vô hiệu
và có mã phiên của ta (loại dịch vụ gọi dịch vụ, loại lần lưu dọn dẹp của thư viện). Mã event lấy từ `jti`. Định danh ghi
`last_active_at` bằng một câu lệnh có điều kiện, ngưỡng 1 phút (cấu hình).

## Còn mở

- **Đổi mã ủy quyền chưa có `sid`** (bộ sinh mã chạy khi `authorization = null`): cần cách riêng (chỉ mục một phần trên `authorization_code_value`,
  hoặc dựa vào mã sống 5 phút nên bảng nhỏ). Chốt khi cài và đo.
- **Cách chèn `sid` ở điểm tra** (bọc `OAuth2AuthorizationService` đọc `sid` từ request, hay converter và provider tùy biến cho grant
  `refresh_token`). Chốt khi cài.
- Đo (bộ đo nền, feature 08): nạp khoảng 187 nghìn bản ghi, tải làm mới 100 đến 250 lần/giây, `EXPLAIN (ANALYZE, BUFFERS)`, p99.

## Phương án đã loại

| Phương án | Lý do loại |
|---|---|
| Dùng `PRIMARY_ID` của Spring Session làm khóa liên kết | Không lấy được qua API công khai |
| Claim là mã phiên người dùng | BFF không phân biệt phiên ứng dụng nào của ứng dụng nào cần xóa; phải kèm khóa client để ghép |
| Hai claim (mã phiên người dùng và mã phiên ứng dụng) | Tiết kiệm một lần đọc khóa chính ở thao tác hiếm, mà thêm byte mỗi yêu cầu |
| Mã `authorization_id` hoặc `SESSION_ID` làm claim | `SESSION_ID` là giá trị cookie (mang là dùng); `authorization_id` là mã nội bộ của Spring, đổi khi xác thực im lặng |
| Chỉ mục trên `refresh_token_value` | Cột `TEXT` dài, chỉ mục lớn; không hiệu quả dài hạn |
| Nhúng `authorization_id` vào đầu refresh token | Cần bộ sinh riêng và tách id khi tra; phụ thuộc mã nội bộ của Spring; gửi `sid` dùng mã của ta |
| Bộ lọc chung kiểm hạn mọi yêu cầu | Phiên chỉ cần kiểm ở điểm cấp token; token có hạn đã cắt |
| Phát hiện dùng lại refresh token | Thư viện không có sẵn; BFF giữ token nên lợi ích thấp so với chi phí tự xây và báo động giả |
| Giữ `lastActiveAt` ở phiên | Thêm một lần ghi mỗi lần làm mới; nguồn hoạt động là `ClientSessionIssued` ở Định danh |

## Lý do

- Mã của ta ở mọi thứ đi ra ngoài; mã nội bộ của Spring không bao giờ rời cơ sở dữ liệu (trừ cookie, vốn là giá trị bí mật).
- Một hạn phiên cố định làm quy tắc "phiên bị chiếm không dùng mãi" kiểm được ở các điểm cấp token (cấp mã, làm mới, và khi aggregate cấp phiên ứng dụng).
- Tra theo khóa chính tránh chỉ mục lớn trên cột `TEXT` mà vẫn dùng bản ghi ủy quyền chuẩn của thư viện.

## Hệ quả

- Phải viết: bộ sinh refresh token, lớp bọc `OAuth2AuthorizationService` (điểm móc), tùy biến điểm tra, bộ tùy biến JWT, kho phiên, use case dọn (chạy qua Lập lịch, C2b). Miền (`LoginSession`, `SessionLifetimePolicy`) và các use case `AuthenticateWithPassword`, `OpenLoginSession`, `ResolveAuthorizationSession` đã có và có test (feature 02, bước 3 đến 6).
- Mất `lastActiveAt` ở phiên: hiển thị hoạt động phải đi qua Định danh (từ `ClientSessionIssued`).
- Mỗi lần làm mới nạp phiên theo `sid` (một truy vấn theo khóa, cộng một đọc và một ghi `oauth2_authorization`); số liệu ở `tech.md` T2, đo ở bước 14.
- Rủi ro chưa biết lớn nhất: hành vi của `oauth2_authorization` dưới tải làm mới; đo ở feature 02 (kế hoạch bước 14).
- Đã cài ở bước 9a (`2026-10-14`) và kiểm bằng luồng đầy đủ qua HTTP thật. Hai điểm mở ban đầu đã chốt: `sid` chèn bằng việc lớp bọc đọc tham số của yêu cầu (refresh) hoặc claim của JWT (userinfo); đổi mã ủy quyền dùng chỉ mục một phần V14 (mã 16 ký tự, sống 5 phút). Ba điều khi nối Spring ghi ở `docs/stage/c0-foundation/02-login-session/tech.md` T6 (chép `details`, `FactorGrantedAuthority`, giao dịch của kho).

## Tham khảo

- `docs/service/oauth2-service/analysis/login-session.md`, `0.overview.md` mục 5; `service.md` mục 7.
- ADR-0001 (mã UUID v7), ADR-0002 (băm mật khẩu).
