# ADR-012 — API Gateway (Entry Point cho toàn hệ thống)

**Status:** Accepted

## Context

- Hệ thống có nhiều loại client (browser SPA, mobile app tương lai) và nhiều BFF riêng cho từng loại (`web-gateway`, `mobile-gateway` — ADR-009), cộng thêm luồng login/MFA/register cần render trực tiếp bởi `oauth2-service` (Authorization Server) chứ không qua BFF nào.
- Nếu không có 1 entry point chung, mỗi client phải tự biết địa chỉ nội bộ của từng service (`web-gateway`, `oauth2-service`...) — lộ topology nội bộ ra ngoài, mỗi service phải tự lo TLS termination + rate limit thô riêng, và không có chỗ tập trung để set các header chuẩn hoá (`X-Forwarded-*`) cho toàn bộ downstream.
- Cần 1 lớp routing thuần đứng trước tất cả — không giữ business logic, không biết domain service nào cả (đã quyết ở ADR-009: `api-gateway` chỉ biết route tới đúng 3 downstream, không giữ route map chi tiết tới 8+ domain service).

## Decision

`api-gateway` là **entry point duy nhất** cho toàn bộ traffic từ bên ngoài (browser lẫn mobile app) — mọi request đều đi qua đây trước, **kể cả các trang được server-render bởi `oauth2-service`** (login form, MFA challenge, OIDC logout) — không có ngoại lệ nào được phép gọi thẳng vào service nội bộ từ browser/mobile.

### Vai trò

- TLS termination
- Rate limit coarse-grained theo IP (lớp thô nhất — business-specific rate limit vẫn nằm ở downstream, xem `3.technical/rate-limiting-layers.md`)
- Set `X-Forwarded-Host`/`X-Forwarded-Proto`/`X-Forwarded-Prefix` thủ công cho từng route (Spring Cloud Gateway server-webflux **không** tự inject 2 header đầu — khác `X-Forwarded-For` vốn có global filter riêng tự thêm sẵn — thiếu chúng thì `{baseUrl}` phía downstream vẫn resolve ra host:port nội bộ dù `X-Forwarded-Prefix` đã set đúng)
- Routing thuần theo path prefix, strip 1 segment mỗi route — **không** TokenRelay, **không** session, **không** business logic, **không** validate JWT, **không** giữ route map chi tiết tới domain service

### 3 route con — 2 BFF ngang hàng + 1 trường hợp đặc biệt

```
                          ┌─────────────┐
   Browser / Mobile ────▶ │ api-gateway │
                          └──────┬──────┘
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
          /web/**            /mobile/**           /auth/**
              │                  │                  │
        web-gateway       mobile-gateway      oauth2-service
       (BFF browser)     (BFF mobile,        (login form, MFA,
                          chưa có service      register, OIDC
                          thật — ADR-009)       logout)
```

- **`/web/**` → `web-gateway`** — BFF cho browser SPA: OAuth2 Client, session cookie httpOnly, business API proxy (`/api/{service}/**`).
- **`/mobile/**` → `mobile-gateway`** — BFF cho mobile app (bắt đầu với Shipper): OAuth2 Resource Server, aggregation, version gating, device binding. Chưa có service thật, route giữ chỗ theo thiết kế ADR-009.
- **`/auth/**` → `oauth2-service` thẳng, bypass cả 2 BFF** — trường hợp đặc biệt: **KHÔNG** phải con của bất kỳ BFF nào, mà **ngang hàng** với `web-gateway`/`mobile-gateway` ngay dưới `api-gateway`. Áp dụng cho login form, MFA challenge (`/mfa`, `/login/ott`), register (`POST /auth/register`), OIDC logout — những endpoint cần browser tương tác trực tiếp với Authorization Server (nhập password, nhận redirect code, submit OTP...). Cùng 1 route này dùng chung cho cả web lẫn mobile client (xem ADR-009 § Target flow) — không tách riêng theo client type.

### Vì sao `/auth/**` không đi qua BFF nào

`web-gateway`/`mobile-gateway` là OAuth2 Client/Resource Server — vai trò của chúng là *dùng* authorization code hoặc access token đã có sẵn, không phải *tạo ra* chúng. Đẩy luồng login/MFA/register qua BFF sẽ chỉ thêm 1 hop vô nghĩa (BFF không có logic xử lý form password/OTP), đồng thời buộc BFF phải biết chi tiết luồng nội bộ của `oauth2-service` — phá vỡ ranh giới đang có: "BFF chỉ lo session/token cho client tương ứng, `oauth2-service` lo toàn bộ authentication".

## Consequences

**+** 1 entry point duy nhất, public — client (browser/mobile) không cần biết bất kỳ địa chỉ nội bộ nào, kể cả `oauth2-service`
**+** `api-gateway` luôn là routing thuần, dễ audit, không phình logic theo thời gian — mọi business logic/session/token đều đẩy đúng xuống tầng của nó (BFF hoặc domain service)
**+** Symmetry giữa 2 BFF + 1 route đặc biệt — dễ mở rộng thêm client type mới (thêm 1 BFF ngang hàng dưới `api-gateway`) mà không đổi model hiện tại
**−** `X-Forwarded-*` phải set thủ công đúng ở từng route (Spring Cloud Gateway không tự làm) — quên 1 route là downstream build sai URL public (`{baseUrl}`, redirect `Location`, link trong email...), lỗi thường chỉ lộ ra ở nhánh ít test (VD: login failure path, email verification link — đã từng gặp thực tế, xem `docs/feature/02-login`)
**−** Route `/auth/**` là ngoại lệ so với model "mọi domain traffic qua đúng 1 BFF" — cần nhớ khi thêm client type mới hoặc thay đổi luồng OAuth2, không mặc định mọi thứ đều phải chui qua BFF

**Related:** ADR-009 (Mobile Gateway) — quyết định thêm `mobile-gateway` như BFF thứ 2 ngang hàng `web-gateway`, cùng dưới `api-gateway` mô tả ở đây.
