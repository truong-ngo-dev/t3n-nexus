# Feature 06: giải pháp kỹ thuật

> Mỗi mục: quyết định, phương án đã loại, lý do. Quyết định đúng cho cả BC hoặc mọi BC nằm ở ADR; file này ghi kết quả thử và chi tiết riêng của feature.
> **Kết quả thử là bước 2 của `plan.md`, T1 đến T9 là bước 3 (`2026-10-17`).** Nhãn "Đã chốt" là người đã quyết; "Đề xuất" là đề xuất của agent chờ người chốt; các điểm "Chưa kiểm" thử ở bước làm tương ứng.

## Kết quả thử

Chạy ba dịch vụ cục bộ như đang có (tên lúc thử; `web-gateway` nay đổi thành `storefront-web-bff`, bước 4b) (`api-gateway` 8000, `web-gateway` 8090, `oauth2-service` 8004 profile `dev`, Redis, Postgres), một nguồn gốc `localhost:8000`, bằng curl có cookie và một thí nghiệm trình duyệt; **chưa sửa mã**. Hạn token ở `dev`: access 1 phút, refresh 3 phút, phiên 10 phút.

| Ca | Kết quả |
|---|---|
| 1. Đăng nhập qua BFF | Hoàn tất: về trang đăng nhập của Xác thực, nhập mật khẩu, cấp mã, callback `/web/login/oauth2/code/web-gateway`, rồi chuyển về `http://localhost:4200/customer`. Yêu cầu cấp mã **có `code_challenge` (S256) và `state`**: BFF gửi PKCE được (R17 phần gửi; chưa thử `state` sai) |
| 2. `GET /webgw/auth/session` | 200 `{"userId":"<sub>","role":null}`: `role` luôn rỗng vì đọc claim `roles` (R16 xác nhận) |
| 3. Ánh xạ phiên BFF | **Không có khóa Redis `webgw:*`**; log BFF `WARN ... oss_id claim missing ... mapping skipped` (R16 xác nhận) |
| 4. Làm mới token không `sid` | Xác thực trả 400 `invalid_grant`. Ở BFF: vì hạn access chỉ 1 phút và lệch đồng hồ mặc định của Spring là 60 giây, **mọi yêu cầu đều kích hoạt làm mới**; lần gọi API đầu **500** (`ClientAuthorizationException [invalid_grant]`), lần sau **302** (BFF đã bỏ ủy quyền). Phiên người dùng và phiên ứng dụng ở Xác thực vẫn còn tới khi hết hạn hoặc đăng xuất (R12 xác nhận, và hiện là 500 chứ chưa phải 401) |
| 4b. Làm mới kèm `sid` (trực tiếp ở Xác thực) | 200, refresh token **xoay** (token mới khác cũ). Dùng lại token cũ: 400 `invalid_grant`, **không thu hồi phiên** (token mới vẫn dùng được) |
| 5. Làm mới đồng thời (R11) | Hai làm mới **cùng một refresh token** chạy song song: **cả hai 200**, ra hai refresh token mới khác nhau; kiểm lại thì **chỉ một** dùng được (token kia 400). Xoay refresh token ở Xác thực không nguyên tử. Nếu BFF có hai yêu cầu đồng thời gặp token hết hạn thì có thể lưu nhầm token đã hết hiệu lực và lần làm mới sau bị từ chối (người dùng bị đăng xuất). Ca "5 làm mới song song" của kịch bản thử không dùng được (script dùng lại token đã xoay), bỏ |
| 6. Đăng xuất qua BFF và báo `{"sid"}` | `POST /webgw/auth/logout` (CSRF đang tắt) trả 202 kèm `Location` tới `http://localhost:8000/auth/connect/logout?id_token_hint=...`; theo `Location` được 302 về `http://localhost:4200`; phiên của lần đăng nhập này ở Xác thực bị kết thúc (đúng feature 03), API sau đó 302. Xác thực log `revoked sid=<sid>` **nhưng BFF log `no mapping found ... ossId=null`**: nhận 200, không xóa gì (nợ của feature 03 xác nhận) |
| 7. Chéo host (thí nghiệm Chrome, máy chủ thử cổng 9101, host `a.localhost` và `b.localhost`) | Hai host là **hai site khác nhau**: cookie `SameSite=Lax` đặt ở `a` **không** theo `fetch` (có `credentials`) từ trang ở `b` tới `a`; **có** theo điều hướng cấp cao (nhấp liên kết) từ `b` tới `a`. Nghĩa là luồng chuyển hướng OAuth (GET cấp cao) và đăng xuất đi được, còn lời gọi XHR chéo host không mang cookie, khớp quyết định "giao diện và BFF cùng nguồn gốc" của ADR-0006. **Chưa thử**: Firefox, luồng đăng nhập đầy đủ qua hai host (cần đổi cấu hình `issuer`, redirect URI), POST biểu mẫu chéo host |
| 8. IPv6 và `Host` | `*.localhost` phân giải ra `::1`; cả ba dịch vụ nghe `[::]` (netstat); `curl http://auth.localhost:8000/auth/login` trả 200 qua `::1` (R26 không gặp vấn đề với Java mặc định). `Host: evil.test` hiện **được chấp nhận** (200): chưa có kiểm host (R21 là việc thật) |

**Kết luận từ ca thử:**
- R11 là rủi ro **thật**, và có hai phía: BFF phải tuần tự hóa làm mới theo phiên (T5), và Xác thực hiện cho hai làm mới đồng thời cùng một refresh token cùng thành công (R29 mới).
- R12: BFF phải trả 401 (hiện 500) và dọn phiên khi làm mới bị từ chối.
- R17: BFF gửi PKCE và `state`; phần BFF từ chối `state` sai là hành vi mặc định của Spring, kiểm ở KT-22.
- R25: không có điều gì ngăn ADR-0006 ở mức trình duyệt; luồng chuyển hướng đầy đủ kiểm ở bước 5 và 11. R26: không cần làm gì thêm với Java mặc định.
- R21: baseline xác nhận cần kiểm host.
- Dev TTL (access 1 phút, lệch 60 giây) làm mọi yêu cầu làm mới; giữ cho test nhưng ghi để khỏi nhầm khi đọc log.

## T1. Luật giới hạn tần suất theo đường dẫn và cách đếm hai tầng (R3, R4, R5) · Đã làm ở bước 6 (luật theo cấu hình)

- **Quyết định:** một bộ lọc toàn cục `RateLimitFilter` ở `api-gateway` thay `IpRateLimitFilter`, chạy hai tầng theo thứ tự **luật theo đường dẫn trước, mức chung sau**. Quy tắc đếm (người chốt): **mỗi tầng chỉ đếm yêu cầu mà chính nó cho qua; yêu cầu bị chặn ở một tầng dừng ở đó và không làm tăng bộ đếm của tầng sau** (thư viện đã vậy cho từng tầng: vượt mức thì không ghi lượt). Khóa Redis: `ratelimit:rule:<tên luật>:<địa chỉ>` và `ratelimit:ip:<địa chỉ>` (khóa mức chung giữ nguyên).
- **Mô hình luật:** thuộc tính `app.ratelimit.rules[i].{name,host,method,path,limit,window-seconds,enabled}` nạp một lần lúc khởi động thành đối tượng bất biến có kiểm hợp lệ: `limit` và `window-seconds` dương, `name` không trùng, `host` nằm trong danh sách host của cổng (T9), `method` và `path` không rỗng; sai thì không khởi động. Luật tắt không áp. Không có luật nào thì chỉ còn mức chung 300 mỗi 60 giây. Luật đầu: `name=register, host=auth, method=POST, path=/register, limit=10, window-seconds=3600`.
- **Khớp luật:** host so khớp chính xác (chữ thường, bỏ cổng), phương thức chính xác, đường dẫn chuẩn hóa (bỏ dấu `/` cuối, khớp chính xác; không dùng mẫu `/**` ở C0 vì luật đầu không cần). Đường dẫn có tiền tố giống (`/register-x`) không khớp (R3).
- **Kẽ hở chấp nhận:** yêu cầu luật cho qua nhưng mức chung từ chối đã tốn một lượt của luật. Chỉ xảy ra khi địa chỉ đã vượt 300 mỗi 60 giây (đang bị chặn gần hết yêu cầu), thiệt hại tối đa vài lượt trong mức 10 mỗi giờ.
- **Loại:** đoạn Lua kiểm cả hai khóa và chỉ ghi khi cả hai cho qua (đúng tuyệt đối nhưng phải sửa thư viện dùng chung, không đáng với kẽ hở nhỏ); bộ lọc `RequestRateLimiter` có sẵn của Spring Cloud Gateway (thùng token chứ không phải cửa sổ trượt, không cùng thuật toán với thư viện, thân 429 rỗng, khai báo theo từng tuyến chứ không theo danh sách luật); khai báo luật trong từng tuyến của `RouteConfiguration` (trộn cấu hình luật vào định tuyến, không có chỗ kiểm hợp lệ chung).
- **Thứ tự với kiểm host (T9):** kiểm host chạy trước; host lạ bị 404 trước khi đếm bất cứ gì (không để yêu cầu rác đốt bộ đếm).

## T2. Thân 429 và `Retry-After` (R7) · Đã làm ở bước 6

- **Quyết định:** thân JSON dùng khung lỗi chung của dự án (`success`, `code`, `message`, `errors`, `traceId`): `{"success":false,"code":"TOO_MANY_REQUESTS","message":"Bạn thao tác quá nhanh, vui lòng thử lại sau", ...}`, kèm `Retry-After` là **số giây còn lại đến khi giải phóng một lượt** (thời điểm của lượt cũ nhất trong cửa sổ cộng độ dài cửa sổ trừ bây giờ, làm tròn lên). Áp cho cả luật theo đường dẫn và mức chung, ở cả `api-gateway` và `UserRateLimitFilter` của `storefront-web-bff`.
- **Cài đặt:** script Lua hiện chỉ trả 0 hoặc 1. Mở rộng để khi từ chối trả thêm thời gian chờ (đọc điểm của phần tử cũ nhất bằng `ZRANGE key 0 0 WITHSCORES`), thêm phương thức `acquire(...)` trả kết quả có `retryAfter` vào `ReactiveRateLimiter` (giữ `tryAcquire` để không phá chỗ gọi cũ). Đây là thay đổi thư viện dùng chung (`reactive-rate-limiter-starter`), có test riêng.
- **Loại:** `Retry-After` bằng độ dài cửa sổ như hiện tại (đăng ký sẽ báo chờ 1 giờ ngay cả khi lượt cũ nhất sẽ rơi khỏi cửa sổ sau 5 phút: sai và làm giao diện đếm ngược sai); thân rỗng (giao diện không rẽ nhánh theo `code` được, mâu thuẫn với khung lỗi chung ở feature 01).

## T3. Thời gian chờ của lệnh đếm Redis (R1) · Đã làm ở bước 6

- **Quyết định:** thêm thời gian chờ ngắn (mặc định 200 ms, cấu hình `app.ratelimit.redis-timeout-ms`) quanh lời gọi Redis trong `ReactiveRedisRateLimiter`; hết giờ hoặc lỗi thì mở cửa kèm cảnh báo (đã có nhánh lỗi). Đặt ở thư viện nên cả hai cổng được hưởng.
- **Loại:** để mặc định của Lettuce (vài giây; mỗi yêu cầu có thể treo khi Redis chết); ngắt mạch (Resilience4j) cho mỗi yêu cầu khỏi phải chờ hết thời gian (hữu ích khi Redis chết lâu, nhưng thêm phụ thuộc; ghi `deferred.md`).
- **Ghi nhận:** khi Redis chết, mỗi yêu cầu ghi một dòng cảnh báo; chấp nhận ở C0, ngắt mạch sẽ gom lại.

## T4. CSRF ở BFF và vòng đời mã (R8, R9, R10) · Đã làm ở bước 9

- **Cơ chế:** Spring Security WebFlux, `csrf` với `CookieServerCsrfTokenRepository` đặt cookie không `HttpOnly`. Cookie `XSRF-TOKEN` (Angular đọc sẵn tên này; sau ADR-0006 không còn va chạm với cookie cùng tên của Xác thực vì hai host có hai kho cookie), `Path=/`, `Secure`, `SameSite=Lax`, **không `Max-Age`** (cookie phiên của trình duyệt, giống `STOREFRONT_SESSION`). Header `X-XSRF-TOKEN`. Chuỗi `/bff/internal/**` giữ nguyên không CSRF (xác thực bằng token dịch vụ).
- **Vòng đời mã (độc lập với phiên Redis):**
  1. **Phát khi thiếu:** một `WebFilter` nhỏ kích hoạt việc lấy mã ở mọi yêu cầu (WebFlux chỉ phát cookie khi mã được dùng), nên `GET /bff/auth/session` đầu tiên luôn kèm cookie.
  2. **Xoay khi phiên BFF mới được tạo** (callback đăng nhập thành công, kể cả xác thực im lặng không qua biểu mẫu): bộ xử lý đăng nhập thành công của BFF phát mã mới.
  3. **Xóa khi đăng xuất** (handler đăng xuất của Spring).
  4. **Phiên mất** (hết hạn chờ, Xác thực kết thúc, làm mới bị từ chối): không có sự kiện nào; yêu cầu kế tiếp nhận 401 nếu mã còn khớp hoặc 403 `CSRF_TOKEN_INVALID` nếu cookie đã mất, và giao diện gọi `GET /bff/auth/session` (phát lại mã) rồi thử một lần hoặc về đăng nhập.
- **Mã lỗi:** lỗi CSRF dùng `csrf.accessDeniedHandler(...)` riêng trả 403 `{"success":false,"code":"CSRF_TOKEN_INVALID"}`; 403 thiếu quyền (xử lý ở `exceptionHandling`) trả mã khác (`FORBIDDEN`). Phiên không bị hủy khi CSRF sai.
- **Gia hạn trượt (ADR-0005 A) không cần** vì cookie CSRF cùng loại với cookie phiên (không `Max-Age`). Nếu sau này cho cookie `Max-Age` thì dùng cách theo ngưỡng: BFF lưu thời điểm phát mã trong phiên Redis và phát lại khi còn dưới ngưỡng; ghi ở `deferred.md` nếu cần.
- **Loại:** lưu mã trong phiên máy chủ (`WebSessionServerCsrfTokenRepository`): mã có vòng đời đúng bằng phiên nhưng giao diện phải lấy mã qua một đường riêng và mã nằm trong Redis; gia hạn cookie ở mọi phản hồi (không cần với cookie phiên).
- **Chưa kiểm trong repo:** việc WebFlux chỉ phát cookie khi có bộ lọc kích hoạt, và handler xóa mã khi đăng xuất; kiểm ở bước 9 bằng test đỏ trước.
- **Đã chốt khi làm bước 9:** (1) xoay mã ở callback (BFF không có biểu mẫu mở lâu như Xác thực); (2) `GET /bff/auth/session` là điểm phát lại (mọi `GET` qua BFF đều phát khi thiếu).

## T5. Làm mới token, tự phục hồi và tuần tự hóa (R11, R12, R29) · Đã làm ở bước 8 (cách A cho hạn phiên do người chốt)

- **Phục hồi theo loại lỗi** (chỉ `invalid_grant` mới là "phiên không cứu được"):

  | Tình huống | BFF làm gì | Người dùng thấy |
  |---|---|---|
  | Token truy cập hết hạn, refresh token còn | Làm mới kèm `sid`, lưu token mới, gọi tiếp | Không thấy gì |
  | `invalid_grant` (refresh token hết hạn, bị cắt theo phiên, đã xoay, hoặc phiên đã kết thúc) | Xóa phiên BFF, ủy quyền và ánh xạ rồi trả **401** | Giao diện chuyển cấp cao tới đăng nhập; phiên Xác thực còn sống thì **SSO im lặng**, hết rồi mới nhập mật khẩu |
  | Lỗi khác (Xác thực chậm, 5xx, mất kết nối, hết thời gian chờ) | **Giữ phiên và ánh xạ**, trả **503** | Thử lại sau, không bị đăng xuất |
- **Gửi `sid`:** bộ quản lý ủy quyền client của BFF (`ReactiveOAuth2AuthorizedClientManager`, bean tự định nghĩa thay bean mặc định mà bộ lọc `TokenRelay` dùng) dùng `RefreshTokenReactiveOAuth2AuthorizedClientProvider` với bộ chuyển đổi tham số thêm `sid` đọc từ **claim của token truy cập hiện có** (chỉ đọc payload, không kiểm chữ ký: token do Xác thực cấp qua kênh máy chủ và đang nằm trong phiên của chính BFF). Thiếu `sid` (token cũ) thì không gửi, Xác thực trả `invalid_grant`, đi đường 401.
- **Lệch đồng hồ của BFF:** cấu hình `app.security.client.clock-skew` (mặc định 5 giây) cho bộ cung cấp làm mới, thay mặc định 60 giây của Spring. Ở `dev` access chỉ 1 phút nên mặc định 60 giây làm **mọi yêu cầu** kích hoạt làm mới.
- **Tuần tự hóa (bắt buộc, R11 đã thử là thật):** mỗi phiên BFF có tối đa một làm mới đang chạy; các yêu cầu đồng thời gặp token hết hạn chờ cùng kết quả (một `Mono` dùng chung theo mã phiên, xóa khi xong). Một thể hiện BFF ở C0 nên khóa trong tiến trình đủ; nhiều thể hiện cần khóa Redis, ghi `deferred.md`.
- **Hạn khi lưu token mới:** theo cách A của T6 (hằng số bằng TTL refresh, đặt lại ở mỗi lần lưu token mới). Hạn token có thể bị cắt sớm hơn phiên BFF (ADR-0003); đường `invalid_grant` luôn dọn nên chấp nhận.
- **Hoãn (`deferred.md`):** cách B, Xác thực trả hạn refresh token trong phản hồi `/oauth2/token` (tham số bổ sung `refresh_token_expires_in`, mở rộng qua bộ xử lý phản hồi của Spring Authorization Server) để phiên BFF tắt đúng lúc refresh token hết. Giá trị: khớp chính xác, không có phiên BFF sống lâu hơn refresh token. Lý do xếp sau: cần sửa Xác thực (feature 02) và test, trong khi đường `invalid_grant` đã dọn đúng.
- **R29** (Xác thực cho hai làm mới đồng thời cùng một mã làm mới cùng thành công): **ghi nợ `deferred.md`, không sửa trong feature này**. Lý do: sửa đúng cần bao cả yêu cầu `/oauth2/token` trong một giao dịch khóa hàng theo `sid` (khóa hiện chỉ có ở điểm móc lưu), là thay đổi ở đường token của feature 02 và 03 với test riêng; một BFF có tuần tự hóa đã chặn tình huống thật ở C0. Chưa có KT-37.
- **Loại:** coi mọi lỗi làm mới như `invalid_grant` (đăng xuất oan khi Xác thực chỉ chậm); làm mới chủ động trước hạn (không giải quyết đồng thời); tăng lệch đồng hồ để làm mới ít hơn (không giải quyết đồng thời); cho phép mã làm mới cũ dùng thêm một khoảng ngắn ở Xác thực (làm yếu việc xoay mã).
- **Chưa kiểm:** cách `TokenRelay` lấy bộ quản lý ủy quyền trong Spring Cloud Gateway 5 (bước 8); chỗ bắt lỗi làm mới (bộ xử lý mức WebFlux); việc lưu của yêu cầu thứ hai không ghi đè token mới (Spring Session Redis chỉ ghi thuộc tính đã đổi, tôi chưa chạy).

## T6. Ánh xạ phiên BFF sang phiên ứng dụng của ta theo `sid`, `role`, đăng xuất, nhận báo (R13 đến R16, R18, R31) · Đã làm ở bước 7

- **Chuỗi định danh:** cookie `STOREFRONT_SESSION` của trình duyệt trỏ tới phiên BFF (Redis, giữ token); `sid` (phiên ứng dụng ở Xác thực) nối với phiên BFF bằng **ánh xạ hai chiều**; `sid` trỏ tiếp tới phiên người dùng. Xác thực chỉ biết `sid`, nên ánh xạ là cầu nối duy nhất từ phía Xác thực sang phiên BFF.
- **Khóa Redis:** `storefront:sid:<sid>` → mã phiên BFF và `storefront:session:<mã phiên>` → `sid`.
- **Vòng đời (dùng chung với T5):**

  | Sự kiện | Phiên BFF | Ánh xạ `sid` |
  |---|---|---|
  | Đăng nhập, kể cả xác thực im lặng | Tạo, đặt hạn | Ghi hoặc ghi đè (khác mã phiên thì xóa phiên BFF cũ) |
  | Làm mới thành công | Lưu token mới, **đặt lại hạn** | **Gia hạn** |
  | `invalid_grant` | Xóa | Xóa |
  | Lỗi tạm thời khi làm mới | Giữ nguyên | Giữ nguyên |
  | Đăng xuất ở BFF | Xóa | Xóa |
  | Xác thực báo `{sid}` (đăng xuất nơi khác, khóa tài khoản) | Xóa | Xóa |
  | Phiên BFF hết hạn chờ ở Redis | Biến mất | Còn tới hết hạn riêng (vô hại, xóa phiên đã mất vẫn 200) |
- **Quy tắc:** (1) một `sid` ứng với tối đa một phiên BFF; (2) **hạn ánh xạ không được ngắn hơn hạn phiên BFF**, được phép dài hơn một chút (`app.session.mapping-grace`, mặc định 5 phút), hai hạn đặt lại **cùng lúc** mỗi khi lưu token mới (đăng nhập, xác thực im lặng, làm mới); (3) mọi đường kết thúc phiên BFF xóa **cả hai** khóa; (4) `sid` đọc từ claim token truy cập ở callback, thiếu thì không ánh xạ và cảnh báo (R13).
- **Hạn (cách A, người chọn):** một cấu hình `app.session.ttl` bằng TTL của refresh token (mặc định 7 ngày; `dev` 3 phút cho khớp), thay cho `spring.session.timeout=86400s` và `app.session.mapping-ttl-seconds=86400` hiện lệch với 7 ngày. Hạn phiên đặt bằng `WebSession.setMaxIdleTime` mỗi lần lưu token mới. Hạn token có thể bị cắt sớm hơn (T5); đó là đường `invalid_grant`, không cố đoán `exp`. Cách B ghi ở T5.
- **Khóa cũ `storefront:oauth:*`:** `app.session.mapping-ttl-seconds` được khai báo nhưng **không có mã nào dùng**, nên khóa cũ **không tự hết hạn**. Hiện không có khóa nào (bước thử xác nhận BFF không tạo được vì thiếu `oss_id`) nên không cần dọn; nếu thấy thì xóa tay. Khóa mới có hạn ngay từ đầu.
- **`role`:** `AuthController.session` đọc claim `role` (chuỗi), không còn `roles`.
- **Đăng xuất BFF:** `sid` đọc từ thuộc tính `sid` của người dùng OIDC (ID token có claim này); **nếu thuộc tính không có trong `OidcUser`** thì đọc từ token truy cập trong phiên. Xóa ánh xạ và phiên rồi trả 202 kèm `Location` tới `connect/logout` ở host `auth`.
- **Nhận báo (`POST /bff/internal/sessions/revoke`):** thân `{"sid"}` (đổi tên từ `ossId`); `sid` không phải UUID thì 400; tra `storefront:sid:<sid>`, xóa phiên và hai khóa; không có thì vẫn 200. Xác thực bằng token dịch vụ phạm vi `storefront.internal` như cũ.
- **Độ lệch đồng hồ của bộ giải mã token dịch vụ ở BFF:** 5 giây (cấu hình `app.security.jwt.clock-skew`), như Xác thực (R18).
- **Loại:** đọc `exp` của refresh token để đặt hạn chính xác (cách B, xếp sau); đổi tên khóa sang `storefront:oauth2:` (chỉ đổi tên, không lợi ích); hạn ánh xạ đặt một lần lúc đăng nhập (hết trước phiên khi người dùng hoạt động liên tục).
- **Chưa kiểm:** thuộc tính `sid` có trong `OidcUser` (KT-20); cách đặt hạn phiên theo từng lần lưu token mới (KT-39).

## T7. Địa chỉ mạng tin cậy và log của cổng (R19, R20) · Đã làm ở bước 10

- **Địa chỉ:** `api-gateway` đặt tiêu đề riêng **`X-Client-Ip`** bằng địa chỉ kết nối (ghi đè, xóa giá trị client tự gửi); `storefront-web-bff` chuyển tiếp nguyên giá trị đó xuống dịch vụ sau (chỉ khi nhận từ cổng). Dịch vụ sau **giữ `server.forward-headers-strategy=framework`**, không đổi sang `native`.
- **`client.ip` ở dịch vụ sau:** `RequestLoggingFilter` (`observability-starter`, dùng chung mọi dịch vụ servlet) lấy `client.ip` từ `X-Client-Ip` **chỉ khi** `request.getRemoteAddr()` nằm trong danh sách proxy tin cậy (`app.logging.trusted-proxies`, mặc định loopback); ngoài danh sách thì bỏ qua tiêu đề và dùng `getRemoteAddr()` như cũ. Ở `storefront-web-bff` (WebFlux) áp cùng quy tắc; bộ xử lý tiêu đề của Spring có thể đã xử lý địa chỉ từ xa, test cho biết.
- **Vì sao không đổi sang `native`:** `native` (Tomcat `RemoteIpValve`) thay cách dựng địa chỉ công khai đang chạy bằng `ForwardedHeaderFilter`; tôi nhớ Tomcat không xử lý `X-Forwarded-Prefix` (cần cho tới khi nhóm E bỏ tiền tố) và có thể bỏ phần cổng trong `X-Forwarded-Host` dạng `host:port`, làm chuyển hướng đăng nhập sai. **Chưa chạy thử** hai điểm này, nhưng rủi ro nằm ở luồng đăng nhập chính chỉ để lấy một trường log. `X-Client-Ip` riêng cũng tránh việc `ForwardedHeaderFilter` đã liệt kê `X-Forwarded-For` trong các tiêu đề nó xử lý (kiểm trong Spring 7.0.7), nên bộ lọc log có thể không còn thấy `X-Forwarded-For`.
- **Log:** bộ lọc servlet của feature 07 không dùng được cho WebFlux; viết bộ lọc WebFlux dùng cùng lõi (cùng tên trường, che nhạy cảm, một dòng hoàn tất mỗi yêu cầu) cho `api-gateway` và `storefront-web-bff`, truyền `traceId` qua ngữ cảnh Reactor (Micrometer Context Propagation, đã có trong kho cục bộ) và tiêu đề `traceparent` xuống dịch vụ sau. **Phần lớn nhất của feature mà không phải luật bảo vệ**: cho phép tách thành bước riêng hoặc hoãn nếu quá lâu.
- **Loại:** đổi `forward-headers-strategy` sang `native` và dùng `remoteip.internal-proxies` (rủi ro ở trên); tin `X-Forwarded-For` ở mọi dịch vụ (client giả được nếu gọi thẳng, và `ForwardedHeaderFilter` đã xử lý nó); bỏ `client.ip` (mất dấu vết khi điều tra lạm dụng).
- **Ghi nhận:** khi triển khai bằng Docker Compose (`deployment.md`), địa chỉ kết nối của `api-gateway` có thể là địa chỉ cầu của Docker thay vì địa chỉ thật; ghi `deferred.md`, xem lại ở demo. Có bộ cân bằng tải đứng trước thì đổi sang tin tiêu đề của nó (R2).
- **Chưa kiểm:** địa chỉ từ xa ở WebFlux (`ForwardedHeaderTransformer`), thứ tự với bộ lọc `X-Forwarded-For` có sẵn của Spring Cloud Gateway, `X-Client-Ip` có tới dịch vụ sau không bị đổi.

## T8. Hạ tầng test cho hai cổng (R1 đến R30) · Đã làm ở bước 4

- **Quyết định:** Redis thật bằng Testcontainers `GenericContainer("redis:7-alpine")` (thư viện lõi đã có trong kho Maven cục bộ, cùng cách dùng Postgres ở `oauth2-service`), một thể hiện dùng chung cho cả bộ test mỗi cổng; Xác thực, máy chủ giao diện và dịch vụ sau giả bằng `com.sun.net.httpserver.HttpServer` của JDK (cùng cách `WebGatewayRevocationClientTest` đã dùng, **không thêm phụ thuộc mới**, vì kho cục bộ không có `MockWebServer` hay WireMock); ứng dụng thật chạy với `WebTestClient` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).
- **Phạm vi:** thêm `testcontainers` ở phạm vi test cho `api-gateway` và `storefront-web-bff`; một lớp nền chung nạp Redis, địa chỉ máy chủ giả vào thuộc tính của cổng. Không test nào chạm Redis, Postgres hay dịch vụ cục bộ của người dùng.
- **Loại:** WireMock hoặc MockWebServer (thêm phụ thuộc và tải mạng); Redis nhúng (không cùng hành vi với Lua của Redis thật).

## T9. Nguồn gốc theo subdomain (nhóm E, R21 đến R28, R30) · Đã làm ở bước 5

- **Danh sách host:** `app.gateway.hosts.auth`, `.shop`, `.ops` (dev: `auth.localhost`, `shop.localhost`, `ops.localhost`, cổng 8000); miền gốc là một biến để demo đổi một chỗ. Host ngoài danh sách (sau khi bỏ cổng, chữ thường) bị 404 bởi một bộ lọc chạy **trước** mọi bộ lọc khác (T1).
- **Tuyến:** vị ngữ `Host`; `auth` tới Xác thực không có tiền tố và không cắt đường dẫn; `shop` có hai tuyến: đường dẫn BFF (`/api/**`, `/oauth2/authorization/**`, `/login/oauth2/code/**`, `/bff/**`) tới `storefront-web-bff`, còn lại tới địa chỉ máy chủ giao diện cấu hình (`app.gateway.frontends.shop`, feature 05); `ops` giữ chỗ (chưa có tuyến). Giao diện không dùng các tiền tố BFF (R28).
- **Tiêu đề chuyển tiếp:** giữ `Host` của trình duyệt; đặt `X-Forwarded-Host` và `X-Forwarded-Proto` từ yêu cầu thật; **xóa** `X-Forwarded-Prefix`, `X-Forwarded-For` hay `Forwarded` do client gửi (R22). Bỏ `apigateway.public-host`, `apigateway.public-proto`, `stripPrefix`, `X-Forwarded-Prefix`.
- **Xác thực:** `issuer` thành `http://auth.localhost:8000`; client `storefront-web` đăng ký lại redirect URI `http://shop.localhost:8000/login/oauth2/code/storefront-web` và trang đích `http://shop.localhost:8000/`, đặt qua `RegisteredClientSeeder` theo cấu hình (V4, V9 đã chạy và chỉ có dữ liệu dev; không thêm migration xóa dữ liệu). URI cũ bị từ chối.
- **BFF:** `authorization-uri` và `end-session-uri` trỏ host `auth`; bỏ cặp `(/web)?` ở `RouteConfiguration`; `AuthController.login` trả `/oauth2/authorization/storefront-web` không dùng `contextPath`; bộ chuyển hướng "chưa đăng nhập" giữ nguyên và test chứng minh (R30, KT-36).
- **Cookie:** theo máy, không đặt `Domain`; Xác thực giữ `OAUTH2_SESSION`, `DEVICE_KEY` và cookie CSRF của Xác thực; BFF dùng `STOREFRONT_SESSION` và `XSRF-TOKEN`.
- **CORS (người chọn phương án 2):** danh sách nguồn gốc đọc từ cấu hình (`app.cors.allowed-origins`, dùng `allowedOriginPatterns`, vẫn `allowCredentials(true)`), mặc định ở dev `http://*.localhost:*`; demo đổi một biến. Áp ở `api-gateway`, `storefront-web-bff`, `oauth2-service`. Không dùng `*` kèm cookie (trình duyệt từ chối, và nới hết mọi nguồn gốc kèm cookie là lỗ hổng).
- **IPv6:** Java mặc định nghe `[::]`, đã thử chạy, không cần gì thêm (R26).
- **Loại:** xem ADR-0006 (giữ tiền tố; host API chung; mỗi ứng dụng một cổng; chia sẻ cookie).
- **Cần kiểm khi làm:** luồng đăng nhập đầy đủ qua hai host trên Chrome (R25), cơ chế ghép `contextPath` của Spring ở bộ chuyển hướng (R30).

## Ghi nhận khi làm bước 5 (nhóm E, `2026-10-18`)

Thay đổi và lý do, theo từng dịch vụ.

**`api-gateway`**
- Tuyến theo `Host` bằng `HostMatcher` riêng (không phân biệt chữ hoa, bỏ cổng) thay cho tiền tố `/auth`, `/web`, `/mobile`; tuyến `/mobile/**` bỏ vì BFF di động chưa có (ADR-0006). **Không viết bộ lọc host riêng** (T9 ban đầu dự kiến): tên máy lạ không khớp tuyến nào nên Gateway trả 404 trước mọi bộ lọc toàn cục, nên cũng không làm tăng bộ đếm. Tự có sẵn từ cách định tuyến, đơn giản và đúng hơn một bộ lọc thêm.
- `ForwardedHeadersSanitizer`: xóa `Forwarded`, `X-Forwarded-*`, `X-Client-Ip` do client gửi rồi đặt `X-Forwarded-Host`, `-Proto`, `-Port` từ yêu cầu thật. Lý do phải **tự đặt**: đã kiểm, Gateway không tự đặt các tiêu đề này (khớp ghi chú cũ của cổng), nên BFF và Xác thực không dựng được địa chỉ công khai đúng host nếu cổng không đặt. Giao thức lấy từ kết nối tới cổng; khi có bộ kết thúc TLS đứng trước thì phải tin tiêu đề của nó (nợ ghi ở `service.md` của cổng).
- CORS đọc `app.cors.allowed-origins` bằng `allowedOriginPatterns` (mặc định `http://*.localhost:*`). Lý do: `*` kèm cookie bị trình duyệt từ chối và nới mọi nguồn gốc là lỗ hổng; mẫu theo subdomain đúng với cấu trúc host mới. Mẫu không khớp `http://localhost:4200` (host không subdomain) nên ứng dụng phải mở ở `shop.localhost:8000`.
- Thuộc tính mới `app.gateway.hosts.{auth,shop,ops}` và `app.gateway.frontends.shop`; bỏ `apigateway.public-host`, `public-proto`, `routes.mobile-gateway`. Lý do: địa chỉ công khai nay lấy từ yêu cầu thật, không còn giá trị tĩnh; miền thật chỉ đổi biến môi trường.

**`storefront-web-bff`**
- Bỏ `(/web)?` và `/web/api/...` ở tuyến, ở danh sách `permitAll`. Lý do: không còn tiền tố (R30).
- Điểm vào không phụ thuộc `contextPath`: bộ chuyển hướng "chưa đăng nhập" đặt `setContextRelative(false)`, `AuthController.login` trả đường dẫn cố định. Lý do: **R30 là thật**, test đỏ cho thấy khi `X-Forwarded-Prefix: /web` lọt tới BFF thì Spring chuyển hướng tới `/web/oauth2/authorization/...` (đường dẫn không tồn tại sau ADR-0006).
- `authorization-uri` và `end-session-uri` theo `AUTH_PUBLIC_URL` (host `auth`); CORS dùng mẫu nguồn gốc; `FRONTEND_URL` mặc định `http://shop.localhost:8000`; trang đích đăng xuất là `${FRONTEND_URL}/` **kèm dấu `/` cuối**. Lý do dấu `/`: Xác thực so khớp chính xác `post_logout_redirect_uri` với giá trị đã đăng ký, hai bên phải cùng một chuỗi.

**`oauth2-service`**
- `issuer` theo `AUTH_PUBLIC_URL` (mặc định `http://auth.localhost:8000`, không `/auth`). Lý do: ADR-0006; token mới mang `iss` mới, bộ kiểm hint chấp nhận `iss` mới và từ chối `iss` cũ.
- `RegisteredClientSeeder` đặt lại danh sách redirect URI và trang đích đăng xuất của client `storefront-web` theo cấu hình mỗi lần khởi động (`app.clients.storefront-web.redirect-uris`, `post-logout-redirect-uris`, mặc định derive từ `FRONTEND_URL`). Lý do: đổi redirect URI bằng migration mới thì URI nằm trong mã và mỗi môi trường một giá trị; seeder đã là chỗ đặt bí mật theo môi trường, nên URI cũng đặt ở đó, và **gỡ hẳn URI cũ** của V4, V9 (KT-32). Không sửa V4, V9.
- CORS dùng mẫu nguồn gốc, `app.frontend.url` mặc định host `shop` (email-worker cùng mặc định, để liên kết trong thư đúng nguồn gốc).

**Ngoài ba dịch vụ**
- `web/src/environments/environment.ts`: BFF và API gọi bằng đường tương đối, Xác thực ở `http://auth.localhost:8000`. Lý do: giao diện và BFF cùng nguồn gốc (ADR-0006); đổi cấu hình này là tối thiểu để giao diện hiện có không gãy, còn lại thuộc feature 05.

**Điều rút ra khi làm**
- Khi phá thử bằng cách sửa tệp cấu hình rồi khôi phục **bản nguồn**, thư mục `target/classes` vẫn giữ bản đã sửa cho tới lần biên dịch kế tiếp; lần chạy thật đầu tiên đã dùng cấu hình của phép phá thử (chuyển hướng về địa chỉ cũ). Chạy lại biên dịch trước khi chạy thật.
- Chạy thật cả hai cách (curl có kho cookie theo host và Chrome) cho cùng kết quả; Chrome mới xác nhận điều hướng cấp cao chéo site giữa `auth.localhost` và `shop.localhost` đi được với `SameSite=Lax`.

### Chạy giao diện t3n-nexus thật qua cổng (`2026-10-18`, sau khi cổng 4200 được giải phóng)

Chạy `nx serve app --port=4201` (cổng 4200 còn ứng dụng khác chiếm; cổng đặt `SHOP_FRONTEND_URL=http://localhost:4201`), mở `http://shop.localhost:8000` trong Chrome, đăng nhập bằng nút của giao diện. Phát hiện và xử lý:
- **Mức chung 300 mỗi 60 giây chặn chính giao diện (đã sửa).** Máy chủ phát triển phục vụ hàng trăm tệp mô-đun cho một lần mở trang, nên `main.js` và các tệp khác nhận 429 và ứng dụng không khởi động. Lý do sửa: mức chung bảo vệ API và Xác thực, không phải tài nguyên tĩnh (môi trường thật phục vụ bằng CDN hoặc máy chủ tĩnh). Tuyến tới máy chủ giao diện (`FRONTEND_ROUTE_ID`) **không bị đếm** và không làm cạn bộ đếm của tuyến khác; test `FrontendRateLimitExemptionTest` (đỏ trước rồi mới sửa). Việc này sửa lại T1: mức chung áp cho "mọi tuyến trừ tuyến giao diện".
- **WebSocket tự tải lại của `ng serve` hỏng qua cổng, gây vòng lặp tải lại trang (chưa sửa nguyên nhân gốc, có cách tránh).** Gateway nâng cấp WebSocket được, nhưng bắt tay Gateway tới Vite nhận 400 (`Invalid handshake response getStatus: 400`) và kết nối đóng ngay (1006); Vite client coi là mất kết nối, thăm dò rồi tải lại trang, lặp lại và sinh hàng trăm yêu cầu. Kết nối trực tiếp tới Vite (cùng token) chạy bình thường; Gateway gửi `Origin` và `Host` của đích (`127.0.0.1:<cổng>`) và Vite 6.4 kiểm mã thông báo WebSocket khi có `Origin`; chưa tìm ra lý do cụ thể. **Cách tránh:** `nx serve app --live-reload=false --hmr=false` khi mở qua cổng (tải lại trang bằng tay); khi đó trang khởi động bình thường, 38 yêu cầu, không vòng lặp. Ghi vào việc của feature 05 (cấu hình `ng serve` cho môi trường qua cổng) và `docs/service/api-gateway/service.md` `TD-06`.
- **Luồng đăng nhập bằng giao diện chạy hết:** nút "Đăng nhập" của giao diện đưa trình duyệt sang `auth.localhost:8000/login`, nhập mật khẩu, về `shop.localhost:8000`, `/bff/auth/session` trả 200. **Nhưng giao diện dừng ở trang `/login` của chính nó**, vì `/bff/auth/session` trả `role = null` (lỗi R16, sửa ở nhóm D) và giao diện định tuyến theo `role`. Thêm: giao diện dùng giá trị vai `CUSTOMER`, `SELLER`, `ADMIN` trong khi Xác thực cấp `USER` và `ADMIN`; bất đồng bộ này cần quyết khi làm nhóm D hoặc feature 05 (ghi vào `deferred.md` nếu chưa xử lý).

## Ghi nhận khi làm bước 6 (nhóm A, `2026-10-18`)

Thay đổi và lý do, theo từng thành phần.

**`reactive-rate-limiter-starter`**
- Thêm `acquire(key, limit, window)` trả `RateLimitDecision(allowed, retryAfter)`; `tryAcquire` giữ lại, gọi `acquire`. Lý do: T2 cần thời gian chờ thật; script Lua nay trả thêm mili giây còn lại đến khi lượt cũ nhất rời cửa sổ.
- Thêm `TooManyRequestsResponse` (429, khung lỗi chung `TOO_MANY_REQUESTS`, `Retry-After`) dùng chung cho cổng và BFF. Lý do: tránh hai bản thân 429 khác nhau.
- Lệnh đếm bọc `Mono.defer` + `subscribeOn(boundedElastic)` + `.timeout(app.ratelimit.redis-timeout-ms)`, lỗi hay hết giờ thì mở cửa kèm cảnh báo (T3). **Phát hiện khi thử:** chỉ đặt `.timeout` là chưa đủ, vì Lettuce có thể chặn chính luồng gọi lúc lấy kết nối (Redis nhận kết nối nhưng im lặng làm treo 10 giây); phải đưa việc lấy kết nối sang luồng riêng thì thời gian chờ mới có tác dụng.

**`api-gateway`**
- `RateLimitFilter` thay `IpRateLimitFilter`: luật đường dẫn rồi mức chung (T1); bỏ qua tuyến giao diện (`FRONTEND_ROUTE_ID`).
- `RateLimitProperties` + `PathRateLimitRule` + `RateLimitPolicy`: luật nạp từ `app.ratelimit.rules[i]`, kiểm hợp lệ lúc khởi động, luật tắt không vào chính sách, khớp chính xác (host không cổng, phương thức, đường dẫn bỏ dấu `/` cuối).
- `ClientAddress`: khóa đếm là địa chỉ kết nối, không đọc `X-Forwarded-For` (client tự gửi được nên đổi tiêu đề là né luật).
- `application.properties`: luật đầu `POST /register` ở host `auth`, 10 mỗi giờ; `redis-timeout-ms` 200; `spring.data.redis.connect-timeout` 500 ms.
- **Lưu ý cấu hình Boot (ghi cho các test sau):** danh sách thuộc tính không gộp giữa các nguồn, nên test phải khai báo đủ `rules[0]`, không chỉ trường muốn đổi. `@DynamicPropertySource` của lớp con chạy trước lớp cha nên không ghi đè được giá trị của lớp cha; test Redis chết dùng thuộc tính hệ thống `test.redis.port.override` do `GatewayTestBase` đọc.

**`storefront-web-bff`**
- `UserRateLimitFilter` dùng `acquire` và `TooManyRequestsResponse` (trước đó 429 thân rỗng, `Retry-After` bằng cả cửa sổ). Thêm `spring.data.redis.connect-timeout`.
- Sửa lỗi sẵn có: bộ lọc dùng `switchIfEmpty(chain.filter(...))` sau `flatMap` trả `Mono<Void>`; `Mono<Void>` luôn rỗng khi xong nên chuỗi lọc có thể chạy lần hai. Nay chọn nhánh trước rồi chạy chuỗi đúng một lần (`UserRateLimitFilterTest`).

**Kiểm chứng**
- Test mới: thư viện 7; `api-gateway` `RateLimitPolicyTest` (KT-06, 07; 7), `ClientAddressTest` (KT-04; 3), `RegisterRuleTest` (KT-01, 02; 2), `RuleWindowTest` (KT-03), `TierBudgetTest` (KT-33), `RedisOutageTest` (KT-05), `GatewayFrameworkTest` (KT-06, thân 429 mức chung); `storefront-web-bff` `UserRateLimitFilterTest` (3). Redis của test là Testcontainers, không đụng Redis dev.
- Phá thử (bỏ kiểm phương thức, bỏ kiểm host, bỏ chuẩn hóa `/` cuối): lần lượt 3, 2, 1 test đỏ trong `RateLimitPolicyTest` và `RegisterRuleTest`; mã đã khôi phục (so byte). Phần `X-Forwarded-For` và thời gian chờ chưa phá thử lại sau khi hoàn tất; test tương ứng (`ClientAddressTest`, `RedisOutageTest`, bộ test thư viện) xanh.

## Ghi nhận khi làm bước 7 (nhóm D, `2026-10-18`)

Thay đổi và lý do, ở `storefront-web-bff`.

- **`SessionMappingStore` (mới):** một chỗ duy nhất ghi và xóa ánh xạ `storefront:sid:<sid>` ↔ `storefront:session:<mã phiên>`. Lý do: trước đó ba nơi (đăng nhập, đăng xuất, nhận báo) tự ghép khóa, T6 quy tắc 3 ("mọi đường kết thúc xóa cả hai khóa") khó giữ nếu để rải rác. `link` trả mã phiên cũ khi `sid` đang trỏ phiên khác; `unlink` xóa cả hai khóa, gọi lặp vô hại.
- **`AccessTokenClaims` (mới):** đọc claim chuỗi (`sid`, `role`) từ token truy cập. Lý do: mã giải JWT bị chép ở hai nơi.
- **`SessionMappingAuthenticationSuccessHandler`:** đặt lại hạn phiên (`setMaxIdleTime(app.session.ttl)`) mỗi lần lưu token, ghi ánh xạ theo `sid`, xóa phiên BFF cũ nếu `sid` trỏ phiên khác (KT-38); thiếu `sid` thì cảnh báo, không ánh xạ, đăng nhập vẫn xong (R13).
- **Cấu hình hạn (T5, T6 cách A):** `app.session.ttl` (mặc định 7 ngày) thay `app.session.mapping-ttl-seconds` (không có mã nào dùng) và `spring.session.timeout` cố định 24 giờ; `app.session.mapping-grace` 5 phút; hạn ánh xạ = hạn phiên + ân hạn nên không bao giờ ngắn hơn phiên.
- **Đăng xuất:** `sid` đọc từ thuộc tính `sid` của người dùng OIDC (ID token có claim này; KT-20 xác nhận). Nhánh dự phòng "đọc từ token truy cập trong phiên" ở T6 **không cần** nên không cài.
- **Nhận báo đăng xuất:** thân `{"sid"}`, không phải UUID thì 400, lũy đẳng 200.
- **Cleanup khi phiên hết hạn ở Redis:** script Lua chỉ xóa `storefront:sid:<sid>` nếu nó còn trỏ đúng phiên vừa hết hạn. Lý do: sau khi `sid` ghi đè sang phiên mới, phiên cũ hết hạn muộn không được xóa ánh xạ của phiên mới.
- **`/bff/auth/session`:** `role` đọc claim `role` (chuỗi, đã sửa lỗi trả `null`); không còn `roles`.
- **Bộ giải mã token dịch vụ:** bean `serviceTokenDecoder` với `JwtTimestampValidator(app.security.jwt.clock-skew)`, 5 giây (R18).
- **Bộ khung test:** `FakeIdentityProvider` (khóa RSA thật, đăng `/oauth2/jwks`, ký JWT) và `login(sub, sid, role)` trong `GatewayTestBase`: mở `/oauth2/authorization/storefront-web`, lấy `state` và `nonce`, dựng phản hồi điểm đổi mã, gọi callback. Gọi lại với cùng `sid` mô phỏng xác thực im lặng. Redis của test đọc qua `RedisContainerHolder.get/ttl/keys/expire`.
- **Bài học khi phá thử:** token "hết hạn" tạo cho test phải có `iat` trước `exp`, nếu không bị từ chối vì lý do khác (`expiresAt must be after issuedAt`) và phá thử độ lệch đồng hồ không đỏ.

## Ghi nhận khi làm bước 8 (nhóm C, `2026-10-18`)

Thay đổi và lý do, ở `storefront-web-bff`.

- **`authorizedClientManager` (bean tự định nghĩa, thay bean mặc định mà `TokenRelay` dùng):** nhận ra là đúng bean `ReactiveOAuth2AuthorizedClientManager` (chứng minh bằng việc `sid` xuất hiện trong yêu cầu làm mới sau khi thay). Bộ chuyển đổi tham số thêm `sid` đọc từ token truy cập hiện có (`addParametersConverter`, giữ tham số mặc định); độ lệch đồng hồ `app.security.client.clock-skew` 5 giây thay 60 giây.
- **`SessionAuthorizedClientManager` (mới, bọc bộ quản lý trên):** (1) tuần tự hóa theo mã phiên BFF: một `Mono` dùng chung (`cache()`) cho mỗi phiên, xóa khỏi bảng khi xong; (2) phân loại lỗi: `invalid_grant` thì `session.invalidate()` + xóa ánh xạ theo phiên rồi 401; mọi lỗi khác (5xx, phản hồi không đọc được, mất kết nối) thì giữ phiên và trả 503, bằng `ResponseStatusException`.
- **`SessionMappingStore.unlinkBySession` (mới):** xóa ánh xạ của phiên bị bỏ, chỉ xóa `storefront:sid:<sid>` nếu nó còn trỏ đúng phiên này.
- **Kết quả các điều "chưa kiểm" của T5:** (a) `TokenRelay` lấy bộ quản lý từ bean của Spring, bean tự định nghĩa được dùng; (b) chỗ bắt lỗi là bên trong bộ quản lý, `ResponseStatusException` ném từ đó thành đúng mã trả về; (c) yêu cầu đồng thời thứ hai không ghi đè token mới (test KT-15: sau 6 yêu cầu chồng nhau yêu cầu kế tiếp không làm mới lại).
- **Exchange trong bộ quản lý:** lấy từ ngữ cảnh Reactor dưới khóa lớp `ServerWebExchange` (Spring Security đặt), không phải bộ lọc ngữ cảnh của spring-web; nếu thiếu thì chạy thẳng bộ quản lý gốc.
- **Khe hở chấp nhận:** yêu cầu đến ngay sau khi lần làm mới kết thúc nhưng trước khi phiên của yêu cầu làm mới được ghi vào Redis (cuối bộ lọc `saveSession`) có thể thấy token cũ và làm mới lại bằng mã làm mới đã xoay, nhận `invalid_grant` và 401 (giao diện đăng nhập lại, SSO im lặng nếu phiên Xác thực còn). Cửa sổ rất ngắn; chưa đo trên chạy thật.
- **Test:** `login(..., accessExpiresIn)` (1 giây = hết hạn ngay), `respondToRefreshWithNewTokens`, `refreshRequests()`, `FakeHttpServer.delayResponses`. Lời gọi `/api/**` không có cookie hợp lệ trả 401 chỉ khi gửi `Accept: application/json` (không thì 302 sang đăng nhập, đúng thiết kế cho điều hướng trình duyệt).
- **Sửa phụ:** `ReactiveRedisRateLimiterTest` dùng thời gian chờ 500 ms cho ca bình thường nên lần gọi đầu lúc máy bận có thể bị mở cửa nhầm; đổi 5 giây (ca "Redis im lặng" giữ thời gian chờ ngắn riêng).

## Ghi nhận khi làm bước 9 (nhóm B, `2026-10-18`)

Thay đổi và lý do, ở `storefront-web-bff`.

**CSRF (T4)**
- `CsrfConfiguration`: `CookieServerCsrfTokenRepository.withHttpOnlyFalse()` với `Path=/`, `SameSite=Lax`, không `Max-Age`. Tách thành bean riêng vì bộ xử lý đăng nhập thành công cũng cần kho này.
- **Bộ xử lý mã thô** (`ServerCsrfTokenRequestAttributeHandler`), không dùng mặc định XOR của Spring Security 6+: SPA đọc cookie rồi gửi lại **nguyên giá trị** trong `X-XSRF-TOKEN`. Mã XOR (chống BREACH) cần mã đã che mỗi lần, không hợp với cách gửi hai lần đọc từ cookie.
- **Bộ lọc buộc phát cookie** (`csrfCookieFilter`, sau `SecurityWebFiltersOrder.CSRF`): WebFlux chỉ phát cookie khi mã được dùng; không có bộ lọc thì `GET` đầu tiên không có cookie (test KT-09 đỏ khi bỏ). Đã kiểm điều T4 ghi "chưa kiểm".
- **Xoay mã ở callback:** `SessionMappingAuthenticationSuccessHandler` tạo và lưu mã mới sau khi lưu ánh xạ (kể cả xác thực im lặng).
- **Xóa khi đăng xuất:** `CsrfServerLogoutHandler` của Spring tự có khi bật CSRF; test xác nhận cookie bị `Max-Age=0`.
- **Phiên mất mà cookie còn khớp:** yêu cầu ghi nhận 401, không phải 403 (CSRF kiểm trước, rồi mới tới xác thực).
- `/bff/internal/**` giữ chuỗi riêng không CSRF; `POST /bff/auth/logout` cần CSRF (nên SPA gọi đăng xuất phải mang mã).
- **Hệ quả cho yêu cầu ghi công khai** (`POST /api/identity/users/resend-verification`): người thăm chưa từng có cookie nhận 403 `CSRF_TOKEN_INVALID` ở lần ghi đầu; SPA gọi một `GET` (ví dụ `/bff/auth/session`) để lấy cookie rồi thử lại một lần, theo R8.

**Hành vi lỗi hai loại người gọi (bảng chốt với người dùng, xem `service.md` của BFF mục 3.1)**
- `SecurityErrorHandlers`, `ErrorFrame`: ba bộ xử lý bảo mật (`UNAUTHENTICATED` 401, `CSRF_TOKEN_INVALID` 403, `FORBIDDEN` 403) và bộ viết khung lỗi chung. Điểm vào xác thực thành 401 mọi `Accept`; không còn chuyển hướng sang đăng nhập cho `/api/**` (người gọi là fetch; 302 sang host khác thành lỗi CORS).
- `BffErrorWebExceptionHandler` (order -2, trước bộ xử lý của Boot): mọi lỗi chưa xử lý (404, 400, 401 và 503 của làm mới, 500) thành khung lỗi chung với mã chữ ổn định, thay Whitelabel và JSON thô.
- **Đăng xuất khi hết phiên:** không còn nhánh chuyển hướng `/login?logout` của Spring; vẫn 202 + `Location` về trang đích. Phiên Xác thực (SSO) có thể còn tới hạn của nó vì không còn `id_token_hint` để kết thúc (xem `03-logout/tech.md` T1).
- **Phiên mất client ủy quyền:** `SessionAuthorizedClientManager` bắt `ClientAuthorizationRequiredException` (Spring ném khi phiên không còn client) cùng với `invalid_grant`: xóa phiên, xóa ánh xạ, 401. Trước đó ca này ra 503 (bị coi là lỗi tạm thời) và `TokenRelay` có thể chuyển yêu cầu đi không có token.
- **Dịch vụ sau trả 401:** chuyển nguyên và giữ phiên (có test). Lý do: tín hiệu mơ hồ (thu hồi thật, hay dịch vụ sau cấu hình sai `aud`, `iss`, khóa, đồng hồ); hai đường thu hồi thật đã tự dọn phiên (báo `{sid}` và `invalid_grant`); xóa phiên khi dịch vụ sau lỗi cấu hình chỉ gây vòng lặp đăng nhập. SPA phải chặn vòng lặp (đăng nhập xong mà vẫn 401 thì dừng, hiện lỗi).
- **Lỗi callback:** giữ `/login?error` của Spring OAuth2 Client, không thêm mã. Qua cổng, đường này rơi xuống route `/login` của giao diện (BFF không có trang này, đo được 404 khi gọi thẳng). Giao diện hiện thông báo chung và không tự động chuyển lại sang đăng nhập khi thấy `?error`.

**Đã kiểm điều "chưa kiểm" của T4:** WebFlux chỉ phát cookie khi có bộ lọc kích hoạt (đúng); handler đăng xuất xóa mã (đúng).

**Việc còn lại liên quan, ngoài bước này (sau đó đã làm `2026-10-18`, xem `service/oauth2-service/service.md` mục 7.1):** bảng hành vi lỗi của `oauth2-service` (điểm cấp token và `/userinfo` luôn 401 theo RFC 6749 và 6750, đường dẫn lạ không chuyển hướng người gọi JSON về `/login`, trang lỗi riêng thay Whitelabel) chưa làm; kiểm bằng test từng hàng của bảng đo hiện trạng.

## Ghi nhận khi làm bước 10 (log và địa chỉ tin cậy, `2026-10-18`)

Thay đổi và lý do. Quyết định của T7 giữ nguyên; các điều "chưa kiểm" của T7 nay đã rõ (cuối mục).

**`observability-starter`**
- `ClientIpResolver` (mới, dùng chung servlet và WebFlux): chọn `client.ip` từ `X-Client-Ip` **chỉ khi** kết nối tới từ proxy tin cậy (`observability.logging.http.trusted-proxies`, địa chỉ IP hoặc dải CIDR, mặc định loopback) và giá trị đúng là một địa chỉ IP dạng chữ số (không phân giải tên, không ký tự lạ, nên không chèn được vào log); còn lại dùng địa chỉ kết nối. `RequestLoggingFilter` (servlet) dùng nó nên mọi dịch vụ servlet (Xác thực và các dịch vụ khác) tự đúng, không sửa riêng. `HttpLoggingProperties` thêm trường `trustedProxies`.
- `ReactiveRequestLoggingFilter` (mới, WebFlux): một dòng hoàn tất mỗi yêu cầu cùng tên trường và logger `http.request` với bản servlet, ghi trong `doFinally` nên phủ cả yêu cầu bị từ chối trước khi tới tuyến (host lạ 404, 401 của Spring Security). `traceId`, `spanId` lấy từ ngữ cảnh quan sát của yêu cầu (`ServerRequestObservationContext.findCurrent`), không từ MDC của luồng vì ở WebFlux việc ghi có thể xảy ra trên luồng khác. Chưa ghi nội dung yêu cầu khi lỗi (hai cổng không đọc thân; đệm thân trong WebFlux chưa làm). Tự đăng ký cho ứng dụng reactive (`RequestLoggingAutoConfiguration.ReactiveRequestLogging`), cùng cờ `observability.logging.http.enabled`.
- `reactor-core` thêm vào starter dạng tùy chọn (chỉ để biên dịch bộ lọc WebFlux).

**`api-gateway`**
- `ForwardedHeadersSanitizer` đặt `X-Client-Ip` bằng địa chỉ kết nối (cùng `ClientAddress` dùng làm khóa giới hạn tần suất), sau khi xóa giá trị client gửi. Cổng là điểm vào ngoài cùng nên `observability.logging.http.trusted-proxies` để **rỗng** (không tin tiêu đề `X-Client-Ip` nào khi ghi log của chính nó).
- Phụ thuộc `observability-starter` và `logback-spring.xml` dùng cấu hình dùng chung.

**`storefront-web-bff`**
- `ClientIpFilter` (bộ lọc toàn cục): đặt `X-Client-Ip` xuống dịch vụ sau bằng kết quả của `ClientIpResolver`: giữ giá trị cổng đặt khi kết nối tới từ proxy tin cậy, thay bằng địa chỉ kết nối khi gọi thẳng hoặc giá trị hỏng. Lý do: dịch vụ sau chỉ phải tin kết nối từ BFF.
- Cũng phụ thuộc `observability-starter` và `logback-spring.xml`.

**Điều "chưa kiểm" của T7, nay đã rõ**
- Spring Cloud Gateway **tự truyền `traceparent`** xuống dịch vụ sau khi có truy vết (test: `traceId` trong dòng log của cổng và của BFF trùng với `traceparent` mà dịch vụ sau nhận); không cần bộ lọc truyền tay.
- Địa chỉ từ xa ở WebFlux đọc được từ `ServerHttpRequest.getRemoteAddress()` và là địa chỉ kết nối thật (test: `127.0.0.1`, không phải tiêu đề giả).
- `X-Client-Ip` tới được dịch vụ sau không bị đổi (test với dịch vụ giả).
- Thêm khi chạy: khi `observability-starter` vào ứng dụng WebFlux, cổng quản trị tự nằm ở `server.port + 10000`, chỉ nghe `127.0.0.1` (cổng 18000 và 18090); thêm kết xuất span OTLP (cần Collector, không có thì OTel ghi cảnh báo bất đồng bộ, không ảnh hưởng yêu cầu).

**Còn lại cho bước 11 (chạy thật):** KT-24 và KT-25 ở mức hệ thống: gọi qua cổng thật tới Xác thực, đọc log JSON của ba dịch vụ để thấy dòng hoàn tất có cùng `trace.id` và `client.ip` đúng. Ghi nợ (xem lại ở demo bằng Docker Compose): địa chỉ kết nối của `api-gateway` có thể là địa chỉ cầu của Docker thay vì địa chỉ thật; khi có bộ cân bằng tải đứng trước thì đổi sang tin tiêu đề của nó (R2).

**Thử `ObservationHandler` thay cho bộ lọc log WebFlux (`2026-10-18`, kết luận: giữ bộ lọc).** Đã viết thử một `ObservationHandler<ServerRequestObservationContext>` ghi dòng hoàn tất khi quan sát của yêu cầu dừng và chạy cùng hai bộ `RequestLogTest` (cổng, BFF): cả hai cách đều xanh, kể cả ca host lạ 404 và 401 của Spring Security. Hai điểm quyết định:
- Khi tắt quan sát máy chủ HTTP (`management.observations.enable.http.server.requests=false`), cách `ObservationHandler` **mất toàn bộ dòng log** (0 dòng), còn bộ lọc vẫn ghi dòng (chỉ thiếu `traceId`). Dòng hoàn tất là nhật ký truy cập, không được phụ thuộc vào việc quan sát có bật hay không; bộ lọc giữ được bảo đảm "mỗi yêu cầu đúng một dòng" tốt hơn.
- Điều handler hơn là `http.route` (`getPathPattern`), nhưng chỉ có với endpoint của controller (`/bff/auth/session`), còn yêu cầu đi qua tuyến của Gateway (đa số lưu lượng của cổng và BFF) thì không có khuôn đường dẫn nên vẫn trống; không đáng đổi.
Đã gỡ phần thử khỏi mã (không còn công tắc `reactive-style`). Nếu sau này cần `http.route` cho endpoint của controller thì thêm trực tiếp vào bộ lọc.

## Ghi nhận khi làm bước 11 (chạy thật, `2026-10-18`)

Chạy ba dịch vụ thật (`oauth2-service` profile `dev`, `storefront-web-bff`, `api-gateway`) trên Redis, Postgres, Kafka dev; gọi bằng curl có kho cookie theo host qua `auth.localhost:8000` và `shop.localhost:8000`, và một lần bằng Chrome thật (đăng nhập chéo host, `fetch` như SPA). Đọc log JSON của cả ba, khóa Redis, bảng phiên sau mỗi ca. Dữ liệu thử đã dọn (xem cuối mục).

**Kết quả theo kịch bản**

| KT / ca | Kết quả |
|---|---|
| KT-31 đăng nhập chéo host | Đạt (curl và Chrome). Trang đăng nhập ở `auth`, callback về `shop`, cookie đúng host (`STOREFRONT_SESSION` HttpOnly và `XSRF-TOKEN` đọc được chỉ ở `shop`). JavaScript thấy duy nhất `XSRF-TOKEN` |
| KT-22 PKCE và `state` | Đạt. Yêu cầu cấp mã có `code_challenge` (S256) và `state`; callback với `state` sai nhận 302 `/login?error`, không có phiên (`/bff/auth/session` 401) |
| Gọi API với token thật (TokenRelay) | Đạt: 200; không phiên: 401 `UNAUTHENTICATED` khung lỗi chung |
| Làm mới token (KT-13) | Đạt: sau khi access hết hạn, BFF làm mới (mỗi lần một `POST /oauth2/token` 200), yêu cầu vẫn 200 |
| KT-15 đồng thời | Đạt: 5 yêu cầu cùng lúc khi token hết hạn, cả 5 trả 200, **đúng 1** lần gọi `/oauth2/token` |
| KT-38 xác thực im lặng | Đạt: vào lại `/bff/auth/login` khi phiên Xác thực còn thì được cấp mã không hỏi mật khẩu, về callback; ánh xạ `sid` được thay, không thừa khóa |
| Hai thiết bị | Đạt: hai `sid`, hai phiên BFF, hai phiên Xác thực; đăng xuất một thiết bị không ảnh hưởng thiết bị kia |
| CSRF (KT-08, 11, 34) | Đạt: thiếu hoặc sai mã nhận 403 `CSRF_TOKEN_INVALID` và phiên còn; đúng mã tới được dịch vụ (409 nghiệp vụ); đăng xuất thiếu mã 403; cookie CSRF bị xóa khi đăng xuất |
| KT-20, 21 đăng xuất ở BFF | Đạt sau khi sửa (xem lỗi 1): 202 + `Location` có `id_token_hint` và `post_logout_redirect_uri` ở host `shop`; theo `Location` ở host `auth` thì 302 về `http://shop.localhost:8000/`; phiên Xác thực, ủy quyền, ánh xạ và phiên BFF đều hết |
| KT-19 báo đăng xuất từ Xác thực | Đạt: đăng xuất thẳng ở host `auth` (không qua BFF) thì BFF xóa phiên và ánh xạ, cookie cũ nhận 401 |
| `invalid_grant` (R12) | Đạt: xóa bản ghi ủy quyền rồi gọi khi token hết hạn: 401, phiên và ánh xạ bị xóa, gọi lại không làm mới nữa |
| KT-24 địa chỉ tin cậy | Đạt: qua cổng, dịch vụ sau ghi `client.ip` là địa chỉ kết nối dù client gửi `X-Client-Ip` và `X-Forwarded-For` giả. Gọi thẳng dịch vụ từ địa chỉ LAN (không loopback) kèm `X-Client-Ip` giả: cả ba dịch vụ ghi địa chỉ LAN, bỏ qua tiêu đề |
| KT-25 truy vết | Đạt: một yêu cầu qua cổng, BFF, Xác thực có dòng hoàn tất ở cả ba với cùng `trace.id` |
| 11 lần đăng ký (A, R7) | Đạt: lượt thứ 10 trong cửa sổ bị 429 `TOO_MANY_REQUESTS`, `Retry-After` 3586 giây (đúng thời gian còn lại); `POST /login` không bị luật này |
| Host lạ | Đạt: `evil.example`, `localhost:8000`, `ops.localhost` đều 404 |
| Dev profile của BFF | Đạt: ánh xạ có hạn 240 giây (`app.session.ttl=3m` cộng ân hạn 1 phút) |
| Log | Cổng và BFF: không có JWT, mật khẩu, `id_token_hint`, mã phiên. Xác thực: không có mật khẩu; token chỉ có khi bật cờ `reveal-secrets` của dev (có chủ ý) |

**Lỗi tìm thấy khi chạy thật, đã sửa (có test đỏ trước)**
1. **Đăng xuất để lại phiên BFF cùng token trong Redis.** Chuỗi đăng xuất mặc định của WebFlux (`ServerHttpSecurity.LogoutSpec`) chỉ có `SecurityContextServerLogoutHandler` (xóa ngữ cảnh bảo mật), **không có** bộ xóa phiên. Theo dõi bằng `MONITOR`: lúc xóa ngữ cảnh, `WebSessionServerSecurityContextRepository.save` gọi `changeSessionId()` và phiên được lưu với mã mới (`RENAME` khóa cũ sang khóa mới), không lệnh `DEL` nào cho khóa phiên; phiên (access token, refresh token, ngữ cảnh bảo mật) nằm lại trong Redis tới hết hạn (7 ngày ở mặc định). `LogoutTest` cũ không thấy vì kiểm theo mã cũ (đã "biến mất" do đổi tên). Sửa: thêm `WebSessionServerLogoutHandler` (bộ xóa phiên chuẩn của Spring) vào `.logout(...)`; test mới đếm phiên còn giữ token bất kể mã (`RedisContainerHolder.sessionsHoldingTokens`) ở các ca đăng xuất, báo đăng xuất, làm mới thất bại; bỏ bộ xử lý thì `LogoutTest` đỏ. (Bản sửa đầu tiên là một bộ xử lý tự viết xóa trong `beforeCommit`, dựa trên giả định sai rằng chuỗi mặc định đã gọi `invalidate()`; đã thay bằng bộ chuẩn sau khi kiểm lại bằng `MONITOR` và bytecode.)
2. **BFF chạy với mức log DEBUG cho Spring Security, OAuth2 và Gateway** (còn sót trong `application.properties`): dòng log in tiêu đề yêu cầu gồm cookie phiên và JWT ra console. Đã bỏ; bật tạm khi cần bằng điểm cuối `loggers`.
3. **Yêu cầu đầu tiên sau khi khởi động bị mở cửa nhầm** ở cổng: kết nối Redis mở lần đầu chậm hơn thời gian chờ 200 ms nên lượt đó không được đếm, kèm một dòng ERROR "onErrorDropped" của Reactor khi lỗi kết nối báo muộn. Sửa: mở sẵn kết nối lúc khởi động (`ApplicationRunner` ping, thời gian chờ rộng), và xử lý lỗi báo muộn của lần gọi đã hết giờ để không thành ERROR. Có test tái hiện (Redis chậm kết nối).
4. **`SessionRevokeController` ghi cả "cleared" lẫn "no mapping"** cho một lần xóa thành công, vì `deleteById` trả `Mono<Void>` luôn rỗng nên `switchIfEmpty` luôn chạy. Chỉ lệch log, không lệch hành vi. Đã sửa, có test kiểm log.
5. **Thiếu `application-dev.properties` của BFF** (T6 ghi dev 3 phút nhưng chưa có file). Đã thêm.

**Điều tìm thấy ngoài phạm vi feature 06 (ghi lại; hạn refresh token đã sửa sau đó)**
- **Hạn refresh token ở `oauth2-service` không theo `app.session.user.refresh-ttl` (feature 02): đã điều tra và sửa `2026-10-18`.** Nguyên nhân gốc: Spring Authorization Server tra bộ sinh token bằng `getIfUnique()` trên kiểu `OAuth2TokenGenerator`, dự án có hai bean kiểu này nên nhận `null` và dùng bộ sinh mặc định (TTL cố định 24 giờ của client); `SessionBoundRefreshTokenGenerator` chưa từng được gọi. Sửa bằng `@Primary` cho bộ sinh gộp. Chi tiết, test và kiểm chứng chạy thật: `global/technical-debt.md` mục 3.
- Khi máy chủ giao diện chết, cổng trả 500 (kèm dòng ERROR "Connection refused") thay vì 502 hoặc 503 có khung lỗi. Chưa xử lý.
- Cảnh báo lúc khởi động `Global AuthenticationManager configured with an AuthenticationProvider bean` ở `oauth2-service` đã có từ trước.

**Dữ liệu thử đã dọn:** tài khoản `step11-*`, mọi hàng `login_sessions`, `client_sessions`, `oauth2_authorization` (gồm các hàng của client dịch vụ `oauth2-service-internal` do việc báo đăng xuất tạo ra), `spring_session` của Xác thực; Redis: khóa `storefront:*`, `spring:session:*`, `ratelimit:*`, `login_pair:*`, `login_email:*`. Giữ nguyên: `outbox_events` (3 dòng có từ trước lần chạy này) và khóa `{inventory:...}`. Sự kiện `UserRegistered` của tài khoản thử đã được ghi vào Kafka theo outbox lúc đăng ký (không thu hồi được).

## Ghi nhận khi đóng feature (bước 12, `2026-10-18`)

- **ADR-0006 sang Accepted.** Cả ba điều "Cần kiểm" đã qua ở Chrome trên Windows và curl (R25, R26): `SameSite=Lax` không cản đăng nhập và đăng xuất chéo host, `*.localhost` qua `::1` dùng được, cờ `Secure` theo lược đồ của yêu cầu. Firefox và Safari chưa thử (ghi ở `deferred.md`).
- **ADR-0005:** mục "CSRF ở BFF" từ "chưa làm" sang đã làm kèm cơ chế đã chọn (mã thô, bộ lọc buộc phát cookie, xoay ở callback, xóa khi đăng xuất, tự phục hồi bằng `GET` rồi thử một lần; gia hạn trượt bỏ vì cookie CSRF cùng loại với cookie phiên); mục `__Host-` ghi lại rằng host riêng với cookie theo máy đã đủ ở C0. ADR-0005 vẫn Proposed, chờ giao diện (feature 05) gửi đúng `X-XSRF-TOKEN`.
- **Nợ của feature 02 và 03 đã trả:** BFF gửi `sid` khi làm mới và nhận `{"sid"}` để xóa phiên; CSRF ở BFF; `client.ip` đúng ở dịch vụ sau; **PKCE của `storefront-web-bff` bật lại** (`app.clients.storefront-web.require-proof-key` mặc định `true`, trước đó `false` "tạm tắt cho tới feature 06"). BFF đã gửi `code_challenge` S256 từ trước (KT-22); seeder đồng bộ cờ này vào bản ghi client mỗi lần khởi động (dev DB đã đổi sang `true`), và đăng nhập qua BFF chạy lại sau khi bật.
- **Tài liệu đã cập nhật cho khớp mã:** `security-architecture.md` (định tuyến theo `Host`), `spring-security-logout-bff.md` (tên lớp, `sid`), `4. communication.md` và `notification-service/service.md` (điểm cuối `GET /bff/auth/ws-ticket`), `rate-limiting-layers.md` mục 8, `api.yaml` của BFF (đăng xuất, `401`), trạng thái các mục T1 đến T9 ở trên, hai `service.md` (ngày cập nhật, tham chiếu test), `flow.md` (KT-36, trạng thái), `plan.md` (cột "Triển khai" đủ), `00-features.md` (hàng 06, điều học được, nợ của 02, 03, 07).
- **Nợ ghi lại:** `deferred.md` (chín phần hoãn trong phạm vi) và `global/technical-debt.md` mục 3 (ba việc ngoài phạm vi), `api-gateway/service.md` TD-07.
