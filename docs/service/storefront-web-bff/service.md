# Cổng ứng dụng Cửa hàng, kênh web: service (`storefront-web-bff`)

> **Trạng thái:** DRAFT · **Cập nhật:** `2026-10-18` · **Thư mục mã:** `services/storefront-web-bff`
>
> Tầng kỹ thuật của BFF kênh web cho ứng dụng Cửa hàng. **Không có `analysis.md`**: BFF không có nghiệp vụ riêng (không có aggregate, lệnh hay sự kiện); nó giữ phiên và token thay cho trình duyệt, chuyển tiếp API và áp dụng các lớp bảo vệ. Vì vậy file này là nguồn thiết kế duy nhất và nhiều mục của khuôn mẫu ghi "N/A".
>
> Mô tả thiết kế **đích** sau feature 06 ([`stage/c0-foundation/06-access-gateway/`](../../stage/c0-foundation/06-access-gateway/flow.md)). Tên đã chốt ở bước 4b của feature 06: đường dẫn `/bff/**`, cookie `STOREFRONT_SESSION`, tiền tố khóa Redis `storefront:`, phạm vi token `storefront.internal`, client OAuth2 `storefront-web`.
>
> Cổng ngoài cùng đứng trước: [`api-gateway`](../api-gateway/service.md).

## 1. Tổng quan kỹ thuật

BFF kênh web của ứng dụng Cửa hàng (host `shop`, ADR-0006): thay trình duyệt làm việc với Xác thực (OAuth2 mã ủy quyền kèm PKCE), giữ token phía máy chủ, trình duyệt chỉ có cookie phiên; chuyển tiếp API kèm token; chống CSRF; nhận báo kết thúc phiên từ Xác thực.

- **Loại service:** gateway kiểu BFF (không có domain).
- **Stack:** Java 21, Spring Boot 4, Spring Cloud Gateway Server WebFlux, Spring Security (OAuth2 Client, Resource Server), Spring Session Redis, Redis phản ứng (reactive).
- **Kiến trúc trong service:** cấu hình và bộ lọc, không hexagonal. **Module:** `infrastructure/configuration` (bảo mật, tuyến, Redis, CORS), `infrastructure/ratelimit`, `presentation` (endpoint phiên, đăng xuất, nhận báo).
- **Kênh khác:** kênh di động của cùng ứng dụng là BFF riêng (`storefront-mobile-bff`, không cookie, không CSRF, PKCE; ADR-0006), không thuộc file này.

**Trách nhiệm:** (1) đăng nhập OAuth2 thay trình duyệt; (2) phiên BFF và ánh xạ sang `sid`; (3) CSRF; (4) chuyển tiếp API kèm token (`TokenRelay`); (5) làm mới token và tự phục hồi; (6) nhận báo kết thúc phiên; (7) giới hạn tần suất theo người dùng; (8) ticket một lần cho WebSocket.

---

## 2. Ánh xạ domain → code

N/A: không có aggregate, thuật ngữ nghiệp vụ hay trục trạng thái. Khái niệm kỹ thuật:

| Khái niệm | Nghĩa | Biểu diễn |
|---|---|---|
| Phiên BFF | Phiên của trình duyệt ở BFF, giữ token | Spring Session Redis, cookie `STOREFRONT_SESSION` (`HttpOnly`, `Secure`, `SameSite=Lax`, không `Max-Age`) |
| Ủy quyền client | Token truy cập, token làm mới và hạn | Thuộc tính của phiên BFF (`WebSessionServerOAuth2AuthorizedClientRepository`) |
| `sid` | Mã phiên ứng dụng ở Xác thực (client `storefront-web`) | Claim của token; khóa của ánh xạ |
| Ánh xạ phiên | `sid` ↔ mã phiên BFF, hai chiều | Khóa Redis, xem [`data.md`](data.md) |
| Mã CSRF | Giá trị so khớp cookie và header | Cookie `XSRF-TOKEN` (không `HttpOnly`) |
| Ticket WebSocket | Mã dùng một lần, 10 giây, đổi sang `userId` | Khóa Redis `wsticket:` |

---

## 3. Đảm bảo quy tắc

Quy tắc **kỹ thuật và bảo mật** của BFF (không phải bất biến nghiệp vụ). Cột Test trỏ về kịch bản của feature 06 (`flow.md` mục 6); `—` nghĩa là chưa có test.

| ID | Quy tắc | Loại | Cơ chế | Test |
|---|---|---|---|---|
| `Q-01` | Mọi yêu cầu ghi của trình duyệt phải mang mã CSRF khớp; sai thì 403 `CSRF_TOKEN_INVALID`, phiên không bị hủy | Liên tục | CSRF của Spring Security, `CookieServerCsrfTokenRepository` không `HttpOnly`, mã thô (không XOR), bộ lọc buộc phát cookie, bộ xử lý lỗi riêng (T4) | KT-08, 09, 12, 34 (`CsrfTest`); KT-10 (`SecurityErrorHandlersTest`) |
| `Q-02` | Token không ra trình duyệt; trình duyệt chỉ có cookie phiên `HttpOnly` | Liên tục | Token nằm trong phiên Redis; cookie phiên `HttpOnly` | — |
| `Q-03` | Một `sid` ứng với tối đa một phiên BFF; hạn ánh xạ không ngắn hơn hạn phiên; hai hạn đặt lại cùng lúc khi lưu token mới | Liên tục | Bộ xử lý đăng nhập thành công và bộ làm mới (T6) | KT-16, 38, 39 (`SidMappingTest`) |
| `Q-04` | Mọi đường kết thúc phiên BFF (đăng xuất, `invalid_grant`, báo từ Xác thực) xóa cả phiên và ánh xạ | Liên tục | Dùng chung một thao tác xóa (T6) | KT-18, 20 (`InternalRevokeTest`, `LogoutTest`); KT-14 (`RefreshTest`); KT-21 (chạy thật) |
| `Q-05` | Làm mới chỉ xóa phiên khi không cứu được: `invalid_grant`, hoặc phiên còn nhưng mất client ủy quyền (đều 401); lỗi khác (5xx, mất kết nối) trả 503 và giữ phiên | Liên tục | `SessionAuthorizedClientManager` (T5) | KT-14, 40 (`RefreshTest`); phiên mất client (`ErrorResponsesTest`) |
| `Q-06` | `/api/**` cần đăng nhập, trừ nhóm đọc công khai; các đường còn lại cho qua | Liên tục | `authorizeExchange` ở `SecurityConfiguration`; nhóm công khai hiện có: `GET` danh mục, thương hiệu, sản phẩm của catalog và xác minh email của identity (**hiện có, xem `TD-05`**) | — |
| `Q-07` | `/bff/internal/**` chỉ nhận token dịch vụ phạm vi `storefront.internal`, không CSRF | Liên tục | Chuỗi bảo mật riêng, Resource Server JWT (kiểm bằng khóa công khai của Xác thực, lệch đồng hồ 5 giây) | KT-18, 23 (`InternalRevokeTest`); KT-12 (`CsrfTest`) |
| `Q-08` | Điểm vào của BFF không phụ thuộc `contextPath`: `GET /bff/auth/login` chuyển tới `/oauth2/authorization/storefront-web` dù `X-Forwarded-Prefix` có lọt tới; `/api/**` chưa có phiên luôn 401, không chuyển hướng | Liên tục | `AuthController.login` trả đường dẫn cố định (R30); điểm vào xác thực trả 401 khung lỗi chung | KT-36 (`EntryPointRedirectTest`), `ErrorResponsesTest` |
| `Q-09` | Địa chỉ công khai dựng từ tiêu đề cổng đặt (redirect URI ở host `shop`, điểm cấp mã ở host `auth`); cookie phiên là cookie theo máy (không `Domain`, `HttpOnly`) tên `STOREFRONT_SESSION`; CORS theo mẫu nguồn gốc cấu hình, vẫn cho cookie, không dùng `*` | Liên tục | `server.forward-headers-strategy=framework`, `authorization-uri` theo `AUTH_PUBLIC_URL`, `CorsFilterConfiguration` với `allowedOriginPatterns` (T9) | KT-28, 30 (`OriginsTest`), KT-35 (`BffCorsTest`) |

### 3.1 Hành vi lỗi theo hai loại người gọi

BFF nhận hai loại người gọi, và cách trả lỗi phụ thuộc loại, không phụ thuộc đoán từ `Accept`:

- **Loại 1, SPA nắm quyền quyết định (fetch/XHR):** `/api/**` có bảo vệ và công khai, `POST /bff/auth/logout`, `GET /bff/auth/session`, `/bff/auth/ws-ticket`. BFF trả mã trạng thái và khung lỗi chung (`success`, `code`, `message`, `errors`); không bao giờ 302. SPA đọc kết quả rồi tự quyết định có điều hướng không. Điều hướng thẳng một địa chỉ `/api/**` từ thanh địa chỉ cũng nhận 401 (giao diện không dẫn người dùng tới đó).
- **Loại 2, trình duyệt nắm quyền (điều hướng, không có mã SPA chen vào):** `/oauth2/authorization/{id}`, `/login/oauth2/code/{id}`, `/bff/auth/login`. Là giao thức đăng nhập của Spring OAuth2 Client (không phải chuẩn OAuth2), BFF trả 302 trực tiếp. Lỗi của callback (`state` sai, `error=` từ Xác thực) giữ hành vi mặc định của Spring: 302 về `/login?error` (không kèm mã lý do); giao diện hiện thông báo chung cho người dùng bấm đăng nhập lại và không tự động chuyển lại sang đăng nhập (tránh vòng lặp). Yêu cầu cho feature 05.
- **Ngoại lệ nối hai loại:** đăng xuất là fetch (loại 1) nhưng kết quả là chỉ dẫn điều hướng: `202` + `Location` tới `connect/logout` (cần điều hướng cấp cao để mang cookie của host `auth`); khi phiên đã mất và không có `id_token_hint`, vẫn `202` + `Location` về trang đích (phiên Xác thực có thể còn tới hạn của nó).
- **Nhóm nội bộ (`/bff/internal/**`):** máy gọi máy, chỉ 401, 403, 400, không HTML, không điều hướng.

| Tình huống (loại 1) | Phản hồi | Mã `code` |
|---|---|---|
| Chưa có phiên | 401 | `UNAUTHENTICATED` |
| Làm mới `invalid_grant`, hoặc phiên mất client ủy quyền | 401, xóa phiên và ánh xạ | `UNAUTHENTICATED` |
| Làm mới lỗi tạm thời | 503, giữ phiên | `SERVICE_UNAVAILABLE` |
| Dịch vụ sau trả 401, 403, 5xx | Chuyển nguyên, giữ phiên (SPA chặn vòng lặp: đăng nhập xong mà vẫn 401 thì dừng) | của dịch vụ sau |
| CSRF thiếu hoặc sai | 403, giữ phiên | `CSRF_TOKEN_INVALID` |
| Thiếu quyền | 403 | `FORBIDDEN` |
| Quá tần suất | 429 + `Retry-After` | `TOO_MANY_REQUESTS` |
| Đường dẫn lạ, yêu cầu sai | 404, 400 | `NOT_FOUND`, `BAD_REQUEST` |

---

## 4. Commands

N/A: BFF không có lệnh nghiệp vụ. Các thao tác thay đổi trạng thái chỉ là hạ tầng (`POST /bff/auth/logout`, `POST /bff/internal/sessions/revoke`), xem [`api.yaml`](api.yaml).

---

## 5. Queries

| Truy vấn | Handler | API | Quyền | Nguồn đọc |
|---|---|---|---|---|
| Trạng thái đăng nhập của phiên (`userId`, `role`) | `AuthController.session` | `GET /bff/auth/session` | Có phiên; không có thì 401 | Phiên BFF; `role` đọc từ claim `role` của token |

Cũng là điểm **phát lại mã CSRF** khi giao diện nhận 403 `CSRF_TOKEN_INVALID` (T4).

---

## 6. Use cases: tham gia

| Feature | Vai trò | Bước của service này | Bước bù trừ | Publishes |
|---|---|---|---|---|
| [02 Đăng nhập và phiên](../../stage/c0-foundation/02-login-session/flow.md) | Client của luồng đăng nhập | Bắt đầu cấp mã (có PKCE, `state`), đổi mã, lưu token, ghi ánh xạ | Không có | Không |
| [03 Đăng xuất](../../stage/c0-foundation/03-logout/flow.md) | Phía ứng dụng của đăng xuất OIDC | Xóa phiên, trả 202 kèm `Location`, nhận báo `{"sid"}` | Báo lặp hoặc đã mất ánh xạ vẫn 200 | Không |
| [06 Cổng truy cập](../../stage/c0-foundation/06-access-gateway/flow.md) | Chủ của feature | CSRF, làm mới kèm `sid`, tự phục hồi, ánh xạ theo `sid` | Không có | Không |

---

## 7. Integration contract

### 7.1 Publishes

N/A: không phát sự kiện.

### 7.2 Consumes

N/A: không nhận sự kiện từ broker.

### 7.3 Sync calls

| Chiều | Đối tác | Giao thức | Endpoint | Timeout | Retry | Khi đối tác lỗi |
|---|---|---|---|---|---|---|
| Outbound | Xác thực (`auth` host) | Chuyển hướng của trình duyệt | `GET /oauth2/authorize` | N/A | N/A | Trình duyệt thấy lỗi của Xác thực |
| Outbound | Xác thực | REST, kênh máy chủ | `POST /oauth2/token` (đổi mã; làm mới kèm `sid`) | Chưa định (`TQ-02`) | Không (mã đã dùng một lần) | Làm mới: `invalid_grant` thì xóa phiên và 401; lỗi khác 503 và giữ phiên |
| Outbound | Xác thực | REST | `GET /oauth2/jwks` | Mặc định của Spring | Do thư viện | Token dịch vụ không kiểm được thì từ chối (401) |
| Outbound | Dịch vụ sau (`oauth2`, `identity`, `customer`, `catalog`, `order`) | REST kèm token truy cập | `/api/<dịch vụ>/**` (viết lại thành `/api/**`) | Mặc định của Gateway | Không | Lỗi của dịch vụ sau trả nguyên (**tuyến hiện có, xem `TD-05`**) |
| Inbound | Xác thực | REST, token dịch vụ | `POST /bff/internal/sessions/revoke` | N/A | Bên gửi không thử lại | Luôn 200 khi hợp lệ, kể cả đã mất ánh xạ |
| Inbound | `websocket-gateway` | Redis | `wsticket:<ticket>` (đọc rồi xóa) | N/A | N/A | Ticket hết hạn thì từ chối bắt tay |

---

## 8. Projection

N/A: không có read model.

---

## 9. Concurrency

| ID | Điểm tranh chấp | Ai tranh chấp | Cơ chế | Thứ tự khoá | Khi xung đột | Phục vụ |
|---|---|---|---|---|---|---|
| `C-01` | Làm mới token cùng một phiên | Nhiều yêu cầu của trình duyệt cùng gặp token hết hạn | Một làm mới đang chạy cho mỗi phiên, các yêu cầu còn lại chờ cùng kết quả (T5); Xác thực không chặn được dùng đồng thời một mã làm mới (`TD-02`) | Không | Yêu cầu chờ kết quả chung; thất bại thì cùng xử lý theo `Q-05` | `Q-05` |
| `C-02` | Ghi ánh xạ `sid` | Callback đăng nhập lặp (xác thực im lặng) so với báo `{sid}` | Ghi đè, xóa phiên BFF cũ nếu khác; xóa lũy đẳng | Không | Ghi cuối thắng, xóa phiên đã mất vẫn 200 | `Q-03`, `Q-04` |

---

## 10. Độ tin cậy

| Hạng mục | Thiết kế | Phục vụ |
|---|---|---|
| Redis mất | Phiên BFF mất (người dùng quay lại đăng nhập, SSO nếu phiên Xác thực còn); giới hạn tần suất mở cửa kèm cảnh báo, lệnh đếm có thời gian chờ ngắn | `Q-04`, T3 |
| Xác thực chậm hoặc lỗi lúc làm mới | Trả 503, giữ phiên; không đăng xuất oan | `Q-05` |
| Báo kết thúc phiên bị mất (BFF không nhận được) | An toàn nhờ token truy cập hết hạn ngắn và mã làm mới đã bị xóa ở Xác thực; không thử lại ở C0 | Feature 03 `deferred.md` |

---

## 11. Job định kỳ

N/A: hạn của phiên, ánh xạ và ticket do TTL của Redis lo; không có job.

---

## 12. NFR → giải pháp

Chưa có phân tích nghiệp vụ nên chưa có kỳ vọng từ analysis. NFR của chặng ở `stage/c0-foundation/` (đo ở feature 08). Điểm thiết kế liên quan: giới hạn tần suất theo người dùng 300 mỗi 60 giây (mặc định, `rate-limiting-layers.md`); một lần gọi Redis cho mỗi yêu cầu có đăng nhập.

### 12.1 Cache

N/A: BFF không cache dữ liệu nghiệp vụ.

### 12.2 Observability

Một dòng log hoàn tất mỗi yêu cầu (cùng tên trường với feature 07), `traceId` truyền xuống dịch vụ sau qua `traceparent`, `client.ip` đúng nhờ tiêu đề `X-Client-Ip` do cổng đặt (T7). Chỉ số cần theo dõi: tỉ lệ làm mới thất bại theo loại, số yêu cầu 403 `CSRF_TOKEN_INVALID`, số 429.

---

## 13. Quyền & danh tính

| Hạng mục | Thiết kế | Phụ thuộc |
|---|---|---|
| Danh tính của người gọi | Phiên BFF (cookie) → token truy cập; claim `sub` (mã tài khoản), `role` (chuỗi), `sid` | Xác thực, hợp đồng danh tính (ADR-0003) |
| Quyền theo tuyến | `Q-06`; quyền thật kiểm ở từng dịch vụ sau, BFF không quyết quyền nghiệp vụ | Dịch vụ sau |
| Gọi nội bộ | Token dịch vụ phạm vi `storefront.internal` | Xác thực (client `oauth2-service-internal`) |

---

## 14. Quyết định kỹ thuật

Quyết định riêng của feature 06 nằm ở [`tech.md`](../../stage/c0-foundation/06-access-gateway/tech.md); file này chỉ trỏ.

| ID | Câu hỏi | Chọn | Phục vụ |
|---|---|---|---|
| `T-01` | CSRF và vòng đời mã | → `tech.md` T4 | `Q-01` |
| `T-02` | Làm mới kèm `sid`, tự phục hồi, tuần tự hóa | → `tech.md` T5 | `Q-05`, `C-01` |
| `T-03` | Ánh xạ phiên theo `sid`, hạn, nhận báo | → `tech.md` T6 | `Q-03`, `Q-04` |
| `T-04` | Địa chỉ client tin cậy và log | → `tech.md` T7 | Mục 12.2 |
| — | Nguồn gốc theo subdomain, đặt tên BFF | → [`ADR-0006`](../../global/2.architecture/adr/0006-application-origins-and-host-routing.md) | Mọi mục |
| — | CSRF ở BFF, cookie phiên | → [`ADR-0005`](../../global/2.architecture/adr/0005-browser-request-protection.md) | `Q-01` |
| — | Hợp đồng danh tính, làm mới theo `sid` | → [`ADR-0003`](../../global/2.architecture/adr/0003-login-session-and-identity-contract.md) | `Q-03`, `Q-05` |

---

## 15. Chưa chốt (kỹ thuật)

| ID | Câu hỏi | Ảnh hưởng | Phụ thuộc |
|---|---|---|---|
| `TQ-02` | Thời gian chờ của lời gọi làm mới và các lời gọi sang Xác thực | Mục 7.3, `Q-05` | `tech.md` T5 (viết khi làm bước 8) |
| `TQ-03` | Xoay mã CSRF ở callback và dùng `GET /bff/auth/session` làm điểm phát lại mã | Mục 3 `Q-01`, 5 | Người chốt T4 |

---

## 16. Lệch giữa thiết kế và code

Chưa rà code.

---

## 17. Dependencies

- **Services:** Xác thực (`oauth2-service`), `api-gateway` đứng trước, các dịch vụ sau đang có tuyến (`identity`, `customer`, `catalog`, `order`).
- **Infrastructure:** Redis (phiên, ánh xạ, ticket WebSocket, giới hạn tần suất); không dùng cơ sở dữ liệu quan hệ hay Kafka.

---

## 18. Nợ kỹ thuật

| ID | Hạng mục | Hiện trạng | Hệ quả nếu chưa trả | Vì sao hoãn | Trả khi nào | Liên quan |
|---|---|---|---|---|---|---|
| `TD-01` | Khóa trong tiến trình cho tuần tự hóa làm mới | Đúng với một thể hiện | Nhiều thể hiện BFF vẫn làm mới đồng thời | Một thể hiện ở C0 | Khi chạy nhiều thể hiện BFF: dùng khóa Redis | `C-01`, T5 |
| `TD-02` | Xác thực cho hai làm mới đồng thời cùng một mã cùng thành công | Xoay mã không nguyên tử | Hai bên đồng thời giữ mã khác nhau, một bên bị đăng xuất | Sửa cần bao cả `/oauth2/token` trong một giao dịch khóa hàng, ảnh hưởng feature 02 và 03 | Khi sửa đường token của Xác thực | `C-01`, feature 06 T5 |
| `TD-03` | Hạn phiên BFF bằng hằng số, không theo hạn refresh token thật | Phiên có thể sống lâu hơn refresh token, đường `invalid_grant` dọn | Một lần 401 rồi SSO thay vì tắt đúng lúc | Cách chính xác cần Xác thực trả `refresh_token_expires_in` | Khi muốn khớp chính xác | T5, T6 |
| `TD-04` | Log mức `DEBUG` cho Spring Security, OAuth2 và Gateway bật mặc định trong `application.properties` | Ồn, lộ chi tiết xử lý | Log quá lớn ở môi trường chung | Tiện cho dev | Trước khi đưa lên demo: đặt mức qua biến môi trường | Mục 12.2 |
| `TD-05` | Tuyến tới dịch vụ cũ (`identity`, `customer`, `catalog`, `order`) và danh sách đọc công khai của chúng đang chạy theo thiết kế cũ | Chưa thiết kế lại; chỉ đổi cách tới BFF theo host (ADR-0006) | Các đường này có thể không khớp mô hình danh tính mới | Chưa có feature của các BC đó | **Sửa khi làm feature của BC tương ứng**: mỗi BC sửa tuyến và `permitAll` của nó | `Q-06`, mục 7.3 |
| `TD-06` | Ngắt mạch cho lệnh đếm Redis | Mỗi yêu cầu chờ hết thời gian chờ ngắn khi Redis chết | Cảnh báo mỗi yêu cầu | Đủ cho C0 | Khi Redis chết lâu hoặc có tải thật | T3 |

## Tài liệu liên quan

- [`data.md`](data.md): khóa Redis của BFF.
- [`api.yaml`](api.yaml): hợp đồng HTTP của BFF.
- [`../api-gateway/service.md`](../api-gateway/service.md): cổng ngoài cùng đứng trước BFF.
- [`stage/c0-foundation/06-access-gateway/`](../../stage/c0-foundation/06-access-gateway/flow.md): feature đang làm cho BFF.
- ADR-0003, ADR-0005, ADR-0006 ở `global/2.architecture/adr/`; `global/3.technical/rate-limiting-layers.md`.
