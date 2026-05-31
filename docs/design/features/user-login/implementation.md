# Implementation Plan: User Login — Device Tracking & Login History

> Tham chiếu design: [`design.md`](design.md)
> Sequence diagrams: [`sequence-registration-refactor.puml`](sequence-registration-refactor.puml),
> [`sequence-login-device.puml`](sequence-login-device.puml)

---

## Phần 1 — Refactor customer-register (identity-service)

Mục tiêu: align identity-service với ADR-001 — loại bỏ password/role khỏi `User` aggregate,
xoá REST registration endpoint, chuyển sang event-driven từ oauth2-service.

---

### Bước 1 · Slim down `User` aggregate

**Domain layer** — `domain/user/`

Xoá khỏi `User.java`:
- Field `UserPassword hashedPassword`
- Field `Role role`
- Factory method `registerAsCustomer(UserId, String, String, UserPassword, String)`
- Factory method `registerAsCustomerViaOAuth(UserId, String, String)`
- Factory method `applyAsSeller(UserId, String, String, UserPassword)`
- Factory method `createShipper(UserId, String, String, UserPassword)`
- Factory method `createAdmin(UserId, String, String, UserPassword)`
- Method `changePassword(UserPassword)`
- Method `hasPassword()`

Thêm vào `User.java`:
- Factory `User.registerPendingVerification(UserId, email, fullName)` — status=PENDING, không raise event
- Factory `User.registerActivated(UserId, email, fullName)` — status=ACTIVE (dành cho OAUTH), không raise event

Xoá hoàn toàn các file:
- `UserPassword.java`
- `Role.java`
- `CustomerRegisteredEvent.java`

Giữ nguyên (không thay đổi):
- `UserStatus.java`
- `UserException.java` — xoá: `emailAlreadyExists()`, `currentPasswordRequired()`, `invalidPassword()`
- `UserErrorCode.java` — xoá: `EMAIL_ALREADY_EXISTS`, `INVALID_PASSWORD`, `INVALID_CURRENT_PASSWORD`, `CURRENT_PASSWORD_REQUIRED`
- `UserId.java`, `UserRepository.java`, `EmailVerification.java` và toàn bộ email verification domain
- `UserRepository.java` — xoá method `existsByEmail(String)`

Verify:
- `VerifyEmail` use case vẫn compile và chạy đúng (chỉ dùng `user.active()`, `user.getFullName()`)
- `ResendVerification` use case vẫn compile và chạy đúng (`user.isPending()`, `user.getFullName()`)

---

### Bước 2 · Thêm event `VerificationEmailRequested`

**Domain layer** — `domain/user/`

Tạo mới `VerificationEmailRequested.java`:
- Extend `AbstractDomainEvent implements DomainEvent`
- Fields: `userId`, `email`, `fullName`, `verificationToken`
- Routing key: `identity.verification.email.requested`
- Payload: `{userId, email, fullName, verificationToken}`

**Application layer** — `application/user/event/`

Tạo mới `VerificationEmailRequestedHandler.java`:
- Implement `EventHandler<VerificationEmailRequested>`
- Logic: `outboxEventStore.store(event)`

Xoá file:
- `CustomerRegisteredHandler.java` (không còn event nào để handle)

---

### Bước 3 · Xoá infrastructure password

**Infrastructure layer**

Xoá hoàn toàn:
- `PasswordServiceAdapter.java`
- `SecurityConfiguration.java` — xoá `PasswordEncoder` bean (chỉ được dùng bởi PasswordServiceAdapter)

Cập nhật `UserJpaEntity.java`:
- Xoá field `hashedPassword`
- Xoá field `role`
- Xoá import `Role`

Cập nhật `UserMapper.java`:
- Xoá mapping `UserPassword`, `Role` sang `User`
- `reconstitute()` call không còn truyền password và role

Cập nhật `UserJpaRepository.java`:
- Xoá tham số `:hashedPassword` và `:role` khỏi native query `upsert()`
- Xoá `existsByEmail(String)` method

Cập nhật `UserRepositoryAdapter.java`:
- `save(User)` không còn gọi `user.getHashedPassword()` và `user.getRole()`
- Xoá `existsByEmail(String)` implementation

---

### Bước 4 · Xoá REST registration endpoint

**Presentation layer** — `presentation/user/`

Cập nhật `UserController.java`:
- Xoá endpoint `POST /api/users/register`
- Xoá field `CustomerRegister customerRegister`
- Xoá record `RegisterRequest`
- Giữ nguyên: `GET /api/users/verify`, `POST /api/users/resend-verification`

Xoá file:
- `application/user/customer_register/CustomerRegister.java`

---

### Bước 5 · Tạo Kafka consumer `UserRegistered`

**Infrastructure layer** — `infrastructure/messaging/`

Tạo payload record `UserRegisteredPayload.java`:
```
Fields: email, fullName, registrationMethod (CREDENTIAL | OAUTH)
```
Note: `userId` lấy từ `event.payload().aggregateId()` (EventEnvelope level).

Tạo `UserRegisteredConsumer.java` (`@KafkaListener`):
```
Topic:   ${app.kafka.topic.user-registered}
Group:   ${app.kafka.consumer-group}
Logic:
  1. Deserialize OutboxEventData → decode UserRegisteredPayload
  2. Gọi CreateUserAccount use case
```

**Application layer** — `application/user/create_user_account/`

Tạo `CreateUserAccount.java` (CommandHandler):
```
Command: userId, email, fullName, registrationMethod
Logic:
  idempotency guard: skip nếu userId đã tồn tại (ON CONFLICT DO NOTHING)
  Nếu CREDENTIAL:
    User.registerPendingVerification(userId, email, fullName)
    EmailVerification.issue(verificationId, userId, email)
    save user, save emailVerification
    eventDispatcher.dispatchAll(List.of(new VerificationEmailRequested(...)))
  Nếu OAUTH:
    User.registerActivated(userId, email, fullName)
    save user
Reply: Void
```

---

### Bước 6 · Database migration — Bước 1

Tạo `V4__user_account_drop_columns.sql`:
```sql
ALTER TABLE users DROP COLUMN IF EXISTS hashed_password;
ALTER TABLE users DROP COLUMN IF EXISTS role;
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'PENDING';
```

Cập nhật `application.properties` — thêm Kafka consumer config:
```
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.consumer.key-deserializer=...StringDeserializer
spring.kafka.consumer.value-deserializer=...StringDeserializer
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false

app.kafka.topic.user-registered=oauth2.user.registered
app.kafka.consumer-group=identity-service
```

Cập nhật `notification-service/application.properties`:
```
app.kafka.topic.customer-registered=identity.verification.email.requested
```
(thay `identity.user.registered` → `identity.verification.email.requested`)

---

## Phần 2 — Login Feature (Device + LoginActivity)

---

### Bước 7 · Domain: `Device`

**Domain layer** — `domain/device/`

| File | Nội dung |
|---|---|
| `DeviceId.java` | Value object, extend `AbstractId<String>` |
| `DeviceStatus.java` | Enum: `ACTIVE`, `REVOKED` |
| `DeviceErrorCode.java` | `DEVICE_NOT_FOUND` (20001, 404), `ALREADY_REVOKED` (20002, 409), `FORBIDDEN` (20003, 403) |
| `DeviceException.java` | `notFound()`, `alreadyRevoked()`, `forbidden()` |
| `Device.java` | Aggregate (xem domain model trong design.md) |
| `DeviceRepository.java` | Port: `save(Device)`, `findByDeviceId(DeviceId)`, `findAllByUserId(UserId)` |

**Device behaviors:**

| Method | Guard | Effect |
|---|---|---|
| `Device.record(deviceId, userId, userAgent, ip)` | — | factory, status=ACTIVE, firstSeenAt=lastSeenAt=now |
| `device.refreshLastSeen()` | — | lastSeenAt = now |
| `device.revoke()` | throw `alreadyRevoked()` nếu REVOKED | status = REVOKED |
| `device.ownedBy(userId)` | — | boolean, dùng để authorization check |

---

### Bước 8 · Domain: `LoginActivity`

**Domain layer** — `domain/login_activity/`

| File | Nội dung |
|---|---|
| `LoginActivityId.java` | Value object, extend `AbstractId<String>` |
| `LoginStatus.java` | Enum: `SUCCESS`, `FAILED`, `BLOCKED` |
| `LoginActivity.java` | Plain class, immutable — xem domain model trong design.md |
| `LoginActivityRepository.java` | Port: `save(LoginActivity)`, `findByUserId(UserId, offset, limit)`, `countByUserId(UserId)` |

---

### Bước 9 · Infrastructure: Device persistence

**Infrastructure layer** — `infrastructure/persistence/device/`

| File | Chi tiết |
|---|---|
| `DeviceJpaEntity.java` | Table `devices`, fields: id, user_id, user_agent, ip, status, first_seen_at, last_seen_at |
| `DeviceJpaRepository.java` | `findByIdAndUserId`, `findAllByUserId`, native `upsert()` (ON CONFLICT id DO UPDATE last_seen_at, status) |

**Infrastructure layer** — `infrastructure/adapter/repository/device/`

| File | Chi tiết |
|---|---|
| `DeviceMapper.java` | `toDomain(DeviceJpaEntity)` |
| `DeviceRepositoryAdapter.java` | Implements `DeviceRepository` |

---

### Bước 10 · Infrastructure: LoginActivity persistence

**Infrastructure layer** — `infrastructure/persistence/login_activity/`

| File | Chi tiết |
|---|---|
| `LoginActivityJpaEntity.java` | Table `login_activities`, PK = BIGINT IDENTITY (seq), có thêm column `id` (ULID) |
| `LoginActivityJpaRepository.java` | `findByUserIdOrderByLoginAtDesc(userId, Pageable)`, `countByUserId(userId)` |

**Infrastructure layer** — `infrastructure/adapter/repository/login_activity/`

| File | Chi tiết |
|---|---|
| `LoginActivityMapper.java` | `toDomain(LoginActivityJpaEntity)` |
| `LoginActivityRepositoryAdapter.java` | Implements `LoginActivityRepository` |

---

### Bước 11 · Application: Kafka consumer `DeviceLoginRecorded`

**Infrastructure layer** — `infrastructure/messaging/`

Tạo payload record `DeviceLoginRecordedPayload.java`:
```
Fields: deviceId, userId, userAgent, ip, loginStatus (SUCCESS|FAILED|BLOCKED), loginAt
```

Tạo `DeviceLoginRecordedConsumer.java` (`@KafkaListener`):
```
Topic:   ${app.kafka.topic.device-login-recorded}
Group:   ${app.kafka.consumer-group}
Logic:
  1. Deserialize → decode DeviceLoginRecordedPayload
  2. Gọi RecordDeviceLogin use case (hoặc inline logic nếu đơn giản)
```

**Application layer** — `application/device/record_device_login/`

Tạo `RecordDeviceLogin.java` (CommandHandler, `@Transactional`):
```
Command: deviceId, userId, userAgent, ip, loginStatus, loginAt

Logic:
  Nếu loginStatus == SUCCESS:
    deviceRepository.findByDeviceId(deviceId)
      → present:  device.refreshLastSeen() → save
      → absent:   Device.record(deviceId, userId, userAgent, ip) → save
  Luôn:
    append LoginActivity.record(newId, userId, deviceId, loginStatus, ip, userAgent)
```

Cập nhật `application.properties`:
```
app.kafka.topic.device-login-recorded=oauth2.device.login.recorded
```

---

### Bước 12 · Application: Device management use cases

**Application layer** — `application/device/list_devices/`

Tạo `ListDevices.java` (query, không cần `@Transactional`):
```
Query:  userId (String từ JWT)
Reply:  List<DeviceView> (chỉ ACTIVE devices)

DeviceView: deviceId, userAgent, ip, firstSeenAt, lastSeenAt
```

**Application layer** — `application/device/revoke_device/`

Tạo `RevokeDevice.java` (CommandHandler, `@Transactional`):
```
Command:  userId (String), deviceId (String)
Logic:
  load Device → notFound nếu không có
  check device.ownedBy(userId) → forbidden nếu sai
  device.revoke() → alreadyRevoked nếu đã revoke
  deviceRepository.save(device)
Reply:  Void
```

**Application layer** — `application/device/get_login_activity/`

Tạo `GetLoginActivity.java` (query):
```
Query:  userId (String), offset (int, default 0), limit (int, default 20, max 50)
Reply:  List<LoginActivityView>, total, offset, limit

LoginActivityView: id, deviceId, loginStatus, ip, userAgent, loginAt
```

---

### Bước 13 · Presentation: DeviceController

**Presentation layer** — `presentation/device/`

Tạo `DeviceController.java`:
```
@RestController
@RequestMapping("/api/v1/identity/me")

GET    /devices                  → ListDevices
DELETE /devices/{deviceId}       → RevokeDevice   (204 No Content)
GET    /login-activity           → GetLoginActivity (query params: offset, limit)
```

Lấy `userId` từ `@AuthenticationPrincipal Jwt jwt` → `jwt.getSubject()`.
Security: yêu cầu valid JWT (oauth2ResourceServer config).

Cập nhật `SecurityConfiguration.java`:
```java
// Uncomment và cấu hình oauth2ResourceServer(jwt)
// permitAll: /api/identity/users/verify, /api/identity/users/resend-verification
// authenticated: /api/v1/identity/me/**
```

---

### Bước 14 · Database migration — Bước 2

Tạo `V5__add_device_login_activity.sql`:
```sql
CREATE TABLE devices (
    id            VARCHAR(26)  NOT NULL,
    user_id       VARCHAR(26)  NOT NULL,
    user_agent    TEXT,
    ip            VARCHAR(45),
    status        VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    first_seen_at TIMESTAMPTZ  NOT NULL,
    last_seen_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_devices PRIMARY KEY (id),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_devices_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_devices_user_id ON devices (user_id);

CREATE TABLE login_activities (
    seq          BIGINT GENERATED ALWAYS AS IDENTITY,
    id           VARCHAR(26)  NOT NULL,
    user_id      VARCHAR(26)  NOT NULL,
    device_id    VARCHAR(26),
    login_status VARCHAR(10)  NOT NULL,
    ip           VARCHAR(45),
    user_agent   TEXT,
    login_at     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_login_activities PRIMARY KEY (seq),
    CONSTRAINT uq_login_activities_id UNIQUE (id),
    CONSTRAINT chk_login_activities_status CHECK (login_status IN ('SUCCESS', 'FAILED', 'BLOCKED'))
);

CREATE INDEX idx_login_activities_user_id ON login_activities (user_id);
CREATE INDEX idx_login_activities_login_at ON login_activities (login_at DESC);
```

---

## Checklist hoàn thành

### Phần 1 — Refactor registration

- [ ] `User` aggregate không còn `hashedPassword` và `role`
- [ ] `PasswordService`, `UserPassword`, `Role` đã xoá khỏi identity-service
- [ ] `POST /api/users/register` không còn expose trên identity-service
- [ ] `CustomerRegister.java` đã xoá, thay bằng `CreateUserAccount.java`
- [ ] `CustomerRegisteredEvent.java` và `CustomerRegisteredHandler.java` đã xoá
- [ ] `VerificationEmailRequested` event được publish khi tạo UserAccount từ CREDENTIAL
- [ ] `identity-service` consume `oauth2.user.registered` → tạo UserAccount + EmailVerification
- [ ] OAUTH registration: UserAccount.status=ACTIVE ngay, không tạo EmailVerification
- [ ] `VerifyEmail` use case vẫn hoạt động đúng sau refactor
- [ ] `ResendVerification` use case vẫn hoạt động đúng sau refactor
- [ ] `notification-service` consumer đã cập nhật topic sang `identity.verification.email.requested`
- [ ] Idempotency: `CreateUserAccount` gọi 2 lần cùng userId → không tạo duplicate
- [ ] Flyway migration V4 chạy thành công

### Phần 2 — Login feature

- [ ] `DeviceLoginRecorded` được consume, Device được upsert khi SUCCESS
- [ ] `DeviceLoginRecorded` với FAILED/BLOCKED chỉ append LoginActivity, không tạo Device
- [ ] `GET /api/v1/identity/me/devices` trả về ACTIVE devices của user
- [ ] `DELETE /api/v1/identity/me/devices/{deviceId}` revoke thành công
- [ ] Revoke device không thuộc về user → 403
- [ ] Revoke device đã REVOKED → 409
- [ ] `GET /api/v1/identity/me/login-activity` trả về đúng thứ tự DESC, pagination đúng
- [ ] Tất cả endpoints yêu cầu valid JWT — 401 nếu không có
- [ ] Kafka consumer idempotent — xử lý event trùng (Kafka redelivery) không tạo duplicate
- [ ] Flyway migration V5 chạy thành công
- [ ] `EventEnvelopeDecoder` bean được config trong `KafkaConsumerConfig.java`
