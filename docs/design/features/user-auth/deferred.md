# Deferred — user-auth

Những việc được xác định trong feature này nhưng **chưa impl**, sẽ làm ở phase khác.

---

## 1. Scheduled job cleanup orphaned OAuthSession (Path C)

**Làm khi nào:** Sau khi logout flow (Path A) và web-gateway TTL cleanup (Path B) đã stable.

**Vấn đề:**  
Khi IDP session [B] hết TTL, Spring Session JDBC cleanup task xóa record khỏi `SPRING_SESSION`.  
Không có session event nào được fire (Spring Session JDBC 4.x removed event support).  
`OAuthSession` [F] và `OAuth2Authorization` [C] tương ứng trở thành orphaned — không được cleanup cho đến khi job chạy.

**Trạng thái hiện tại:**  
`JdbcOAuthSessionExpiryService.expireOrphaned()` đã implement — tìm orphaned [F] bằng LEFT JOIN SPRING_SESSION, batch delete [F] + [C], trả `SessionsBulkExpiredEvent`.  
**Chưa có `@Scheduled` caller.**

**Cần implement:**  
Tạo `ExpiredSessionCleanupJob` trong oauth2-service:
```
@Scheduled(...)
@ConditionalOnProperty("app.job.expired-session-cleanup.enabled")
cleanupJob():
  event = oAuthSessionExpiryService.expireOrphaned()
  if event.oauthSessionIds().isEmpty() → return
  eventDispatcher.dispatch(event)
  event.oauthSessionIds().forEach(revocationClient::revoke)
```

Job mặc định **disabled** — enable khi chốt cron interval.  
Chi tiết impl → [`logout-impl.md`](logout-impl.md) mục 8–9.

**Files liên quan:**
- `oauth2-service`: tạo `application/session/cleanup/ExpiredSessionCleanupJob.java`
