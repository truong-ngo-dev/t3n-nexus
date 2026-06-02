# Deferred — user-login

Những việc được xác định trong feature này nhưng **chưa impl**, sẽ làm ở phase khác.

---

## 1. DeviceRevoked → terminate oauth2-service session

**Làm khi nào:** Khi `oauth2-service` được implement.

**Vấn đề hiện tại:**
`DELETE /api/v1/identity/me/devices/{deviceId}` chỉ đánh dấu `status=REVOKED` trong identity-service.
Session tương ứng trong `oauth2-service` (OAuthSession) vẫn còn valid — user có thể tiếp tục
dùng access token (cho đến khi expire) và refresh token từ device đó.

**Hướng giải quyết:**
identity-service publish `identity.device.revoked { deviceId, userId }` qua Outbox.
oauth2-service consume → delete OAuthSession WHERE deviceId=? AND userId=?.
Từ đó, refresh token của device đó không còn valid.

**Files liên quan:**
- `domain/device/Device.java` — thêm `DeviceRevokedEvent`
- `application/device/revoke_device/RevokeDevice.java` — dispatch event sau `device.revoke()`
- oauth2-service: tạo consumer cho `identity.device.revoked`

---

## 2. Idempotency cho `DeviceLoginRecordedConsumer`

**Làm khi nào:** Khi có yêu cầu exactly-once guarantee hoặc khi Kafka redelivery gây vấn đề thực tế.

**Vấn đề:**
`DeviceLoginRecordedConsumer` dùng `upsert Device` (ON CONFLICT) là safe cho Device,
nhưng `LoginActivity` được append mỗi lần — nếu Kafka redeliver cùng message, sẽ tạo duplicate record.

**Hướng giải quyết:**
Dùng `eventId` (từ `EventEnvelope.eventId`) làm idempotency key trong Redis:
```
idempotencyGuard.tryAcquire("login-recorded:" + eventId, 72h)
→ false: skip
→ true:  process + release on exception
```
Tương tự pattern đã làm ở `customer-service/CustomerRegisteredConsumer`.

**Files liên quan:**
- `infrastructure/messaging/DeviceLoginRecordedConsumer.java`
- Cần inject `RateLimiter` hoặc tạo `IdempotencyGuard` bean riêng

---

## 3. MFA — Email OTP (oauth2-service)

**Làm khi nào:** Khi implement oauth2-service.

**Framework:** Spring Security 7 — `@EnableMultiFactorAuthentication`, `OneTimeTokenService`, `RequiredAuthoritiesRepository`.
Chi tiết framework flow → [`docs/architecture/spring-security-mfa-bff.md`](../../../architecture/spring-security-mfa-bff.md)

**Những gì cần implement trong oauth2-service:**

| Việc | Chi tiết |
|---|---|
| `MfaConfig` entity | Lưu OTP secret, tách khỏi `Credential` |
| `Credential.mfaEnabled` | Denormalized flag — không query `MfaConfig` trên login hot path |
| `RedisOneTimeTokenService` | Implements `OneTimeTokenService` — `SET ott:{token}=username TTL 5min`, consume bằng `GETDEL` |
| `GeneratedOneTimeTokenHandler` | Publish event gửi OTP qua email (hook vào notification flow) |
| `RequiredAuthoritiesRepository` | Backed bởi `Credential.mfaEnabled` |
| Spring Security config | `@EnableMultiFactorAuthentication` + `oneTimeTokenLogin()` |
| Custom `AuthenticationSuccessHandler` | Auto-generate OTT sau password verified, `@Transactional` để Outbox insert cùng transaction |

**identity-service không cần thay đổi** — MFA hoàn toàn do oauth2-service xử lý.
`DeviceLoginRecorded` chỉ được publish sau khi cả 2 factors verified thành công.

**Verify:** `LoginActivity.loginAt` phản ánh thời điểm hoàn thành MFA, không phải thời điểm nhập password.

---

## 4. Login Activity — Filter theo device hoặc status

**Làm khi nào:** Khi có yêu cầu UX từ frontend.

**Hiện tại:** `GET /login-activity` trả về tất cả login attempts (SUCCESS + FAILED + BLOCKED), không filter.

**Có thể thêm sau:**
- `?deviceId={id}` — lọc theo device cụ thể
- `?status=FAILED` — chỉ xem failed attempts
- `?from={date}&to={date}` — lọc theo khoảng thời gian

Không implement ngay vì frontend hiện chưa có màn hình login history.

---

## 5. Device fingerprint storage

**Làm khi nào:** Khi có yêu cầu device recognition nâng cao.

**Context:**
ADR-001: `deviceId = hash(userAgent + ip + fingerprint)` — `fingerprint` là client-side fingerprint
(canvas, WebGL, fonts). Identity-service không lưu fingerprint raw — chỉ nhận `deviceId` đã hash.

Nếu muốn lưu thêm metadata (browser name, OS, device type parsed từ User-Agent), cần:
- Thêm column `parsed_user_agent` (JSON) vào `devices` table
- Parse User-Agent tại identity-service khi nhận `DeviceLoginRecorded`
- Expose thêm trong `ListDevices` response

Không implement ngay vì tăng complexity mà UX benefit chưa rõ.

---

## 6. Admin API — Xem devices/login-activity của bất kỳ user nào

**Làm khi nào:** Khi implement Admin Portal.

**Hiện tại:** Chỉ có self-service API (`/me/devices`, `/me/login-activity`).

**Cần thêm:**
- `GET /api/v1/identity/admin/users/{userId}/devices`
- `GET /api/v1/identity/admin/users/{userId}/login-activity`
- `DELETE /api/v1/identity/admin/users/{userId}/devices/{deviceId}`

Endpoint admin cần ABAC policy riêng (role=ADMIN hoặc permission cụ thể).
