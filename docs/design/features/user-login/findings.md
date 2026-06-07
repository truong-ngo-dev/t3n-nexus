# Findings — User Login Feature

> Ghi nhận trong quá trình implement và phân tích. Tính đến 2026-06-06.

---

## F-001 — OAuth2Authorization.id không rotate khi refresh token

**Nguồn gốc**: Phân tích Spring Authorization Server source + `AuditingOAuth2AuthorizationService`.

### Hành vi đã xác nhận

| Sự kiện                                                            | `OAuth2Authorization.id`     | Ghi chú                                                               |
|--------------------------------------------------------------------|------------------------------|-----------------------------------------------------------------------|
| Authorization code flow (fresh login)                              | **MỚI** — UUID mới           | Record mới trong `oauth_authorizations`                               |
| Silent re-auth / SSO (web-gateway session expire, IDP session còn) | **MỚI** — UUID mới           | Record mới, IDP session được **reuse** ✅ Runtime confirmed 2026-06-06 |
| Refresh token rotation                                             | **STABLE** — giữ nguyên UUID | `OAuth2Authorization.from(existing)` copy `id`, record cũ được UPDATE |

### Quan hệ IDP Session và OAuth2Authorization

IDP Session (JDBC `SPRING_SESSION`) và `OAuth2Authorization` là 2 entity độc lập:

```
IDP Session [B]   ─── 1:N ───►  OAuth2Authorization [T1]  (fresh login)
  session.getId()                OAuth2Authorization [T2]  (SSO sau khi [A] expire)
  sliding                        OAuth2Authorization [T3]  (SSO lần 2, ...)
  reused trong SSO
```

> **Notation:** Label trong diagram dùng `[B]` cho IDP Session — nhất quán với `session-invalidation.md`.
> File này trước đó dùng `[D]` cho IDP Session (notation cũ, đã deprecated).

- IDP session **không bị invalidate hay tạo mới** trong SSO — chỉ sliding reset.
- Mỗi authorization code flow tạo 1 `OAuth2Authorization` mới với UUID mới.
- `OAuthSession.authorizationId` = UUID của `OAuth2Authorization` → stable theo vòng đời authorization.

### Hệ quả cho model session

Với invariant **1 IDP session + 1 client → 1 OAuthSession active** (xem `session-invalidation.md` mục 3),
khi SSO tạo `OAuthSession` mới, `OAuthSession` cũ cùng `idpSessionId` bị revoke ngay trong `IssueSession.handle()`.
Không còn tình trạng nhiều `OAuthSession` active cùng `idpSessionId`.

---

## F-002 — Bug: IssueSession bị gọi lại trên mỗi lần refresh token rotation

**Severity**: Medium — gây duplicate `SessionIssuedEvent`, không gây data corruption do JPA merge.

**File**: `oauth2-service/.../oauth2/AuditingOAuth2AuthorizationService.java`

### Root cause

Phase 2 trong `AuditingOAuth2AuthorizationService.save()`:

```text
// Phase 2: access token just issued — create OAuthSession + publish SessionIssuedEvent
if (hasAccessToken(authorization) && authorization.getAttribute(ATTR_EMAIL) != null) {
    ...
    issueSession.handle(new IssueSession.Command(...));
}
```

Khi refresh token rotation xảy ra, Spring Authorization Server gọi:

```
OAuth2Authorization.from(existing)  // copy toàn bộ attributes, kể cả ATTR_EMAIL và ATTR_OAUTH_SESSION_ID
→ replace tokens
→ authorizationService.save(updatedAuth)  // vào AuditingOAuth2AuthorizationService.save()
```

Condition `hasAccessToken() && ATTR_EMAIL != null` = `true` trên mọi lần refresh
→ `IssueSession.handle()` được gọi lại với cùng `oauthSessionId` (ULID từ `ATTR_OAUTH_SESSION_ID`).

### Hậu quả thực tế

| Layer                       | Hậu quả                                                                            |
|-----------------------------|------------------------------------------------------------------------------------|
| `OAuthSession` (PostgreSQL) | JPA `merge()` với cùng ULID → UPDATE, không tạo bản ghi mới. Không corrupt.        |
| `SessionIssuedEvent`        | Được dispatch mỗi lần refresh → Outbox ghi thêm event                              |
| identity-service consumer   | Nhận duplicate `SessionIssuedEvent` → cố ghi thêm `login_activity` mỗi lần refresh |

### Fix đề xuất

Thêm existence check trước khi gọi `IssueSession` trong Phase 2:

```text
if (hasAccessToken(authorization) && authorization.getAttribute(ATTR_EMAIL) != null) {
    String oauthSessionId = orEmpty(authorization.getAttribute(ATTR_OAUTH_SESSION_ID));
    if (StringUtils.hasText(oauthSessionId)
            && !oAuthSessionRepository.existsById(new OAuthSessionId(oauthSessionId))) {
        issueSession.handle(new IssueSession.Command(...));
    }
}
```

Inject `OAuthSessionRepository` vào `AuditingOAuth2AuthorizationService`.
Check existence → chỉ call `IssueSession` khi `OAuthSession` chưa tồn tại (lần đầu tiên).

**Tradeoff**: Thêm 1 SELECT query trên mỗi lần token save. Chấp nhận được vì token save
không phải hot path tần suất cao, và correctness quan trọng hơn.

**Status**: Chưa fix — cần implement trước khi go-live.
