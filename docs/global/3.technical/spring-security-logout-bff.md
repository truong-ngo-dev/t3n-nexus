# Spring Security 7: đăng xuất qua BFF và thu hồi phiên

**Trạng thái**: Tham chiếu cài đặt, khớp mã sau feature 06 (`2026-10-18`)  
**Áp dụng cho**: `oauth2-service` (Spring Authorization Server 7.0.5), `storefront-web-bff` (Spring Cloud Gateway WebFlux)  
**Spring Boot**: 4.0.x | **Spring Security**: 7.0.5 | **Spring Session**: 4.0.3

> Tài liệu này ở mức **framework**: lớp nào của Spring làm gì, lớp nào của ta nối vào đâu, và những chỗ Spring không làm như tên gọi gợi ý.
> Luồng nghiệp vụ và bất biến: `../../stage/c0-foundation/03-logout/flow.md` (Xác thực), `../../stage/c0-foundation/06-access-gateway/flow.md` (BFF).
> Mô hình phiên và hợp đồng danh tính: ADR-0003. Bảo vệ yêu cầu từ trình duyệt (CSRF): ADR-0005. Nguồn gốc theo `Host`: ADR-0006.

---

## 1. Thành phần và hai tầng phiên

```
oauth2-service        # Spring Authorization Server: điểm cuối đăng xuất OIDC, phiên người dùng và phiên ứng dụng
storefront-web-bff    # Spring Cloud Gateway (WebFlux): OAuth2 Client, kho phiên BFF ở Redis
```

Hai bên giữ phiên khác nhau và nối với nhau bằng **`sid`** (mã phiên ứng dụng, claim trong access token và ID token):

| Bên | Dữ liệu | Nơi lưu | Khóa / định danh |
|---|---|---|---|
| BFF | Phiên Spring của trình duyệt (token, ngữ cảnh bảo mật) | Redis `spring:session:sessions:<mã phiên>` | Cookie `STOREFRONT_SESSION` (`HttpOnly`, chỉ ở host `shop`) |
| BFF | Ánh xạ hai chiều `sid` ↔ phiên BFF | Redis `storefront:sid:<sid>` → mã phiên; `storefront:session:<mã phiên>` → `sid` | Hạn = hạn phiên + `app.session.mapping-grace` |
| Xác thực | Phiên người dùng (`LoginSession`) và phiên ứng dụng (`ClientSession`), bản ghi ủy quyền (mã làm mới) | Postgres | `sid` là khóa của phiên ứng dụng |
| Xác thực | Session đăng nhập của Spring (cookie `OAUTH2_SESSION`, chỉ ở host `auth`) | Spring Session JDBC | Mã session |

Hai host khác nhau (ADR-0006) nên hai kho cookie tách biệt: BFF không nhìn thấy cookie đăng nhập của Xác thực và ngược lại.

---

## 2. Endpoint liên quan

| Endpoint | Dịch vụ | Ai gọi, gọi thế nào |
|---|---|---|
| `POST /bff/auth/logout` | BFF | SPA bằng `fetch`, **bắt buộc có CSRF** (`X-XSRF-TOKEN`). Trả **202 + `Location`**, SPA tự điều hướng tới `Location` |
| `GET /connect/logout?id_token_hint=…&post_logout_redirect_uri=…` | Xác thực | Trình duyệt **điều hướng cấp cao** (để mang cookie `OAUTH2_SESSION` của host `auth`; `fetch` hay 302 sang host khác không mang được) |
| `POST /bff/internal/sessions/revoke` | BFF | Chỉ `oauth2-service`, token dịch vụ phạm vi `storefront.internal`, thân `{"sid":"<UUID>"}`; không CSRF, chuỗi bảo mật riêng |

`/connect/logout` do `OidcLogoutEndpointFilter` của Spring Authorization Server cung cấp. `/bff/internal/sessions/revoke` là lời gọi tự chế, **không phải** OIDC Back-Channel Logout chuẩn (không có `logout_token`); bảo vệ bằng token dịch vụ lấy qua `client_credentials` (client `oauth2-service-internal`).

---

## 3. Lớp Spring và lớp của ta

### 3.1 `oauth2-service`

| Lớp | Của | Vai trò |
|---|---|---|
| `OidcLogoutEndpointFilter`, `OidcLogoutAuthenticationConverter`, `OidcLogoutAuthenticationProvider` | Spring AS | Nhận `GET /connect/logout`, tách `id_token_hint`, `post_logout_redirect_uri`, `state`; kiểm hint tra được và đúng client, trang đích đã đăng ký |
| `SessionBoundAuthorizationService` + `SidAuthorizationLookup` | Ta | Tra bản ghi ủy quyền **theo `sid` trong hint** (không theo giá trị ID token, vì ID token đổi sau mỗi lần làm mới) |
| `IdTokenHintVerifier` (`NimbusIdTokenHintVerifier`) | Ta | Kiểm chữ ký hint, **không kiểm hạn** (hint hết hạn vẫn đăng xuất được) |
| `OidcLogoutSuccessHandler` | Ta | Thay bộ xử lý mặc định: kết thúc phiên, hủy session Spring, báo BFF, chuyển hướng |
| `LoginSessionLogout` + `EndLoginSession` | Ta | **Một giao dịch**: kết thúc phiên người dùng (kéo theo phiên ứng dụng) và xóa các bản ghi ủy quyền của chúng; khóa hàng khi nạp nên hai đăng xuất đồng thời nối tiếp nhau |
| `SecurityContextLogoutHandler` | Spring | Hủy session Spring (cookie `OAUTH2_SESSION` hết tác dụng). Chỉ gọi sau khi giao dịch đã commit |
| `EndedSessionNotifier` (`StorefrontWebBffEndedSessionNotifier`) | Ta | Sau commit, mỗi phiên ứng dụng của client `storefront-web` vừa kết thúc một lời gọi thu hồi; chỉ báo cho BFF |
| `StorefrontWebBffRevocationClient` | Ta | Mint token `client_credentials` mới mỗi lần, `POST /bff/internal/sessions/revoke`; chờ kết nối 3 giây, đọc 8 giây; **cố gắng hết sức**, lỗi chỉ ghi cảnh báo |
| `OidcLogoutFailureHandler` | Ta | Hint không nhận ra → `invalid_token`; trang đích lạ → `invalid_request`; không kết thúc phiên nào |

Spring Session JDBC không có cơ chế sự kiện đáng tin cậy tương đương keyspace notification của Redis, nên việc dọn dữ liệu của Xác thực làm **tường minh trong cùng giao dịch đăng xuất**, không dựa vào `SessionDeletedEvent`.

### 3.2 `storefront-web-bff` (WebFlux)

| Lớp | Của | Vai trò |
|---|---|---|
| `LogoutWebFilter` | Spring Security | Bắt `POST /bff/auth/logout`, chạy chuỗi bộ xử lý đăng xuất rồi bộ xử lý thành công |
| `CsrfWebFilter` + `CookieServerCsrfTokenRepository` | Spring Security | Yêu cầu `X-XSRF-TOKEN` khớp cookie `XSRF-TOKEN` (cookie không `HttpOnly`); thiếu hoặc sai → 403 `CSRF_TOKEN_INVALID` |
| `SecurityContextServerLogoutHandler` | Spring Security | **Có sẵn mặc định.** Xóa ngữ cảnh bảo mật |
| `WebSessionServerLogoutHandler` | Spring Security | **Phải thêm tường minh** (`.logout(spec -> spec.logoutHandler(new WebSessionServerLogoutHandler()))`): xóa phiên khỏi Redis. Xem mục 4 |
| `CsrfServerLogoutHandler` | Spring Security | Có sẵn khi bật CSRF: xóa cookie `XSRF-TOKEN` |
| `StorefrontWebBffLogoutSuccessHandler` | Ta | Đọc `sid` từ thuộc tính của người dùng OIDC, xóa ánh xạ (`SessionMappingStore.unlink`), dựng `Location` tới `/connect/logout` (kèm `id_token_hint`, `post_logout_redirect_uri` ở host `shop`) và trả 202. Không còn phiên OIDC (hết hạn, đăng xuất nơi khác) thì không có `id_token_hint`: vẫn 202 + `Location` về trang đích |
| `SessionMappingStore` | Ta | Một chỗ ghi và xóa ánh xạ `sid` ↔ phiên BFF: `link`, `unlink(sid)`, `unlinkBySession(mã phiên)` |
| `SessionMappingCleanupListener` | Ta | Khi phiên BFF **hết hạn tự nhiên** (Redis keyspace `expired`): dọn ánh xạ bằng Lua, chỉ xóa `storefront:sid:<sid>` nếu nó còn trỏ đúng phiên vừa hết hạn |
| `SessionRevokeController` | Ta | Nhận thu hồi từ Xác thực: `sid` không phải UUID → 400; xóa phiên và ánh xạ; **lũy đẳng** (không có ánh xạ vẫn 200) |
| `SessionAuthorizedClientManager` | Ta | Khi làm mới token nhận `invalid_grant` (hoặc phiên mất client ủy quyền): `invalidate` phiên, `unlinkBySession`, trả 401 |

`OidcClientInitiatedServerLogoutSuccessHandler` của Spring **không dùng**: nó trả 302, mà `fetch` không điều hướng được sang host khác kèm cookie; ta trả 202 + `Location` để SPA tự điều hướng.

---

## 4. Những chỗ Spring không làm như tên gọi (đã gặp khi chạy thật)

1. **Chuỗi đăng xuất WebFlux mặc định chỉ xóa ngữ cảnh bảo mật, không xóa phiên.** `ServerHttpSecurity.LogoutSpec` chỉ đăng ký `SecurityContextServerLogoutHandler`. Không thêm `WebSessionServerLogoutHandler` thì phiên chứa access token, refresh token và ngữ cảnh bảo mật nằm lại trong Redis tới hết hạn (7 ngày ở mặc định), dù mọi khóa ánh xạ đã mất và cookie cũ đã nhận 401.
2. **Lúc xóa ngữ cảnh, Spring đổi mã phiên.** `WebSessionServerSecurityContextRepository.save` gọi `WebSession.changeSessionId()`; ở Redis việc đổi mã thể hiện bằng `RENAME` khóa phiên lúc lưu. Hệ quả: **kiểm "phiên đã biến mất" theo mã cũ luôn xanh** dù phiên còn dưới mã mới. Test phải đếm phiên còn giữ token bất kể mã (`RedisContainerHolder.sessionsHoldingTokens` ở BFF).
3. **Xóa theo mã trong cookie trước khi lưu sẽ làm `RENAME` lỗi** (khóa nguồn không còn) và yêu cầu đăng xuất ra 500. Dùng bộ chuẩn `WebSessionServerLogoutHandler` thay vì tự xóa.
4. **`MONITOR` và bytecode là công cụ đáng dùng** để kiểm các điểm này: `redis-cli monitor` trong lúc chạy một ca, và `javap -v` (tham chiếu phương thức như `WebSession::changeSessionId` chỉ hiện ở phần bootstrap, `-c` không thấy).
5. **`SessionMappingCleanupListener` không chạy khi đăng xuất chủ động**: xóa phiên chủ động là sự kiện `del`, không phải `expired`. Vì vậy đăng xuất chủ động và hết hạn tự nhiên dùng hai cơ chế dọn khác nhau (mục 5).

---

## 5. Dọn dữ liệu theo từng đường kết thúc phiên

### 5.1 Người dùng bấm Đăng xuất (RP-Initiated Logout)

| Bước | Dọn gì | Ai làm |
|---|---|---|
| 1 | Ngữ cảnh bảo mật và phiên BFF `spring:session:sessions:…` | `SecurityContextServerLogoutHandler`, `WebSessionServerLogoutHandler` (BFF) |
| 2 | Cookie `XSRF-TOKEN` | `CsrfServerLogoutHandler` |
| 3 | Ánh xạ `storefront:sid:*` và `storefront:session:*` | `StorefrontWebBffLogoutSuccessHandler` → `SessionMappingStore.unlink(sid)` |
| 4 | Phiên người dùng, phiên ứng dụng, bản ghi ủy quyền (mã làm mới) | `OidcLogoutSuccessHandler` → `LoginSessionLogout` (một giao dịch, Xác thực) |
| 5 | Session Spring `OAUTH2_SESSION` | `SecurityContextLogoutHandler` (sau commit) |
| 6 | Thu hồi lần hai ở BFF (lưới an toàn, lũy đẳng: thường đã không còn ánh xạ) | `StorefrontWebBffRevocationClient` → `SessionRevokeController` |

Bước 1 đến 3 ở BFF (202). SPA điều hướng tới `Location`, bước 4 đến 6 xảy ra ở Xác thực trong cùng yêu cầu.

### 5.2 Đăng xuất hoặc kết thúc phiên ở nơi khác (hoặc đăng xuất thẳng ở host `auth`)

Xác thực kết thúc phiên (bước 4 đến 5) rồi `StorefrontWebBffRevocationClient` báo BFF (bước 6); BFF xóa phiên và ánh xạ nên cookie cũ nhận 401. Đã chạy thật: đăng xuất thẳng ở `connect/logout` không qua BFF vẫn xóa phiên BFF.

### 5.3 Phiên BFF hết hạn tự nhiên

| Bước | Dọn gì | Ai làm |
|---|---|---|
| 1 | Phiên BFF | Redis hết TTL → sự kiện `expired` |
| 2 | Ánh xạ | `SessionMappingCleanupListener` (Lua nguyên tử, chỉ xóa nếu ánh xạ còn trỏ đúng phiên này) |

Phiên Xác thực tồn tại độc lập tới hạn của nó. Hạn ánh xạ luôn không ngắn hơn hạn phiên BFF (`app.session.ttl` cộng `app.session.mapping-grace`).

### 5.4 Làm mới token thất bại (`invalid_grant`)

`SessionAuthorizedClientManager` xóa phiên BFF và ánh xạ, trả 401 `UNAUTHENTICATED`; SPA điều hướng tới `/bff/auth/login`, nếu phiên Xác thực còn thì SSO im lặng cấp lại không hỏi mật khẩu. Lỗi tạm thời (5xx, mất kết nối) thì **giữ phiên** và trả 503.

### 5.5 Chưa có: dọn bản ghi mồ côi của Xác thực

Phiên Xác thực hết hạn tự nhiên mà không có đăng xuất tường minh để lại phiên và bản ghi ủy quyền chờ job dọn (chưa có use case dọn; `02-login-session/deferred.md`). Mã làm mới trong đó không dùng được vì phiên đã quá hạn (`invalid_grant`).

---

## 6. Luồng chi tiết

### Đường A: người dùng bấm Đăng xuất

```
SPA → POST /bff/auth/logout      (Cookie: STOREFRONT_SESSION, XSRF-TOKEN; Header: X-XSRF-TOKEN)

[storefront-web-bff]
  CsrfWebFilter: mã khớp?  Không → 403 CSRF_TOKEN_INVALID (phiên giữ nguyên)
  LogoutWebFilter
    → SecurityContextServerLogoutHandler    ngữ cảnh bảo mật xóa
    → WebSessionServerLogoutHandler         phiên Redis xóa
    → CsrfServerLogoutHandler               cookie XSRF-TOKEN xóa
    → StorefrontWebBffLogoutSuccessHandler:
        sid = thuộc tính "sid" của OidcUser
        SessionMappingStore.unlink(sid)     ánh xạ xóa
        202 Accepted + Location: {auth}/connect/logout?id_token_hint=…&post_logout_redirect_uri={shop}/
        (không còn phiên OIDC → 202 + Location: {shop}/ , không có hint)

SPA: đọc Location → window.location = Location   (điều hướng cấp cao, mang cookie OAUTH2_SESSION của host auth)

[oauth2-service]
  GET /connect/logout?id_token_hint=…&post_logout_redirect_uri=…
    OidcLogoutEndpointFilter → Converter → Provider: hint tra được theo sid, đúng client, trang đích đã đăng ký
      (sai → OidcLogoutFailureHandler: 400 invalid_token / invalid_request, không kết thúc phiên nào)
    OidcLogoutSuccessHandler:
      LoginSessionLogout.end(…)             một giao dịch: phiên người dùng, phiên ứng dụng, bản ghi ủy quyền xóa
      SecurityContextLogoutHandler          session Spring hủy (chỉ khi vừa kết thúc phiên)
      EndedSessionNotifier.notifyEnded(…)   → POST /bff/internal/sessions/revoke {"sid"}   (lũy đẳng)
      302 post_logout_redirect_uri (kèm state)

SPA: về trang đích
```

### Đường B: Xác thực báo thu hồi

```
[oauth2-service] kết thúc phiên (đăng xuất ở thiết bị khác hoặc host auth, khóa tài khoản về sau)
  → StorefrontWebBffRevocationClient: mint token client_credentials (scope storefront.internal)
  → POST {bff}/bff/internal/sessions/revoke   {"sid":"<UUID>"}

[storefront-web-bff]  chuỗi nội bộ: JWT dịch vụ, độ lệch đồng hồ 5 giây, không CSRF
  SessionRevokeController:
    sid không phải UUID → 400
    SessionMappingStore.unlink(sid) → mã phiên → xóa phiên BFF
    không có ánh xạ → vẫn 200 (lũy đẳng)
```

### Đường C: phiên BFF hết hạn tự nhiên

```
[storefront-web-bff]  ReactiveRedisMessageListenerContainer (notify-keyspace-events Kx)
  session hash hết TTL → sự kiện "expired" trên khóa phiên
  RedisConfiguration.sessionExpiryContainer → SessionMappingCleanupListener.cleanupBySessionId(id):
    Lua nguyên tử: đọc sid từ storefront:session:<id>; xóa storefront:sid:<sid> nếu còn trỏ đúng <id>; xóa khóa ngược
```

---

## 7. Lưu ý

- **Đăng xuất của Xác thực luôn thành công kể cả khi phiên đã mất** (INV-ATH-05): hint hợp lệ mà không tìm ra phiên sống thì chỉ chuyển hướng, không lỗi, không event. Gọi hai lần, kể cả song song, đều thành công; một lần kết thúc thật.
- **Không có cookie đăng nhập thì không kết thúc phiên theo mỗi hint** (chốt tạm): hint không chứng minh người gọi đang cầm phiên.
- **Báo BFF là đồng bộ, cố gắng hết sức, không thử lại.** BFF chậm hoặc chết thì đăng xuất vẫn hoàn tất ở Xác thực; phiên BFF còn lại vô hại vì bản ghi ủy quyền đã xóa nên làm mới trả `invalid_grant` và access token hết hạn ngắn. Lưới an toàn thêm là hạn Redis của phiên BFF.
- **`id_token_hint` nằm trong URL** nên tránh vào log: Xác thực chỉ ghi tên tham số; dòng log hoàn tất của cổng và BFF chỉ ghi `url.path`, không có chuỗi truy vấn.
- **Mạng nội bộ không thay cho xác thực:** lời gọi thu hồi mang token dịch vụ (`storefront.internal`); thiếu 401, sai phạm vi 403.
- **Hướng nâng cấp:** thay lời gọi tự chế bằng Back-Channel Logout chuẩn OIDC (`logout_token`) và thêm thử lại: `03-logout/deferred.md`. Hạn phiên BFF theo hạn thật của refresh token (cách B): `06-access-gateway/deferred.md`.
