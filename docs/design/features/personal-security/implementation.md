# Personal Security — Implementation Checklist

## UI Structure

```
Tab: Bảo mật (AccountProfileComponent)
├── [Section] Đổi mật khẩu
└── [mat-tab-group]
    ├── Lịch sử đăng nhập   — per-user audit log, chronological
    └── Thiết bị            — device list, status derived from last login event
```

---

## Backend — identity-service

### Schema

- [ ] `V10` migration: bảng `login_history`
  ```sql
  id, user_id, device_id (nullable), action (LOGIN|LOGOUT),
  ip, user_agent, created_at
  INDEX (user_id, created_at DESC)
  ```
  > `device_id` nullable — login không có fingerprint vẫn ghi được

- [ ] `V11` migration: bảng `user_device`
  ```sql
  id, user_id, fingerprint, display_name, browser, os,
  last_history_id (nullable FK → login_history.id), is_trusted, created_at
  INDEX (user_id)
  ```
  > `last_history_id` là pointer đến record cuối trong `login_history` — JOIN by PK (O(1)), không copy field

### Kafka Consumers

**Consumer: `oauth2.session.issued`** — cập nhật thêm login capture
- [ ] Parse `User-Agent` từ event payload → browser, os, display_name
- [ ] Sinh fingerprint: hash(`user_agent + ip`)
- [ ] UPSERT `user_device` ON CONFLICT (user_id, fingerprint) — lấy `device_id`
- [ ] INSERT `login_history` (action=LOGIN, device_id, ip, user_agent) — lấy `history_id` mới
- [ ] UPDATE `user_device SET last_history_id = history_id` WHERE id = device_id
- [ ] Cả 3 bước cuối trong 1 transaction

**Consumer: `oauth2.session.expired.bulk`** — chưa có, cần tạo mới
- [ ] Với mỗi session trong event: xác định `device_id` từ session metadata (nếu có)
- [ ] INSERT `login_history` (action=LOGOUT, device_id)
- [ ] UPDATE `user_device SET last_history_id` nếu device_id xác định được

### Retention

- [ ] Chỉ giữ 90 ngày hoặc 200 record gần nhất / user trong `login_history`
- [ ] Scheduled job hoặc trigger xóa record cũ

### API

**Lịch sử đăng nhập**
- [ ] `GET /v1/me/login-history?page&size`
  - Response: `{ action, ip, browser, os, createdAt }[]`
  - Sort: `created_at DESC`
  - Index: `(user_id, created_at DESC)`

**Thiết bị**
- [ ] `GET /v1/me/devices`
  - Response: `{ deviceId, displayName, browser, os, lastSeenAt, lastAction, isCurrent, isTrusted }[]`
  - `isCurrent` = fingerprint request hiện tại khớp với device
  - JOIN `login_history` ON `last_history_id` (PK lookup, O(1))
- [ ] `DELETE /v1/me/devices/{deviceId}`
  - Revoke: xóa BFF session liên quan nếu có, không cho phép revoke thiết bị hiện tại

**Đổi mật khẩu**
- [ ] `PUT /v1/me/password` — body: `{ currentPassword, newPassword }`
  - Verify `currentPassword` trước khi update
  - Hash mật khẩu mới (BCrypt)
  - Insert `login_history` action = `PASSWORD_CHANGED`

---

## Frontend — libs/storefront/feature-profile

### IdentityService — thêm API calls

- [ ] `getLoginHistory(page, size): Observable<PagedResponse<LoginHistoryItem>>`
- [ ] `getDevices(): Observable<DeviceItem[]>`
- [ ] `revokeDevice(deviceId): Observable<void>`
- [ ] `changePassword(req): Observable<void>`

### Components

**Đổi mật khẩu** — `change-password/`
- [ ] Form 3 field: mật khẩu hiện tại / mới / xác nhận
- [ ] Validate: mới ≠ hiện tại, xác nhận phải khớp
- [ ] Gọi `PUT /v1/me/password`, snackbar thành công/thất bại

**Lịch sử đăng nhập** — `login-history/`
- [ ] Danh sách có phân trang
- [ ] Mỗi row: icon action (login/logout) + browser/OS + IP + thời gian tương đối
- [ ] Empty state khi chưa có lịch sử

**Thiết bị** — `device-list/`
- [ ] Mỗi device: tên + browser/OS + last seen + badge "Thiết bị này" nếu `isCurrent`
- [ ] Nút Thu hồi (ẩn với thiết bị hiện tại)
- [ ] Confirm trước khi revoke, reload list sau khi revoke thành công

**Security tab** — `security-tab/`
- [ ] Section đổi mật khẩu ở trên
- [ ] `mat-tab-group`: Lịch sử đăng nhập | Thiết bị ở dưới
- [ ] Lazy load data khi tab được chọn (không load cả 2 cùng lúc khi mở trang)

### Wiring

- [ ] Thêm `SecurityTabComponent` vào `AccountProfileComponent` tab Bảo mật
- [ ] Export interfaces `LoginHistoryItem`, `DeviceItem` từ `libs/shared/model`

---

## Tương lai — Trust Device

> Chưa implement, phụ thuộc `user_device` table ổn định trước

- Khi login từ untrusted device → trigger email OTP verification
- Sau verification → `user_device.is_trusted = true`
- Toggle trust/untrust từ device-list UI
