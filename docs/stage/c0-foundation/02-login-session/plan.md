# Feature 02: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định thiết kế; **Agent** viết phần
thường, người rà), điều cần trao đổi (nếu có), và cách biết bước đã xong. Chọn cách thực hiện khi bắt đầu từng bước.

Feature gồm hai phần A và B (xem `flow.md` mục 1). Các bước chung dẫn vào phần A. Quy tắc giống feature 01: mô hình miền được ưu tiên, hạ tầng đi sau và
định hình theo miền; mỗi bước chỉ làm và kiểm đúng phạm vi của nó; quyết định kỹ thuật (ADR) đặt ngay trước bước cần dùng nó.

**Xác thực không phân quyền** (`analysis/0.overview.md` mục 6): kế hoạch này không có bước nào kiểm vai có được dùng ứng dụng hay không. Việc đó thuộc ứng dụng và BC nhận mã.

## Từ luồng đến giải pháp

Mỗi dòng là một bước của luồng (nút trong sơ đồ `flow.md` mục 3, thêm làm mới token và event từ các mục 10, 11). Ba cột đầu là phần thiết kế, viết **trước khi code**: hướng giải ngắn (chi tiết ở `tech.md` và ADR, không chép lại ở đây), rồi các hướng giải gộp thành thành phần nào. Cột cuối điền **sau khi làm xong**: lớp và KT cụ thể. Các bước 1 đến 16 bên dưới là thứ tự thi công; trạng thái từng bước ở đó là bản gốc.

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai (điền sau khi xong) |
|---|---|---|---|
| Vào ứng dụng chưa đăng nhập, chuyển tới trang đăng nhập kèm ứng dụng đích | Spring Authorization Server giữ yêu cầu cấp mã đang chờ; client đăng ký sẵn, `state` và PKCE bắt buộc (ADR-0005) | Cấu hình máy chủ ủy quyền | `OAuth2AuthorizationServerConfig`, `RegisteredClientSeeder`, `StateRequiredValidator`; KT-30, 32 (bước 9b) |
| Cấp cookie mã thiết bị trước khi nhập mật khẩu | Máy chủ sinh mã ngẫu nhiên 256 bit khi trang đăng nhập chưa thấy cookie, chỉ lưu bản băm (ADR-0005, `tech.md` T5) | Cookie thiết bị | `DeviceKeyCookieIssuer`, `LoginPageController`; KT-27 (bước 9b) |
| Đã có phiên người dùng còn hiệu lực? | Cửa kiểm hạn trước khi cấp mã: đọc phiên của ta theo mã ghi trong session Spring, hết hạn hoặc không còn thì về trang đăng nhập (ADR-0003 mục 7) | Cửa kiểm phiên ở điểm cấp mã | `AuthorizationSessionGateFilter`, `ResolveAuthorizationSession`; KT-10, 12, 13, 23 (bước 9a.2, 12, 13) |
| Chuẩn hóa email, giới hạn số lần thử | Đếm hai khóa (cặp email và IP, riêng email) bằng Redis trước khi xác thực; vượt thì 429, Redis lỗi thì mở (`tech.md` T3) | Bộ lọc trước bộ xác thực | `LoginRateLimitFilter`, `LoginRateLimitProperties`; KT-15, 16, 17 (bước 10) |
| Tìm tài khoản và kiểm mật khẩu; từ chối và ghi event thất bại | Một use case xác thực, email lạ vẫn tốn đúng công kiểm; sai thì cùng thông báo, chỉ ghi event khi tìm ra tài khoản (INV-ATH-03) | Use case xác thực nằm sau `AuthenticationProvider` của Spring | `AuthenticateWithPassword`, `PasswordAuthenticationProvider`, `PasswordLoginFailureHandler`; KT-03 đến 07, 28 (bước 6, 9a.1, 9a.7) |
| Tạo phiên người dùng | Mở phiên ở điểm cuối chuỗi xác thực, khi `session.getId()` đã là giá trị cuối; nới hạn session Spring bằng hạn phiên; không mở được thì hủy xác thực (ADR-0003 mục 1, 6) | Bộ xử lý thành công + use case mở phiên | `LoginSession.open`, `OpenLoginSession`, `LoginSessionAuthenticationSuccessHandler`, `LoginSessionRepositoryAdapter`; KT-01, 14, 24, 26 (bước 5, 8, 9a.7, 15) |
| Cấp phiên ứng dụng cho ứng dụng đích (mỗi phiên người dùng, ứng dụng: một phiên) | Chỉ ghi khi token đã được cấp, trong cùng giao dịch với bản ghi ủy quyền (điểm móc ở `save()`); ràng buộc duy nhất ở cơ sở dữ liệu chốt khi đua nhau (ADR-0003 mục 5) | **Lớp bọc dịch vụ ủy quyền**: cùng với bước cắt hạn và event bên dưới | `LoginSession.issueClientSession`, `SessionBoundAuthorizationService`, `RecordClientSessionIssued`; KT-10, 11, 22 (bước 5, 8, 9a.5, 9a.6, 12) |
| Hợp đồng danh tính trong token, cắt hạn theo hạn phiên | Bộ tùy biến JWT thêm `sub`, `role`, `sid`, cắt `exp` bằng nhỏ hơn của TTL và hạn phiên; không đưa mã Spring hay `authorization_id` ra ngoài (ADR-0003 mục 3, 4) | Bộ tùy biến token | `IdentityClaims`, `JwtTokenCustomizer`; KT-02, 18, 25 (bước 3, 9a.3) |
| Event ghi trong miền | Aggregate ghi event, use case lấy ra phát cùng giao dịch; chưa có hộp thư (`service.md` mục 5) | Aggregate và lớp bọc ủy quyền | `SessionIssuedEvent`, `ClientSessionIssuedEvent`, `LoginFailedEvent`; KT-06, 26, 28 (bước 5, 6, 9a.5) |
| Làm mới token | Refresh token mang `sid`, tra phiên ứng dụng và bản ghi ủy quyền theo khóa chính, so refresh token, giữ nguyên `sid`; hết hạn thì `invalid_grant` (ADR-0003 mục 8) | Bộ sinh refresh token gắn phiên + lớp bọc ủy quyền | `SessionBoundRefreshTokenGenerator`, `LoginSession.refreshClientSession`; KT-19, 20, 21 (bước 9a.4, 9a.6, 12) |
| Giữ log không lộ bí mật, có `user.id` | Che trường nhạy cảm trong bộ ghi log, đặt `user.id` một chỗ sau bước kiểm quyền (`logging.md`) | Bộ lọc log | `UserIdLogContextFilter`, `SecretPatterns`; KT-08 (bước 9b.5) |
| Dọn phiên quá hạn và bản ghi ủy quyền mồ côi | Dọn theo lô qua use case riêng, Lập lịch gọi định kỳ (C2b) | Use case dọn (chưa có) | **Chưa làm**, chỉ có `LoginSessionRepository.findExpired`; KT-31 (hoãn, `deferred.md`) |

## Điều kiện bắt đầu

Những điều thuộc **nghiệp vụ** đã chốt ở `analysis/` (`0.overview.md`, `login-session.md`; một hạn phiên duy nhất, nhiều thiết bị, đăng xuất và thu hồi, giới hạn số lần thử theo email), không bàn lại trong kế hoạch này. Không còn câu hỏi nghiệp vụ mở nào chặn feature.

Riêng KT-09 cần một tài khoản vai Quản trị: test tự tạo, không phải chờ feature 04.

## Các bước chung

### Bước 1. Chốt các tham số kỹ thuật và NFR · Người

**Trạng thái:** Đã làm `2026-10-13` (`tech.md` T1 đến T5, `nfr.md`)

Phân tích từ NFR ra thông số, ghi vào `tech.md`, `nfr.md`: hạn mã đăng nhập; hạn phiên và TTL các loại token (theo `analysis/login-session.md`); hệ số băm giữ ở mức hiện tại (ADR-0002);
tải đăng nhập thiết kế (khoảng 5 lần/giây bình thường, khoảng 700 lần/giây ở làn sóng trước flash sale, là giả định) và hệ quả lên CPU băm; hai mức giới hạn số lần thử theo email (cặp email và địa chỉ mạng, riêng email), đo trần băm để đặt mức.

Xong khi: các giá trị trên có số cụ thể trong tài liệu và không còn chữ "chưa chốt".

### Bước 2. Rà kịch bản test · Người

**Trạng thái:** Đã làm `2026-10-13` (KT-01 đến KT-33)

Đối chiếu KT-01 đến KT-33 ở `flow.md` với S1, S3, S4 và INV-ATH-03, 06, 08, 10; thiếu hay thừa thì sửa ở `flow.md`.

Xong khi: danh sách được chốt, mỗi kịch bản trỏ về một nguồn.

## Phần A. Đăng nhập một ứng dụng

### Bước 3. Mô hình hóa miền · Người

**Trạng thái:** Đã làm `2026-10-13`: khung ở gói `domain/login_session` (thân hàm chưa cài, chờ bước 5); `INVALID_CREDENTIALS` do use case ném ở cả hai nhánh (aggregate chỉ trả đúng hoặc sai)

Dựng khung mã và rà `service.md` ở mức aggregate: phiên (aggregate `LoginSession`, đã chốt là một aggregate: phiên người dùng là gốc, phiên ứng dụng là con), hợp đồng danh tính
(`IdentityClaims`, giá trị miền, đã chốt), cổng xác thực mật khẩu (dùng lại `PasswordHasher`), khái niệm ứng dụng, mã thiết bị (giá trị miền, chỉ giữ bản băm). Nội dung còn cần trao đổi:
ai ném lỗi `INVALID_CREDENTIALS`.

Xong khi: khung mã biên dịch được, `service.md` khớp ở mức aggregate, miền không phụ thuộc thư viện hay khung ứng dụng.

### Bước 4. Viết test cho miền · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-13`: 4 tệp test ở `domain/login_session` (`LoginSessionTest` 22, `SessionLifetimePolicyTest` 9, `IdentityClaimsTest` 3, `LoginFailedTest` 3); 20 + 9 test đỏ vì hành vi chưa cài, 8 test xanh (hợp đồng danh tính, event `LoginFailed`, danh sách phiên con bất biến và không có cách kéo dài hạn đã đúng ở khung)

Test trên giao diện công khai của khung: cấp phiên, mỗi (phiên người dùng, ứng dụng) một phiên (phần miền của INV-ATH-10), hợp đồng danh tính không mang thông tin thừa (KT-02), hạn token không vượt hạn phiên (KT-18), event phát đúng nội dung (KT-26, 28).

Xong khi: test biên dịch được và chạy đỏ vì hành vi chưa cài.

### Bước 5. Cài hành vi của miền · Người

**Trạng thái:** Đã làm `2026-10-14`: cài `LoginSession` và `SessionLifetimePolicy`; 57 test miền xanh (gồm 37 test mới của feature 02). Phiên giữ cờ `ended` trong bộ nhớ (không lưu) để `end` idempotent và chặn cấp token sau khi kết thúc

Cài cho đến khi test bước 4 xanh. Miền vẫn không phụ thuộc thư viện, cơ sở dữ liệu hay khung ứng dụng.

### Bước 6. Use case đăng nhập trên cổng giả · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-14`: use case đăng nhập `LoginWithPassword` (sau đó tách ở bước 9a thành `AuthenticateWithPassword` và `OpenLoginSession`) và 14 test trên cổng giả (KT-01, 03, 04, 05, 06, 18, 26, 28 phần use case); 71 test miền và use case xanh; đã phá thử hai chỗ (bỏ kiểm giả cho email lạ, event mang mã thiết bị thô) và test bắt được. Chưa gắn `@Service`/`@Transactional` vì các cổng chưa có cài đặt (bước 8, 9a). Khi có hộp thư event phải giữ được `LoginFailedEvent` dù use case ném lỗi (không quay lui giao dịch cùng event)

Use case: chuẩn hóa email, tìm tài khoản, kiểm mật khẩu (email lạ vẫn chạy một lần kiểm giả), kiểm trạng thái, tạo phiên người dùng, event thất bại chỉ khi có tài khoản. Chạy trên kho giả,
bộ băm giả, đồng hồ cố định. Phạm vi bước này: chưa giới hạn số lần thử theo email (bước 10), chưa đăng nhập chung (phần B), không ghi event ra đâu cả như feature 01.

Test: KT-01, 03, 04, 05, 06, 18, 26, 28.

Xong khi: test use case xanh, chạy nhanh vì không chạm cơ sở dữ liệu.

### Bước 7. ADR mã đăng nhập và phiên hai tầng · Người

**Trạng thái:** Đã rà lại `2026-10-14` cả ba ADR (ADR-0003 sửa 5 chỗ; ADR-0004 sửa 2; ADR-0005 sửa 1 chỗ về `state`). Cả ba còn Proposed, chuyển Accepted sau khi cài và đo (bước 8 đến 14)

Viết ba ADR (bản nháp **Proposed** đã có, ngày `2026-10-13`): **ADR-0003** (phiên hai tầng, hợp đồng danh tính `sid`, hạn và cắt hạn token, đăng xuất và thu hồi, làm mới token tra theo khóa chính), **ADR-0004** (mã một lần), **ADR-0005** (CSRF, cookie, PKCE, `state`, cookie mã thiết bị). Cách xoay khóa ký thuộc `tech.md`.

Việc dọn phiên quá hạn và ủy quyền mồ côi hoãn tới khi có Lập lịch (C2b); ở bước 8 chỉ cần viết thành use case dọn theo lô và truy vấn phiên lọc theo hạn.

Xong khi: ba ADR rà lại và chuyển Accepted sau khi cài và đo (bước 8 đến 14), `service.md` mục kỹ thuật trỏ tới chúng. Hai chỗ còn mở của ADR-0003 (đổi mã ủy quyền chưa có `sid`; cách chèn `sid` ở điểm tra) chốt khi cài.

### Bước 8. Lưu trữ phiên · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-14`: migration V13, thực thể JPA, kho `LoginSessionRepositoryAdapter` và 16 test tích hợp trên Postgres 16 do Testcontainers dựng (Flyway chạy từ trống); đã phá thử hai chỗ (bỏ lọc hạn, bỏ dịch ràng buộc duy nhất) và test bắt được; test tranh chấp 16 luồng ép xung đột thật bằng rào chắn (mọi luồng đọc xong rồi mới cùng ghi) và khẳng định ít nhất 15 xung đột rồi còn đúng một hàng. Hai use case đăng nhập (xác thực, mở phiên) chạy lại trên kho thật vẫn xanh. `data.md` mục 2 cập nhật. Chưa gồm khóa ký (`rsa_key_pairs`, T4) và bảng `oauth_sessions` cũ (bỏ ở bước 9a)

Lược đồ và ánh xạ theo miền đã định hình (ADR-0003 mục 1): `login_sessions` (mã UUID v7, `idp_session_id`, một hạn phiên, mã thiết bị băm) và `client_sessions` (`id` là `sid`, `authorization_id` đổi được); chỉ mục đã chốt (`_viec-con-lai.md` B3: duy nhất `(loginSessionId, clientId)` và `authorizationId`, `userAccountId`, `(userAccountId, deviceKey)`, hạn phiên); kho phiên; use case dọn phiên quá hạn theo lô (chạy qua Lập lịch ở C2b, truy vấn "đang sống" lọc theo hạn).
Chỉ giữ phiên đang hoạt động (`analysis/login-session.md`). Test tích hợp chỉ ca không thay được bằng test miền (lưu rồi đọc lại; ràng buộc duy nhất của (phiên người dùng, ứng dụng) ở cơ sở dữ liệu).

Test: KT-31 (dọn, lọc theo hạn) và phần lưu trữ của KT-21, 22.

Xong khi: test tích hợp xanh, test use case bước 6 chạy lại trên kho thật vẫn xanh.

### Bước 9a. Tích hợp Spring Authorization Server · Người rồi Agent

**Trạng thái:** Đã làm `2026-10-14` (chia bước con): **9a.1** tách use case đăng nhập thành `AuthenticateWithPassword` và `OpenLoginSession`; **9a.2** cửa kiểm hạn `ResolveAuthorizationSession`; **9a.3** bộ tùy biến JWT (`sid`, `role`, cắt `exp`, `invalid_grant`; cấu hình `app.session.*`, profile dev); **9a.4** `SessionBoundRefreshTokenGenerator`; **9a.5** lớp bọc `SessionBoundAuthorizationService` (điểm móc `save()` trong một giao dịch, use case `RecordClientSessionIssued`); **9a.6** tra bản ghi ủy quyền theo `sid` (refresh theo tham số `sid`, access token theo claim, mã ủy quyền theo chỉ mục V14); **9a.7** nối xác thực của Spring: `PasswordAuthenticationProvider` (dùng `AuthenticateWithPassword`), `LoginSessionAuthenticationSuccessHandler` (mở phiên sau bước chống chiếm phiên), `PasswordLoginFailureHandler`, `AuthorizationSessionGateFilter` trước `AuthorizationFilter`, bean `SessionBoundAuthorizationService` thay bean cũ, `Sha256DeviceKeyHasher` và `SecureRandomDeviceKeyGenerator`, `LoginSessionApplicationConfig`; **9a.8** gỡ `SessionEstablishingAuthorizationService` (kèm dòng log nguyên token), `EstablishSession`, `IssueSession`, `DeviceAwareAuthenticationFailureHandler`. Kiểm bằng 210 test, trong đó `LoginFlowIntegrationTest` chạy toàn bộ ngữ cảnh ứng dụng qua HTTP thật trên Postgres Testcontainers (Kafka tắt, Redis trỏ địa chỉ chết) và đã bắt được ba lỗi mà test đơn vị không thấy (xem `tech.md` T6). Chưa gỡ phần mô hình cũ còn được dùng: `OAuthSession`, `EndIdpSession`, `RevokeSession`, `MeController` (đăng xuất và thu hồi, feature 03), `DeviceAwareAuthenticationSuccessHandler` và đường Google, MFA (C3c hoặc cắt tạm). Đăng xuất chưa kết thúc được phiên mới cho tới feature 03.

Nối miền với thư viện theo ADR-0003: ghi `idp_session_id` và cập nhật khi mã session Spring đổi; kiểm hạn ở `/oauth2/authorize` (cấp mã) và `/oauth2/token` (làm mới); cắt hạn token (bộ tùy biến JWT cho access và ID token, bộ sinh refresh token riêng); thuộc tính trên `oauth2_authorization`; đối chiếu phiên ứng dụng lúc đổi mã lấy token (tạo, giữ, đổi liên kết cùng giao dịch); điểm tra bản ghi ủy quyền theo `sid` khi làm mới; lớp bọc `OAuth2AuthorizationService` phát `ClientSessionIssued`; phát `SessionIssued` ở điểm cuối chuỗi xác thực; không để `SESSION_ID` và `authorization_id` lọt ra token, event, log.
Use case ở điểm móc so `sid` do `issueClientSession` trả về với `sid` đã nằm trong token và quay lui nếu khác (KT-22). Hai chỗ còn mở của ADR-0003 chốt ở bước này: cách chèn `sid` ở điểm tra, và đổi mã ủy quyền chưa có `sid`.

Test: KT-18 (trên Spring), 19, 20, 21, 22, 23, 24, 25, 26 (phần ghi thật).

Xong khi: test tích hợp và hệ thống xanh; cookie Spring còn sống mà phiên đã xóa thì không cấp mã; token không có mã nội bộ.

### Bước 9b. API, trang đăng nhập, client seed · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-14`: **9b.1** cookie `DEVICE_KEY` cấp ở trang đăng nhập khi chưa có, gia hạn ở bộ xử lý thành công (`DeviceKeyCookieIssuer`, `app.device-key.*`), cookie phiên `HttpOnly`, `SameSite=Lax`, `Secure` đọc từ `SESSION_COOKIE_SECURE`, hạn 30 ngày (tắt `Secure` ở profile dev); **9b.2** bật CSRF cho chuỗi dùng cookie (mã trong cookie `XSRF-TOKEN` riêng, không xoay khi đăng nhập), miễn `/register`; **9b.3** `StateRequiredValidator` (bắt buộc `state`, nối sau kiểm tra mặc định) và PKCE (bắt buộc cho `postman-dev`, của `storefront-web-bff` tạm tắt bằng cấu hình, xem `tech.md` T7); **9b.4** `RegisteredClientSeeder` đặt lại bí mật client từ biến môi trường (băm BCrypt, không còn `{noop}`), `require-proof-key` theo cấu hình (mặc định tắt), và tạo client `postman-dev` (công khai, PKCE) ở profile dev; **9b.5** `SecretPatterns` che `Cookie`, `Set-Cookie` và cookie đã biết, `UserIdLogContextFilter` đặt `user.id` sau bước kiểm quyền ở cả ba chuỗi bảo mật; **9b.6** băm lại khi hệ số lưu thấp hơn (`PasswordHasher.needsRehash`, `UserAccount.upgradePasswordHash`, lỗi lưu không làm hỏng đăng nhập; `UserAccountRepositoryAdapter.save` thành `@Transactional`). `LoginFlowIntegrationTest` có 15 test (CSRF, `DEVICE_KEY`, `state`, PKCE, seed, log JSON thật có `user.id` và không có token, cookie). Đã phá thử ba chỗ (CSRF, `state`, bộ lọc `user.id`) và test bắt được. Ba test cũ (`UserAccountApiTest`, `RequestLoggingIntegrationTest`, `UserAccountConcurrencyTest`) dùng cơ sở dữ liệu cấu hình sẵn thay vì Testcontainers nên chưa chạy lại ở bước này (xem `tech.md` T7).. Bổ sung `2026-10-16`: KT-32 (`a_request_from_an_unregistered_client_or_with_another_redirect_uri_is_refused_and_no_code_is_issued`: client lạ hoặc `redirect_uri` lạ đều bị 400, không chuyển hướng, không cấp mã, kể cả khi đã có phiên) và KT-33 (`an_authorization_code_cannot_be_used_twice_and_the_replay_leaves_our_session_untouched`: dùng lại mã bị 400 `invalid_grant`, phiên và liên kết của ta không đổi), cả hai ở `LoginFlowIntegrationTest`. Dọn `login.html` `2026-10-16`: bỏ ô ẩn `device_hash` và đoạn JavaScript tính vân tay trình duyệt (mã thiết bị chỉ còn là cookie `DEVICE_KEY` do máy chủ cấp), nút Google chỉ còn chuyển hướng; test hồi quy `the_login_page_no_longer_computes_or_sends_a_browser_fingerprint`

Điểm cuối và trang đăng nhập của Xác thực (cấp cookie `DEVICE_KEY`, KT-27; CSRF cho chuỗi dùng cookie, miễn `/oauth2/token`, KT-29; bắt buộc `state`, PKCE, KT-30), mã đăng nhập mang hợp đồng danh tính, client đã đăng ký cho ứng dụng Cửa hàng và Vận hành cùng client dev cho Postman (`flow.md` mục 7),
sửa seed (bí mật client thật, `require-proof-key=true`), băm lại khi hệ số cũ (KT-07), đặt `user.id` khi phân tích token (KT-08), bỏ log token và che `Cookie`, `Set-Cookie`. Test hợp đồng đối chiếu `api.yaml`: KT-02, 03, 09, 27, 29, 30.

Xong khi: đăng nhập thật ra được mã đăng nhập; test API xanh; test log JSON thật có `user.id`.

### Bước 10. Giới hạn số lần thử theo email · Người rồi Agent

**Trạng thái:** Đã làm `2026-10-16`: `LoginRateLimitFilter` (gói `extension/authentication`) chặn `POST /login` trước bộ xác thực; mức và cửa sổ đọc từ `LoginRateLimitProperties` (`app.login.rate-limit.*`, kiểm lúc khởi động); vượt thì chuyển tiếp tới `/login/throttled` (`LoginPageController.throttled`) trả **HTTP 429 kèm `Retry-After`** và dựng lại trang đăng nhập; bộ đếm lỗi thì mở cửa kèm cảnh báo. 11 test bộ lọc (KT-15, 16, 17 và các ca phụ), 3 test trang 429, 2 test đầu-cuối trong `LoginFlowIntegrationTest` (429 dù mật khẩu đúng và không mở phiên nào; bộ đếm lỗi vẫn đăng nhập được). Điều quyết khi cài: xem `tech.md` T3

Giới hạn nhóm 2 (`docs/global/3.technical/rate-limiting-layers.md`): trong bước đăng nhập, sau khi chuẩn hóa email và trước khi tìm tài khoản, đếm **mọi lần thử** theo hai khóa, cặp
(email, địa chỉ mạng) với mức thấp và riêng email với mức cao hơn, vượt thì 429 kèm thời gian chờ và không kiểm mật khẩu. Áp như nhau cho mọi email kể cả email không tồn tại.
Dùng bộ đếm cửa sổ trượt của thư viện chung gọi trực tiếp (đăng nhập đi qua bộ lọc bảo mật, không phải `CommandHandler`, nên chưa dùng chú thích). Mức và khoảng thời gian đọc từ thuộc tính,
nạp lúc khởi động. **Đã chốt:** Redis lỗi thì mở cửa và ghi cảnh báo (khác phân quyền; ở đây đóng cửa sẽ khóa cả đăng nhập). Trong lúc lỗi, giới hạn theo địa chỉ mạng ở cổng cũng dùng Redis nên cả hai lớp cùng tạm mất; đây là rủi ro đã chấp nhận. Địa chỉ mạng lấy từ tiêu đề do cổng đặt, không tin tiêu đề do client gửi.
Con số hai mức đã chốt ở `tech.md` T3 (5 lần/5 phút theo cặp, 15 lần/15 phút theo email); nội dung cần trao đổi: thông điệp 429 kèm `Retry-After`.

Test: KT-15, 16, 17 (KT-17: bộ đếm lỗi thì đăng nhập vẫn chạy, có cảnh báo).

Xong khi: test xanh; một email bị bắn từ nhiều địa chỉ giả lập thì bị chặn ở mức theo email.

### Bước 11. Ghi token khi cấp (chỉ dev) · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-16`: **đổi hướng so với kế hoạch đầu.** Công cụ giả lập token (`POST /dev/token`) đã làm rồi bỏ vì token giả không gắn phiên thật (`sid` ngẫu nhiên, không làm mới được, không qua `/userinfo`). Thay bằng ghi token **thật** ra log mỗi khi luồng đăng nhập cấp: móc `IssuedTokenListener` (gói `extension/session`), gọi từ `SessionBoundAuthorizationService` sau khi giao dịch cấp token xong (lỗi của bên nhận bị bỏ qua); bên nhận duy nhất là `DevIssuedTokenLogger` (gói `extension/dev`), ghi một dòng INFO có tài khoản, client, `sid`, hạn, access token, refresh token, ID token. Bean chỉ tồn tại khi **cả** profile `dev` **và** cờ `observability.logging.reveal-secrets=true` (cờ có sẵn của bộ quan sát, tự từ chối khởi động ngoài profile `dev`, `logging.md` mục 6); thiếu một trong hai thì không có dòng log nào. Token lấy ra là token thật, gắn phiên thật nên làm mới và `/userinfo` dùng được. Test: 3 test ở `SessionBoundAuthorizationServiceTest` (móc gọi đúng một lần sau khi cấp, không gọi khi bị từ chối, bên nhận lỗi không làm hỏng việc cấp) và 4 test ở `DevIssuedTokenLoggerTest` (nội dung dòng log, điều kiện tồn tại của bean)

Xong khi: chạy dịch vụ ở profile `dev` kèm cờ thì lấy được token thật từ log sau mỗi lần đăng nhập; không có cờ hoặc không phải `dev` thì không có token nào trong log.

## Phần B. Đăng nhập chung và vòng đời phiên

### Bước 12. Đăng nhập chung · Người rồi Agent

**Trạng thái:** Đã làm `2026-10-16` (logic đã nằm trong các bước 5, 8, 9a; bước này chỉ còn đối chiếu kiểm thử). Bằng chứng theo KT:
- **KT-10** (có phiên người dùng, vào ứng dụng thứ hai: không hỏi mật khẩu, tạo phiên ứng dụng): `LoginSessionTest` (`issue_creates_a_client_session_when_the_client_has_none`, `issue_for_another_client_adds_a_second_client_session`), `RecordClientSessionIssuedTest.the_first_token_creates_the_client_session_with_the_sid_chosen_at_authorize`, `ResolveAuthorizationSessionTest.a_live_session_with_no_client_session_gets_a_freshly_chosen_sid...`. Chưa có test đầu-cuối sang một **client khác** (hiện chỉ có `storefront-web-bff` và `postman-dev`); đã phủ ở mức miền và use case.
- **KT-11** (lặp, kể cả song song, vẫn một phiên ứng dụng): `LoginSessionRepositoryAdapterTest.many_threads_issuing_for_the_same_client_really_conflict_and_still_end_with_exactly_one_client_session` (16 luồng, Postgres thật) và `two_aggregates_issuing_for_the_same_client_hit_the_unique_constraint_and_it_is_translated`.
- **KT-19** (làm mới giữ `sid`, refresh token cũ bị từ chối): `LoginSessionTest.refresh_keeps_the_client_session...`, `RecordClientSessionIssuedTest.a_refresh_of_the_same_authorization_changes_nothing...`, `SessionBoundAuthorizationServiceTest.a_refresh_saves_again...`, đầu-cuối `LoginFlowIntegrationTest.a_refresh_with_the_sid_keeps_the_sid_and_a_refresh_without_it_is_rejected`.
- **KT-20** (làm mới khi hạn phiên đã hết: `invalid_grant`): `LoginSessionTest.refresh_is_rejected_once_the_session_has_expired`, `SessionBoundRefreshTokenGeneratorTest.a_session_that_has_expired_cannot_get_a_refresh_token`. Mức API: `LoginFlowIntegrationTest.a_refresh_after_the_session_has_expired_is_invalid_grant_and_issues_nothing` (đẩy hạn phiên về quá khứ ở cả hàng phiên lẫn bản chụp hạn trong bản ghi ủy quyền, làm mới ra 400 `invalid_grant`, không cấp token).
- **KT-22** (xác thực im lặng: đổi liên kết, xóa bản ghi cũ cùng giao dịch): đầu-cuối `LoginFlowIntegrationTest.silent_authentication_reuses_the_login_session_and_the_sid_and_removes_the_old_authorization`, `SessionBoundAuthorizationServiceTransactionTest`, `LoginSessionRepositoryAdapterTest.a_relink_is_persisted_and_the_client_still_has_exactly_one_row`.

Có phiên người dùng còn hiệu lực thì bỏ qua kiểm mật khẩu, chỉ cấp phiên ứng dụng (`issueClientSession`: theo `clientId`, tạo mới hoặc dùng lại); lặp không tạo thêm phiên. Làm mới token (`refreshClientSession`, theo `sid`) và xác thực im lặng chạy qua cơ chế ở bước 9a (cấp lại token cho ứng dụng đã có phiên). Test song song cùng (phiên người dùng, ứng dụng).

Test: KT-10, 11, 19, 20, 22.

### Bước 13. Hết hạn, xác nhận phiên, nhiều thiết bị · Người rồi Agent

**Trạng thái:** Đã làm `2026-10-16` (như bước 12, logic đã nằm ở các bước trước). Bằng chứng theo KT:
- **KT-12** (hết hạn suy ra từ hạn, xác nhận phiên từ chối, không phát event): `LoginSessionTest.confirm_is_true_before_expiry_and_false_at_and_after_it`, `ResolveAuthorizationSessionTest.a_session_whose_expiry_has_arrived_is_rejected` và `an_ended_session_is_rejected`.
- **KT-13** (xác nhận phiên còn hạn trả đúng tài khoản và vai): `ResolveAuthorizationSessionTest.a_live_session_with_no_client_session_gets_a_freshly_chosen_sid_and_the_values_for_the_token`, `AuthorizationSessionGateFilterTest.a_live_session_passes_the_gate_and_leaves_the_result_for_the_authorization_service`.
- **KT-14** (thiết bị khác tạo phiên người dùng khác, không đụng phiên cũ): `OpenLoginSessionTest.every_call_opens_a_new_session_so_a_second_device_gets_its_own`.
- **KT-23** (cookie Spring còn sống mà phiên của ta đã xóa hoặc hết hạn thì không cấp mã): `ResolveAuthorizationSessionTest.a_session_that_no_longer_exists_is_rejected_even_though_the_cookie_is_alive`, `AuthorizationSessionGateFilterTest` (phiên mất, hết hạn, tài khoản không hoạt động), đầu-cuối `LoginFlowIntegrationTest.a_session_that_was_deleted_sends_the_user_back_to_the_login_page_instead_of_issuing_a_code`.

Hết hạn suy ra từ hạn, không phát event; xác nhận phiên trả đúng tài khoản và vai, từ chối khi hết hạn; đăng nhập ở thiết bị khác tạo phiên người dùng khác.

Test: KT-12, 13, 14, 23 (đồng hồ cố định cho các ca hết hạn).

Xong khi: test xanh.

## Các bước cuối

### Bước 14. Đo NFR · Người

**Trạng thái:** Hoãn; ghi ở `deferred.md`. Bộ đo nền (feature 08) đã xong `2026-10-20`: trần máy đã có ở `nfr.md`, phép đo ứng dụng chưa làm.

Theo `nfr.md`: tải đăng nhập ở mức bình thường rồi tăng dần tới điểm gãy, so với trần băm của máy (đã đo ở feature 08). Ghi điều kiện đo cạnh số. Thêm: so `fillfactor` 70 (V18) với mặc định 100 để xem `oauth2_authorization` có phình dưới tải làm mới hay không (ADR-0003 mục 8).

Xong khi: nhãn ở `nfr.md` đổi từ Chỉ thiết kế sang Đã đo kèm điều kiện đo.

### Bước 15. Chạy thật · Người

**Trạng thái:** Đã chạy `2026-10-16` trực tiếp vào dịch vụ (profile `dev`, cổng 8004, trên `oauth2_db` và hạ tầng cục bộ), bằng curl có cookie (không dùng trình duyệt), rồi dọn tài khoản thử. Chưa chạy qua cổng `/auth` (chờ feature 06) và chưa quan sát hạn phiên 10 phút và hạn refresh 3 phút của profile dev (bỏ qua phần chờ lâu).

**Kết quả đạt**
- **Luồng thật với `postman-dev`** (công khai, PKCE): `authorize` chưa đăng nhập về `/login`; trang đăng nhập cấp cookie `DEVICE_KEY` và `XSRF-TOKEN` kèm `_csrf`; `POST /login` rồi `authorize` ra `code` và `state` đúng; đổi mã ra access token và ID token. Claim access token chỉ gồm `sub aud nbf role scope iss exp iat jti sid` (không có `email`, `oss_id`, `roles`), `aud=postman-dev`, `iss` là địa chỉ công khai qua cổng. SSO: đã có phiên thì `authorize` cấp mã ngay không hỏi lại mật khẩu.
- **Luồng thật với `storefront-web-bff`** (bí mật): có refresh token; làm mới thiếu `sid` hoặc `sid` sai thì `invalid_grant`; làm mới đúng thì ra access mới, refresh xoay vòng, **`sid` giữ nguyên**; refresh cũ dùng lại `invalid_grant`; refresh mới dùng được.
- **Dùng token:** `/userinfo` 200; `/api/v1/me/...` 200 với Bearer, 401 khi không token, **401 khi chỉ có cookie phiên** (chuỗi `/api/**` đã stateless); JWKS 200.
- **Ca lỗi:** thiếu `state` và thiếu `code_challenge` bị `invalid_request` về redirect đã đăng ký; client lạ và `redirect_uri` lạ bị 400; đổi lại mã đã dùng và đổi với `code_verifier` sai bị `invalid_grant`; sai mật khẩu về `/login?error`; thiếu `_csrf` bị 403; vượt giới hạn: lần thứ 6 trở đi **429 kèm `Retry-After: 300`** và trang đăng nhập có thông báo.
- **Log:** mỗi yêu cầu hoàn tất có một dòng kèm `trace.id`; yêu cầu đã xác thực có `user.id` (các yêu cầu `/api` không có `user.id` đều là lần gọi không token, 401). Mật khẩu, `Bearer`, JWT, giá trị cookie đều **không** có trong log (0 lần). Bốn cảnh báo đều dự kiến: cấu hình `AuthenticationProvider` tự viết (Spring cảnh báo `UserDetailsService` không còn dùng cho đăng nhập mật khẩu), dùng lại mã, hai lần vượt giới hạn.
- **Cờ ghi token ở dev** (`observability.logging.reveal-secrets=true`): đúng một dòng `dev token issued` mang access token và refresh token thật (khớp từng ký tự với token nhận được); mật khẩu vẫn không có. Không bật cờ thì không có dòng đó.
- **Dữ liệu:** mỗi tài khoản một phiên người dùng, `device_key` chỉ là bản băm (64 ký tự), hạn phiên 10 phút (dev), một phiên ứng dụng có `id` đúng bằng `sid` trong token, trỏ đúng bản ghi ủy quyền còn tồn tại, thuộc tính bản ghi chỉ gồm `login_session_id`, `client_session_id`, `session_expires_at`, `account_role`.

**Phát hiện cần quyết hoặc ghi nhận**
1. **Độ lệch đồng hồ 60 giây của Spring (đã chốt 5 giây, `2026-10-16`):** access token hạn 60 giây vẫn được `/api` chấp nhận ở mốc +65 giây, bị từ chối ở +125 giây. Là giá trị mặc định của `JwtTimestampValidator`. Hệ quả: token sống thêm tới 60 giây sau `exp`, tức cũng sau hạn phiên (quy tắc "token không sống lâu hơn phiên" có dung sai 60 giây ở bên nhận). **Đã sửa:** bộ giải mã của `/api` dùng `JwtTimestampValidator` 5 giây (`app.security.jwt.clock-skew`, `KeyConfiguration`), có test `JwtDecoderClockSkewTest`; 5 giây là quy ước cho mọi dịch vụ nhận token.
2. **`postman-dev` là client công khai nên không có refresh token** (hành vi mặc định được giữ, `SessionBoundRefreshTokenGenerator`); muốn thử làm mới bằng Postman phải dùng client bí mật như `storefront-web-bff` hoặc thêm một client dev bí mật.
3. **Bản ghi ủy quyền mồ côi có thật:** mã được cấp (SSO) mà không đổi thì để lại một bản ghi ủy quyền không phiên ứng dụng nào trỏ tới; chờ job dọn (đã chốt, chưa làm).
4. Cảnh báo `Global AuthenticationManager configured with an AuthenticationProvider bean` có thể hạ về ERROR cho gọn log, vì đó là cấu hình chủ ý.
5. **Session Spring phía máy chủ chỉ sống 30 phút không hoạt động, không phải 30 ngày** (đo `2026-10-16` trên hàng `SPRING_SESSION` của một yêu cầu `authorize` chưa đăng nhập: cookie `Max-Age=2592000` nhưng `max_inactive_interval=1800`). `HttpSessionConfiguration` dùng `@EnableJdbcHttpSession` nên `server.servlet.session.timeout=30d` không được áp dụng và không có chỗ nào đặt hạn; ADR-0003 và `tech.md` ghi "hạn session Spring bằng hạn phiên lớn nhất" nên đang **lệch với mã**. Hệ quả suy ra (chưa chạy thử khi đã đăng nhập): không hoạt động quá 30 phút thì cookie đăng nhập vô tác dụng và SSO bắt đăng nhập lại dù hạn phiên còn dài. **Đã sửa:** giữ 30 phút cho session vô danh, nới hạn session lên đúng hạn phiên lúc mở phiên (`LoginSessionAuthenticationSuccessHandler`); có test đơn vị và test đầu-cuối đo `spring_session.max_inactive_interval` (hạn phiên sau đăng nhập, 1800 khi vô danh).

Build, khởi động, đăng nhập thật bằng luồng mã ủy quyền kèm PKCE (thủ công hoặc qua trình duyệt tự động) với client dev; đọc claim của token; xem log. Kiểm: mỗi lời gọi trả đúng mã
và `code`; dòng log hoàn tất có `trace.id` và `user.id`; mật khẩu và token không xuất hiện trong log. Khi feature 06 xong, chạy lại qua cổng `/auth`.

Xong khi: các ca trong `flow.md` ra đúng kết quả đã ghi, log không có lỗi bất thường.

### Bước 16. Đóng feature · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-16`: `service.md` (nhãn Đã làm cho phiên, use case, event, hợp đồng danh tính; dọn quá hạn và đăng xuất vẫn Dự kiến), `data.md` (ngày), `api.yaml` (429 và `?unavailable` của `POST /login`), điều học ghi ở `../00-features.md`. Cũng đã sửa ADR-0003 và `tech.md` về hạn session Spring.

Cập nhật `service.md`, `data.md`, `api.yaml` cho khớp mã và đổi nhãn Dự kiến thành Đã làm; ghi điều học được vào `../00-features.md`.

Xong khi: không còn chỗ nào trong tài liệu lệch với mã.
