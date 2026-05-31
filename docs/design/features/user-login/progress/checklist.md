# Progress: user-login

> Tracking trạng thái từng bước trong [`implementation.md`](../implementation.md).

## Phần 1 — Refactor customer-register

| # | Bước                                                                     | Status |
|---|--------------------------------------------------------------------------|--------|
| 1 | Slim down `User` aggregate (xoá password, role)                          | ⬜ TODO |
| 2 | Tạo `VerificationEmailRequested` event + handler                         | ⬜ TODO |
    | 3 | Xoá infrastructure password (PasswordServiceAdapter, SecurityConfig)     | ⬜ TODO |
| 4 | Xoá REST registration endpoint + `CustomerRegister` use case             | ⬜ TODO |
| 5 | Tạo Kafka consumer `UserRegistered` + `CreateUserAccount` use case       | ⬜ TODO |
| 6 | Migration V4 (DROP hashed_password, role) + application.properties Kafka | ⬜ TODO |

## Phần 2 — Login feature

| #  | Bước                                                                  | Status |
|----|-----------------------------------------------------------------------|--------|
| 7  | Domain: `Device` aggregate + `DeviceRepository`                       | ⬜ TODO |
| 8  | Domain: `LoginActivity` + `LoginActivityRepository`                   | ⬜ TODO |
| 9  | Infrastructure: Device JPA entity, repository, mapper, adapter        | ⬜ TODO |
| 10 | Infrastructure: LoginActivity JPA entity, repository, mapper, adapter | ⬜ TODO |
| 11 | Kafka consumer `DeviceLoginRecorded` + `RecordDeviceLogin` use case   | ⬜ TODO |
| 12 | Use cases: `ListDevices`, `RevokeDevice`, `GetLoginActivity`          | ⬜ TODO |
| 13 | `DeviceController` REST endpoints + Security config                   | ⬜ TODO |
| 14 | Migration V5 (CREATE devices, login_activities)                       | ⬜ TODO |

---chgo

Status: ⬜ TODO · 🔄 IN PROGRESS · ✅ DONE · ⏸ BLOCKED
