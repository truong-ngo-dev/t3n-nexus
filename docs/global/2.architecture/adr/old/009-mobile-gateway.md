# ADR-009 — Mobile Gateway (BFF cho mobile client)

**Status:** Accepted

## Context

`web-gateway` hiện là BFF duy nhất trong hệ thống — OAuth2 Client, session cookie httpOnly, exchange cookie sang token nội bộ trước khi forward. Model này gắn chặt với Spring Session (`ServerOAuth2AuthorizedClientRepository` lưu token theo session).

Khi có mobile app thật (bắt đầu với Shipper), client giữ access/refresh token trực tiếp trong secure keystore (Keychain/Keystore) và tự thực hiện Authorization Code + PKCE — không có cookie. Route thẳng traffic này qua `web-gateway` không hoạt động: security filter chain của `web-gateway` được cấu hình cho OAuth2 Client (login flow), không phải Resource Server (verify Bearer token).

`api-gateway` hiện chỉ route tới `web-gateway` và `oauth2-service` (`4. communication.md`, `RouteConfiguration`), không giữ route map chi tiết tới từng domain service. Kiến trúc mục tiêu cũ mô tả trong `7. security-architecture.md` ("Shipper App → api-gateway → domain services, api-gateway validate JWT") đòi hỏi `api-gateway` vừa validate JWT vừa biết route tới 8+ domain service — phá vỡ vai trò edge layer thuần mà nó đang giữ với web traffic, và không đối xứng với cách `web-gateway` đang vận hành.

Ngoài vấn đề routing, mobile client có nhu cầu riêng mà cookie-exchange không giải quyết:

- Nhiều màn hình cần gộp dữ liệu từ nhiều service — mạng cellular latency cao hơn broadband, giảm round-trip quan trọng hơn với web.
- App release qua store — nhiều version chạy song song trong thời gian dài (user không bắt buộc update ngay), cần lớp dịch giữa domain model (đổi tự do) và public contract app đang cầm.
- Cần khả năng force-upgrade khi phát hiện version app không an toàn hoặc không còn tương thích.
- Refresh token sống lâu trên thiết bị — cần ràng buộc thêm với thiết bị đã đăng ký để giảm rủi ro nếu token rò rỉ. IAM đã có sẵn `Device`/`DeviceLoginRecorded` (ADR-001) nhưng chưa được dùng cho việc này.

## Decision

Thêm `mobile-gateway` — BFF riêng cho mobile client, đối xứng với `web-gateway`, cùng là downstream trực tiếp của `api-gateway`.

### Phân vai

**`api-gateway`** — giữ nguyên là edge layer thuần:
- Routing tới đúng 3 downstream: `web-gateway`, `mobile-gateway`, `oauth2-service` (`/auth/**`)
- TLS termination, rate limit coarse-grained (theo IP)
- Không giữ route map tới domain service, không validate JWT, không business logic

**`mobile-gateway`** — BFF cho mobile client (bắt đầu với Shipper app):
- OAuth2 Resource Server — validate JWT signature + expiry tại đây (mobile giữ token trực tiếp, không cần cookie exchange như `web-gateway`)
- Routing tới domain service theo path convention riêng cho mobile
- Aggregation/composition — gộp nhiều call downstream thành 1 response cho từng màn hình, chạy song song (fan-out), mỗi nhánh chịu lỗi độc lập
- Anti-corruption layer — map domain model (đổi tự do theo nhịp backend) sang public contract ổn định theo nhịp release của app
- Version gating — kiểm tra `X-App-Version`, chặn cứng (426) khi dưới `minSupportedVersion`, cảnh báo mềm khi có bản mới hơn
- Device binding — đối chiếu `deviceId` claim trong JWT với thiết bị đang gọi, tận dụng `Device`/`DeviceLoginRecorded` đã có ở `oauth2-service`/`identity-service`
- Rate limit theo device/app instance — khác cấp với rate limit theo IP ở `api-gateway`

### Target flow (thay thế diagram cũ trong `7. security-architecture.md`)

```
Shipper App
  │
  ├──[/auth/**]──▶ api-gateway ──▶ oauth2-service        ← login (PKCE), MFA — bypass mobile-gateway
  │
  └──[Bearer token, business call]──▶ api-gateway ──▶ mobile-gateway ──▶ Domain services
                                       (routing thuần,      (validate JWT — Resource Server,
                                        rate limit per-IP)    aggregation, ACL, version gating,
                                                               device binding)
```

`api-gateway` route theo path, không theo client type — cùng 1 quy tắc `/auth/**` → `oauth2-service` áp dụng cho cả browser lẫn mobile (xem diagram tương ứng ở `7. security-architecture.md` § Trust Boundaries).

## Consequences

**+** Giữ symmetry: mỗi loại client (web/mobile) có đúng 1 BFF riêng, `api-gateway` luôn là edge layer thuần cho cả hai
**+** Domain service không cần biết gì về mobile — `mobile-gateway` hấp thụ toàn bộ concern riêng của client này
**+** Backend tự do evolve domain model — ACL cô lập breaking change khỏi app đã release, không cần giữ contract cũ vĩnh viễn ở domain service
**+** Tận dụng lại `Device`/`DeviceLoginRecorded` đã có sẵn ở IAM (ADR-001), không cần thiết kế mới
**−** Thêm 1 service, 1 network hop so với route thẳng `api-gateway` → domain service
**−** Aggregation (khi triển khai) tạo dependency đồng bộ giữa `mobile-gateway` và nhiều domain service cho từng màn hình — cần timeout + graceful degradation riêng cho mỗi nhánh; đây là gateway composition, không phải business logic cross-BC nên không tính vào 4 sync pairs đã duyệt (`4. communication.md`)
