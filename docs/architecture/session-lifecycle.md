# Session & Token Lifecycle

> Hiện trạng tính đến 2026-06-05. Cập nhật khi có thay đổi config hoặc quyết định alignment.

---

## 1. Tổng quan các loại session / token

Hệ thống có 3 lớp session độc lập, mỗi lớp có backend, TTL type và vòng đời riêng.

```
Browser (cookie)
    │
    ▼
web-gateway Spring Session  ──► Redis  (sliding TTL)
    │  session ↔ oss_id mapping
    ▼
oauth2-service IDP Session  ──► JDBC   (sliding TTL)
    │  access token / refresh token / id token
    ▼
OAuthSession domain object  ──► PostgreSQL (no TTL — revoke only)
```

---

## 2. Hiện trạng TTL

### 2.1 web-gateway

| Key | Backend | TTL type | Giá trị hiện tại |
|-----|---------|----------|-----------------|
| `spring:session:sessions:{session_id}` | Redis | **Sliding** (reset mỗi request) | **1800s (30 phút)** — Spring default, chưa config |
| `webgw:oauth:{oss_id}` | Redis | Fixed (từ lúc login) | **86400s (24 giờ)** — `app.session.mapping-ttl-seconds` |
| `webgw:session:{session_id}` | Redis | Fixed (từ lúc login) | **86400s (24 giờ)** — `app.session.mapping-ttl-seconds` |

### 2.2 oauth2-service — Token settings (per registered client `web-gateway`)

| Token | TTL type | Giá trị hiện tại |
|-------|----------|-----------------|
| Access token | Absolute | **3600s (1 giờ)** |
| Refresh token | Absolute | **86400s (24 giờ)** — rotate mỗi lần refresh (`reuse=false`) |
| ID token | Absolute | **3600s (1 giờ)** — inherit từ access token |
| Authorization code | Absolute | 300s (5 phút) |

### 2.3 oauth2-service — IDP Session

| Key | Backend | TTL type | Giá trị hiện tại |
|-----|---------|----------|-----------------|
| `SPRING_SESSION` (JDBC) | PostgreSQL | **Sliding** (reset mỗi request đến oauth2-service) | **1800s (30 phút)** — Spring default, chưa config |

### 2.4 identity-service — Session projection

| Column | Bảng | Ý nghĩa |
|--------|------|---------|
| `login_activities.session_id` | PostgreSQL | FK đến `OAuthSession.id` |
| `login_activities.ended_at` | PostgreSQL | `NULL` = session còn active; set khi nhận `SessionRevokedEvent` |

---

## 3. Misalignment hiện tại

### 3.1 web-gateway session vs refresh token ⚠️
- web-gateway Spring Session: idle **30 phút**
- Refresh token: absolute **24 giờ**
- **Hậu quả**: user idle 30 phút → web-gateway session expire → phải qua OAuth2 login flow lại dù refresh token vẫn còn hiệu lực → UX xấu

### 3.2 oauth2-service IDP session vs refresh token ⚠️
- IDP session: idle **30 phút**
- Refresh token: absolute **24 giờ**
- **Hậu quả**: khi web-gateway cần refresh token (token endpoint), IDP session có thể đã expire → oauth2-service yêu cầu re-authenticate

### 3.3 Mapping keys vs Spring Session (sliding drift) ℹ️
- Mapping keys: fixed TTL 24 giờ từ lúc login
- Spring Session: sliding TTL reset mỗi request
- **Hậu quả**: nếu user active lâu hơn 24 giờ, mapping keys expire trong khi Spring Session vẫn còn → logout sẽ không cleanup được mapping
- Trường hợp ngược: mapping keys sống đến 24h nhưng Spring Session expire sau 30 phút idle → stale mappings tồn tại đến 23.5 giờ

### 3.4 OAuthSession không tự expire ℹ️
- `OAuthSession` trong PostgreSQL không có TTL
- Khi web-gateway session expire tự nhiên (không qua logout tường minh), `OAuthSession` vẫn `ACTIVE` → orphaned
- Query "thiết bị đang đăng nhập" ở identity-service phụ thuộc vào `login_activities.ended_at` — chỉ được set khi có `SessionRevokedEvent` → không được set khi session expire tự nhiên

---

## 4. Hành vi khi session expire

### 4.1 web-gateway Spring Session expire (idle > 30 phút)
1. User request đến → Spring Security không tìm thấy context → redirect login
2. User đi qua OAuth2 flow → tạo Spring Session mới, `OAuthSession` mới, mapping mới
3. Mapping keys cũ → stale, tự hết sau 24h
4. `OAuthSession` cũ → orphaned, không được revoke
5. `login_activities.ended_at` → không được set → device list hiển thị sai (vẫn thấy "đang đăng nhập")

### 4.2 Refresh token expire (absolute 24 giờ)
1. web-gateway cố refresh → oauth2-service từ chối (token expired)
2. web-gateway nhận 401 → Angular nhận 401 → `AuthService.login()` redirect
3. `OAuthSession` vẫn `ACTIVE` → orphaned

---

## 5. Việc cần làm (pending)

| # | Hành động | Ưu tiên |
|---|-----------|---------|
| 1 | Set `spring.session.timeout=24h` ở web-gateway — align với refresh token | Cao |
| 2 | Set `server.servlet.session.timeout=24h` ở oauth2-service — IDP session sống đủ lâu | Cao |
| 3 | Cập nhật mapping key TTL nếu thay đổi refresh token validity | Theo 1 |
| 4 | Scheduled job ở oauth2-service: tìm `OAuthSession ACTIVE` mà refresh token đã expire → revoke → fire `SessionRevokedEvent` → `login_activities.ended_at` được set | Trung bình |
| 5 | (Future) Redis keyspace notifications → `SessionDestroyedEvent` → cleanup mapping + revoke `OAuthSession` khi Spring Session expire tự nhiên | Thấp |

---

## 6. Config cần set ngay (item 1 & 2)

```properties
# web-gateway/application.properties
spring.session.timeout=86400s

# oauth2-service/application.properties
server.servlet.session.timeout=86400s
```

Mapping key TTL đã đúng (`86400s`) — không cần đổi.
