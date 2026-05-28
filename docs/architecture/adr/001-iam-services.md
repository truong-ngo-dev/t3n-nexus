# ADR-001 — Thiết kế IAM Services

**Status:** Accepted

## Context

Hệ thống cần IAM đầy đủ: authentication, session management, ABAC authorization, device tracking, social login. IAM là generic domain — không tạo competitive advantage, cần thiết kế tách biệt khỏi business domain để các domain service không bị coupling vào logic auth.

## Decision

Tách IAM thành 2 services độc lập:

**`oauth2-service`**: authentication, Authorization Code Flow + PKCE, opaque refresh token, JWT access token, session management, device management, social login (Google).

**`identity-service`**: user profile, credentials, roles, ABAC policy management, account lock/unlock.

Roles được support: `GUEST`, `CUSTOMER`, `SELLER`, `SHIPPER`, `ADMIN`.

Scope thiết kế:
- Roles đa dạng hơn marketplace thông thường — `SELLER`, `SHIPPER`, `CUSTOMER` ngoài `ADMIN`, `USER`
- Account lock/unlock: domain BC không tự quản lý account state — khi khóa seller/shipper, gọi `identity-service` set `UserStatus=LOCKED`
- Social login (Google) qua `SocialIdentity` VO, `resolveBySocialIdentity` port

## Consequences

**+** IAM tách biệt — các domain service không cần biết chi tiết auth  
**+** oauth2-service là Authorization Server chuẩn — Resource Server chỉ cần validate JWT  
**+** Social login infrastructure có thể mở rộng thêm provider sau  
**−** Domain BC phụ thuộc vào `identity-service` cho account state — coupling điểm duy nhất này là chấp nhận được
