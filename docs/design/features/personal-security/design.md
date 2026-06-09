# Design: Personal Security

**Status**: Draft

---

## Context

Tab **Bảo mật** trong `AccountProfileComponent` — cho phép user quản lý bảo mật tài khoản gồm 3 nhóm chức năng: đổi mật khẩu, xem lịch sử đăng nhập, quản lý thiết bị đã từng đăng nhập.

**UI structure:**
```
Tab: Bảo mật
├── [Section] Đổi mật khẩu
└── [mat-tab-group]
    ├── Lịch sử đăng nhập   — per-user audit log, chronological
    └── Thiết bị            — device list + trạng thái từ lần login cuối
```

---

## DB Changes

### Bảng `login_history`

```sql
CREATE TABLE login_history (
    id          VARCHAR(26)  PRIMARY KEY,         -- ULID
    user_id     VARCHAR(26)  NOT NULL,
    device_id   VARCHAR(26),                      -- nullable: login không có fingerprint vẫn ghi được
    action      VARCHAR(20)  NOT NULL,            -- LOGIN | LOGOUT | PASSWORD_CHANGED
    ip          VARCHAR(45),
    user_agent  VARCHAR(512),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_lh_user   FOREIGN KEY (user_id)   REFERENCES user_accounts(id),
    CONSTRAINT fk_lh_device FOREIGN KEY (device_id) REFERENCES user_device(id)
);

CREATE INDEX idx_lh_user_time ON login_history (user_id, created_at DESC);
```

Retention: giữ tối đa 90 ngày hoặc 200 record gần nhất / user.

### Bảng `user_device`

```sql
CREATE TABLE user_device (
    id               VARCHAR(26)  PRIMARY KEY,    -- ULID
    user_id          VARCHAR(26)  NOT NULL,
    fingerprint      VARCHAR(64)  NOT NULL,       -- hash(user_agent + ip), MVP
    display_name     VARCHAR(128),                -- "Chrome trên Windows"
    browser          VARCHAR(64),
    os               VARCHAR(64),
    last_history_id  VARCHAR(26),                 -- pointer đến login_history record cuối
    is_trusted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ud_user    FOREIGN KEY (user_id)         REFERENCES user_accounts(id),
    CONSTRAINT fk_ud_history FOREIGN KEY (last_history_id) REFERENCES login_history(id),
    CONSTRAINT uq_ud_fingerprint UNIQUE (user_id, fingerprint)
);

CREATE INDEX idx_ud_user ON user_device (user_id);
```

> `last_history_id` là pointer đến record cuối trong `login_history` — JOIN by primary key (O(1)), không copy field. Tránh inconsistency nếu một code path bỏ sót update.

---

## Services liên quan

| Service            | Vai trò                                                     | Loại tham gia |
|--------------------|-------------------------------------------------------------|---------------|
| `web-gateway`      | BFF — validate session, relay request với Bearer token      | Entry point   |
| `identity-service` | Source of truth — login_history, user_device, password hash | Sync only     |
| `oauth2-service`   | Xử lý login flow — hook capture login event tại đây         | Event source  |

Không có Kafka event — thay đổi password, device, history không trigger downstream consumer nào trong scope này.

---

## Capture on login / logout

Hook vào OAuth2 login flow tại `oauth2-service` hoặc `identity-service` (tùy nơi session được xác nhận):

```
Login thành công:
  1. Parse User-Agent → browser, os, display_name
  2. fingerprint = SHA-256(user_agent + ip)
  3. UPSERT user_device ON CONFLICT (user_id, fingerprint) → giữ nguyên row, chỉ cần device_id
  4. INSERT login_history (user_id, device_id, action=LOGIN, ip, user_agent) → lấy id mới
  5. UPDATE user_device SET last_history_id = <id mới> WHERE id = <device_id>
  → bước 3, 4, 5 trong 1 transaction

Logout:
  1. INSERT login_history (user_id, device_id, action=LOGOUT)
  2. UPDATE user_device SET last_history_id = <id mới> WHERE id = <device_id>
  → cả 2 trong 1 transaction
```

---

## Operations

### GET /api/v1/identity/me/login-history

Lịch sử đăng nhập của user hiện tại, phân trang.

```
Angular → GET /api/identity/me/login-history?page=0&size=20
  → web-gateway: relay với Bearer token
  → identity-service:
      SELECT * FROM login_history WHERE user_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?
      return PagedResponse<LoginHistoryItem>
```

**Response item:**
```json
{
  "action": "LOGIN",
  "ip": "1.2.3.4",
  "browser": "Chrome 124",
  "os": "Windows 11",
  "createdAt": "2026-06-09T14:32:00Z"
}
```

---

### GET /api/v1/identity/me/devices

Danh sách thiết bị đã đăng nhập.

```
Angular → GET /api/identity/me/devices
  → web-gateway: relay + truyền fingerprint request hiện tại
  → identity-service:
      SELECT d.*, h.action, h.created_at
      FROM user_device d
      LEFT JOIN login_history h ON h.id = d.last_history_id
      WHERE d.user_id = ?
      đánh dấu isCurrent nếu fingerprint khớp request
      return List<DeviceItem>
```

**Response item:**
```json
{
  "deviceId": "01J...",
  "displayName": "Chrome trên Windows",
  "browser": "Chrome 124",
  "os": "Windows 11",
  "lastSeenAt": "2026-06-09T14:32:00Z",
  "lastAction": "LOGIN",
  "isCurrent": true,
  "isTrusted": false
}
```

`isCurrent` = `true` → ẩn nút Thu hồi trên frontend.

---

### DELETE /api/v1/identity/me/devices/{deviceId}

Thu hồi thiết bị. Không cho phép revoke thiết bị hiện tại.

```
Angular → DELETE /api/identity/me/devices/{deviceId}
  → web-gateway: relay
  → identity-service:
      verify deviceId thuộc user hiện tại
      verify deviceId ≠ thiết bị của request hiện tại
      DELETE user_device WHERE id=?
      xóa BFF session liên quan nếu lưu session_id ↔ device_id (future)
```

---

### PUT /api/v1/identity/me/password

Đổi mật khẩu. Yêu cầu xác nhận mật khẩu hiện tại.

```
Angular → PUT /api/identity/me/password { currentPassword, newPassword }
  → web-gateway: relay
  → identity-service:
      load UserAccount by userId
      BCrypt.verify(currentPassword, storedHash) — nếu sai → 400
      BCrypt.hash(newPassword) → UPDATE user_accounts SET password_hash=?
      INSERT login_history (action=PASSWORD_CHANGED)
```

---

## Error Cases

| Lỗi                              | Nơi xử lý        | HTTP |
|----------------------------------|------------------|------|
| `currentPassword` sai            | identity-service | 400  |
| `newPassword` trùng current      | identity-service | 400  |
| Revoke thiết bị hiện tại         | identity-service | 400  |
| `deviceId` không thuộc user      | identity-service | 403  |
| `deviceId` không tồn tại         | identity-service | 404  |
| User chưa có password (OAuth only)| identity-service | 409  |

---

## Technical Constraints

| Concern              | Giải pháp                                                                              |
|----------------------|----------------------------------------------------------------------------------------|
| Authorization        | userId từ JWT claims — user chỉ xem/thao tác được dữ liệu của chính mình              |
| Device fingerprint   | Hash(user_agent + ip) — không chính xác 100% nhưng đủ cho MVP, không cần browser SDK  |
| Device last activity | `last_history_id` pointer — JOIN by PK (O(1)), không copy field, không có inconsistency risk |
| Retention            | login_history giữ 90 ngày / 200 record gần nhất để tránh table bloat                 |
| OAuth-only account   | User đăng nhập bằng Google chưa có password → `PUT /password` trả 409, FE hiển thị hướng dẫn set password qua email |
| Trust device         | `is_trusted` đã có trên schema, chưa enforce logic — reserved cho phase sau            |

---

## Frontend

**Component tree** trong `libs/storefront/feature-profile/`:

```
security-tab/
  change-password/       — form 3 field, validate confirm match
  login-history/         — paginated list, relative time
  device-list/           — device cards, revoke action
```

**Behavior:**
- Data của login-history và device-list chỉ load khi sub-tab tương ứng được chọn
- Device list: refresh sau khi revoke thành công
- Đổi mật khẩu thành công → reset form, snackbar 3s
- User chưa có password (409) → hiển thị inline message thay form
