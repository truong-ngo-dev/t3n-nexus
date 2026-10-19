# Feature 03: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định thiết kế; **Agent** viết phần
thường, người rà), điều cần trao đổi (nếu có), và cách biết bước đã xong. Chọn cách thực hiện khi bắt đầu từng bước.

Quy tắc giống feature 01 và 02: mô hình miền được ưu tiên, hạ tầng đi sau và định hình theo miền; mỗi bước chỉ làm và kiểm đúng phạm vi của nó; quyết định kỹ thuật (ADR)
đặt ngay trước bước cần dùng nó. Miền đã có sẵn `LoginSession.end` (feature 02, kể cả test) và cơ chế đăng xuất OIDC đã chạy được ở mô hình phiên cũ, nên feature này là **áp cơ chế đó
cho miền mới và rà lỗ hổng** (`flow.md` mục 4), cộng use case kết thúc phiên, báo BFF và **gỡ mô hình cũ**.

## Từ luồng đến giải pháp

Mỗi dòng là một bước của luồng (`flow.md` mục 3). Ba cột đầu là phần thiết kế, viết **trước khi code**: hướng giải ngắn (chi tiết ở `tech.md` và ADR, không chép lại ở đây),
rồi các hướng giải gộp thành thành phần nào. Cột cuối điền **sau khi làm xong**. Các bước 1 đến 9 bên dưới là thứ tự thi công; trạng thái từng bước ở đó là bản gốc
(quy trình: `docs/global/4.convention/work-process.md` mục 3.4).

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai (điền sau khi xong) |
|---|---|---|---|
| Ứng dụng xóa phiên của nó, trả URL đăng xuất | Thuộc BFF (feature 06); ở đây chỉ chốt URL gồm điểm cuối, `id_token_hint`, trang đích | BFF, ngoài dịch vụ | Chưa làm, feature 06 |
| Trình duyệt tới điểm cuối đăng xuất; kiểm hint, client, trang đích | Giữ điểm cuối OIDC có sẵn của Spring Authorization Server; đăng ký trang đích cho client (T3); tra ID token theo `sid` (T1); `SessionRegistry` rỗng để Spring bỏ qua kiểm `sid` (T2); lỗi `invalid_token` về `/` không động session (T4) | Cấu hình máy chủ ủy quyền + seed client + lớp bọc ủy quyền (tra token) + bean sổ phiên rỗng | `OAuth2AuthorizationServerConfig` (hai handler), `SidAuthorizationLookup`, `NimbusIdTokenHintVerifier`, `NoSessionRegistry`; KT-09, 10, 13, 18, 19, 20, 21 (bước 5) |
| Tìm phiên người dùng còn sống theo cookie đăng nhập | Kho tìm phiên theo `idp_session_id` (cần chỉ mục); không tìm ra thì không phải lỗi (INV-ATH-05) | Cổng kho phiên + use case kết thúc phiên | `LoginSessionRepository.findActiveByIdpSessionIdForUpdate`, `EndLoginSession`, migration V15; KT-03, 22, 23 (bước 3, 4) |
| Một giao dịch: kết thúc phiên và mọi phiên ứng dụng, xóa bản ghi ủy quyền | Chụp `sid` và mã bản ghi ủy quyền trước khi `LoginSession.end(SELF_LOGOUT)`; kho xóa phiên; bản ghi ủy quyền xóa cùng giao dịch; chặn hồi sinh khi đua (R7) | Use case kết thúc phiên (một giao dịch) | `EndLoginSession`, `LoginSessionLogout`, `LoginSessionRepositoryAdapter` (khóa hàng); KT-01, 02, 04, 08, 15, 24 (bước 3, 4) |
| Hủy session Spring, rồi báo BFF theo `sid` | Hủy session sau khi giao dịch xong (lỗi thì giữ để thử lại); báo qua lời gọi nội bộ có sẵn, đổi sang `sid`; lỗi thì cảnh báo | Bộ xử lý đăng xuất của Spring + bên báo BFF (cổng của use case) | `OidcLogoutSuccessHandler`, `EndedSessionNotifier`, `WebGatewayEndedSessionNotifier`, `WebGatewayRevocationClient`; KT-07, 11 (bước 5, 6) |
| Chuyển hướng về trang đích | Cùng đích dù trước đó có phiên hay không; hint đã dùng vẫn về trang đích (R4) | Bộ xử lý đăng xuất của Spring | `OidcLogoutSuccessHandler`, `OidcLogoutFailureHandler`; KT-03, 14 (bước 5) |
| Event `SessionEnded` | Aggregate ghi event (đã có); chỉ khi thật sự kết thúc; chưa có bus | Aggregate + use case | `LoginSession.end`, `SessionEndedEvent` (feature 02), `EndLoginSession`; KT-06 (bước 3) |
| Gỡ mô hình phiên cũ | Gỡ sau khi đường mới chạy, trong cùng feature; migration bỏ bảng | Dọn dẹp, không phải thành phần mới | migration V16, gỡ các gói `domain/session`, `application/session`, `persistence/session`; `MeController` bỏ endpoint thu hồi cũ; KT-12 (bước 7) |

## Điều kiện bắt đầu

Những điều thuộc **nghiệp vụ** đã chốt ở `analysis/` (`0.overview.md` CAP-ATH-04, `login-session.md` bảng lý do kết thúc), không bàn lại ở đây: đăng xuất kết thúc phiên của lần
đăng nhập hiện tại và mọi phiên ứng dụng của nó; luôn thành công; không giữ lịch sử ở Xác thực. **Cơ chế** (BFF xóa phiên, trình duyệt tới điểm cuối đăng xuất OIDC kèm hint) cũng đã chốt,
không bàn lại; còn lại là rà lỗ hổng khi áp cho miền mới.

Feature 02 xong (phiên hai tầng, `LoginSession.end`, bản ghi ủy quyền theo `sid`). Feature 05, 06 chưa cần: đi hết luồng bằng client HTTP có cookie, và hợp đồng báo BFF chỉ cần chốt.

## Các bước

### Bước 1. Thử các rủi ro trên điểm cuối đăng xuất có sẵn · Người rồi Agent

**Trạng thái:** Đã làm `2026-10-16`: thử bằng yêu cầu thật tới `/connect/logout` (Postgres Testcontainers); kết quả và cách sửa đề xuất ở `tech.md` T1 đến T6. Phát hiện chính: điểm cuối đăng xuất hiện **hỏng hoàn toàn** vì lớp bọc ủy quyền của feature 02 không tra ID token (R12), và sau khi sửa vẫn bị kiểm `sid` của Spring chặn (R2). Đã thêm phép thử nhiều thiết bị `2026-10-17` (R13). Chưa thử R7 (cần bước 3, 4). Mã thử tạm đã gỡ, không để lại thay đổi mã chính.

Chạy điểm cuối đăng xuất hiện có (`/connect/logout`) sau khi đăng nhập bằng luồng của feature 02 và quan sát từng rủi ro ở `flow.md` mục 4 đang **Cần thử**:
- **R2:** hint lấy lúc đăng nhập, cookie còn sống: Spring có từ chối vì `sid` khác băm mã session không? Kiểm xem sổ phiên của Spring có đang giữ session đăng nhập không.
- **R3, R4:** hint cũ sau làm mới token (bản ghi có đổi ID token không?), sau xác thực im lặng, và khi gọi lần hai.
- **R5:** không cookie, hoặc cookie đã hết hạn: kết quả là gì (trang xác nhận, lỗi, chuyển hướng)?
- **R7, R8:** đua với làm mới và đổi mã; mã đã cấp trước đăng xuất.
- **R10:** `id_token_hint` có lọt vào log của dịch vụ không.

Với mỗi rủi ro ghi kết luận vào `tech.md` (T1, T2, ...): đã ổn, hoặc cách sửa (bộ chuyển đổi hint, bộ xử lý lỗi của điểm cuối, đổi nội dung `sid`, giữ ID token). Rủi ro nào buộc đổi quy tắc chung thì viết ADR.

Xong khi: mỗi rủi ro R2 đến R8, R10, R12 có kết luận có bằng chứng (kết quả chạy hoặc test), và các KT tương ứng (KT-13 đến 19) được xếp vào đúng bước làm. **Đã đạt** trừ R7.

### Bước 2. Rà kịch bản test · Người

**Trạng thái:** Đã làm `2026-10-17`: rà KT-01 đến KT-24 ở `flow.md` mục 6. Điều chỉnh: KT-03, 06, 19 có cả mức use case; KT-07 chuyển về bộ xử lý ở hạ tầng (use case chỉ trả danh sách, việc báo ứng dụng nằm ở hạ tầng như điểm móc lúc cấp token); thêm KT-22 (phiên quá hạn), KT-23 (không cookie), KT-24 (kết quả liệt kê đủ phiên ứng dụng). Hai điều chốt tạm ở use case: không cookie thì không kết thúc theo mỗi hint; phiên đã quá hạn coi như không có (hàng do job dọn lo).

Rà KT-01 đến KT-24 ở `flow.md` mục 6 sau khi có kết luận bước 1: đủ chưa, mức đúng chưa (miền, use case, lưu trữ, API, đồng thời, hệ thống), ca nào là của feature 06.

Xong khi: bảng KT chốt, mỗi KT có mức và chứng minh.

### Bước 3. Use case kết thúc phiên trên cổng giả · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-17`: use case `EndLoginSession` (gói `application/login_session/end_login_session`) và 8 test trên cổng giả (`EndLoginSessionTest`: KT-01, 03, 04, 06, 19, 22, 23, 24). Thêm cổng `LoginSessionRepository.findActiveByIdpSessionId` (sau đổi thành `findActiveByIdpSessionIdForUpdate` ở bước 4) (adapter và truy vấn JPA đã nối để build xanh; chỉ mục và test tích hợp ở bước 4). Use case chỉ dùng kho phiên: trả danh sách `(sid, clientId, authorizationId)` đã chụp trước `end`; việc xóa bản ghi ủy quyền (cùng giao dịch) và báo ứng dụng (sau giao dịch) nằm ở hạ tầng, theo mẫu của điểm móc lúc cấp token, nên không có cổng xóa bản ghi ủy quyền hay cổng báo trong use case. Đã phá thử ba chỗ (chụp sau `end`, bỏ kiểm hint thuộc phiên của cookie, bỏ kiểm cookie) và test bắt được.

Use case tìm phiên còn sống theo cookie, kiểm hint thuộc phiên đó, chụp phiên ứng dụng rồi `LoginSession.end(SELF_LOGOUT)` và xóa phiên. Không tìm ra phiên thì trả kết quả rỗng, không ném lỗi.

Xong khi: test use case xanh, đã phá thử một chỗ (bỏ chụp `sid` trước khi `end`, hoặc báo BFF trước khi xóa) và test bắt được.

### Bước 4. Lưu trữ, xóa bản ghi ủy quyền, chống hồi sinh · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-17`: **test KT-15 viết trước và đỏ** (`a_logout_racing_with_an_issue_never_leaves_a_resurrected_session`: "login session resurrected"), xác nhận R7 là thật; sửa bằng **khóa hàng** (`tech.md` T9) thì xanh. Kết quả: cổng `findByIdForUpdate` và `findActiveByIdpSessionIdForUpdate` (truy vấn `SELECT id ... FOR UPDATE` rồi nạp phiên), `RecordClientSessionIssued` và `EndLoginSession` nạp bằng hai cổng này; migration V15 chỉ mục `idx_login_sessions_idp_session`; `LoginSessionLogout` (`infrastructure/security/extension/logout`) bọc một giao dịch gồm `EndLoginSession` và xóa từng bản ghi ủy quyền. Test mới: 2 test tra theo cookie và khóa hàng, 1 dòng kế hoạch truy vấn có chỉ mục, KT-15 ở `LoginSessionRepositoryAdapterTest`; KT-01, 02, 08 ở `LoginSessionLogoutTest` (3 test, Postgres Testcontainers: xóa đủ, quay lui khi lỗi ở bản ghi thứ hai, 4 đăng xuất đồng thời chỉ một bên thật sự kết thúc). Đã phá thử bỏ `FOR UPDATE` và hai test (KT-15, KT-08) cùng đỏ. Toàn bộ test của `oauth2-service`: 291 xanh.

Truy vấn `findActiveByIdpSessionId` đã nối ở bước 3; bước này thêm migration chỉ mục trên `login_sessions.idp_session_id` (hiện không có chỉ mục, nên truy vấn đang quét bảng) và test tích hợp (tìm đúng phiên còn sống, bỏ phiên quá hạn, xóa kéo theo phiên ứng dụng). Bộ xử lý đăng xuất ở hạ tầng bọc một giao dịch: gọi `EndLoginSession` rồi xóa bản ghi ủy quyền của từng phiên ứng dụng trong kết quả; lỗi giữa chừng quay lui cả hai. Chống hồi sinh (R7): `save` hiện là upsert theo khóa chính (`INSERT ... ON CONFLICT DO UPDATE`), nên một yêu cầu làm mới nạp phiên trước khi bị xóa rồi lưu lại sẽ tạo lại hàng; cần cách để lưu một phiên đã bị xóa thì lỗi thay vì chèn mới (đọc mã, chưa thử). Test tích hợp trên Postgres Testcontainers: KT-02, KT-08, KT-15.

Xong khi: test lưu trữ và đồng thời xanh; use case của bước 3 chạy lại trên kho thật vẫn xanh.

### Bước 5. Nối Spring: bộ xử lý đăng xuất và seed client · Người rồi Agent

**Trạng thái:** Đã làm `2026-10-17`: **đổi tên** `LoginSessionAuthorizationService` thành `SessionBoundAuthorizationService` (lớp, cấu hình, 3 lớp test, ADR-0003 và tài liệu) và tách phần tra thành `SidAuthorizationLookup` (T1: ID token theo `sid` và chữ ký, loại `null` theo hình dạng token). Mới: `IdTokenHintVerifier` và `NimbusIdTokenHintVerifier`, `NoSessionRegistry` (T2), `LoginSessionLogout` nối vào cấu hình, `EndedSessionNotifier` và `WebGatewayEndedSessionNotifier` (bước 6 chốt nội dung báo), `OidcLogoutSuccessHandler` viết lại, `OidcLogoutFailureHandler` (T4), trang đích sau đăng xuất cho `postman-dev` (T3; `storefront-web-bff` đã có từ V4). Test: 10 test đầu-cuối đăng xuất mới ở `LoginFlowIntegrationTest` (KT-01, 02, 03, 05, 07, 09, 10, 11, 13, 14, 16, 17, 18, 19, 20, 21, 23), 5 test `NimbusIdTokenHintVerifierTest`, thêm test loại ID token và loại `null` ở `SessionBoundAuthorizationServiceLookupTest`. Đã phá thử thay sổ phiên rỗng bằng sổ mặc định (3 test đỏ) và bắt hint bằng đúng ID token đã lưu (test KT-13 đỏ). Toàn bộ test của `oauth2-service`: 312 xanh.

Viết lại `OidcLogoutSuccessHandler` theo use case mới (chuyển hướng cả khi không có phiên, kiểm hint với cookie bằng `idp_session_id`); đăng ký trang đích cho client trong `RegisteredClientSeeder` (T3); cho `SessionBoundAuthorizationService.findByToken` tra ID token theo chữ ký và `sid` và xử lý loại token `null` (T1), rồi đổi tên lớp thành `SessionBoundAuthorizationService` và tách phần tra; thêm bean `SessionRegistry` không lưu gì (T2) và bộ xử lý lỗi cho điểm cuối đăng xuất (T4); không phát `auth_time` (T8); giữ nhánh không cookie (T5). Test đầu-cuối trong `LoginFlowIntegrationTest`: KT-03, 05, 09, 10, 11, 13, 14, 16, 17, 18, 19, 20, 21.

Xong khi: test đầu-cuối xanh, gồm đăng nhập, làm mới, đăng xuất, rồi thử làm mới, cấp mã và `/userinfo`.

### Bước 6. Báo BFF theo `sid` · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-17`: `WebGatewayRevocationClient` gửi `{"sid": ...}` (thay `ossId`), gọi qua `WebGatewayEndedSessionNotifier` sau commit, lỗi chỉ ghi log. Hợp đồng ở `tech.md` T7. Test KT-07: `WebGatewayRevocationClientTest` (4, máy chủ HTTP cục bộ đóng vai điểm cuối token và storefront-web-bff), `WebGatewayEndedSessionNotifierTest` (3), và 2 test đầu-cuối mới (báo sau commit; lỗi báo không làm hỏng đăng xuất và để lại một cảnh báo). Đã phá thử đổi khóa về `ossId` và bỏ lọc client (test đỏ). Phía nhận ở feature 06.

Đổi `WebGatewayRevocationClient` sang gửi `sid`, gọi sau khi giao dịch xong, lỗi chỉ ghi cảnh báo. Chốt nội dung gửi trong `tech.md` để feature 06 cài bên nhận (và đổi khóa Redis phía BFF từ mã phiên cũ sang `sid`). Test KT-07 bằng bên nhận giả.

Xong khi: test xanh, nội dung gửi đã ghi trong `tech.md`.

### Bước 7. Gỡ mô hình phiên cũ · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-17`: gỡ `domain/session` (`OAuthSession`, kho, dịch vụ hết hạn, event `SessionIssued` / `SessionRevoked` / `SessionsBulkExpired` cũ), `application/session` (`EndIdpSession`, `RevokeSession` và hai handler), `SessionIssuedHandler` (đưa event cũ vào hộp thư), `infrastructure/adapter/repository/session`, `persistence/session`, `JdbcOAuthSessionExpiryService`, và endpoint `DELETE /api/v1/me/sessions/{ossId}` ở `MeController` (đăng xuất từ xa thuộc C3c). Migration `V16__drop_oauth_sessions.sql` bỏ bảng `oauth_sessions`. KT-12: `the_old_session_table_is_gone_after_the_migrations_and_the_new_ones_exist` (Flyway từ trống), tìm không còn tham chiếu trong mã và cấu hình của `oauth2-service` (chỉ còn các migration lịch sử V2, 5, 7, 8, 12, 13). Toàn bộ test: 322 xanh, hai lần chạy liên tiếp. Trong lúc chạy lại đã sửa test `tampered`: đổi ký tự cuối của chữ ký JWT đôi khi không đổi byte nào (bit đệm của base64url) nên token "giả" vẫn hợp lệ.

Gỡ `OAuthSession` và kho, mapper, adapter của nó, `EndIdpSession`, `RevokeSession`, event và handler phiên cũ, endpoint thu hồi cũ ở `MeController`, migration bỏ `oauth_sessions`, các test cũ tương ứng. Làm sau khi đường mới đã chạy (bước 5, 6) để không có thời điểm nào không đăng xuất được.

Xong khi: KT-12: ứng dụng khởi động, toàn bộ test xanh, tìm không còn tham chiếu tới các lớp đã gỡ.

### Bước 8. Chạy thật · Người

**Trạng thái:** Đã chạy `2026-10-17` vào dịch vụ thật (profile `dev`, cổng 8004, cơ sở dữ liệu cục bộ) bằng curl có cookie (`storefront-web-bff`, PKCE), 7 nhóm ca, 38 kiểm tra: 37 đạt, 1 lệch do kỳ vọng của kịch bản chạy (xem dưới); rồi dọn tài khoản thử. **Phát hiện và đã sửa:** (1) bộ báo BFF so thẳng chữ `storefront-web-bff` với `client_sessions.client_id`, mà cột này giữ mã **nội bộ** của client (`storefront-web-client-id`), nên BFF **không bao giờ được báo** trong lần chạy đầu (log không có dòng `WebGatewayRevocation`) dù test đơn vị và đầu-cuối đều xanh vì giả định sai; đã sửa bằng tra client đã đăng ký, đổi test đầu-cuối sang giả ở lời gọi HTTP để chạy bộ báo thật (phá thử bộ báo cũ: test đỏ); chạy lại thấy 6 dòng `revoke call failed` (storefront-web-bff cục bộ không chạy) và đăng xuất vẫn thành công. (2) Mỗi lần báo BFF mint một token `client_credentials` mới nên để lại một hàng `oauth2_authorization` của `oauth2-service-internal` (6 hàng sau 6 lần), không bao giờ bị xóa (`tech.md` T7). Lệch còn lại: ở ca hai thiết bị, kịch bản cho B xin mã (đăng nhập chung) mà không đổi; sau đăng xuất còn một bản ghi ở trạng thái mã chưa đổi (R9, chấp nhận, đổi mã sau đó bị `invalid_grant`), đã xác nhận bằng một lần chạy riêng.

Chạy vào dịch vụ thật (profile `dev`, cơ sở dữ liệu cục bộ), bằng curl có cookie: đăng nhập, đăng xuất, thử làm mới, cấp mã, `/userinfo`; đăng xuất khi chưa đăng nhập; đăng xuất lần hai; trang đích lạ; hai thiết bị. Xem log và hàng `SPRING_SESSION`, `login_sessions`, `oauth2_authorization` sau đăng xuất.

Xong khi: các ca trong `flow.md` ra đúng kết quả, log không lỗi bất thường (không có `id_token_hint`), không còn hàng nào của phiên đã đăng xuất.

### Bước 9. Đóng feature · Agent, Người rà

**Trạng thái:** Đã làm `2026-10-17`: `service.md` (use case đăng xuất, báo BFF, `SessionEnded` đổi nhãn Đã làm; thêm hàng khái niệm kỹ thuật "Đăng xuất"; ghi `sid` khác `sid` của Spring), `data.md` (chỉ mục V15, bỏ `oauth_sessions` V16, `client_id` là mã nội bộ, cách ghi dùng khóa hàng), `api.yaml` (`GET`, `POST` `/connect/logout`), `deferred.md` (5 phần hoãn), cột "Triển khai" của bảng luồng ở đầu tệp này, và điều học được ở `../00-features.md`. `flow.md` đổi trạng thái sang Đã làm. ADR-0003 đến 0005 vẫn Proposed (chuyển Accepted sau khi đo NFR, `02-login-session` bước 14).

Cập nhật `service.md` (use case đăng xuất, giao diện báo BFF, đổi nhãn Dự kiến thành Đã làm), `data.md` (bỏ `oauth_sessions`), `api.yaml` (điểm cuối đăng xuất), `deferred.md` (bản ghi ủy quyền mồ côi R9, Back-Channel Logout chuẩn); điền cột cuối của bảng "Từ luồng đến giải pháp" ở trên; ghi điều học được vào `../00-features.md`.

Xong khi: không còn chỗ nào trong tài liệu lệch với mã.
