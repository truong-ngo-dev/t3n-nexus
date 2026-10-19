# Feature 03: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho cả BC hoặc mọi BC nằm ở ADR; file này ghi kết quả thử và chi tiết riêng của feature.
> **T1 đến T6 là kết quả bước 1 của `plan.md` (`2026-10-16`)**: các quan sát lấy từ yêu cầu thật tới `/connect/logout` trên miền phiên của feature 02 (Postgres Testcontainers, client `storefront-web`).
> Các cách sửa ghi "Đề xuất" chốt khi làm bước 5.

## Kết quả thử

Mô hình đăng xuất cũ chưa bị gỡ nên đường đăng xuất hiện tại vẫn là cơ chế cũ; thử chỉ quan sát bước kiểm của Spring Authorization Server (SAS), không kiểm đường kết thúc phiên mới.

| Ca | Kết quả |
|---|---|
| Hint đúng, cookie còn, không trang đích | 400 `invalid_token` tham số `id_token_hint` (dòng 102 của `OidcLogoutAuthenticationProvider`: không tìm thấy bản ghi theo ID token) |
| Cùng ca, nhưng tạm cho tra được ID token theo `sid` | 400 `invalid_token` tham số `sid` (dòng 169: `sid` của hint khác băm mã session Spring) |
| Có `post_logout_redirect_uri` chưa đăng ký | 400 `invalid_request` tham số `post_logout_redirect_uri` |
| Không cookie đăng nhập, hint hợp lệ (đã cho tra được) | 302 về `/` |
| Không có hint | 400 `invalid_request` |
| Làm mới token | Trả ID token **mới**, khác cái cũ |
| Một tài khoản, hai trình duyệt A và B; A đăng nhập, B đăng nhập, A xin mã lại (đăng nhập chung), đọc `sid` do Spring sinh (`2026-10-17`) | `sid` của A lần 1 = băm(A), của B = băm(B), nhưng của A lần 2 = **băm(B)**: Spring chọn session đăng ký sau cùng |
| Đổi mã cấp trước khi phiên của ta bị xóa | 400 `invalid_grant` "The login session is no longer valid"; bản ghi ủy quyền còn sót |
| Log sau khi chạy các ca trên | Không có giá trị `id_token_hint`; chỉ tên tham số trong thông báo lỗi |

## T1. ID token trong `id_token_hint` phải tra được (R12, R3) · chốt `2026-10-17`

- **Phát hiện:** `SessionBoundAuthorizationService.findByToken` trả "không thấy" cho mọi kiểu token ngoài mã ủy quyền, refresh token và access token, kể cả ID token (ADR-0003 mục 8: không quét cột token).
  Spring tra bản ghi theo chính ID token ở đầu mỗi yêu cầu đăng xuất, nên điểm cuối đăng xuất hỏng hoàn toàn từ feature 02.
- **Quyết định:** thêm nhánh ID token vào `findByToken`: giải mã và **kiểm chữ ký, issuer** bằng khóa của chính ta, **không kiểm hạn** (spec cho phép hint hết hạn; Spring cũng chấp nhận), lấy `sid`, tra bản ghi qua phiên ứng dụng theo khóa chính.
  Không bắt buộc giá trị hint bằng ID token đã lưu, vì làm mới token đổi ID token trong bản ghi còn BFF giữ ID token lúc đăng nhập; chữ ký + `sid` đã gắn hint với đúng phiên.
- **Loại:** quét cột ID token (chậm, trái ADR-0003 mục 8); bắt buộc bằng giá trị đã lưu (hỏng sau lần làm mới đầu tiên, khoảng 15 phút sau đăng nhập); không kiểm chữ ký (ai biết `sid` cũng đăng xuất được người khác).
- **Cùng đợt sửa `findByToken`:** loại token `null` (introspect, revoke của Spring) hiện trả `null` nên `/oauth2/revoke` không thu hồi gì dù trả 200 (đọc mã, chưa chạy thử). Phân theo hình dạng token: JWT thì lấy `sid` từ claim, chuỗi mờ thì lấy từ tham số `sid`, còn lại `null`; `state` và mã thiết bị giữ `null` vì không dùng.
- **Giữ nguyên tên claim `sid`** nên `sidClaimOf` không đổi; tách phần tra theo `sid` sang một lớp riêng khi sửa, và đổi tên lớp bọc thành `SessionBoundAuthorizationService` (lớp hiện giữ ba việc: gắn phiên khi `save`, tra theo `sid`, báo bên nghe).
- **Đã cài `2026-10-17`:** `SidAuthorizationLookup` (tách khỏi lớp bọc), `IdTokenHintVerifier` và `NimbusIdTokenHintVerifier` (chữ ký và issuer bằng khóa của dịch vụ, không kiểm hạn; không làm bean `JwtDecoder` để không lẫn với bộ giải mã của `/api`), `NimbusIdTokenHintVerifier` là `@Component` (nhận `JWKSource` và issuer).
- **Test:** KT-18 ở `NimbusIdTokenHintVerifierTest` (5), `SessionBoundAuthorizationServiceLookupTest` (loại ID token và loại `null`), và đầu-cuối `a_forged_or_garbage_hint_goes_home_and_does_not_touch_the_session`; KT-13 ở `the_hint_from_login_time_still_logs_out_after_a_refresh_and_after_a_silent_authentication`.

## T2. `sid` là mã phiên ứng dụng; Spring không sinh và không kiểm `sid` (R2, R13) · chốt `2026-10-17`

- **Quyết định:** giữ claim `sid` = mã phiên ứng dụng (không đổi tên). Cung cấp một bean `SessionRegistry` **không lưu gì**; Spring Authorization Server (SAS) dùng bean này thay sổ phiên mặc định (`OAuth2AuthorizationServerConfigurer.initSessionRegistry`). Hệ quả:
  Spring không sinh `sid` và `auth_time` (hai claim đó nằm sau điều kiện "sổ phiên có session", `JwtGenerator`), và bước kiểm `sid` lúc đăng xuất bị bỏ qua (`OidcLogoutAuthenticationProvider` chỉ kiểm khi sổ biết session của cookie). Không thay provider nào của Spring.
- **Ràng buộc hint với cookie do ta kiểm** (thay bước kiểm của Spring), ở bộ xử lý thành công của điểm cuối đăng xuất: phiên tìm qua `sid` của hint phải có `idp_session_id` bằng mã session của yêu cầu; khác thì không kết thúc phiên nào (T4).
- **Vì sao không dùng `sid` của Spring:** nó là băm mã session HTTP của người dùng ở máy chủ ủy quyền, và Spring phải *đoán* session khi đổi mã vì yêu cầu đó không có cookie (`OAuth2AuthorizationCodeAuthenticationProvider.getSessionInformation`: lấy mọi session của người dùng trong sổ, chọn cái `lastRequest` mới nhất; `lastRequest` chỉ đặt lúc đăng ký).
  **Đã thử `2026-10-17`:** một tài khoản, hai trình duyệt A và B; A đăng nhập, B đăng nhập, rồi A xin mã lại (đăng nhập chung, cùng client). ID token của A mang băm session của **B**. Đăng xuất của A sẽ bị `invalid_token` dù đúng người, đúng cookie. Không liên quan đến đồng thời hay cơ sở dữ liệu; tái hiện được bằng thao tác tay.
- **Vì sao code cũ không gặp:** nó dùng claim tùy biến `oss_id` và để nguyên `sid` của Spring (nhất quán với bước kiểm của chính Spring), và chỉ thử với một thiết bị.
- **Lỗi R2 là riêng:** vì `sid` của ta khác băm của Spring nên mọi đăng xuất có cookie đều bị từ chối nếu sổ phiên mặc định còn hoạt động; sổ rỗng loại bỏ cả R2 lẫn R13.
- **Loại:**
  - đổi tên claim của ta và để `sid` của Spring: vẫn dính R13 (Spring chọn nhầm), muốn tránh vẫn phải thay sổ phiên;
  - đặt lại `sid` của Spring bằng băm `idp_session_id` thật: kiểm của Spring chạy đúng, nhưng phải lặp hàm băm của Spring và xử lý khi mã session đổi; `sid` của ta vẫn phải có tên khác;
  - thay `OidcLogoutAuthenticationProvider`: sửa một thành phần lõi, trái nguyên tắc chỉ mở rộng.
- **Đánh đổi chấp nhận:** `sid` không mang nghĩa chuẩn OIDC (phiên của OP). Nếu sau này BFF bật Back-Channel Logout thì phải gửi `logout_token` mang `sid` này (mã phiên ứng dụng), mỗi phiên ứng dụng một lần. Ghi vào `deferred.md` khi làm.
- **Đã cài `2026-10-17`:** `NoSessionRegistry` là `@Component` (Spring dùng bean `SessionRegistry` nếu có); `OidcLogoutSuccessHandler` kiểm hint với cookie qua `EndLoginSession` (phiên tìm theo cookie phải chứa phiên ứng dụng `sid` của hint). Đã phá thử thay bằng `SessionRegistryImpl`: 3 test đăng xuất đầu-cuối cùng đỏ.
- **Test:** KT-13, KT-19, KT-20, KT-21 ở `LoginFlowIntegrationTest` (`logout_ends_the_session_...`, `a_hint_of_another_session_...`, `two_devices_of_one_account_...`).

### T2a. Sổ phiên (`SessionRegistry`): vai trò, hành vi mặc định và vì sao chọn sổ rỗng

Phần này giải thích nền của quyết định T2 (đọc mã Spring Security 7.0.5, Spring Authorization Server 7.0.5, Spring Session 4.0.3).

**Vai trò gốc.** Servlet chỉ cho biết session của yêu cầu hiện tại, nên Spring thêm một chỉ mục ngược "người dùng → các session" để **kiểm soát session đồng thời**: `RegisterSessionAuthenticationStrategy` ghi session lúc đăng nhập, `ConcurrentSessionControlAuthenticationStrategy` đếm và đá session cũ khi vượt `maximumSessions`, `ConcurrentSessionFilter` mỗi yêu cầu kiểm cờ hết hạn và làm mới `lastRequest`. Cả bộ chỉ chạy khi bật `sessionConcurrency`; dự án này không bật.

**Authorization Server mượn lại sổ khi bật OIDC** (có `.oidc(...)`), chỉ ở ba điểm, và không kèm `ConcurrentSessionFilter`:

| Điểm | Gọi gì trên sổ |
|---|---|
| `/oauth2/authorize` có `openid` | `getSessionInformation(id)`, rỗng thì `registerNewSession(id, principal)` (một lần cho mỗi session HTTP) |
| `/oauth2/token` đổi mã | `getAllSessions(principal, false)`, chọn mục `lastRequest` mới nhất, băm mã session làm `sid`, đặt cả `auth_time` |
| `/connect/logout` | `getAllSessions(principal, true)`, tìm mục đúng mã session của cookie; có thì so băm với `sid` của hint, không có thì bỏ qua kiểm |

Đơn vị của một mục là **một session HTTP của người dùng** (dùng chung cho mọi ứng dụng của trình duyệt đó), tương ứng với phiên người dùng `login_sessions`, không phải phiên ứng dụng `client_sessions`. Không có sổ nào của Spring biết phiên ứng dụng.

**Ba cách cài đặt và hành vi:**

| | Sổ mặc định (`SessionRegistryImpl`, khi không có bean) | `SpringSessionBackedSessionRegistry` | `NoSessionRegistry` (ta chọn) |
|---|---|---|---|
| Lưu ở đâu | Bộ nhớ tiến trình | Đọc bảng `SPRING_SESSION` và thuộc tính của nó | Không lưu |
| Ghi | Một lần mỗi session ở lần cấp mã `openid` đầu; `lastRequest` đứng yên vì không có `ConcurrentSessionFilter` | Không ghi (các hàm ghi là no-op) | Không |
| Chọn session lúc đổi mã | Session **đăng ký sau cùng** | Session **truy cập gần nhất** (đỡ nhầm hơn, vẫn là phỏng đoán) | Không chọn |
| Gỡ mục | Chỉ khi có sự kiện hủy session; kho session JDBC không phát sự kiện nên mục **không bao giờ bị gỡ** (tăng dần tới khi khởi động lại, chưa đo) | Không cần | Không cần |
| Khởi động lại, nhiều bản chạy | Mất sổ, mỗi bản một sổ, kiểm `sid` bị bỏ qua khi bản đó không biết session | Nhất quán | Không có gì để lệch |
| Kiểm `sid` lúc đăng xuất | Chạy khi sổ biết session | **Luôn chạy** (session còn trong cơ sở dữ liệu) | **Luôn bỏ qua** |
| Chi phí | Không thêm truy vấn | Mỗi lần cấp mã `openid` thêm một `findById`; mỗi lần đổi mã và đăng xuất thêm truy vấn theo `PRINCIPAL_NAME` tải mọi thuộc tính của mọi session của người dùng, **không lọc hết hạn** | Không |

**Vì sao chọn sổ rỗng:**
1. Ta cần `sid` là mã phiên ứng dụng để tra bản ghi ủy quyền bằng khóa chính. Sổ mặc định (khi biết session) và Backed (luôn biết) đều chạy phép kiểm `sid` của Spring và sẽ từ chối mọi đăng xuất vì `sid` của ta khác băm mã session (R2). Backed còn tệ hơn mặc định vì không bao giờ rơi vào nhánh "không biết session nên bỏ qua".
2. Ta đã có dữ liệu chính xác: `login_sessions.idp_session_id` và ràng buộc phiên, nên không cần để Spring đoán session bằng `lastRequest` (R13).
3. Sổ mặc định chỉ mượn chỉ mục mà bỏ vòng đời (không làm mới `lastRequest`, không gỡ mục), nên cho kết quả sai ở nhiều thiết bị và có thể tăng dần; Backed khắc phục việc đó nhưng tốn truy vấn cơ sở dữ liệu cho mỗi lần cấp mã, đổi mã và đăng xuất mà ta không cần.

**Khi nào Backed đáng dùng lại:** nếu muốn `sid` mặc định của Spring (đúng nghĩa OIDC, cho Back-Channel Logout chuẩn) cùng một claim riêng cho phiên ứng dụng. Khi đó Backed (hoặc một sổ đọc từ `login_sessions`) tốt hơn sổ mặc định.

**Chỗ đặt `sid` và `auth_time` của ta:** `JwtTokenCustomizer` (đã đặt `sid` từ ràng buộc phiên; `auth_time` chưa phát, khi cần lấy từ `login_sessions.issued_at`, T8).

## T3. Trang đích sau đăng xuất đã đăng ký (R1) · chốt `2026-10-17`

- **Hiện trạng (đã sửa nhận định ban đầu):** `storefront-web-bff` **đã có** `post_logout_redirect_uris = http://localhost:4200` từ migration V4 (cột `post_logout_redirect_uris` của bảng client, không phải mã Java nên lần rà đầu bằng tìm tên `postLogoutRedirectUri` trong mã đã bỏ sót). Chỉ client dev `postman-dev` chưa có; bước đầu tiên của lần thử dùng trang `http://localhost:8000/web/` nên bị `invalid_request` là đúng.
- **Quyết định:** giữ trang đích của `storefront-web-bff` ở V4 (khớp `FRONTEND_URL` mặc định của BFF); `RegisteredClientSeeder` đăng ký thêm trang đích cho `postman-dev` (trùng redirect URI của Postman). Spring so khớp chính xác theo tập, không mẫu. Client dev đã tạo từ trước thì không được cập nhật (seed chỉ tạo khi chưa có).
- **Test:** KT-09 (`an_unregistered_landing_page_or_a_missing_hint_is_refused_and_the_session_survives`; trang đích đăng ký hợp lệ được dùng ở mọi test đăng xuất đầu-cuối).

## T4. Hint đã mất bản ghi vẫn phải về trạng thái "đã đăng xuất" (R4) · chốt `2026-10-17`

- **Phát hiện:** đăng xuất thành công xóa bản ghi ủy quyền, nên lần gọi thứ hai (bấm đúp, thử lại, BFF gọi lại) có hint không còn bản ghi và nhận 400, trái INV-ATH-05.
- **Quyết định:** dùng điểm mở rộng `logoutEndpoint(...).errorResponseHandler` (không bọc provider): mọi lỗi `invalid_token` (hint không tra được, `sid` sai, `sub` sai) thì **không hủy session nào** (tránh để trang ngoài đăng xuất người khác bằng hint rác) và chuyển về trang mặc định (`/`) thay vì trả 400. Lỗi `invalid_request` (trang đích lạ, client không khớp) vẫn từ chối, không chuyển hướng (tránh chuyển hướng mở).
  Người dùng thật ở trạng thái này đã đăng xuất (bản ghi mất kéo theo phiên mất), nên chuyển về trang mặc định là đúng.
- **Loại:** hủy session khi hint sai (mở đường đăng xuất ngầm); chuyển hướng tới trang đích bất kỳ trong lỗi (chuyển hướng mở).
- **Đã cài `2026-10-17`:** `OidcLogoutFailureHandler` đăng ký qua `logoutEndpoint(...).errorResponseHandler(...)`. Lưu ý: Javadoc của Spring ghi phương thức này "restrict for internal use only" dù là phương thức công khai của bộ cấu hình; nếu sau này bị đổi thì cần bọc provider thay thế.
- **Test:** KT-14, KT-10 ở `a_second_logout_with_the_same_hint_goes_home_without_an_error` và `a_forged_or_garbage_hint_goes_home_and_does_not_touch_the_session` (Location phải bằng `/` của chính dịch vụ; so với "localhost:4200" từng làm test lúc đỏ lúc xanh vì cổng ngẫu nhiên có thể bắt đầu bằng 4200).

## T5. Không cookie đăng nhập (R5)

- **Quan sát:** hint hợp lệ mà không có cookie thì Spring cho qua nhánh người dùng chưa xác thực và bộ xử lý hiện tại chuyển về `/`; không lỗi, không trang xác nhận.
- **Quyết định (chốt tạm `2026-10-17`):** không có cookie thì `EndLoginSession` không kết thúc gì (hint không chứng minh người gọi đang cầm phiên); bộ xử lý vẫn chuyển hướng về trang đích (nếu có và đã kiểm) hoặc `/`. Còn phải thử ca có `post_logout_redirect_uri` khi không có cookie, và nếu muốn kết thúc theo hint khi mất cookie thì đổi quyết định này (đánh đổi: ai cầm ID token cũ, kể cả hết hạn, kết thúc được đúng phiên đó; được: không để sót mã làm mới khi cookie đã mất).
- **Test:** KT-03.

## T6. Log và mã ủy quyền đã cấp (R10, R8, R9)

- `id_token_hint` không vào log của dịch vụ này (chỉ tên tham số trong thông báo lỗi). Log của cổng và BFF kiểm ở feature 06.
- Đổi mã sau khi phiên bị xóa bị từ chối (`invalid_grant`) nên không cấp token cho phiên đã kết thúc; bản ghi ủy quyền chưa đổi còn sót tới khi job dọn (hoãn, `02-login-session/deferred.md`).
- **Còn chưa thử:** R7 (đăng xuất đua với làm mới hoặc đổi mã) cần use case kết thúc phiên của bước 3, 4.

## T7. Hợp đồng báo ứng dụng khi phiên kết thúc (KT-07) · chốt `2026-10-17`

- **Lời gọi:** `POST /bff/internal/sessions/revoke` tới storefront-web-bff, nội bộ (không qua api-gateway), thân JSON **chỉ có `sid`** (mã phiên ứng dụng, đúng giá trị claim `sid` trong token của phiên đó):
  `{"sid": "<mã phiên ứng dụng>"}`. Một lời gọi cho mỗi phiên ứng dụng của client `storefront-web` vừa kết thúc; phiên của client khác chưa có BFF để báo ở C0.
- **Xác thực:** token của service account `oauth2-service-internal` (scope `storefront.internal`), mint mới mỗi lần bằng `client_credentials`; bên nhận kiểm qua JWKS có sẵn.
- **Thời điểm:** sau khi giao dịch đăng xuất đã commit (phiên và bản ghi ủy quyền đã mất khỏi cơ sở dữ liệu), trước khi chuyển hướng. Đã kiểm bằng test đọc cơ sở dữ liệu ngay lúc bên báo được gọi.
- **Lỗi:** không bao giờ ném ra ngoài: không mint được token, storefront-web-bff lỗi hay không với tới được đều chỉ ghi log (mức INFO ở bên gọi HTTP) và đăng xuất vẫn thành công; lỗi còn lại ở bộ xử lý đăng xuất ghi một cảnh báo. Không thử lại ở C0; mã làm mới đã bị xóa và mã truy cập hết hạn ngắn (15 phút, cộng độ lệch 5 giây) là lưới an toàn.
- **Bên nhận (feature 06), việc đã giao:** thân đổi từ `ossId` sang `sid`; khóa Redis và bản ghi phiên của BFF phải tra theo `sid` (claim `sid` của token, thay `oss_id` đã không còn trong token), kể cả bước dọn khi đăng xuất phía BFF (`WebGatewayLogoutSuccessHandler.extractOssId` hiện đọc `oss_id` nên trả `null`). Xóa lặp phải idempotent (đã có: không có ánh xạ thì trả 200).
- **Hướng sau (không làm ở C0):** Back-Channel Logout chuẩn OIDC (`logout_token`) thay cho lời gọi tự chế; mỗi phiên ứng dụng một `logout_token` mang `sid` này (`deferred.md` khi làm).
- **Test:** `WebGatewayRevocationClientTest` (4: thân chỉ có `sid`, token mới, các lỗi không ném), `WebGatewayEndedSessionNotifierTest` (3), và đầu-cuối `the_applications_are_told_only_after_the_transaction_has_committed`, `a_failure_telling_the_applications_does_not_fail_the_logout_and_leaves_one_warning`, cộng kiểm `notifyEnded` đúng một lần với đúng `sid` ở test đăng xuất đầu-cuối. Đã phá thử đổi khóa về `ossId` và bỏ lọc client: test đỏ.

## T8. `auth_time` (chốt `2026-10-17`)

- Với sổ phiên rỗng (T2) Spring không phát `auth_time` trong ID token. Đã rà mã: không có nơi nào của ta (dịch vụ, BFF) đọc `auth_time` hay dùng `max_age`, nên **không phát `auth_time` ở C0**.
- Khi có nơi cần, ta tự đặt trong bộ tùy biến token bằng `login_sessions.issued_at` (đúng thời điểm nhập mật khẩu), qua ràng buộc phiên đã có.
- `FACTOR_PASSWORD` mà bộ xác thực thêm vào (feature 02, T7.2) không còn cần để cấp ID token vì nhánh dùng nó nằm sau điều kiện sổ phiên; giữ lại vì rẻ và dùng được cho các yếu tố xác thực về sau.

## T9. Khóa hàng phiên chống hồi sinh (R7) · chốt `2026-10-17`

- **Phát hiện (đã thử):** `LoginSessionRepositoryAdapter.save` ghi bằng `INSERT ... ON CONFLICT (id) DO UPDATE`, nên nếu hàng đã bị xóa thì lưu lại sẽ **chèn mới**. Một yêu cầu cấp phiên ứng dụng nạp phiên, một đăng xuất xóa phiên và commit, rồi yêu cầu cấp ghi lại: phiên và phiên ứng dụng sống lại sau khi người dùng đã đăng xuất.
  Test KT-15 (`a_logout_racing_with_an_issue_never_leaves_a_resurrected_session`) viết trước, đỏ với "login session resurrected".
- **Quyết định:** hai cổng mới ở kho phiên, `findByIdForUpdate` và `findActiveByIdpSessionIdForUpdate`: chạy `SELECT id ... FOR UPDATE` (khóa hàng tới hết giao dịch của bên gọi), rồi nạp phiên bằng truy vấn đọc thường. Dùng ở nơi sắp ghi lên phiên: `RecordClientSessionIssued` (điểm móc lúc cấp token) và `EndLoginSession`. Hai bên nối tiếp theo từng phiên: bên đến sau chờ, rồi đọc trạng thái đã commit; phiên đã bị xóa thì rỗng và bên cấp thất bại bằng `sessionExpired`.
  Phải gọi trong giao dịch (cả hai nơi dùng đều đã trong giao dịch). Khóa chỉ trên một hàng nên không chặn phiên khác.
- **Đã xác nhận bằng phá thử:** bỏ `FOR UPDATE` thì KT-15 và KT-08 (đăng xuất đồng thời, mong đúng một bên kết thúc) cùng đỏ.
- **Loại:**
  - khóa lạc quan (cột `version`, ghi kèm `WHERE version = ?`): cần migration, mapper và miền biết `version`;
  - tách `save` thành chèn mới và cập nhật (cập nhật mà 0 dòng thì lỗi): đổi đường ghi, dễ sót nhánh;
  - khóa luôn ở `findById`: vỡ khi gọi ngoài giao dịch (cửa kiểm hạn ở `/oauth2/authorize` đọc không giao dịch).
- **Không khóa:** `ResolveAuthorizationSession` (chỉ đọc), `OpenLoginSession` (tạo phiên mới, chưa có gì để tranh).

## T10. Điều rút ra khi chạy thật bước 8 (`2026-10-17`)

- **`client_sessions.client_id` giữ mã nội bộ của client đã đăng ký** (`RegisteredClient.getId()`, ví dụ `storefront-web-client-id`), không phải `client_id` công khai (`storefront-web-bff`), vì điểm móc lúc cấp token chỉ có `OAuth2Authorization.getRegisteredClientId()`. Bộ báo BFF từng so thẳng chữ `storefront-web-bff` với cột này nên không bao giờ khớp: test đơn vị (giả định tên công khai) và test đầu-cuối (giả ở bộ báo) đều xanh, chỉ lần chạy thật mới thấy log không có dòng báo. Đã sửa bằng tra client qua `RegisteredClientRepository` (T7); test đầu-cuối nay giả ở lời gọi HTTP để bộ báo thật chạy.
  Hệ quả còn mở: sự kiện `ClientSessionIssued` ("ứng dụng") cũng mang mã nội bộ này; khi Định danh cần tên ứng dụng (C3c) thì ghi `client_id` công khai thay vào, hoặc tra lại lúc phát.
- **Mỗi lần báo BFF để lại một hàng `oauth2_authorization`:** token service account được mint mới mỗi lần (`ClientCredentialsTokenClient`, cố ý không cache), và Spring lưu một bản ghi `client_credentials` cho mỗi token; 6 lần báo để lại 6 hàng của `oauth2-service-internal`, không hàng nào bị xóa. Tốc độ tăng bằng tốc độ đăng xuất. Hướng sửa (không làm ở C0): cache token tới gần hạn ở bên gọi, hoặc để job dọn bản ghi quá hạn (`02-login-session/deferred.md`) xóa luôn các bản ghi `client_credentials`. Đã dọn tay 6 hàng do lần chạy này tạo ở cơ sở dữ liệu dev.
- **Test cũ chạm cơ sở dữ liệu dev:** `UserAccountApiTest`, `RequestLoggingIntegrationTest`, `UserAccountConcurrencyTest` dùng cơ sở dữ liệu cấu hình sẵn (cục bộ), không phải Testcontainers, nên chạy cả bộ test cũng áp migration lên cơ sở dữ liệu dev: V15 và V16 (bỏ `oauth_sessions`) đã được áp như vậy trước khi chạy thật.

## T11. Cách khai báo bean của nhóm phiên đăng nhập (chốt `2026-10-17`)

- **Quyết định:** năm use case của `application/login_session` (`AuthenticateWithPassword`, `OpenLoginSession`, `ResolveAuthorizationSession`, `RecordClientSessionIssued`, `EndLoginSession`) gắn `@Service`, **không** `@Transactional` (giao dịch nằm ở hạ tầng: `LoginSessionLogout`, `SessionBoundAuthorizationService`, kho phiên). Các lớp hạ tầng chỉ cần bean có sẵn (`LoginSessionLogout`, `NoSessionRegistry`, `NimbusIdTokenHintVerifier`, hai nguồn `sid` và phiên) gắn `@Component`. Xóa `LoginSessionApplicationConfig` (trước đó dựng use case bằng `@Bean`).
- **Giữ một chỗ lắp ghép tay:** `OAuth2AuthorizationServerConfig.auth2AuthorizationService` vì phải dựng bản `JdbcOAuth2AuthorizationService` gốc (bộ ánh xạ Jackson riêng) làm `delegate` cho cả lớp tra lẫn lớp bọc, trong khi lớp bọc cũng là `OAuth2AuthorizationService`.
- **Test:** test đơn vị vẫn dựng bằng `new` (annotation không ảnh hưởng); test tích hợp thay một bean bằng `@MockitoBean` hoặc `@TestConfiguration`. Quy tắc: mọi thành phần mà test có thể muốn thay phải là bean riêng, không phải `new` ẩn trong hàm config.
- **Lý do đổi:** nhất quán với use case của `user_account` (đã dùng `@Service`), bớt một lớp cấu hình và hai hàm bean mới thêm ở feature này.
