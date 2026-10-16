# Việc còn lại sau khi tách phiên, thiết bị, lịch sử đăng nhập (file tạm)

> Gộp từ các file tạm đã xóa (`_chot`, `_tong-hop`, `_mo-hinh`, `_so-sanh`, hai file "chưa chuyển"). Phần nghiệp vụ đã nằm ở `oauth2-service/analysis/` (`0.overview.md`, `user-account.md`, `login-session.md`) và `identity-service/analysis/` (`device.md`, `login-history.md`). File này chỉ giữ ba thứ chưa có chỗ ở: (A) tài liệu cần đồng bộ, (B) việc kỹ thuật, (C) mã cần đổi. Xong thì xóa file.
> Ngày: 2026-10-12.

## A. Tài liệu cần đồng bộ (theo thứ tự: root rồi analysis rồi service.md rồi data/api/mã)

### A1. Tài liệu root (`docs/global`)

**Đã xong (2026-10-12):** glossary (phiên, thiết bị, thiết bị đã xác minh, lịch sử đăng nhập; đã đổi "Portal" thành "Ứng dụng"); `4-context-map.md` (B16, B28, bảng liên kết, mục 9.5); `naming.md`; `doc-structure.md` (quy ước thư mục `analysis/`); `stage.md`, `flow.md`, `plan.md` của feature 02 (thuật ngữ phiên). **Để khi triển khai kỹ thuật (đã thống nhất):** `2.architecture/5. event-catalog.md` (mô tả event của mã hiện tại) và tên event cấp token (mục D) sửa cùng lúc khi mã đổi. **Còn lại:** nội dung ADR-0003 và mô hình hạn trong `plan.md` (bảng TTL), kiểm lại khi viết ADR.

| File | Chỗ | Việc |
|---|---|---|
| `1.requirement/3-glossary.md` | Dòng "Phiên" (39) | Đổi thành phiên đăng nhập, phiên người dùng, phiên ứng dụng (định nghĩa ở `login-session.md`) |
| | Dòng "Thiết bị" (40) | Nhận ra bằng mã thiết bị do Xác thực cấp (cookie); thêm "Thiết bị đã xác minh" |
| | Dòng "Lịch sử đăng nhập" (41) | Sai nghĩa: hiện ghi "đăng nhập, đăng xuất và thất bại". Đúng: chỉ lần đăng nhập thành công và thất bại, **không ghi đăng xuất** |
| `1.requirement/4-context-map.md` | B16, dòng "Thiết bị tin cậy" (312) | Đổi thành "Thiết bị đã xác minh": xác minh bằng mã một lần qua email, hạ mức xác nhận hành động nhạy cảm từ mật khẩu + mã xuống mật khẩu, **không** bỏ qua xác thực nhiều lớp |
| | B16, dòng "Thu hồi phiên (đăng xuất từ xa)" | Thêm mức xác nhận: nhập lại mật khẩu, thêm mã một lần email khi thiết bị người gọi chưa xác minh; có thông báo email cho chủ tài khoản |
| | B16, dòng "Giới hạn đăng nhập sai" | Tách hai việc: giới hạn số lần thử theo email và địa chỉ mạng (đã nằm trong CAP-ATH-02, kế hoạch feature 02) và **khóa tạm tài khoản** (Cắt tạm) |
| | B16, "Ràng buộc chính" (316) | Câu "một phiên đang hoạt động cho mỗi (phiên gốc, portal)" đổi sang (phiên người dùng, ứng dụng) |
| | B28 Định danh (464 đến 469) | Bỏ "trừ thời điểm kết thúc phiên ghi một lần" (lịch sử chỉ ghi thêm); "Ra: lệnh thu hồi phiên, bỏ tin cậy thiết bị" đổi thành đăng xuất từ xa ở Xác thực |
| | Bảng liên kết (559) | Dòng Định danh gọi Xác thực: "Thu hồi phiên; bỏ tin cậy thiết bị" đổi theo cách làm mới: người dùng gọi đăng xuất từ xa ở Xác thực; Xác thực hỏi Định danh trạng thái xác minh; Định danh hỏi Xác thực các phiên đang sống |
| | Mục 9.5 (Ngoài phạm vi) | Thêm dòng **Phát hiện dùng lại refresh token**. Lý do: thư viện không có sẵn, phải tự xây, dễ báo động giả khi lỗi mạng; với BFF giữ token phía máy chủ thì lợi ích thấp. Điều kiện xem xét lại: có client công khai giữ token phía người dùng (ví dụ ứng dụng di động tự giữ token) |
| `2.architecture/5. event-catalog.md` | Dòng 19 đến 26 | `SessionIssuedEvent` đang phát cho mỗi phiên ứng dụng: đổi thành chỉ khi mở phiên người dùng; bỏ `SessionsBulkExpiredEvent` (hết hạn không có event); thêm `ClientSessionIssued`, `SessionEnded` (kèm lý do), `UserAccountLocked/Unlocked`, `DeviceVerified/Unverified`. Tên event 2 còn mở (mục D) |
| `4.convention/naming.md` | Dòng 11 | Ví dụ đường dẫn `docs/service/oauth2-service/analysis.md` đổi sang dạng thư mục `analysis/` |
| `4.convention/doc-structure.md` | Phần analysis | Thêm quy ước: BC lớn tách `analysis/0.overview.md` (phần chung) + một file cho mỗi aggregate; ngưỡng áp dụng; giữ số INV và CAP theo BC. Cập nhật mẫu tham chiếu `analysis.md` (dòng 743) |
| `docs/stage/c0-foundation/stage.md` | Dòng 106, 140, 141 | Link "Chưa làm" trỏ `oauth2-service/analysis.md` đổi sang `analysis/0.overview.md`; đổi "portal", "phiên portal" |
| `.../02-login-session/flow.md`, `plan.md` | Nhiều chỗ | Đổi "phiên gốc, phiên portal, portal" sang phiên người dùng, phiên ứng dụng, ứng dụng. Cập nhật mô hình hạn: một hạn phiên duy nhất, không hạn không hoạt động (plan.md 23). Nội dung ADR-0003 (plan.md 77) đổi theo. Bảng TTL chốt ở `login-session.md` mục 3.4 |

### A2. `oauth2-service`

**Đã xong (2026-10-12):** xóa `analysis.md` cũ (nội dung ở `analysis/`); `service.md` đã viết lại AGG-ATH-02, hành vi, event, điểm nối, khái niệm kỹ thuật theo `login-session.md`; `data.md` và `api.yaml` chưa có nội dung phiên nên không cần sửa. **Còn lại:** tách `service.md` thành thư mục theo aggregate (làm khi cần); `data.md`, `api.yaml` khi có bảng phiên và điểm cuối đăng xuất từ xa. Bảng dưới là bản gốc, giữ để đối chiếu.

| File | Việc |
|---|---|
| `analysis.md` (cũ) | Xóa sau khi đã sửa mọi liên kết trỏ tới nó (stage.md:106, naming.md, `identity-service/analysis.md:8`, `0.overview.md:27`). Nội dung đã tách sang `analysis/` |
| `service.md` | Viết lại AGG-ATH-02 (dòng 53 đến 101) theo `login-session.md`: một aggregate, hạn phiên duy nhất, phiên ứng dụng gọn (mã, ứng dụng, liên kết token đổi được, thời điểm mở), bỏ hạn không hoạt động và hạn riêng của phiên ứng dụng. Sửa events (147 đến 148): `SessionIssued` chỉ khi mở phiên người dùng, thêm event cấp token, `SessionEnded` kèm lý do và không mang `lastActiveAt`. Sửa điểm nối (164, 165, 174, 176) và CAP (131, 134). Tách `service.md` thành thư mục theo aggregate giống `analysis/` |
| `data.md`, `api.yaml` | Kiểm bảng phiên (`login_sessions`, `client_sessions`) và điểm cuối đăng xuất từ xa (C1b) sau khi `service.md` đổi |

### A3. `identity-service`

**Đã xong (2026-10-12):** `analysis.md` cũ đã tách thành `analysis/` (`0.overview.md`, `identity-profile.md`, `device.md`, `login-history.md`) và xóa; INV-IDN-01, 04 sửa; Q3, Q4 đã chốt; điểm nối mới đã ghi. **Còn lại:** `service.md` của Định danh (chưa có) khi vào C1b. Bảng dưới là bản gốc, giữ để đối chiếu.

| File | Việc |
|---|---|
| `analysis.md` (cũ) | Tách thành `analysis/`: `0.overview.md` (ranh giới, ngôn ngữ, tác nhân, điểm nối, Chưa làm), hồ sơ định danh (`IdentityProfile`), cộng `device.md` và `login-history.md` đã có. Xóa file cũ sau khi sửa liên kết |
| Dòng 25, 37, 126 | "Thu hồi phiên, bỏ tin cậy thiết bị": đổi theo đăng xuất từ xa (điểm cuối ở Xác thực); "Phiên đang hoạt động ... suy ra từ lịch sử": đúng ra lấy từ Xác thực qua một lời gọi đọc, không dựng bản sao |
| INV-IDN-01 | Bỏ ngoại lệ "trừ thời điểm kết thúc": lịch sử chỉ ghi thêm |
| INV-IDN-04 | "Mỗi (mã phiên, loại sự kiện) chỉ ghi một dòng" đổi thành chống trùng bằng mã event |
| Q3 | Đã chốt: giữ 12 tháng; địa chỉ mạng lưu đầy đủ, rút gọn khi hiển thị cho người dùng (IPv4 che octet cuối, IPv6 giữ /48) |
| Q4 | Đã chốt: có cảnh báo thiết bị lạ, nhờ Thông báo gửi; kênh do Thông báo quyết. Chặng theo khi Thông báo có |
| Thêm | Điểm nối mới: Định danh nhận `ClientSessionIssued`; đăng xuất từ xa; mã một lần (khuôn dùng chung đặt ở Xác thực) |
| `service.md` (chưa có) | Tạo theo `device.md` và `login-history.md` khi vào C1b |

### A4. Phụ thuộc cần chốt khi chuyển

Mã một lần qua email, thông báo đăng xuất từ xa và cảnh báo thiết bị lạ đều cần Thông báo gửi thư. Các việc cần gửi thư ở B16 (quên mật khẩu, xác minh email) đang Cắt tạm, nên cần chốt Thông báo có mặt ở chặng nào trước khi đưa các việc này vào chặng.

## B. Việc kỹ thuật (nhóm kỹ thuật, bàn sau)

### B1. Sự kiện đã kiểm (không phải suy đoán)

| Sự kiện | Cách kiểm | Hệ quả |
|---|---|---|
| Spring Session JDBC (4.0.3) chạy mỗi lưu, xóa trong **giao dịch riêng, `REQUIRES_NEW`** | Đọc mã byte `JdbcHttpSessionConfiguration.createTransactionTemplate` | `deleteById` gọi trong `@Transactional` của ta vẫn commit riêng |
| Có thể thay bằng bean `TransactionOperations` tên `springSessionTransactionOperations` | Thông báo lỗi trong lớp đó | Đổi sang `REQUIRED` toàn cục nếu muốn |
| `SPRING_SESSION_ATTRIBUTES` có khóa ngoại `ON DELETE CASCADE` | `V2__spring_auth_server_and_session_schema.sql` | Xóa `SPRING_SESSION` theo `primary_id` xóa luôn thuộc tính |
| `JdbcOAuth2AuthorizationService` chỉ dùng `JdbcOperations`, không mở giao dịch riêng | Đọc mã | Xóa `oauth2_authorization` tham gia giao dịch của ta |
| `SPRING_SESSION` có hai khóa: `PRIMARY_ID` (cố định) và `SESSION_ID` (đổi khi đổi mã phiên chống chiếm phiên) | Kiến thức nền, **chưa kiểm bằng mã** | Mã hiện dùng `session.getId()` làm `idp_session_id`, mất liên kết nếu mã đổi |
| Spring AS 7.0.5, `OAuth2RefreshTokenAuthenticationProvider`: làm mới cập nhật cùng bản ghi tại chỗ, token cũ bị từ chối như token rác, không có phát hiện dùng lại | Đọc mã nguồn | Chốt xoay vòng, không phát hiện dùng lại |
| Client `web-gateway` có `reuse-refresh-tokens=false` | `V4__seed_oauth2_client.sql` | Xoay vòng đang bật |

### B2. Liên kết với Spring và cách xóa

- Spring chỉ còn là nơi giữ cookie và ngữ cảnh bảo mật của trình duyệt; bảng của ta là nguồn sự thật. Phiên người dùng là bảng riêng (mã UUID v7, ADR-0001); cột `idp_session_id` giữ `SESSION_ID` của session Spring (chốt 2026-10-12: không dùng `PRIMARY_ID`, vì không lấy được từ API công khai). Mã phiên của ta ghi vào thuộc tính session Spring; cửa kiểm hạn so `session.getId()` với `idp_session_id` và cập nhật khi khác. Phiên ứng dụng liên kết bằng `authorization_id` (chốt).
- **Kiểm hạn phiên (chốt 2026-10-12):** không có bộ lọc chung. Kiểm ở mọi điểm cấp token: `/oauth2/authorize` (cấp mã, đọc phiên của ta theo mã trong thuộc tính session Spring; không còn hoặc hạn phiên trừ bây giờ không dương thì từ chối; cập nhật `idp_session_id` nếu `session.getId()` khác) và `/oauth2/token` (làm mới, dùng hạn phiên mang theo bản ghi ủy quyền; phiên đã kết thúc thì bản ghi đã bị xóa). Trang dùng cookie không cấp token (đăng nhập, đăng xuất, mã một lần) không cần cửa. Có kiểm thử cookie Spring còn sống nhưng phiên đã xóa hoặc hết hạn thì không cấp mã. Cơ sở dữ liệu lỗi lúc đọc phiên thì từ chối. Hạn của session Spring đặt bằng hạn phiên lớn nhất.
- **Xóa (chốt 2026-10-12):** một giao dịch xóa phiên của ta (phiên ứng dụng xóa theo) và `oauth2_authorization` (qua service Spring AS); sau commit gọi `sessionRepository.deleteById(idp_session_id)` theo kiểu cố gắng hết sức và báo BFF xóa mã; cửa kiểm hạn là lưới an toàn. Không đụng bảng Spring bằng SQL.
- **Cấp lại token cho cùng ứng dụng:** phiên ứng dụng đổi liên kết sang `oauth2_authorization` mới, xóa bản ghi cũ trong cùng giao dịch.
- **Mang dữ liệu và cắt hạn token (chốt 2026-10-12):** `oauth2_authorization` mang thuộc tính: mã phiên người dùng, hạn phiên (bất biến), mã thiết bị (bản băm), loại tài khoản; ghi lúc cấp mã (`/oauth2/authorize`) khi đã đọc phiên; làm mới cập nhật cùng bản ghi tại chỗ nên không đọc bảng phiên. Cắt hạn (đã kiểm Spring AS 7.0.5): access token và ID token qua `OAuth2TokenCustomizer<JwtEncodingContext>` (`exp` bằng nhỏ hơn của bây giờ cộng TTL access theo loại tài khoản và hạn phiên; ID token mặc định cố định 30 phút cũng cắt); refresh token cần `OAuth2TokenGenerator<OAuth2RefreshToken>` riêng vì `OAuth2RefreshTokenGenerator` lấy TTL cố định từ client; mã ủy quyền giữ TTL 5 phút. TTL theo loại tài khoản (Người dùng, nội bộ, dev theo hồ sơ môi trường) đặt trong cấu hình, kiểm lúc nạp: refresh TTL ít nhất gấp hai access TTL, hạn phiên lớn hơn refresh TTL. Hạn phiên còn lại không dương lúc làm mới thì `invalid_grant` (kiểm khi triển khai việc ném lỗi từ bộ sinh token có ra `invalid_grant` không). Dự phòng: đọc phiên theo khóa chính mỗi lần làm mới.
- **Phát `ClientSessionIssued` (chốt 2026-10-12):** móc ở `save()` của lớp bọc `OAuth2AuthorizationService`; phát khi bản ghi có access token **chưa bị vô hiệu** và có mã phiên của ta (loại dịch vụ gọi dịch vụ, loại lần lưu dọn dẹp của thư viện: mã ủy quyền hết hạn hoặc dùng lại, thu hồi). Mã event lấy từ `jti` của access token để bên nhận bỏ qua trùng. Khi có hộp thư event thì ghi cùng giao dịch với bản ghi ủy quyền. Định danh ghi `last_active_at` bằng một câu lệnh có điều kiện: `UPDATE ... WHERE last_active_at < :t - ngưỡng`, ngưỡng **1 phút** (tham số cấu hình); ngưỡng chỉ gộp cụm event dồn lúc đăng nhập và SSO, tải thật chỉnh bằng hạn access token (rồi mới tính gộp lô trong bộ nhớ nếu đo thấy cần).
- **Đối chiếu phiên ứng dụng với `oauth2_authorization` (chốt 2026-10-12):** làm ở lúc đổi mã lấy token (cùng chỗ phát event, không phải lúc cấp mã): chưa có phiên ứng dụng cho (phiên người dùng, client) thì tạo; có và cùng `authorization_id` (làm mới) thì không làm gì; có nhưng khác `authorization_id` (xác thực im lặng) thì đổi liên kết và xóa bản ghi cũ trong cùng giao dịch. Dùng lại mã đã dùng: thư viện từ chối và vô hiệu token, ta không làm gì (BFF làm mới lỗi, đi qua xác thực im lặng, rơi vào trường hợp đổi liên kết); chủ động kết thúc phiên ở đây chính là phát hiện dùng lại, không làm.
- **Mở phiên người dùng ở bước đăng nhập thành công (chốt 2026-10-12, quyết định 4b):** ở bộ xử lý thành công của chuỗi xác thực (C0: sau mật khẩu đúng; về sau sau mọi lớp, một lần ở điểm kết thúc chuỗi). Địa chỉ mạng, trình duyệt, cookie mã thiết bị lấy thẳng từ yêu cầu của trình duyệt, không chép qua bản ghi ủy quyền. Spring chống chiếm phiên chạy trước bộ xử lý thành công nên `idp_session_id` ghi lúc đó là giá trị cuối. Phiên ứng dụng tạo ở lúc đổi mã lấy token (cơ chế đối chiếu). `SessionIssued` phát lúc mở phiên người dùng.
- **Bỏ `lastActiveAt` khỏi phiên người dùng (chốt 2026-10-12):** nguồn hoạt động là `ClientSessionIssued` ở Định danh (cho cả thiết bị đang đăng nhập và đã đăng xuất); giữ ở phiên là thêm một lần ghi mỗi lần làm mới. Đã sửa `login-session.md` và `service.md`.
- **Job dọn `oauth2_authorization` mồ côi (chốt 2026-10-12):** Spring không tự xóa; mã cấp ra mà không đổi (đăng nhập bỏ dở) để lại bản ghi. Job định kỳ theo lô: bản ghi không được phiên ứng dụng nào trỏ tới và cũ hơn TTL mã ủy quyền; cùng nhóm với job dọn phiên quá hạn.
- **Mã một lần (chốt 2026-10-13, quyết định 6):** mã MFA khi đăng nhập giữ ở session (đồng bộ với `SendLoginOtp`). Mã xác minh thiết bị (Định danh) và mã xác nhận đăng xuất từ xa (Xác thực) lưu Redis, khuôn chung: khóa `otp:<mục đích>:<account>` giữ mã băm và số lần sai, hạn 5 phút, xin mã mới ghi đè; số lần xin ở khóa riêng `otp-req:...` (3 lần mỗi 5 phút); kiểm bằng script Lua nguyên tử (đọc, so, tăng số lần sai, xóa khi đúng hoặc sai lần 3); băm bằng HMAC-SHA256 với khóa bí mật, so thời gian hằng; mã ràng buộc ngữ cảnh theo `device.md` (xác minh thiết bị gắn (tài khoản, mã thiết bị); đăng xuất từ xa chỉ gắn (tài khoản, mục đích), không gắn phiên người gọi hay thiết bị đích); event gửi email mang mã rõ nên không log, không đưa vào nhật ký. Dự phòng session: chỉ chỗ nào có session; hai luồng Redis đều do BFF gọi bằng token, không có cookie phiên ở Xác thực lẫn Định danh nên **không dự phòng được**, Redis lỗi thì từ chối (fail closed). Gộp thành một thư khi một lần bấm đăng xuất nhiều thiết bị kết thúc nhiều phiên. Nguyên tắc: chỗ nào dự phòng được thì làm để tăng tính sẵn sàng; việc rà các phụ thuộc Redis khác (giới hạn tốc độ, v.v.) xem Redis lỗi thì mỗi nơi hạ cấp hay từ chối để làm khi triển khai kỹ thuật, vì Redis dùng nhiều nên lỗi Redis có thể kéo nhiều chức năng.
- **Cookie mã thiết bị (chốt 2026-10-13, quyết định 7):** tên `DEVICE_KEY`; `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`; hạn 12 tháng, gia hạn mỗi lần đăng nhập; giá trị ngẫu nhiên 256 bit, cơ sở dữ liệu chỉ lưu bản băm; cookie mất thì lần đăng nhập sau là thiết bị mới.

### B3. Hiệu năng (ước tính minh họa, không phải số đo)

- Giả định: 5 đăng nhập/giây trung bình, 700 đỉnh (kế hoạch feature 02); 8 giờ mỗi phiên và 1,3 phiên ứng dụng mỗi phiên là **giả định của tôi**. Phiên người dùng sống khoảng 144 nghìn, phiên ứng dụng khoảng 187 nghìn.
- Đăng nhập là bài toán CPU (bcrypt, ADR-0002), không phải cơ sở dữ liệu. Xác thực im lặng nhẹ hơn nhiều. Cửa kiểm hạn là một lần đọc khóa chính.
- **Làm mới token là điểm nóng:** số lần làm mới mỗi giây bằng số phiên ứng dụng chia hạn access token tính giây: khoảng 208 (15 phút), 104 (30 phút), 52 (60 phút). Mỗi lần làm mới là một đọc và một ghi `oauth2_authorization`, **cộng một cập nhật `lastActiveAt` của thiết bị ở Định danh** nay nhờ `ClientSessionIssued` (trước đây ước tính hai cập nhật ở Xác thực).
- `oauth2_authorization` chưa có chỉ mục ngoài khóa chính (migration V2 lấy lược đồ mẫu): tìm theo refresh token, mã ủy quyền, access token có thể quét toàn bảng. Cần chỉ mục cho `refresh_token_value`, `authorization_code_value` (cột `TEXT`, kiểm `EXPLAIN`), `principal_name`. Mỗi lần làm mới ghi lại cả cột token `TEXT` (JWT hàng KB). **Chưa đo.**
- Bảng tăng trưởng duy nhất là lịch sử đăng nhập: khoảng 430 nghìn dòng/ngày; giữ 12 tháng là khoảng 157 triệu (ước tính, chưa tính dòng thất bại) nên cân nhắc phân vùng theo tháng khi số liệu thật đòi.
- Chỉ mục cần: `login_sessions` (PK; `userAccountId`; `(userAccountId, deviceKey)`; hạn phiên để dọn theo lô); `client_sessions` (duy nhất `(loginSessionId, clientId)`, duy nhất `authorizationId`); `Device` duy nhất `(userAccountId, deviceKey)`; lịch sử `(userAccountId, occurredAt DESC)`, `(deviceId, occurredAt DESC)`, duy nhất `eventId`.
- Rủi ro lớn nhất chưa biết: hành vi thật của `oauth2_authorization` dưới tải làm mới; đo ở bước 14 kế hoạch feature 02.

## C. Mã hiện tại cần đổi (ghi lại, chưa sửa; đọc mã 2026-10-10)

| Thứ | Việc |
|---|---|
| `idpSessionId` chỉ là chuỗi trỏ vào session Spring | Thêm phiên người dùng làm khái niệm có mã, hạn phiên, `deviceKey` |
| `OAuthSession` đứng một mình; `oss_id` ULID | Thành phiên ứng dụng con của phiên người dùng; mã UUID v7 (ADR-0001), đi trong hợp đồng danh tính (`sid`) |
| `SessionIssuedEvent` phát cho mỗi `OAuthSession` | Chỉ phát khi mở phiên người dùng; thêm event cấp token; `SessionEnded` có lý do |
| Hết hạn qua `SessionsBulkExpiredEvent`, job dọn mồ côi không thấy nơi gọi theo lịch | Bỏ event; hết hạn suy ra từ hạn phiên; job dọn dòng quá hạn theo lô |
| Session Spring trượt 86400s theo mỗi request; không có kiểm hạn phiên khi làm mới | Hạn phiên cố định; cửa kiểm hạn; token cắt theo hạn phiên |
| `deviceHash` vân tay JavaScript do client gửi (`login.html`, hidden `device_hash`, cookie `dh`) | Thay bằng cookie mã thiết bị do Xác thực cấp trước khi nhập mật khẩu; bỏ `composite_hash` (có User-Agent, Accept-Language) |
| `devices.trusted`, luồng OTP tin cậy ở Định danh (`RequestDeviceTrustOtp`, `TrustDevice`, `RedisDeviceTrustOtpStore`) | Đổi nghĩa thành thiết bị đã xác minh; tách bảng `device_verification`; bỏ kiểm `isTrusted()` khi đăng xuất từ xa; thêm `verifiedAt`; nâng khuôn mã một lần (6 số, hạn 5 phút, băm, xin mã tối đa 3 lần mỗi 5 phút, nhập sai tối đa 3 lần). Hiện phần này chưa nối với xác thực nhiều lớp |
| `GetDevices` suy trạng thái từ `last_history_id` + `ended_at` | Kéo từ Xác thực; đổi `last_seen_at` thành `last_login_at`, thêm `last_active_at` |
| Lịch sử `login_activities` một dòng cho mỗi `OAuthSession`, có `ended_at`, OFFSET + COUNT | Hai loại dòng, chỉ thêm, không `ended_at`; phân trang khóa; chỉ mục `(device_id, occurred_at)`; tên thiết bị lưu trên dòng |
| `RevokeSession` hủy session Spring trong giao dịch riêng; xóa cả phiên đăng nhập khi hủy một `OAuthSession` | Một giao dịch; thêm xác nhận mật khẩu; đăng xuất thiết bị khác và đăng xuất mọi thiết bị khác |
| Đổi mật khẩu, khóa tài khoản chưa nối hủy phiên | Nối khi làm C1b (lý do "đổi mật khẩu" trừ phiên đang đổi, "tài khoản bị khóa") |
| `JwtTokenCustomizer` thêm claim `email` | Bỏ (KT-02) |
| `log.warn("[DEV] access_token ...")` ở `SessionEstablishingAuthorizationService` dòng 97 | Bỏ ngay: ghi token ra log, vi phạm quy tắc log |
| Secret mặc định của Google ở `application.properties` (`client.google.secret`) | Đưa ra ngoài mã, nhất là nếu đã dùng thật |
| Client `web-gateway` seed secret `{noop}secret` (V4) | Đã ghi ở bước 9 kế hoạch |
| Client `web-gateway` seed `require-proof-key=false` (V4) | Bật PKCE cho client; hiện chỉ dựa vào bí mật client để chặn dùng mã bị lộ |
| CSRF tắt ở `oauth2-service` (hai chuỗi lọc) và `web-gateway` (hai chuỗi lọc) (chốt 2026-10-13, quyết định 8, làm trong đợt này vì chi phí thấp) | `oauth2-service`: bật CSRF cho chuỗi dùng cookie (đăng nhập, đăng xuất, mã một lần; `login.html` đã có ô `_csrf`), miễn `/oauth2/token`. `web-gateway`: `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN`), frontend gửi header `X-XSRF-TOKEN` cho request thay đổi dữ liệu. Cookie `OAUTH2_SESSION` đặt tường minh `HttpOnly`, `Secure`, `SameSite=Lax`. Bắt buộc `state`. Test: thiếu hoặc sai mã CSRF thì 403; `/oauth2/token` vẫn gọi được không cần mã |
| `deviceHash` do client khai quyết định "thiết bị đã biết" | Hết khi mã thiết bị do máy chủ cấp |
| Đăng nhập Google, xác thực nhiều lớp bằng OTP email, `provider` trong event và lịch sử | Ngoài phạm vi C0 (`service.md` đã ghi "Tạm"); đối chiếu khi thiết kế lại |
| Rerun `RequestLoggingIntegrationTest` với Postgres | Khi được phép khởi động Postgres |

## D. Để khi triển khai kỹ thuật

**Tên event cấp token.** `ClientSessionIssued` nay phát cả khi làm mới, nên nghe như chỉ phát khi mở mới; có thể đổi thành "token được cấp cho phiên ứng dụng". Đồng thời cân nhắc đổi `SessionIssued`, `SessionEnded` theo tên hai loại phiên (phiên người dùng). Chốt tên rồi sửa đồng loạt ở `login-session.md`, `device.md`, `login-history.md`, `0.overview.md`, event-catalog.

## E. Quyết định kỹ thuật còn đang bàn (cập nhật 2026-10-12)

- **Quyết định 5 (tra `oauth2_authorization` theo token), đã chốt hướng 2026-10-13:** không chỉ mục trên giá trị token, không nhúng `authorization_id` vào refresh token. Refresh: BFF gửi thêm `sid` (mã phiên ứng dụng của ta) làm tham số ở `/oauth2/token`; Xác thực đọc phiên ứng dụng theo khóa chính `sid`, lấy `authorization_id`, đọc `oauth2_authorization` theo khóa chính, rồi so refresh token bằng phép so thời gian hằng; `sid` sai thì không khớp và bị từ chối. Cần tùy biến điểm tra authorization theo token (bọc `OAuth2AuthorizationService` đọc `sid` từ request, hoặc converter + provider tùy biến cho grant `refresh_token`; chọn khi triển khai kỹ thuật), cùng bộ sinh refresh token TTL động (quyết định 2). **Còn mở:** bước đổi mã ủy quyền chưa có `sid` (bộ sinh mã gọi với `authorization = null`), cần cách riêng (chỉ mục một phần trên `authorization_code_value`, hoặc dựa vào mã sống 5 phút nên bảng nhỏ). Mọi kiểu tra cứu khác (access token, id token, `state`, mã thiết bị, kiểu không xác định): trả không tìm thấy, chặn `/oauth2/revoke`, `/oauth2/introspect`; `fillfactor` thấp cho bảng. Đo hiệu năng khi triển khai (nạp 187 nghìn bản ghi, tải làm mới 100 đến 250 lần/giây, `EXPLAIN (ANALYZE, BUFFERS)`, p99).
- **Mã định danh trong token, đã chốt 2026-10-13 (một claim, không thêm claim phiên người dùng: claim thứ hai chỉ tiết kiệm một lần đọc khóa chính ở thao tác hiếm mà tốn khoảng 60 byte mỗi request):** token mang một claim phiên là `sid` (mã phiên ứng dụng của ta, UUID v7); mã phiên người dùng không vào token (tra từ `sid` khi cần, ví dụ loại phiên đang gọi lúc đăng xuất từ xa). Thuộc tính bản ghi ủy quyền thêm mã phiên ứng dụng: lúc cấp mã dùng lại mã phiên ứng dụng đã có của (phiên người dùng, client) hoặc sinh trước mã mới; bộ tùy biến JWT đặt `sid` từ thuộc tính; bước đối chiếu tạo hàng với đúng mã đó.
- **Quy tắc `idp_session_id`, đã chốt 2026-10-13:** `SESSION_ID` của Spring là giá trị cookie đăng nhập (mang là dùng, lộ thì đăng nhập chung được mọi ứng dụng đến hết hạn phiên). Chỉ nằm ở cookie và cơ sở dữ liệu của Xác thực; không vào token, event, log. Event `SessionIssued` hiện mang `idpSessionId`: bỏ. Rà log che `Cookie`, `Set-Cookie` (`SecretScrubbingCustomizer`).
- **Quyết định 8 (chống giả mạo yêu cầu), đã chốt 2026-10-13, cả bốn mục (CSRF hai nơi, cookie, PKCE, `state`) làm luôn trong đợt này; việc mã nằm ở bảng C:** CSRF đang tắt ở `oauth2-service` (hai chuỗi lọc) và `web-gateway` (hai chuỗi lọc); `login.html` đã có ô `_csrf` nhưng không hiện vì CSRF tắt. Cần: bật CSRF cho các chuỗi dùng cookie ở máy chủ xác thực (miễn `/oauth2/token`); CSRF cho BFF (cookie `SameSite` và mã CSRF qua header); đặt tường minh `HttpOnly`, `Secure`, `SameSite=Lax` cho cookie `OAUTH2_SESSION`; bật PKCE và bắt buộc `state`. CSRF không chống được cookie bị lộ hay trình duyệt bị chiếm; đó là vệ sinh cơ bản trước trang độc.
