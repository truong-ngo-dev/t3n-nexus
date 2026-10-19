# Feature 06: kế hoạch triển khai

Làm theo thứ tự dưới đây. Mỗi bước nêu việc phải làm, ai làm (**Người** viết điểm cần insight và quyết định thiết kế; **Agent** viết phần
thường, người rà), điều cần trao đổi (nếu có), và cách biết bước đã xong. Chọn cách thực hiện khi bắt đầu từng bước.

Quy tắc giống feature 01 đến 04 (bước 3b viết tài liệu service của hai cổng trước khi sửa mã; bước 4b đổi tên BFF theo ADR-0006 làm trước nhóm E): mỗi bước chỉ làm và kiểm đúng phạm vi của nó; test viết từ `flow.md` mục 6 trước khi viết mã (đỏ trước, phá thử sau); quyết định kỹ thuật
đặt ngay trước bước cần dùng nó. Feature này **sửa ba ứng dụng có sẵn** (`api-gateway`, `storefront-web-bff`, `oauth2-service`) chứ không dựng mới, và hai cổng **chưa có hạ tầng test** (chỉ `contextLoads`),
nên có một bước dựng hạ tầng test trước khi sửa. Năm nhóm E, A, B, C, D của `flow.md` mục 1 là các bước thi công riêng, tách được nếu cần. **Nhóm E (tách nguồn gốc theo subdomain, ADR-0006) làm trước**
vì nó đổi đường dẫn, địa chỉ và `issuer` mà các nhóm còn lại dùng; làm sau thì phải sửa lại test và luật.

## Từ luồng đến giải pháp

Mỗi dòng là một bước của luồng (`flow.md` mục 3). Ba cột đầu là phần thiết kế, viết **trước khi code**: hướng giải ngắn (chi tiết ở `tech.md` và ADR, không chép lại ở đây),
rồi các hướng giải gộp thành thành phần nào. Cột cuối điền **sau khi làm xong**
(quy trình: `docs/global/4.convention/work-process.md` mục 3.4).

| Bước của luồng | Hướng giải | Gộp vào thành phần | Triển khai (điền sau khi xong) |
|---|---|---|---|
| Chọn tuyến theo `Host`; host lạ thì 404 | Tuyến theo vị ngữ `Host` đọc từ danh sách cấu hình; không có tiền tố, không `X-Forwarded-Prefix` (`tech.md` T9, ADR-0006) | Cấu hình tuyến ở `api-gateway` | Đã làm `2026-10-18` (bước 5) |
| Đặt `X-Forwarded-Host`, `X-Forwarded-Proto` từ yêu cầu thật | Ghi đè từ yêu cầu, mỗi host một giá trị; bỏ `apigateway.public-host` (`tech.md` T9) | Cùng cấu hình tuyến | Đã làm `2026-10-18` (bước 5) |
| Đường dẫn BFF ở host `shop` tới BFF, còn lại tới giao diện | Hai tuyến cùng host, phân theo tiền tố BFF cố định (`tech.md` T9) | Cấu hình tuyến ở `api-gateway`, tuyến của `storefront-web-bff` bỏ biểu thức kép | Đã làm `2026-10-18` (bước 5) |
| `issuer`, redirect URI, trang đích đăng xuất theo host mới; cookie theo máy | Đổi cấu hình `issuer` và client đã đăng ký; cookie không đặt `Domain` (`tech.md` T9) | `oauth2-service` (cấu hình, `RegisteredClientSeeder`, migration nếu cần), `storefront-web-bff` (cấu hình) | Đã làm `2026-10-18` (bước 5) |
| Luật theo (host, phương thức, đường dẫn), rồi mức chung theo địa chỉ mạng | Danh sách luật nạp từ thuộc tính thành đối tượng bất biến, kiểm hợp lệ lúc khởi động; hai tầng đếm (`tech.md` T1) | Bộ lọc giới hạn tần suất ở `api-gateway` (mở rộng bộ lọc hiện có) + đối tượng chính sách | Đã làm `2026-10-18` (bước 6) |
| 429 có thân lỗi chuẩn và `Retry-After` đúng | Thân JSON chung, `Retry-After` là thời gian còn lại của cửa sổ (`tech.md` T2) | Cùng bộ lọc | Đã làm `2026-10-18` (bước 6) |
| Redis lỗi thì mở cửa, lệnh đếm có thời gian chờ | Thư viện đã mở cửa; thêm thời gian chờ ngắn (`tech.md` T3) | Thư viện `reactive-rate-limiter-starter` | Đã làm `2026-10-18` (bước 6) |
| Địa chỉ mạng tin cậy chuyển xuống dịch vụ sau | Cổng ghi đè tiêu đề, dịch vụ sau chỉ tin tiêu đề từ cổng (`tech.md` T7) | Bộ lọc toàn cục ở `api-gateway` + cấu hình tin tiêu đề ở dịch vụ sau | Đã làm `2026-10-18` (bước 10) |
| Kiểm `X-XSRF-TOKEN` cho yêu cầu ghi tới BFF, 403 có mã ổn định | Bật CSRF của Spring Security cho chuỗi phiên, kho cookie không `HttpOnly`, bộ xử lý lỗi mã `CSRF_TOKEN_INVALID` (`tech.md` T4) | Cấu hình chuỗi bảo mật `storefront-web-bff` | Đã làm `2026-10-18` (bước 9) |
| Mất cookie CSRF thì sinh lại, không đăng xuất | Gia hạn trượt cookie và điểm `GET` nhẹ sinh lại mã (`tech.md` T4) | Cùng cấu hình CSRF | Đã làm `2026-10-18` (bước 9) |
| Làm mới token kèm `sid` | Bộ chuyển đổi tham số của yêu cầu làm mới thêm `sid` từ token hiện có (`tech.md` T5) | Bộ quản lý ủy quyền client của `storefront-web-bff` | Đã làm `2026-10-18` (bước 8) |
| Làm mới bị từ chối thì xóa phiên và ánh xạ | Bắt lỗi làm mới, xóa phiên, 401; gộp làm mới đồng thời nếu bước thử cho thấy cần (`tech.md` T5) | Cùng bộ quản lý + bộ xử lý lỗi | Đã làm `2026-10-18` (bước 8) |
| Đăng nhập xong: ánh xạ hai chiều theo `sid`, đọc `role` | Đổi khóa từ `oss_id` sang `sid`; đọc `role` chuỗi (`tech.md` T6) | `SessionMappingAuthenticationSuccessHandler`, `AuthController` | Đã làm `2026-10-18` (bước 7) |
| Đăng xuất BFF xóa ánh xạ theo `sid`, trả 202 + `Location` | Đọc `sid` từ thuộc tính người dùng OIDC (`tech.md` T6) | `WebGatewayLogoutSuccessHandler` | Đã làm `2026-10-18` (bước 7) |
| Nhận báo đăng xuất `{"sid"}` và xóa phiên | Đổi trường `ossId` thành `sid`, kiểm UUID, giữ lũy đẳng (`tech.md` T6) | `SessionRevokeController` | Đã làm `2026-10-18` (bước 7) |
| Ghi log hoàn tất mỗi yêu cầu, mã truy vết xuyên suốt | Bộ lọc log cho WebFlux dùng lõi của feature 07 (`tech.md` T7) | Bộ lọc log ở `api-gateway` (và `storefront-web-bff`) | Đã làm `2026-10-18` (bước 10) |

## Điều kiện bắt đầu

Những điều thuộc **nghiệp vụ** đã chốt, không bàn lại: mô hình hai tầng phiên và hợp đồng danh tính (ADR-0003), cách bảo vệ yêu cầu từ trình duyệt (ADR-0005), cấu trúc nguồn gốc theo subdomain (ADR-0006, Proposed, chuyển Accepted sau khi chạy thật),
mô hình giới hạn tần suất hai nhóm (`rate-limiting-layers.md`), hợp đồng báo đăng xuất `{"sid"}` (feature 03, `03-logout/tech.md` T7). Còn lại là kỹ thuật cài đặt ở hai cổng và Xác thực.

Feature 01 đến 04 xong (Xác thực đã chạy trên mô hình mới). Cần chạy được Redis, Postgres của Xác thực và ba dịch vụ cục bộ (`api-gateway`, `storefront-web-bff`, `oauth2-service`) cho bước thử và bước chạy thật. Feature 05 (giao diện) chưa cần: gọi bằng curl có cookie, máy chủ giao diện là địa chỉ cấu hình (giả khi test).

## Các bước

### Bước 1. Rà kịch bản test · Người

Đọc `flow.md` mục 4 và 6: mỗi rủi ro R1 đến R33 đã có kịch bản test hoặc lý do không cần chưa; mỗi nhóm E, A đến D có test mức cổng; KT-21, 22, 24, 25, 31 là ca cần ba dịch vụ thật (chạy ở bước "Chạy thật" và bằng script).

**Trạng thái:** Đã rà `2026-10-17`. Bốn kịch bản thêm: KT-33 (R5, yêu cầu bị chặn không tính vào mức chung; chờ T1), KT-34 (R8, gia hạn trượt cookie CSRF; chờ T4), KT-35 (CORS sau khi cùng nguồn gốc; chờ T9), KT-36 (điểm vào BFF và `contextPath` sau khi bỏ tiền tố); KT-06 thêm thân 429 của mức chung. R6 chấp nhận không test. KT-21, 22, 24, 25, 31 chạy ở bước 11, không nằm trong Maven.

Xong khi: người rà đồng ý danh sách KT-01 đến KT-41 (KT-37 bỏ: R29 ghi nợ); thêm hoặc bớt thì sửa `flow.md` mục 6.

### Bước 2. Thử hiện trạng của BFF với Xác thực mới · Người rồi Agent

Chạy ba dịch vụ như đang có (một nguồn gốc `localhost:8000`) và quan sát, **chưa sửa mã** (giống bước 1 của feature 03): (1) đăng nhập qua BFF có hoàn tất không (PKCE, `state`, đổi mã); (2) `/bff/auth/session` trả gì (kỳ vọng `role = null`); (3) ánh xạ có được tạo không (kỳ vọng không, `oss_id` không còn); (4) làm mới token không `sid` bị Xác thực từ chối thế nào; (5) hai yêu cầu đồng thời gặp token hết hạn làm gì với mã làm mới (R11); (6) đăng xuất qua BFF và báo `{"sid"}` từ Xác thực tới BFF trả gì. **Thêm cho nhóm E:** (7) với `auth.localhost`, `shop.localhost` trỏ về máy (kiểm phân giải, IPv6 `::1`): trình duyệt hoặc curl đi hết luồng đăng nhập chéo host có hoàn tất không, cookie có đi theo (R25, R26); (8) dịch vụ nào nghe được trên IPv6. Ghi cơ sở dữ liệu dev: báo trước và xóa dữ liệu thử sau.

**Trạng thái:** Đã thử `2026-10-17` (ba dịch vụ cục bộ, curl có cookie, một thí nghiệm Chrome; chưa sửa mã); kết quả ở `tech.md` mục "Kết quả thử". Tóm tắt: R16, R12 xác nhận (BFF trả `role = null`, không ánh xạ, làm mới không `sid` thì 500 rồi 302, báo `{"sid"}` nhận 200 và không xóa gì); R11 là thật và Xác thực cũng có phần (hai làm mới đồng thời cùng một mã làm mới cùng thành công, chỉ một mã mới dùng được; thêm R29, KT-37); R17: BFF gửi PKCE S256 và `state`; R25: Chrome coi hai host `*.localhost` là hai site (cookie Lax không theo `fetch` chéo host, có theo điều hướng cấp cao), khớp ADR-0006; R26: ba dịch vụ nghe `[::]`, không vấn đề; R21: `Host` lạ hiện được chấp nhận. Chưa thử: Firefox, luồng đăng nhập đầy đủ qua hai host, POST biểu mẫu chéo host (làm ở bước 5 và 11). Đã dọn: tài khoản, phiên, bản ghi ủy quyền thử trong cơ sở dữ liệu dev; ba dịch vụ và máy chủ thử đã dừng.

Xong khi: bảng "Kết quả thử" ở `tech.md` điền đủ tám ca; R11, R17, R25, R26 có kết luận; các rủi ro mới (nếu có) thêm vào `flow.md` mục 4. Nếu R25 cho thấy luồng chéo host hỏng thì dừng, bàn lại ADR-0006 trước khi làm tiếp.

### Bước 3. Chốt giải pháp kỹ thuật (`tech.md`) · Người rồi Agent

Mỗi lựa chọn có phương án đã loại, dựa vào kết quả bước 2:
- **T1** mô hình luật theo (host, đường dẫn) và cách đếm hai tầng (thứ tự, yêu cầu bị chặn có tính vào mức chung không; R5).
- **T2** thân 429 và `Retry-After` đúng thời gian còn lại (R7).
- **T3** thời gian chờ cho lệnh đếm ở thư viện dùng chung (R1).
- **T4** CSRF ở BFF: cookie, gia hạn trượt, mã lỗi, điểm sinh lại mã (ADR-0005 A và C).
- **T5** làm mới kèm `sid` và xử lý từ chối, làm mới đồng thời (R11, R12).
- **T6** ánh xạ phiên theo `sid` (đổi tên khóa Redis, dọn khóa cũ), `role`, đăng xuất, nhận báo.
- **T7** địa chỉ tin cậy và bộ lọc log cho cổng (R19, R20).
- **T8** hạ tầng test cho hai cổng.
- **T9** nguồn gốc theo subdomain (ADR-0006): danh sách host và miền gốc đọc từ cấu hình, tiêu đề chuyển tiếp, danh sách tiền tố BFF (R28), cách đổi `issuer` và client đã đăng ký (seeder hay migration mới), IPv6 (R26), tên và thuộc tính cookie.

**Trạng thái:** Đã viết `2026-10-17`: `tech.md` T1 đến T9, đã sửa T5, T6, T7 sau rà soát cùng ngày. **Đã chốt (người):** T1 quy tắc đếm hai tầng (mỗi tầng chỉ đếm yêu cầu nó cho qua, luật theo đường dẫn trước, mức chung sau); T5 và T6 cách A (hạn phiên BFF là hằng số bằng TTL refresh, đặt lại cùng hạn ánh xạ mỗi lần lưu token mới; cách B, Xác thực trả hạn refresh token, hoãn); CORS phương án 2 (T9); R29 ghi nợ `deferred.md`. **Sửa so với bản đầu:** T5 thêm ba lớp lỗi (chỉ `invalid_grant` xóa phiên rồi SSO, lỗi khác trả 503 giữ phiên), lệch đồng hồ BFF 5 giây; T6 thêm vòng đời ánh xạ và bốn quy tắc, sửa câu sai "khóa cũ tự hết hạn" (thực tế `app.session.mapping-ttl-seconds` không có mã nào dùng); T7 giữ `framework`, dùng tiêu đề riêng `X-Client-Ip` thay vì đổi sang `native`. Thêm KT-39 (đặt lại hạn phiên và ánh xạ khi lưu token mới), KT-40 (lỗi tạm thời giữ phiên); KT-37 bỏ. **Đề xuất, chờ người chốt:** T4 (xoay mã CSRF ở callback; `GET /bff/auth/session` làm điểm phát lại mã; cookie CSRF là cookie phiên của trình duyệt nên không gia hạn trượt), T2, T3, T8, T9 phần còn lại. Điểm chưa kiểm trong repo ghi ở từng mục (cách WebFlux phát cookie CSRF, cách `TokenRelay` lấy bộ quản lý ủy quyền, thuộc tính `sid` ở `OidcUser`, `contextPath` ở bộ chuyển hướng, địa chỉ từ xa ở WebFlux).

Xong khi: `tech.md` có T1 đến T9; mỗi hướng giải ở bảng đầu tệp này trỏ tới đúng mục.

### Bước 3b. Tài liệu service của hai cổng · Agent, Người rà

Viết thiết kế **đích** của hai cổng ở `docs/service/` trước khi sửa mã (quy ước: tạo tài liệu trước khi code), theo khuôn `service.md` của `doc-structure.md`, nhiều mục ghi "N/A" vì không có nghiệp vụ nên **không có `analysis.md`**:
- `docs/service/storefront-web-bff/`: `service.md`, `data.md` (khóa Redis), `api.yaml`.
- `docs/service/api-gateway/`: `service.md` (bảng tuyến ở mục 7.3, khóa giới hạn ở mục 12.1); không có `data.md`, `api.yaml`.
- Phần tuyến và danh sách công khai của dịch vụ nghiệp vụ cũ (`identity`, `customer`, `catalog`, `order`) ghi là **hiện có, sửa khi làm feature của BC tương ứng** (`TD-05` của BFF, `TD-04` của cổng).
- Mục "Lệch giữa thiết kế và code" (§16) ghi "Chưa rà code"; điền ở bước 12 khi rà mã thật. Tên đường dẫn, cookie, khóa Redis theo hiện tại, sửa ở bước 4b nếu đổi.

**Trạng thái:** Đã viết `2026-10-17`: hai `service.md`, `data.md` và `api.yaml` của BFF (đã kiểm `api.yaml` đọc được bằng SnakeYAML, 8 đường dẫn). Các quy tắc kỹ thuật `Q-01` đến `Q-08` trỏ về KT của `flow.md`; nợ `TD-*` ghi các việc hoãn và các tuyến sẽ sửa sau.

Xong khi: hai bộ tài liệu có đủ mục khuôn mẫu (N/A kèm lý do), mọi `Q-`, `C-` có kịch bản test hoặc ghi chưa có, tài liệu không mâu thuẫn với `tech.md`.

### Bước 4. Hạ tầng test cho hai cổng · Agent, Người rà

Thêm vào `api-gateway` và `storefront-web-bff`: Redis của Testcontainers dùng chung, máy chủ HTTP giả cho Xác thực, máy chủ giao diện và dịch vụ sau, lớp nền dựng ứng dụng thật với `WebTestClient`. Một test mẫu chứng minh bộ khung chạy (ví dụ yêu cầu qua cổng tới máy chủ giả).

**Trạng thái:** Đã làm `2026-10-17`. Mỗi cổng có bộ khung ở `src/test/.../support/`: `RedisContainerHolder` (Redis thật `redis:7-alpine` bằng Testcontainers, một thể hiện cho cả bộ test, `flush()` mỗi test), `FakeHttpServer` (máy chủ HTTP giả bằng `HttpServer` của JDK, ghi lại yêu cầu nhận được, trả phản hồi đã đăng ký theo phương thức và đường dẫn), `GatewayTestBase` (`@SpringBootTest` cổng ngẫu nhiên, thuộc tính tuyến trỏ vào máy chủ giả, `WebTestClient.bindToServer`). `api-gateway` có `GatewayFrameworkTest` (2 test: tuyến `/auth` bỏ tiền tố và thêm `X-Forwarded-Prefix`; mức chung theo địa chỉ dùng Redis thật, 429 kèm `Retry-After`, yêu cầu bị chặn không tới dịch vụ sau). `storefront-web-bff` có `GatewayFrameworkTest` (3 test: thăm dò phiên 401; tuyến công khai identity viết lại đường dẫn; chưa đăng nhập: JSON 401, HTML chuyển hướng tới `/oauth2/authorization/storefront-web`) và `WebGatewayApplicationTests` (ngữ cảnh dựng được, giờ dùng Redis của Testcontainers thay Redis cục bộ). 6 test xanh. Phá thử: viết lại đường dẫn identity sai làm đúng test tuyến đỏ; bộ lọc IP cho qua mọi yêu cầu làm đúng test 429 đỏ; mã nguồn đã khôi phục (so byte). Ghi nhận: thêm `org.testcontainers:testcontainers` (phạm vi test, phiên bản theo BOM 2.0.5) vào hai `pom.xml`; không thêm WireMock hay MockWebServer (kho cục bộ không có); `@AutoConfigureWebTestClient` của Boot 4 ở module không có trong kho nên `WebTestClient` dựng bằng `bindToServer`; hai bộ `support` giống nhau (copy, đổi gói), có thể gom thành thư viện test chung nếu thêm cổng thứ ba. Các test ở đây mô tả hành vi **hiện có**, sẽ được viết lại ở nhóm E, A, B, C, D.

Xong khi: hai bộ khung chạy xanh trong Maven; không test nào chạm Redis, Postgres hay dịch vụ cục bộ của người dùng.

### Bước 4b. Đổi tên BFF theo mẫu `<ứng dụng>-<kênh>-bff` · Người rồi Agent, Người rà

Đổi **cơ học**, không đổi hành vi, ngay trước nhóm E (vì nhóm E đã đổi redirect URI, `issuer` và client nên đổi tên cùng lúc rẻ hơn đổi sau). Bộ khung test ở bước 4 chạy xanh **trước** và **sau** đổi tên, nên là bằng chứng chỉ đổi tên. Đầu bước, người chốt các tên chưa chốt (đề xuất trong ngoặc):
- tiền tố đường dẫn `/webgw/**` (`/bff/**`), cookie phiên `WEBGW_SESSION` (`STOREFRONT_SESSION`; chỉ BFF kênh web có cookie nên không cần `-web`), tiền tố Redis `webgw:` (`storefront:`), phạm vi token `webgw.internal` (`storefront.internal`), tên gói Java (`vn.t3nexus.storefrontwebbff`).

Việc phải đổi:
- **BFF:** thư mục và module `services/web-gateway` thành `services/storefront-web-bff`, `artifactId`, gói Java, tên lớp có `WebGateway`, thuộc tính `webgateway.routes.*` và `app.webgateway.base-url`.
- **Client OAuth2:** mã công khai `web-gateway` thành `storefront-web`, mã nội bộ `web-gateway-client-id` thành `storefront-web-client-id`, biến `WEB_GATEWAY_CLIENT_ID` thành `STOREFRONT_WEB_CLIENT_ID`; registration id trong `/oauth2/authorization/<id>` và `/login/oauth2/code/<id>`. Seeder `RegisteredClientSeeder`, và một **migration mới** để đổi hoặc xóa dòng client cũ cùng dữ liệu tham chiếu (`client_sessions.client_id`, `oauth2_authorization`), vì **không được sửa migration V4, V9, V10 đã chạy** (Flyway so tổng kiểm). Phạm vi `webgw.internal` ở client `oauth2-service-internal` (V10) cũng đổi qua seeder hoặc migration mới. Dữ liệu cũ chỉ có ở dev nên được phép xóa; báo trước vì động tới cơ sở dữ liệu dev.
- **`oauth2-service`:** `WebGatewayRevocationClient`, `WebGatewayEndedSessionNotifier` (tên và kiểm tra client công khai `web-gateway`), test liên quan, `api.yaml` nếu nhắc tên.
- **Tài liệu:** các tài liệu của feature 06, ADR-0006, và `rate-limiting-layers.md`; các tài liệu cũ còn nhắc `web-gateway` ở phần mô hình đã gỡ (`spring-security-logout-bff.md`...) xử lý như đã ghi ở `flow.md` mục 7.

**Trạng thái:** Đã làm `2026-10-17`. **Tên đã chốt (người, theo đề xuất):** module và thư mục `services/storefront-web-bff` (`artifactId`, `spring.application.name`), gói `vn.t3nexus.storefrontwebbff`, lớp `StorefrontWebBff*`, đường dẫn `/bff/**`, cookie `STOREFRONT_SESSION`, tiền tố Redis `storefront:`, phạm vi token `storefront.internal`, client OAuth2 `storefront-web` (mã nội bộ `storefront-web-client-id`), biến `STOREFRONT_WEB_CLIENT_ID`, `STOREFRONT_WEB_CLIENT_SECRET`, `STOREFRONT_WEB_REQUIRE_PKCE`, thuộc tính `app.clients.storefront-web.*`, `storefront.routes.*`, `app.storefront-web-bff.base-url`, `apigateway.routes.storefront-web-bff.uri`. **Đã đổi:** mã và cấu hình của `storefront-web-bff`, `api-gateway`, `oauth2-service` (lớp `StorefrontWebBffRevocationClient`, `StorefrontWebBffEndedSessionNotifier`, `RegisteredClientSeeder`, test), `services/pom.xml`, chú thích ở các thư viện và dịch vụ khác, `testdata/*.http` (phạm vi `storefront.internal`), và một dòng `web/src/environments/environment.ts` (đường dẫn BFF `/web/bff/auth`; tên khóa `webgw` của Angular và phần giao diện còn lại thuộc feature 05). **Migration mới `V17__rename_web_gateway_client_to_storefront_web.sql`:** đổi tại chỗ dòng client (`id`, `client_id`, tên, redirect URI), các cột tham chiếu mã nội bộ (`client_sessions.client_id`, `oauth2_authorization.registered_client_id`, `oauth2_authorization_consent.registered_client_id`) và phạm vi của `oauth2-service-internal`; không sửa V4, V9, V10, V11. Đã áp lên cơ sở dữ liệu dev (3 dòng client đổi tên, không phiên nào bị mất vì cơ sở dữ liệu dev đang trống phiên). **Kiểm chứng:** bộ test **trước** đổi tên: `oauth2-service` 335, `storefront-web-bff` (lúc đó `web-gateway`) 4, `api-gateway` 2; **sau** đổi tên: 335, 4, 2, cùng xanh (V17 chạy từ trống trên Postgres của test và các test đăng nhập đầu-cuối dùng client `storefront-web`). Chạy thật ba dịch vụ (một nguồn gốc như trước nhóm E): đăng nhập qua BFF hoàn tất với `client_id=storefront-web`, callback `/web/login/oauth2/code/storefront-web`, `/web/bff/auth/session` 200, đăng xuất qua BFF 202, Xác thực kết thúc phiên và báo BFF bằng token phạm vi `storefront.internal` được nhận (BFF vẫn log `ossId=null` như cũ, chờ nhóm D); hành vi y hệt trước đổi tên. Đã dọn dữ liệu thử trong cơ sở dữ liệu dev (0 tài khoản, 0 phiên, 0 bản ghi ủy quyền). **Chưa đổi (có chủ ý):** các tài liệu mô tả mô hình cũ ở `docs/feature/` (thiết kế cũ, đã đánh dấu lỗi thời) và ADR-0006 mục Bối cảnh (nhắc tên cũ để mô tả hiện trạng lúc viết).

Xong khi: bộ test của `oauth2-service` và hai cổng xanh trước và sau với cùng số test; `grep` không còn `web-gateway`, `webgateway`, `WEB_GATEWAY`, `webgw` trong mã và cấu hình (trừ migration cũ không sửa); ba dịch vụ khởi động và đăng nhập qua BFF được (curl có cookie, một nguồn gốc như trước nhóm E); không còn dòng client cũ trong cơ sở dữ liệu dev.

### Bước 5. Nhóm E: tách nguồn gốc theo subdomain · Agent, Người rà

Viết KT-26 đến KT-30, KT-32, KT-35, KT-36 (đỏ trước), rồi tuyến theo `Host`, danh sách host, tiêu đề chuyển tiếp, bỏ tiền tố và biểu thức kép, đổi `issuer` và client đã đăng ký, cookie theo máy (T9). Sửa test của Xác thực đang dùng `localhost:8000/auth` và `api.yaml` (`servers`). KT-31 chạy ở bước "Chạy thật". Chuyển ADR-0006 sang Accepted ở bước 12 sau khi KT-31 qua.

**Trạng thái:** Đã làm `2026-10-18`. Test viết đỏ trước rồi mới viết mã; phá thử từng phần làm đúng test tương ứng đỏ (cổng: bộ lọc không xóa tiêu đề, so khớp host luôn đúng, CORS cho mọi nguồn gốc; BFF: `AuthController` ghép lại `contextPath`, bộ chuyển hướng ghép `contextPath`, `authorization-uri` về địa chỉ cũ; Xác thực: seeder không đặt lại URI, CORS danh sách cố định, `issuer` về cũ), mã nguồn đã khôi phục (so byte). **Test mới:** `api-gateway` `HostRoutingTest` (KT-26, 5 test), `ForwardedHeadersTest` (KT-27, 2), `GatewayCorsTest` (KT-35, 2), `GatewayFrameworkTest` viết lại theo host; `storefront-web-bff` `OriginsTest` (KT-28, 30, 2), `EntryPointRedirectTest` (KT-36, 3), `BffCorsTest` (KT-35, 2); `oauth2-service` thêm 4 test vào `LoginFlowIntegrationTest` (KT-29, 30, 32, 35). **Tổng sau bước:** `oauth2-service` 339 (trước 335), `storefront-web-bff` 11 (trước 4), `api-gateway` 10 (trước 2), tất cả xanh. **Chạy thật** ba dịch vụ qua `auth.localhost:8000` và `shop.localhost:8000` (KT-31): bằng curl có kho cookie theo host (đăng nhập qua host `auth`, callback về host `shop`, `/bff/auth/session` 200, cookie `OAUTH2_SESSION`, `DEVICE_KEY`, `XSRF-TOKEN` chỉ ở `auth`, `STOREFRONT_SESSION` chỉ ở `shop`, đăng xuất 202 rồi 302 về `http://shop.localhost:8000/`, phiên Xác thực về 0) **và trong Chrome thật** (đăng nhập qua biểu mẫu ở `auth`, hạ cánh `http://shop.localhost:8000/customer` (trang do máy chủ ở cổng 4200 phục vụ qua cổng; lúc thử cổng 4200 là một ứng dụng khác, "Dynamic Case Management", không phải giao diện t3n-nexus, nên lúc đó **giao diện t3n-nexus chưa được chạy**; đã chạy sau đó, xem đoạn "Chạy giao diện thật" cuối trạng thái này), thăm dò phiên 200, đăng xuất về `http://shop.localhost:8000/`, thăm dò phiên 401): R25 và R26 đã kiểm với Chrome trên Windows; Firefox chưa thử. Host ngoài danh sách (`localhost:8000`) và `ops.localhost` nhận 404. **Cơ sở dữ liệu dev:** seeder đặt lại URI của client `storefront-web` (redirect URI `http://shop.localhost:8000/login/oauth2/code/storefront-web`, trang đích đăng xuất `http://shop.localhost:8000/`; URI cũ bị gỡ); đã dọn tài khoản và phiên thử (0 tài khoản, 0 phiên, 0 bản ghi ủy quyền). Chi tiết thay đổi và lý do: `tech.md` mục "Ghi nhận khi làm bước 5". **Chạy giao diện thật** (`nx serve app` cổng 4201, `SHOP_FRONTEND_URL=http://localhost:4201`, mở `http://shop.localhost:8000` trong Chrome): phát hiện và **đã sửa** mức chung 300 mỗi 60 giây chặn chính giao diện (429 ở `main.js`; tuyến giao diện nay không bị đếm, test `FrontendRateLimitExemptionTest`, `api-gateway` lên 11 test); phát hiện WebSocket tự tải lại của `ng serve` hỏng qua cổng gây vòng lặp tải lại (cách tránh `--live-reload=false --hmr=false`, nguyên nhân gốc chưa tìm ra, ghi `TD-06` của cổng); luồng đăng nhập bằng nút của giao diện chạy hết (sang `auth`, nhập mật khẩu, về `shop`, `/bff/auth/session` 200) nhưng giao diện dừng ở `/login` vì `role = null` (R16, nhóm D) và vì giao diện dùng vai `CUSTOMER` còn Xác thực cấp `USER`. Đã dọn dữ liệu thử (0 tài khoản, 0 phiên).

Xong khi: KT-26 đến KT-30, KT-32, KT-35, KT-36 xanh; bộ test của Xác thực xanh với `issuer` mới; không còn chuỗi `/auth/`, `/web/` hay `X-Forwarded-Prefix` trong cấu hình cổng; phá thử (đặt lại tiền tố, bỏ kiểm host, đặt `Domain` cho cookie, nhận `X-Forwarded-Host` của client) làm đúng test tương ứng đỏ.

### Bước 6. Nhóm A: giới hạn tần suất theo đường dẫn · Agent, Người rà

Viết KT-01 đến KT-07 và KT-33 (đỏ trước), rồi đối tượng chính sách, bộ lọc hai tầng, thân 429, thời gian chờ lệnh đếm (T1 đến T3), thuộc tính luật đầu `POST /register` trên host `auth`. Cập nhật `rate-limiting-layers.md` mục 8 khi xong.

**Trạng thái:** Đã làm `2026-10-18`. Test viết đỏ trước rồi mới viết mã. **Test mới:** thư viện 7; `api-gateway` 15 (tổng 26); `storefront-web-bff` `UserRateLimitFilterTest` 3 (tổng 14). Hồi quy ba mô-đun xanh. Phá thử bỏ kiểm phương thức, host, chuẩn hóa `/` cuối làm test tương ứng đỏ (3, 2, 1 test), mã đã khôi phục; `X-Forwarded-For` và thời gian chờ không phá thử lại (test xanh). Phát hiện: `.timeout` không đủ vì Lettuce có thể chặn luồng gọi, phải `subscribeOn(boundedElastic)`; danh sách thuộc tính Boot không gộp giữa các nguồn. Sửa thêm lỗi chạy chuỗi lọc hai lần ở `UserRateLimitFilter` của BFF. Chưa chạy thật qua curl/Chrome (gộp ở bước 11). Chi tiết và lý do: `tech.md` mục "Ghi nhận khi làm bước 6".

Xong khi: KT-01 đến KT-07 và KT-33 xanh; phá thử (bỏ kiểm phương thức, bỏ kiểm host, bỏ chuẩn hóa dấu `/` cuối, đọc `X-Forwarded-For`, bỏ thời gian chờ) làm đúng test tương ứng đỏ.

### Bước 7. Nhóm D: BFF theo hợp đồng danh tính mới · Agent, Người rà

Viết KT-16 đến KT-20, KT-23, KT-38 và KT-39 (đỏ trước), rồi đổi `SessionMappingAuthenticationSuccessHandler`, `WebGatewayLogoutSuccessHandler`, `SessionRevokeController`, `AuthController` sang `sid` và `role` (T6), đặt độ lệch đồng hồ 5 giây cho bộ giải mã token dịch vụ. Không còn mã nào đọc `oss_id` hay `roles`.

**Trạng thái:** Đã làm `2026-10-18`. Test viết đỏ trước (7 trên 11 đỏ đúng lý do) rồi mới viết mã, xanh ngay lần chạy đầu. **Test mới** ở `storefront-web-bff`: `SidMappingTest` (KT-16, 38, 39; 4), `SessionProbeTest` (KT-17; 2), `InternalRevokeTest` (KT-18, 19, 23; 4), `LogoutTest` (KT-20; 1); tổng `storefront-web-bff` 25 (trước 14), tất cả xanh. Bộ khung test thêm Xác thực giả ký token thật (`FakeIdentityProvider`) và `login(...)` đi hết luồng mã ủy quyền. Phá thử: đọc lại `oss_id` (5 test đỏ), không xóa phiên cũ khi ghi đè (KT-38 đỏ), bỏ đặt lại hạn phiên (2 đỏ), bỏ ân hạn ánh xạ (2 đỏ), độ lệch đồng hồ 60 giây (KT-23 đỏ); mã đã khôi phục. Hai phá thử đầu lần chạy đầu không bị bắt, do lỗi của test (token hết hạn giả có `iat` sau `exp` nên bị từ chối vì lý do khác; kiểm hạn ánh xạ quá lỏng), đã sửa test rồi phá thử lại. **Chưa làm ở bước này:** xoay mã CSRF của KT-38 (nhóm B, bước 9); chạy thật với Xác thực thật (bước 11). Chi tiết và lý do: `tech.md` mục "Ghi nhận khi làm bước 7".

Xong khi: KT-16 đến KT-20, KT-23, KT-38 và KT-39 xanh; `grep` không còn `oss_id`, `ossId`, `roles` trong `storefront-web-bff`; phá thử (đọc lại `oss_id`) làm KT-16 đỏ.

### Bước 8. Nhóm C: làm mới token kèm `sid` · Agent, Người rà

Viết KT-13 đến KT-15 và KT-40 (đỏ trước, Xác thực giả), rồi bộ chuyển đổi tham số làm mới thêm `sid`, xử lý từ chối (xóa phiên, 401) và, nếu bước 2 cho thấy cần, gộp làm mới đồng thời (T5).

**Trạng thái:** Đã làm `2026-10-18`. Test viết đỏ trước (5 trên 6 đỏ), xanh sau khi cài. **Test mới** ở `storefront-web-bff`: `RefreshTest` (KT-13, 14, 40, thêm kiểm token lưu lại và độ lệch 5 giây; 6 test), `ConcurrentRefreshTest` (KT-15; 1 test); tổng `storefront-web-bff` 32 (trước 25), tất cả xanh; `api-gateway` 26 và thư viện 7 vẫn xanh. Phá thử: bỏ `sid` (KT-13 đỏ), độ lệch 60 giây (đỏ), bỏ tuần tự hóa (KT-15 đỏ), bỏ xóa phiên khi `invalid_grant` (KT-14 đỏ), coi mọi lỗi là `invalid_grant` (2 test KT-40 đỏ); mã đã khôi phục (so byte). Ba điều `tech.md` T5 ghi "chưa kiểm" nay đã rõ (xem `tech.md` mục "Ghi nhận khi làm bước 8"). Sửa thêm một test của thư viện giới hạn tần suất không ổn định khi máy bận. Chưa chạy thật với Xác thực thật (bước 11).

Xong khi: KT-13 đến KT-15 và KT-40 xanh; phá thử (bỏ `sid`, bỏ xóa phiên khi từ chối) làm test tương ứng đỏ.

### Bước 9. Nhóm B: CSRF ở BFF · Agent, Người rà

Viết KT-08 đến KT-12 và KT-34 (đỏ trước), rồi bật CSRF cho chuỗi phiên (cookie không `HttpOnly`, mã lỗi `CSRF_TOKEN_INVALID`, gia hạn trượt, điểm sinh lại mã), giữ `/bff/internal/**` không CSRF (T4). Cùng nguồn gốc nên không cần CORS cho header `X-XSRF-TOKEN` giữa giao diện và BFF; chỉ kiểm cấu hình CORS còn lại không mở nhầm.

**Trạng thái:** Đã làm `2026-10-18`, kèm bảng hành vi lỗi hai loại người gọi đã chốt với người dùng (ghi ở `service.md` của BFF, mục 3.1). Test viết đỏ trước (14 đỏ), xanh sau khi cài. **Test mới** ở `storefront-web-bff`: `CsrfTest` (KT-08, 09, 11, 12, 34; 8), `SecurityErrorHandlersTest` (KT-10; 1), `ErrorResponsesTest` (5); sửa `EntryPointRedirectTest`, `GatewayFrameworkTest`, `LogoutTest`, `RefreshTest` theo hành vi mới; tổng `storefront-web-bff` 45 (trước 32), tất cả xanh; `api-gateway` 26 và thư viện 7 vẫn xanh. Phá thử: tắt CSRF (7 đỏ), bỏ bộ xử lý lỗi CSRF riêng, bật CSRF cho chuỗi `/bff/internal/**`, bỏ bộ lọc phát cookie, bỏ xoay mã ở callback, bỏ xóa phiên khi mất client ủy quyền: mỗi lần đúng test tương ứng đỏ; mã đã khôi phục (so byte). **Thay đổi hành vi lỗi theo bảng đã chốt:** `/api/**` chưa có phiên luôn 401 (không còn 302 theo `Accept`); khung lỗi chung cho 401, 403, 404, 503 do BFF sinh; đăng xuất khi hết phiên vẫn 202 + `Location` về trang đích; phiên mất client ủy quyền thì xóa phiên và 401; dịch vụ sau trả 401 thì chuyển nguyên và giữ phiên; lỗi callback giữ `/login?error` của Spring. Chi tiết và lý do: `tech.md` mục "Ghi nhận khi làm bước 9". Chưa làm: `oauth2-service` (bảng hành vi lỗi bộ 2 và kiểm CSRF phía Xác thực), chạy thật (bước 11).

Xong khi: KT-08 đến KT-12 và KT-34 xanh; phá thử (tắt CSRF, bỏ mã lỗi riêng, bật CSRF cho `/bff/internal/**`) làm test tương ứng đỏ.

### Bước 10. Log và địa chỉ tin cậy (việc giao từ feature 07) · Agent, Người rà

Viết KT-24, 25 (đỏ trước), rồi bộ lọc log cho `api-gateway` và `storefront-web-bff` (WebFlux, truyền ngữ cảnh Reactor) dùng lõi của feature 07, ghi đè tiêu đề địa chỉ ở `api-gateway`, và cấu hình dịch vụ sau tin tiêu đề đó (T7).

**Trạng thái:** Đã làm `2026-10-18`. **Test mới:** `observability-starter` `ClientIpResolverTest` (4) và 1 ca trong `RequestLoggingFilterTest` (tổng 67); `api-gateway` `RequestLogTest` (2; KT-25 nửa cổng) và sửa `ForwardedHeadersTest` (KT-24 nửa cổng, tổng 28); `storefront-web-bff` `ClientIpTest` (2), `UntrustedProxyClientIpTest` (1), `RequestLogTest` (2; tổng 50). Phá thử: bỏ đặt `X-Client-Ip` ở cổng, bộ chọn luôn tin tiêu đề, bộ chọn nhận mọi chuỗi, bộ lọc servlet bỏ qua tiêu đề, bỏ bộ lọc log WebFlux, cổng tin loopback: mỗi lần đúng test tương ứng đỏ; mã đã khôi phục (so byte). `observability-starter` thành phụ thuộc của hai cổng (log JSON ECS, che bí mật, truy vết OTel, cổng quản trị riêng). **KT-24 và KT-25 ở mức "Hệ thống" (đọc log thật qua ba dịch vụ đang chạy) còn lại cho bước 11.** Chi tiết và lý do: `tech.md` mục "Ghi nhận khi làm bước 10".

Xong khi: hai test xanh bằng cách đọc log thật; phá thử (không ghi đè tiêu đề) làm KT-24 đỏ.

### Bước 11. Chạy thật · Người

Chạy `api-gateway`, `storefront-web-bff`, `oauth2-service`, Redis và Postgres (đã có sẵn trong Docker) và gọi qua cổng 8000 với `Host` là `auth.localhost`, `shop.localhost` bằng curl có cookie (và một lần bằng trình duyệt thật cho luồng đăng nhập chéo host): đăng nhập qua BFF, gọi API, để token hết hạn rồi gọi tiếp (làm mới), đăng nhập hai thiết bị, đăng xuất ở BFF, báo đăng xuất từ Xác thực, 11 lần đăng ký liên tiếp (429), yêu cầu ghi thiếu CSRF, `Host` lạ. Xem log của ba dịch vụ, khóa Redis và bảng phiên sau mỗi ca. Ghi cơ sở dữ liệu dev: báo trước, hỏi giữ hay xóa dữ liệu thử.

**Trạng thái:** Đã làm `2026-10-18` bởi Agent (không phải Người) với ba dịch vụ thật và Chrome thật; kết quả từng ca, ba lỗi sửa trong lúc chạy (đăng xuất không xóa phiên (chuỗi mặc định thiếu bộ xóa phiên) nên token còn trong Redis, mức log DEBUG làm lộ cookie, yêu cầu đầu tiên bị mở cửa nhầm ở cổng) và một phát hiện ngoài phạm vi (hạn refresh token ở Xác thực): `tech.md` mục "Ghi nhận khi làm bước 11". Dữ liệu thử đã dọn.

Xong khi: KT-21, 22, 24, 25, 31 và các ca ở `flow.md` mục 5 ra đúng kết quả; log không lỗi bất thường (không có token, mật khẩu, `id_token_hint`); không còn khóa Redis hay hàng cơ sở dữ liệu của phiên đã đăng xuất.

### Bước 12. Đóng feature · Agent, Người rà

Chuyển ADR-0006 sang Accepted nếu R25, R26 đã qua (nếu không thì ghi kết quả và giữ Proposed); cập nhật `rate-limiting-layers.md` mục 8, ADR-0005 (mục "CSRF ở BFF" từ "chưa làm" sang đã làm kèm cơ chế đã chọn, và mục về `__Host-`), `api.yaml` (`servers`), `security-architecture.md` và `spring-security-logout-bff.md` nếu còn nói `oss_id`, `/auth`, `/web` ở phía BFF (hoặc ghi vào `deferred.md`), `deferred.md`,
cột cuối của bảng "Từ luồng đến giải pháp" ở trên, điều học được và hàng 06 ở `../00-features.md`, các nợ của feature 02 và 03 đã trả (BFF gửi `sid`, nhận `sid`, CSRF); `flow.md` đổi trạng thái sang Đã làm.

**Trạng thái:** Đã làm `2026-10-18`: ADR-0006 Accepted, ADR-0005 cập nhật, `deferred.md`, `00-features.md`, bảng "Từ luồng đến giải pháp" đủ cột cuối, các tài liệu nói `oss_id`, `/auth`, `/web` ở phía BFF đã sửa (`spring-security-logout-bff.md` đã viết lại theo mô hình hiện tại), PKCE của `storefront-web-bff` bật lại. Chi tiết: `tech.md` mục "Ghi nhận khi đóng feature".

Xong khi: không còn chỗ nào trong tài liệu lệch với mã và test.
