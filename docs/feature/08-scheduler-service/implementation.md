# Implementation Plan: Scheduler Service

**Design**: [`design.md`](design.md)

**Thứ tự ưu tiên đã chốt**: làm xong phần **Execution** (Phase 1-5 — đăng ký → tìm việc đến hạn → fire) trước, dùng được thật với 4 job cố định (loyalty expiry, cart cleanup, payout, outbox cleanup). Phần Định nghĩa/Quản trị/Extensibility (đăng ký động, pause/resume, interceptor hook — xem Knowledge base `distributed-systems/patterns/job-scheduling/7. definition-management-extensibility.md`) để sau, chưa cần cho scope hiện tại.

**Quyết định kỹ thuật đã chốt** (khác `tech-stack.md` — cần lưu ý khi review):
- **Không dùng thư viện Quartz** dù `tech-stack.md` đang ghi "Quartz + Spring Batch" cho `scheduler-service` — tự build theo mô hình 5-interface (Schedule/ScheduleStore/DueItemFinder/ClaimStrategy/TriggerPublisher) đã thiết kế trong Knowledge base, lấy cảm hứng cơ chế lock của Quartz (row-lock DB) nhưng không phụ thuộc thư viện. Lý do: Quartz không có tầng tăng tốc (`DueItemFinder`) và không tường minh hoá được payload/taskType theo đúng mô hình đã thiết kế — xem Knowledge base `6. components-and-contracts.md` mục so sánh Quartz. **Cần cập nhật lại `tech-stack.md` sau khi Phase 1-5 xong**, hoặc ghi ADR nếu coi đây là quyết định kiến trúc đủ lớn.
- **ClaimStrategy = DB row-lock** (`SELECT ... FOR UPDATE` qua JPA `@Lock(PESSIMISTIC_WRITE)`) — không dùng bảng lock riêng kiểu `QRTZ_LOCKS`, dùng thẳng row của `scheduled_job` làm lock — kết hợp Claim + Re-verify thành 1 round-trip.
- **DueItemFinder = Redis ZSET** — implement dù cardinality hiện tại chưa thật sự cần, có chủ đích rehearse pattern Profile 2 cho mục đích học tập/portfolio (xem `design.md` §NFR Assessment, quyết định 2026-09-05). Triển khai theo shape **pipeline** của Aniket Yadav (Knowledge base `distributed-systems/components/job-scheduling/7. profile-distributed-poll.md`): Postgres chỉ nạp lại index qua reconciliation sweep, **KHÔNG tự fire độc lập** — khác phương án "2 loop độc lập cùng fire" đã cân nhắc và bỏ (lý do: tránh trùng lặp business flow poll+fire cho cùng 1 outcome; xem Session Log 2026-09-05).

---

## Docs cần tạo / cập nhật

| Tài liệu                                          | Hành động         | Nội dung                                                                                                                  |
|---------------------------------------------------|-------------------|---------------------------------------------------------------------------------------------------------------------------|
| `infra/README.md`                                 | **Đã cập nhật (2026-09-16)** | Thêm connector `scheduler-outbox-connector` — đăng ký/status/cleanup + bảng topics                                       |
| `service/scheduler-service/service.md`            | Đã tạo            | —                                                                                                                         |
| `service/scheduler-service/data.md`               | Đã tạo            | —                                                                                                                         |
| `global/2.architecture/5. event-catalog.md`       | Cập nhật          | Thêm `ScheduledJobFiredEvent` — sửa luôn dòng 167 đang sai ("trigger trực tiếp qua internal API call")                    |
| `global/2.architecture/tech-stack.md`             | Cập nhật          | Dòng 50 ("Scheduler: Quartz + Spring Batch") — sửa lại theo quyết định thật sau khi xong Phase 1-5, hoặc viết ADR nếu cần |
| `global/4.convention/ddd-structure.md`            | **Đã cập nhật**   | Thêm mục "Double Dispatch" — xem Phase 1.5 §Double Dispatch                                                               |
| `service/scheduler-service/service.md`, `data.md` | **Chưa cập nhật** | Còn mô tả version trước guard re-verify + event ownership mới — cần đồng bộ (xem Phase 1.5 §Data)                         |
| `feature/08-scheduler-service/design.md`, `service/scheduler-service/service.md`, `data.md` | **Đã cập nhật (2026-09-05)** | Bỏ khung "Lớp 1/Lớp 2 độc lập cùng fire" — chuyển sang pipeline (reconciliation sweep chỉ nạp index, 1 poller duy nhất claim+fire), đánh dấu `DueItemFinder` là component tuỳ chọn/rehearsal — xem Session Log |
| `feature/08-scheduler-service/deferred.md`        | **Đã tạo (2026-09-05)** | Circuit-breaker fallback Redis, time-bucket+segment sharding, audit log, retry-on-fail — các mở rộng đã bàn nhưng không build ở scope hiện tại |
| `service/scheduler-service/data.md`, `global/2.architecture/5. event-catalog.md` | **Đã cập nhật (2026-09-05)** | Race callback-vs-callback (2 redelivery Kafka xử lý đồng thời cùng `instanceId`) — thêm `@Version` vào `scheduled_job_instance` (lớp phòng thủ chính) + khuyến nghị partition key `instanceId` cho producer (cộng thêm) |
| `service/scheduler-service/api.yaml`              | **Đã tạo (2026-09-15)** | REST API Admin — 6 endpoint dưới `/api/admin/scheduled-jobs/**` (create/list/detail/edit/start/stop), xem Phase 5 |
| `service/scheduler-service/service.md`            | **Đã cập nhật (2026-09-15)** | Thêm bảng Queries (`GetScheduledJob`/`ListScheduledJobs`), đánh dấu REST Admin API đã implement |

---

## Phase 0 — Infra: Debezium connector cho `scheduler-service` outbox

**Status:** `IN PROGRESS` (2026-09-16) — file + doc xong, đăng ký + verify RUNNING cần môi trường docker thật, chưa chạy trong session này.

- [x] Tạo `infra/debezium/connector-scheduler-outbox.json` — copy mẫu `connector-order-outbox.json`, đổi `database.hostname=postgres-scheduler`, `database.dbname=scheduler_db`, `topic.prefix=scheduler`
- [x] `postgres-scheduler` thêm vào `infra/docker-compose.yml` (2026-09-16, trước đó CHƯA có — `application.properties` trỏ `localhost:5439` nhưng compose không có service nào map port đó) — `wal_level=logical` (cần cho Debezium), `POSTGRES_DB=scheduler_db`, port `5439:5432`, volume `postgres_scheduler_data`
- [x] Cập nhật bảng topics + mục đăng ký/status/cleanup trong `infra/README.md`
- [ ] Đăng ký connector, verify `RUNNING` — cần chạy tay: `docker compose up -d`, đợi `kafka-connect` healthy, rồi `curl -X POST http://localhost:8083/connectors ... -d @debezium/connector-scheduler-outbox.json`

**Verify:** `curl http://localhost:8083/connectors/scheduler-outbox-connector/status` → `RUNNING`.

**Lưu ý**: connector đăng ký xong cũng chưa có gì chảy qua — `ScheduledJobFiredHandler` hiện mới `log.info`, chưa `outboxEventStore.store(event)` (xem Phase 4). Đăng ký connector ở đây là chuẩn bị hạ tầng trước, không phải test end-to-end được ngay.

---

## Phase 1 — Domain: `ScheduledJob` + `ScheduledJobInstance` aggregate + `Schedule` VO

**Status:** `DONE` (session bootstrap — code viết xong, `mvn compile` sạch; unit test ở dưới vẫn `TODO`)

- [x] `ScheduledJobId` (`AbstractId<String>`, `generate()` tự gọi `UUID.randomUUID()` — theo đúng convention thật của repo, khác ví dụ `AbstractId<UUID>` trong `ddd-structure.md`, xem rà soát 19 `{Aggregate}Id` hiện có)
- [x] `Schedule` — sealed interface, `domain/schedule/` (domain concept riêng, không nằm trong package `scheduled_job/` — có tiềm năng dùng lại cho BC khác). `nextFireTime(after, cronCalculator)` — nhận thêm `CronCalculator` sau khi rà lại lần 3 (xem mục `CronCalculator` bên dưới)
  - [x] `OneOffSchedule(dueAt: Instant)` — bỏ qua `cronCalculator` hoàn toàn
  - [x] `RecurringSchedule(cron: String, zone: ZoneId)` — **không còn tự import `CronExpression`** — delegate hoàn toàn cho `CronCalculator`; compact constructor chỉ check null/blank, không validate cú pháp cron nữa (validate xảy ra ở lần gọi `nextFireTime()` đầu, qua adapter)
- [x] `ScheduledJobStatus` enum — `PENDING` \| `FIRED` \| `CANCELLED`
- [x] `MisfireInstruction` enum — `FIRE_NOW` \| `DO_NOTHING`
- [x] `ScheduledJob` aggregate (`extends AbstractAggregateRoot<ScheduledJobId>`)
  - [x] `create(taskType, schedule, payload, misfireInstruction, cronCalculator)` factory — guard `schedule.nextFireTime(now, cronCalculator)` phải có giá trị; `misfireInstruction` default `FIRE_NOW` nếu không truyền; tự generate `ScheduledJobId`
  - [x] `fire(now, instanceId, cronCalculator)` — guard `canProcess()`, tính `next`, cập nhật state, raise `ScheduledJobFiredEvent` mang theo `instanceId`. `instanceId` (`ScheduledJobInstanceId`) do `ScheduledJobFireService` generate + truyền vào — aggregate không tự tạo `ScheduledJobInstance` (aggregate boundary)
  - [x] `skipMisfire(now, cronCalculator)` — guard `canProcess()`, tính lại `next`, cập nhật `nextFireAt`, giữ `PENDING`, **không** raise event, **không** tạo run (rỗng → `FIRED`, case one-off hiếm gặp)
  - [x] `cancel()` — idempotent no-op nếu đã terminal
  - [x] `canProcess()` — `status == PENDING`
  - [x] `reconstitute(...)` factory cho persistence, không fire event
- [x] `ScheduledJobRepository` interface (port) — `save`, `findById`, `delete`, `findDueBefore(asOf, limit)`, `findByIdForUpdate(id)`
- [x] `ScheduledJobFiredEvent` — payload `{ scheduledJobId, instanceId, taskType, payload }`
- [x] `ScheduledJobErrorCode`/`ScheduledJobException` — `NOT_FOUND`, `ALREADY_DUE`, `INVALID_TRANSITION`

### `ScheduledJobInstance` — aggregate riêng (KHÔNG phải child entity của `ScheduledJob`)

Bổ sung sau khi rà lại: thiếu domain cho "1 lần fire cụ thể" — cần lưu lại đã dispatch, và có chỗ nhận callback outcome từ consumer khi xử lý xong (xem `service.md` §Callback, `design.md` bước 6-7). Aggregate riêng vì volume (N instance/1 job theo thời gian) và transaction khác hẳn `ScheduledJob.fire()`.

- [x] `ScheduledJobInstanceId` (`AbstractId<String>`, cùng pattern `ScheduledJobId`)
- [x] `ScheduledJobInstanceStatus` enum — `DISPATCHED` \| `SUCCEEDED` \| `FAILED`
- [x] `ScheduledJobInstance` aggregate (`extends AbstractAggregateRoot<ScheduledJobInstanceId>`)
  - [x] `dispatch(id, scheduledJobId, taskType, payload, firedAt)` factory — gọi bởi `ScheduledJobFireService` ngay sau `ScheduledJob.fire()`
  - [x] `succeed(completedAt)` / `fail(reason, completedAt)` — idempotent no-op nếu đã terminal (chặn double-callback do Kafka at-least-once redeliver)
  - [x] `reconstitute(...)`
- [x] `ScheduledJobInstanceRepository` interface (port) — chỉ `save`/`findById`/`delete` (base `Repository<>`), KHÔNG có `findByScheduledJobId` — query lịch sử theo job là read-side, thuộc `adapter/query/`, chưa cần ở scope hiện tại
- [x] `ScheduledJobInstanceErrorCode`/`ScheduledJobInstanceException` — `NOT_FOUND`

**Không raise event nào ra ngoài** — `ScheduledJobInstance` là pure sink, chỉ nhận callback vào, không publish gì tiếp (khác `ScheduledJob`).

### `ScheduledJobFireService` — Domain Service, điều phối `ScheduledJob` + `ScheduledJobInstance`

Bổ sung sau khi rà lại lần 2: tách coordination "fire tạo đúng 1 instance" khỏi Application Handler — đúng tiêu chí Domain Service ("Logic cần phối hợp nhiều aggregate", `ddd-structure.md`), tránh mỗi entry point cần fire (hiện chỉ có `FireScheduledJobHandler`, nhưng có thể có thêm sau) phải tự lặp lại đúng 2 bước (generate id + gọi `fire()` + tạo `dispatch()`).

- [x] `ScheduledJobFireService` (`domain/scheduled_job/`, `@Service` + `@RequiredArgsConstructor`, inject `CronCalculator`) — `fire(scheduledJob: ScheduledJob, now: Instant): ScheduledJobInstance`: generate `ScheduledJobInstanceId` → `scheduledJob.fire(now, instanceId, cronCalculator)` → return `ScheduledJobInstance.dispatch(instanceId, scheduledJob.getId(), scheduledJob.getTaskType(), scheduledJob.getPayload(), now)`
  - Không gọi Repository, không tự check `canProcess()` (tin caller đã verify)
  - `@Service` dù `domain/` "không nên" import Spring theo `ddd-structure.md` — theo đúng precedent thật `AttributeTemplateDomainService` (catalog-service)

### `CronCalculator` — port, không thuộc aggregate nào (bổ sung lần 3)

Phát hiện lúc rà lại: `RecurringSchedule` đang `import org.springframework.scheduling.support.CronExpression` trực tiếp — vi phạm câu chữ `ddd-structure.md` ("domain/ không import Spring"). Tách ra port riêng, cùng shape `ULIDGenerator` (`common-domain`).

- [x] `CronCalculator` (`domain/schedule/`, `@FunctionalInterface`) — `nextFireTime(cron: String, zone: ZoneId, after: Instant): Optional<Instant>`
- [x] `SpringCronCalculatorAdapter implements CronCalculator` (`infrastructure/adapter/service/cron/`, `@Component`) — duy nhất chỗ còn import `org.springframework.scheduling.support.*` trong toàn service; bắt `IllegalArgumentException` từ `CronExpression.parse()` sai cú pháp, dịch lại thành `ScheduleException.invalidCronExpression(cron)` (domain exception, không leak exception của Spring ra ngoài)

**Quyết định kèm theo (đã thảo luận, không phải hiển nhiên)**: truyền `CronCalculator` thẳng vào `Schedule.nextFireTime()` làm tham số, KHÔNG resolve trước ở Handler rồi truyền value (khác pattern `CollaboratorService`/`AssigneeService`) — vì đây là Strategy thuần (deterministic, không I/O, không cross-BC), không phải Collaborator. Giữ nguyên polymorphism của `Schedule`, tránh tái sinh nhánh `if (recurring)`. `ScheduledJobFireService` là nơi giữ instance thật qua DI; `CreateScheduledJobHandler` (Phase 5, chưa code) cũng cần tự inject `CronCalculator` riêng vì `create()` không đi qua `ScheduledJobFireService`.

**Verify (TODO — chưa viết test, và đã stale sau Phase 1.5 — xem verify list mới ở đó):**
- `mvn compile` sạch — **đã pass** (tại thời điểm Phase 1, trước rework 3-state).

---

## Phase 1.5 — Rework: 3-state model (PENDING/RUNNING/COMPLETED) + Start/Stop/Edit

**Status:** `DONE` (code viết xong, `mvn compile` sạch; unit test vẫn `TODO`, xem Verify)

**Executor:** `Agent` (ngoại lệ có chủ đích) — Phase này mặc định `User` (module học tập trọng yếu, xem `feedback_learning_critical_modules` memory), nhưng sau 1 chuỗi review/pair rất dài (state machine, guard `start()`/`fire()`, Double Dispatch vs pre-compute, event ownership — xem Session Log) đã hội tụ đủ rõ, user yêu cầu agent viết trực tiếp lần này. Không phải agent tự ý full-auto — đúng nhánh "hỏi lại trước khi code core logic" mà convention đã đặt ra.

**Bối cảnh phát sinh**: sau khi rà lại nghiệp vụ quản trị (Create/List/Detail/Start/Stop/Edit qua UI Admin — xem `service.md` §Domain Model), phát hiện model 2-status cũ (`PENDING` vừa là "chưa activate" vừa là "đang được poll" — lẫn 2 nghĩa) không đủ diễn tả. Chốt lại theo pattern K8s CronJob `suspend`: 3 status, `stop()` không phải hủy vĩnh viễn.

### Domain — đã sửa trong `ScheduledJob.java`, `ScheduledJobStatus.java`

- [x] `ScheduledJobStatus` — đổi `PENDING | FIRED | CANCELLED` → `PENDING | RUNNING | COMPLETED`
- [x] `ScheduledJob.create(id, taskType, schedule, payload, misfireInstruction)` — bỏ tham số `CronCalculator`, không tính `nextFireAt` (để `null`), bỏ guard `alreadyDue` (dời sang `start()`); `status = PENDING`. Nhận `id` từ ngoài — xem mục ULID bên dưới (đổi sau, cùng round này)
- [x] `ScheduledJob.start(now, cronCalculator)` — method mới, **Double Dispatch** (xem `ddd-structure.md` §Double Dispatch mới thêm): guard `status == RUNNING` → throw `invalidTransition` (request có chủ đích, không nên âm thầm no-op — khác `stop()`); `next = schedule.nextFireTime(now, cronCalculator)`; rỗng → throw `alreadyDue` (rỗng ở đây LÀ lỗi thật, khác ý nghĩa "rỗng" trong `fire()`); còn giá trị → `nextFireAt = next`, `status = RUNNING`
- [x] `ScheduledJob.fire(now, cronCalculator)` — **KHÔNG còn nhận `instanceId`** (event dời sang `ScheduledJobInstance`, xem mục dưới); Double Dispatch giống `start()`; **guard đổi hẳn bản chất** — không phải status-guard (status không đổi giữa các lần fire của recurring job nên không đủ chặn double-fire), mà là **re-verify `nextFireAt <= now`** tại đúng thời điểm gọi — chặn đúng race 2-poller thật (Redis fast-path + Postgres backstop), khớp "guard quan trọng nhất" theo Knowledge base `5. core-components.md` Flow D bước 2; nhánh rỗng → `COMPLETED` + `nextFireAt = null`
- [x] `ScheduledJob.skipMisfire(now, cronCalculator)` — giữ `canProcess()` guard (không cần re-verify riêng — Handler đã tự load+lock trước khi rẽ nhánh này); target rỗng đổi `FIRED` → `COMPLETED` + `nextFireAt = null`
- [x] `ScheduledJob.stop()` — rename từ `cancel()`; `RUNNING` → `PENDING`, `nextFireAt = null`; idempotent no-op nếu đã `PENDING`/`COMPLETED` (an toàn gọi lại nhiều lần — khác `start()`)
- [x] `ScheduledJob.edit(schedule, payload, misfireInstruction)` — method mới: guard `status == RUNNING` → throw; set 3 field mới (thay hoàn toàn); **luôn** `status = PENDING`, `nextFireAt = null` — không tự activate, kể cả sửa job `COMPLETED` (cho phép "chạy lại 1 lần" — one-off không nhất thiết dùng đúng 1 lần). `taskType` KHÔNG sửa được
- [x] `ScheduledJob.canProcess()` — đổi nghĩa: `status == RUNNING` (trước đây `PENDING`) — dùng để lọc candidate ở tầng poll, không phải guard correctness chính (đó là việc của re-verify trong `fire()`)
- [x] `ScheduledJobErrorCode`/`ScheduledJobException` — tái dùng nguyên `NOT_FOUND`/`ALREADY_DUE`/`INVALID_TRANSITION` có sẵn, không cần thêm mã lỗi mới

### Event ownership — dời `ScheduledJobFiredEvent` sang đúng aggregate phát ra nó

- [x] `ScheduledJobFiredEvent` — **di chuyển** từ `domain/scheduled_job/` sang `domain/scheduled_job_instance/`; `aggregateId`/`aggregateType` đổi từ `scheduledJobId`/"ScheduledJob" → `instanceId`/"ScheduledJobInstance" (nội dung event toàn bộ là field của `ScheduledJobInstance`, đúng convention "Domain Event đặt trong package của aggregate phát ra event")
- [x] `ScheduledJobInstance.dispatch()` — raise event tại đây (trước đó `ScheduledJob.fire()` raise) — hệ quả trực tiếp của việc `fire()` không còn nhận `instanceId` nữa

### Double Dispatch — quyết định kiến trúc mới, đã ghi vào convention

- [x] `docs/global/4.convention/ddd-structure.md` — thêm mục **"Double Dispatch"** ngay sau "Domain Service convention": phân biệt field/constructor injection (sai) vs method-parameter (đúng, có tên riêng), tiêu chí quyết định ("kết quả có persist thành field của aggregate không"), ví dụ đối chiếu `CollaboratorService` (pre-compute) vs `CronCalculator` (double dispatch)
- [x] `ScheduledJobFireService` — **quay lại** truyền `CronCalculator` thẳng vào `fire()` (double dispatch), **bỏ** phương án tính sẵn `Optional<Instant>` rồi truyền value đã cân nhắc trước đó trong buổi — lý do đảo ngược: `nextFireAt` là field thật sự persist của `ScheduledJob`, đúng tiêu chí double-dispatch, không phải data tạm kiểu factory-only

### Data — CHƯA cập nhật `data.md`/`service.md` theo đúng round cuối này (chỉ implementation.md lần này)

- [ ] `service.md`/`data.md` — vẫn đang mô tả version **trước** guard re-verify + event ownership mới nhất; cần đồng bộ lại (chưa làm ở lượt này, chỉ update đúng `implementation.md` theo yêu cầu)
- [ ] Cột `status` — CHECK constraint đổi giá trị hợp lệ
- [ ] Cột `next_fire_at` — đổi `NOT NULL` → **nullable**, thêm CHECK "NULL khi status IN ('PENDING','COMPLETED')"
- [ ] Index `idx_scheduled_job_next_fire` — đổi điều kiện `WHERE status='PENDING'` → `WHERE status='RUNNING'`

### ULID — đã áp dụng, **2 cách khác nhau** ở `ScheduledJob` vs `ScheduledJobInstance`

- [x] `pom.xml` — thêm `com.github.f4b6a3:ulid-creator:5.2.3`
- [x] `IdGeneratorConfig` (`infrastructure/crosscutting/config/`) — `@Bean ULIDGenerator` = `() -> UlidCreator.getMonotonicUlid().toString()`, copy đúng pattern `order-service`
- [x] `ScheduledJobId`/`ScheduledJobInstanceId` — bỏ hẳn `.generate()` tĩnh, chỉ còn `.of(String)`
- [x] `ScheduledJob.create(id, taskType, ...)` — **pre-compute**: nhận `id` (`ScheduledJobId`) đã tạo sẵn từ ngoài
- [x] `ScheduledJobInstance.dispatch(ulidGenerator, scheduledJobId, ...)` — **Double Dispatch**: nhận thẳng `ULIDGenerator`, tự `generate()` bên trong — quyết định lại sau khi rà lần 2, khác `create()`
- [x] `ScheduledJobFireService` — inject `ULIDGenerator`, truyền thẳng vào `dispatch()` (không tự gọi `generate()` ở tầng service)

**Quyết định kèm theo, đã ghi vào `ddd-structure.md` §Double Dispatch (mục "Trường hợp ranh giới: sinh ID")**: tiêu chí gốc (2 điều kiện — persist thành field + cần đọc field khác của aggregate) là điều kiện **đủ**, không phải điều kiện **cần**, cho double dispatch. Sinh ID chỉ thoả điều kiện 1 (persist) không thoả điều kiện 2 (không cần field nào của aggregate để tính) — nên **pre-compute vẫn là mặc định hợp lý hơn** (khớp precedent đa số: `order-service`/`identity-service`/`customer-service`), nhưng double dispatch **vẫn đúng đắn kỹ thuật** (sinh ID không có tác dụng phụ, không tạo trạng thái "nửa vời"). `ScheduledJob.create()` giữ pre-compute (đúng mặc định); `ScheduledJobInstance.dispatch()` chọn double dispatch — lý do: `ScheduledJobFireService` đã double-dispatch `CronCalculator` cho `ScheduledJob.fire()`, giữ cùng 1 kiểu gọi cho cả 2 port cùng inject trong class này thay vì trộn 2 kiểu.

`CreateScheduledJobHandler` (Phase 5, chưa code) sẽ cần tự inject `ULIDGenerator` riêng, dùng kiểu pre-compute (giống `create()` hiện tại) — không dùng chung instance với `ScheduledJobFireService`.

### Application layer (chưa có Handler nào code — đây là điểm bắt đầu Phase 3, không phải sửa lại)

Khi tới Phase 3 thật, `FireScheduledJobHandler` cần đọc đúng field mới; các Handler mới (`CreateScheduledJobHandler`, `StartScheduledJobHandler`, `StopScheduledJobHandler`, `EditScheduledJobHandler`) là scope REST API — xem Phase 5 (đã đổi hướng từ "seed nội bộ" sang "REST quản trị thật", cần viết lại Phase 5 khi tới đó).

**Verify:**
- `mvn compile` sạch — **đã pass**.
- Unit test state machine (TODO — chưa viết): `create()` → `PENDING`, `nextFireAt == null`; `start()` từ `PENDING` → `RUNNING`, `nextFireAt` có giá trị; `start()` khi đã `RUNNING` → throw `invalidTransition`; `start()` khi schedule đã quá hạn → throw `alreadyDue`; `fire()` khi `nextFireAt == null` hoặc `nextFireAt.isAfter(now)` → throw (test đúng race re-verify — giả lập gọi `fire()` 2 lần liên tiếp, lần 2 phải throw); `fire()` recurring hợp lệ giữ `RUNNING` tái lập `nextFireAt`; `fire()` one-off hợp lệ → `COMPLETED`, `nextFireAt == null`; `stop()` từ `RUNNING` → `PENDING`, `nextFireAt == null`; `stop()` khi đã `PENDING`/`COMPLETED` → no-op; `edit()` từ `PENDING` hoặc `COMPLETED` → `PENDING`, `nextFireAt == null`; `edit()` khi `RUNNING` → throw; `start()` lại sau `edit()` trên job từng `COMPLETED` → hoạt động bình thường (test đúng nghiệp vụ "sửa job chạy 1 lần để chạy lại"); `ScheduledJobInstance.dispatch()` phải raise đúng `ScheduledJobFiredEvent` với `aggregateType="ScheduledJobInstance"`

---

## Phase 2 — Persistence

**Status:** `DONE`
**Executor:** `Agent`

- [x] Migration `V1__init_schema.sql` — `outbox_events` (đã tạo lúc bootstrap module, khớp schema thật của `outbox-starter`)
- [x] Migration `V2__scheduled_job.sql` — bảng `scheduled_job` (`id VARCHAR(26)`, CHECK `next_fire_at IS NULL` khớp `status IN (PENDING, COMPLETED)`, partial index `idx_scheduled_job_next_fire ON scheduled_job (next_fire_at) WHERE status = 'RUNNING'`)
- [x] Migration `V3__scheduled_job_instance.sql` — bảng `scheduled_job_instance` (cột: `id`, `scheduled_job_id`, `task_type`, `payload` JSONB, `status`, `fired_at`, `completed_at` nullable, `failure_reason` nullable; index `(scheduled_job_id)`; không FK — độc lập aggregate)
- [x] `ScheduledJobJpaEntity` + `ScheduledJobJpaRepository` (Spring Data) — `@JdbcTypeCode(SqlTypes.JSON)` cho `payload`, `@Version` cho optimistic lock
- [x] `ScheduledJobJpaRepository.findByIdForUpdate` — `@Lock(LockModeType.PESSIMISTIC_WRITE)` — dùng bởi Phase 3
- [x] `ScheduledJobMapper` — domain ↔ JpaEntity (polymorphic `Schedule` qua Java 21 `switch` pattern matching; `@Version` qua `ReflectionUtils` — `AbstractAggregateRoot` không có accessor public)
- [x] `ScheduledJobRepositoryAdapter implements ScheduledJobRepository` — `save()` = JPA save + `eventDispatcher.dispatchAll()` cùng transaction (đúng pattern `OrderRepositoryAdapter` đã có ở `order-service`)
- [x] `ScheduledJobInstanceJpaEntity` + `ScheduledJobInstanceJpaRepository` (Spring Data) — `@JdbcTypeCode(SqlTypes.JSON)` cho `payload`, KHÔNG cần `@Version` (chỉ 1 writer tại 1 thời điểm — Handler tạo lúc dispatch, callback update sau, không có race 2 writer đồng thời trên cùng 1 run)
- [x] `ScheduledJobInstanceMapper` — domain ↔ JpaEntity
- [x] `ScheduledJobInstanceRepositoryAdapter implements ScheduledJobInstanceRepository`
- [x] `EventDispatcherConfig` — `@Bean EventDispatcher(List<EventHandler<?>>)` (chưa có Handler nào đăng ký ở Phase 2, danh sách rỗng — có `ScheduledJobFiredHandler` từ 2026-09-15, xem Phase 4 verify)
- [x] `common-utils` thêm vào `pom.xml` (cần cho `JsonUtils`/`ReflectionUtils` dùng trong 2 Mapper)
- [x] **`JpaConfig`** (`infrastructure/crosscutting/config/`, `@EntityScan({"vn.t3nexus.scheduler", "vn.t3nexus.lib.outbox"})`) — **thiếu sót từ lúc bootstrap Phase 2, phát hiện 2026-09-16** khi chạy app thật lần đầu: `OutboxJpaConfiguration` (outbox-starter) chỉ `@EnableJpaRepositories` cho `OutboxEventRepository`, không `@EntityScan` cho `OutboxEvent` — thiếu file này thì Hibernate không biết `OutboxEvent` là managed type, boot fail `IllegalArgumentException: Not a managed type: class vn.t3nexus.lib.outbox.OutboxEvent`. Mọi service khác dùng `outbox-starter` (identity/oauth2/catalog/inventory/order) đều có file này — chỉ scheduler-service bị sót

**Verify:** `mvn compile -pl scheduler-service -am` — **đã pass, sạch**. Integration test `save()`/`findById()` round-trip — TODO (chưa viết, để cùng đợt viết unit test Phase 1.5 đã ghi nhận ở trên).

---

## Phase 3 — Execution: Core Poller (nguồn sự thật, chưa có Redis)

**Chặn bởi**: Phase 1-2. Đây là **model lõi tier-agnostic** — Poller đọc thẳng `ScheduleStore`, KHÔNG cần `DueItemFinder` (khớp shape Mayil Bayramov ở scale 50 triệu task/ngày, không dùng finder nào). Xong phase này, hệ thống đã đúng đắn và **chạy được thật với 4 job cố định** — Phase 4 (Redis) chỉ là accelerant tuỳ chọn thêm sau, không sửa lại correctness của phase này, chỉ đổi nguồn Poller đọc từ.

**Status:** `TODO`

- [ ] Config property `scheduler.misfire-threshold-seconds` (đề xuất default = 2× chu kỳ poll, VD 20-30s cho poll 10-15s) — **đã có trong `application.properties`**
- [x] `FireScheduledJob` command (`application/scheduled_job/fire/`) — `Handler` inject `ScheduledJobRepository`, `ScheduledJobInstanceRepository`, `ScheduledJobFireService`, **và `CronCalculator` trực tiếp** (sửa lại 2026-09-05 — nhánh `skipMisfire()` gọi thẳng aggregate, KHÔNG qua `ScheduledJobFireService`, nên Handler vẫn cần `CronCalculator` của chính mình, không phải "đã giữ sẵn" như ghi trước đó): `findByIdForUpdate` → `canProcess()` guard → check `now - nextFireAt > misfireThreshold && misfireInstruction == DO_NOTHING`:
  - đúng → `job.skipMisfire(now, cronCalculator)` → `scheduledJobRepository.save(job)` (không đụng outbox, không tạo instance) → return, không đi tiếp
  - sai → `instance = scheduledJobFireService.fire(job, now)` (đã mutate `job` qua `fire()` re-verify guard + tạo `instance` bên trong) → `scheduledJobInstanceRepository.save(instance)` + `scheduledJobRepository.save(job)` (cùng transaction, atomic với outbox) → **`eventDispatcher.dispatchAll(instance.pullEvents())`** — LẤY TỪ `instance`, KHÔNG PHẢI `job` (event giờ thuộc `ScheduledJobInstance`, xem Phase 1.5 §Event ownership)
  - `ScheduledJobFireService.fire()` (bên trong `fire()`) có thể throw `invalidTransition` nếu race re-verify phát hiện job đã bị nguồn khác xử lý mất — Handler cần bọc catch riêng (giống `ObjectOptimisticLockingFailureException`), coi là benign skip, không phải lỗi thật
- [ ] `ScheduledJobRepository.findDueBefore(asOf, limit)` implementation — `SELECT id FROM scheduled_job WHERE status='RUNNING' AND next_fire_at < :asOf LIMIT :limit`
- [ ] `@Scheduled` job (`infrastructure/scheduling/SchedulerPoller`) — chu kỳ **5-15s** (đủ nhanh để làm nguồn poll chính khi chưa có `DueItemFinder` — cardinality thấp nên query rẻ, không cần cadence "backstop chậm" vì chưa có fast-path nào để backstop), gọi `findDueBefore` rồi loop `FireScheduledJob.handle()` **từng job một** (transaction riêng, không gộp batch — 1 job optimistic-lock-fail không được rollback cả batch)
- [ ] Bọc try-catch `ObjectOptimisticLockingFailureException` quanh từng lần gọi trong loop — log debug, không throw ra ngoài, tăng counter `scheduler.claim.lost` (xem mục Observability)

**Verify:** Tạo `ScheduledJob` one-off, `start()` để có `nextFireAt=now+10s`, đợi `SchedulerPoller` poll → xác nhận `status=COMPLETED`, `nextFireAt=null`, `ScheduledJobFiredEvent` xuất hiện ở topic `scheduler.job.fired` (console consumer). Riêng nhánh misfire: tạo `ScheduledJob` recurring, `start()`, `misfireInstruction=DO_NOTHING`, `nextFireAt` = quá khứ xa hơn `misfireThreshold` → xác nhận `skipMisfire()` chạy, `nextFireAt` được dời tới tương lai, **không** có event nào xuất hiện ở topic. Test race: gọi `FireScheduledJob.handle()` 2 lần liên tiếp cho cùng 1 job đã fire → lần 2 phải bị chặn bởi guard re-verify trong `fire()`, không tạo thêm `ScheduledJobInstance`/event thừa.

---

## Phase 4 — `DueItemFinder` accelerant (tuỳ chọn — Profile 2 rehearsal): Reconciliation Sweep + Index Poller

**Chặn bởi**: Phase 3 (phải có Poller đúng đắn trên `ScheduleStore` trước). **Không bắt buộc cho correctness** — đây là phần thêm có chủ đích để rehearse pattern scale (Knowledge base `distributed-systems/components/job-scheduling/7. profile-distributed-poll.md`), theo đúng shape Aniket Yadav: **Postgres chỉ nạp lại index, không tự fire song song** với Index Poller — khác thiết kế "2 loop độc lập cùng fire" đã cân nhắc và bỏ (xem Session Log 2026-09-05).

**Status:** `IN PROGRESS` — port + Redis adapter + 2 `@Scheduled` đã có, thân đã bật (ghi index / claim+fire). Còn lại: Observability (4.5), callback (4.6), test runtime với Redis thật.

**Lưu ý deviation (xem Session Log 2026-09-09)**: bỏ qua Phase 3 `SchedulerPoller` (poll DB thuần) theo yêu cầu — đi thẳng vào pipeline Phase 4. Không còn class nào để "refactor"; `IndexReconciler`/`IndexPoller` viết mới từ đầu.

- [x] `DueItemFinder` port (domain interface, `domain/scheduled_job/`) — `index`, `deindex`, `pollDue`
- [x] `RedisDueItemFinderAdapter` — ZSET `scheduler:due`, `ZADD`/`ZREM`/`ZRANGEBYSCORE`+Lua atomic pop
- [x] Sync index sau `start()`/`stop()`/`fire()` (recurring)/`skipMisfire()` — **cơ chế đã chốt**: hoãn-sau-commit + best-effort là **guarantee của port `DueItemFinder`**, gộp thẳng vào impl DUY NHẤT `RedisDueItemFinderAdapter` (`index`/`deindex` gọi `TransactionSynchronizationManager.registerSynchronization(afterCommit)` nếu đang trong tx, chạy ngay nếu ngoài tx; nuốt `RuntimeException`, log `error`). KHÔNG có helper riêng ở application (`DueIndexSync` đã thử rồi bỏ — làm `DueItemFinder` có 2 class `implements`, muốn giữ đúng 1 impl). KHÔNG dùng `@TransactionalEventListener` (phải thêm domain event mới + lẫn với `EventDispatcher` nội-transaction sẵn có). `pollDue` KHÔNG hoãn — `IndexPoller` gọi ngoài tx.
  - Handler chỉ inject `DueItemFinder` và gọi `index`/`deindex` như dòng lệnh thường:
  - `StartScheduledJob` → `index(id, nextFireAt)` — điểm duy nhất job có `nextFireAt` & vào index
  - `StopScheduledJob` / `EditScheduledJob` → `deindex(id)` — cả 2 đưa job về PENDING/`nextFireAt=null` (gọi vô điều kiện, `deindex` idempotent)
  - `FireScheduledJob` — recurring vừa fire hoặc `skipMisfire()` → `index(id, nextFireAt mới)`; one-off vừa `COMPLETED` (`nextFireAt==null`) → `deindex(id)`; nhánh lost-race (`INVALID_TRANSITION`) KHÔNG đụng index
  - `CreateScheduledJob` → không sync (job PENDING chưa có `nextFireAt`) — chỉ ghi chú trong code
- [x] `ScheduledJobRepository.findDueBefore` đổi chữ ký `List<ScheduledJobId>` → **`List<DueJobRef>`** (record `(ScheduledJobId id, Instant dueAt)` ở `domain/scheduled_job/`) — reconciler cần cặp `(id, nextFireAt)` để `ZADD`. 1 caller duy nhất (reconciler) nên đổi thẳng thay vì thêm method song song. JPA: native query trả interface projection `DueRefRow {getId(), getNextFireAt()}`, adapter map sang `DueJobRef`. Javadoc bỏ nhắc `SchedulerPoller` đã xoá.
- [x] `IndexReconciler` (`infrastructure/scheduling/`) — `@Scheduled(fixedDelay + initialDelay)`, cửa sổ `now + lookahead-seconds`, `findDueBefore(horizon, batch-limit)` (read thuần, không lock — an toàn concurrent, xem javadoc class), loop `dueItemFinder.index(ref.id(), ref.dueAt())` (gọi ngoài tx → ghi ngay, không hoãn; lỗi Redis từng entry nuốt trong adapter). KHÔNG gọi `FireScheduledJob`.
- [x] `IndexPoller` (`infrastructure/scheduling/`) — `@Scheduled(fixedDelay)`, `dueItemFinder.pollDue(now, batch-limit)` (nuốt lỗi Redis → skip tick), loop `FireScheduledJob.handle()` từng id (1 transaction/id): `catch OptimisticLockingFailureException` → debug (lost claim race, benign); `catch RuntimeException` → error + đi tiếp (1 job lỗi không kéo các id đã claim khác). Nguồn **DUY NHẤT** chạy Claim+Trigger.
- [x] Config keys đổi tên: `scheduler.backstop-poll.*`/`fastpath-poll.*` → `scheduler.index-reconciler.*` (`fixed-delay-ms`, `initial-delay-ms`, `lookahead-seconds`, `batch-limit`) + `scheduler.index-poller.*` (`fixed-delay-ms`, `batch-limit`) trong `application.properties`; comment `@EnableScheduling` trong `SchedulerServiceApplication` sửa theo.

- [x] `ScheduledJobFiredHandler` (`application/scheduled_job_instance/event/`, `@Component implements EventHandler<ScheduledJobFiredEvent>`) — **TẠM THỜI chỉ `log.info`**, chưa ghi Outbox. Trước class này `ScheduledJobFiredEvent` raise xong rơi vào hư không (registry rỗng). TODO: đổi thân thành `outboxEventStore.store(event)` khi bật publish thật (xem `ProductPublishedHandler` catalog-service làm mẫu) — `outbox-starter` đã có sẵn trong `pom.xml`.

**Verify:** Tạo `ScheduledJob` one-off `dueAt=now+3s` → xác nhận fire qua `IndexPoller` (nhanh hơn hẳn cadence `IndexReconciler`). Mô phỏng mất 1 entry (xoá tay khỏi ZSET, không tắt Redis) → xác nhận `IndexReconciler` tự nạp lại trong chu kỳ kế, job vẫn fire đúng. Tắt hẳn Redis (outage toàn phần) → xác nhận fire dừng hẳn tới khi Redis phục hồi — **đây là trade-off đã chấp nhận, không phải bug** (khác hành vi cũ khi còn 2 loop độc lập; xem `design.md` §Failure Scenarios).

---

## Phase 4.5 — Observability

**Chặn bởi**: Phase 3-4 (cần Poll/Trigger loop tồn tại trước để có chỗ gắn instrumentation). Cross-cutting trên hạ tầng OTel/Prometheus/ELK đã có sẵn toàn hệ (`tech-stack.md` §Observability & Operations) — không cần dựng infra mới, chỉ thêm điểm đo.

**Status:** `TODO`

- [ ] `Timer scheduler.poll.duration` — bọc quanh query trong `SchedulerPoller` (Phase 3) / `IndexPoller` (Phase 4) — không bọc `IndexReconciler` (không phải poll+fire, chỉ nạp index, đo riêng nếu cần) (đo `poll_time` theo định nghĩa Knowledge base `1. definition.md`)
- [ ] `Timer scheduler.dispatch.duration` — bọc quanh `FireScheduledJob.handle()` (đo `dispatch_time`)
- [ ] `Counter scheduler.job.fired{taskType}` — tại điểm raise `ScheduledJobFiredEvent`
- [ ] `Counter scheduler.job.misfire_skipped{taskType}` — tại điểm gọi `skipMisfire()`
- [ ] `Counter scheduler.claim.lost` — vào chỗ try-catch `ObjectOptimisticLockingFailureException` đã có ở Phase 3 (chỉ thêm dòng tăng counter, không đổi logic)
- [ ] `Gauge scheduler.running.count` — poll định kỳ `COUNT(*) FROM scheduled_job WHERE status='RUNNING'` — **đổi từ `scheduler.pending.count`/`status='PENDING'`** sau Phase 1.5 (PENDING giờ nghĩa là "chưa activate", RUNNING mới là tập đang được poll)

**Verify:** Prometheus scrape thấy đủ 5 metric trên sau khi chạy vài chu kỳ poll; Grafana dashboard (tối thiểu 1 panel/metric, không cần polish) — xác nhận số liệu tăng đúng khi test misfire/claim-lost ở Phase 3-4.

---

## Phase 4.6 — Callback: `ScheduledJobInstance` outcome

**Chặn bởi**: Phase 1-3 (`ScheduledJobInstance` phải được tạo lúc dispatch trước khi có gì để callback vào). **Không chặn** Phase 5 — seed 4 job vẫn chạy được, đủ đúng, dù chưa có consumer nào publish callback (xem `design.md` NFR Assessment, gap chấp nhận được).

**Trạng thái mở, chưa chốt hết**: tên topic cụ thể của từng producer (`customer-service`/`cart-service`/`payment-service`) chưa xác định — nằm ngoài phạm vi `scheduler-service`, cần làm khi implement phía consumer. Phần dưới đây chỉ code được phía `scheduler-service` (consume 1 contract tối thiểu, tên topic để config, đổi được sau).

**Status:** `TODO`

- [ ] Migration `V5__scheduled_job_instance_version.sql` (đổi từ V4 → V5, xem 2026-09-15: V4 đã dùng cho `scheduled_job.job_name`) — thêm cột `version bigint NOT NULL DEFAULT 0` vào `scheduled_job_instance` (quyết định 2026-09-05, xem `data.md` §Locking)
- [ ] `ScheduledJobInstanceJpaEntity` — thêm `@Version` field (đảo ngược Phase 2, lúc đó chưa cần)
- [ ] `CompleteScheduledJobInstance` command (`application/scheduled_job_instance/complete/`) — `Handler`: `findById(instanceId)` → `orElseThrow(ScheduledJobInstanceException::notFound)` → `outcome == SUCCEEDED ? succeed(completedAt) : fail(reason, completedAt)` → `save()`. Bọc try-catch `ObjectOptimisticLockingFailureException` quanh `save()` — log debug, coi là benign skip (2 message redeliver cùng `instanceId` mang cùng outcome, không mất gì khi 1 trong 2 thua race), cùng idiom đã dùng ở `SchedulerPoller`/`IndexPoller`
- [ ] `ScheduledJobInstanceCallbackConsumer` (`infrastructure/messaging/`) — Kafka listener, topic đọc từ property (`app.kafka.topic.job-run-callback`, giá trị cụ thể **để trống/placeholder tới khi từng consumer chốt tên topic thật**), parse payload tối thiểu `{ instanceId, outcome, failureReason, completedAt }` → gọi `CompleteScheduledJobInstance.handle(...)`
- [ ] Idempotent theo `instanceId` — không cần dedup table riêng, `succeed()`/`fail()` tự idempotent ở tầng aggregate (no-op nếu đã terminal) + `@Version` chặn race khi 2 redelivery được xử lý đồng thời (xem trên)
- [ ] Thêm `spring-boot-starter-kafka` vào `pom.xml` — **lần đầu scheduler-service thật sự consume Kafka**, khác quyết định "không cần" lúc bootstrap (session trước, khi chưa có khái niệm callback)

**Verify:** Publish thủ công 1 message giả (console producer) đúng contract tối thiểu vào topic test → xác nhận `ScheduledJobInstance.status` chuyển đúng SUCCEEDED/FAILED; publish lại lần 2 cùng `instanceId` → xác nhận không đổi gì thêm (idempotent). Test race callback-vs-callback: 2 thread gọi `CompleteScheduledJobInstance.handle()` đồng thời cùng `instanceId` (cùng outcome, mô phỏng redelivery) → xác nhận đúng 1 thread ghi thành công, thread kia nhận `ObjectOptimisticLockingFailureException` và bị catch êm, `status` cuối cùng đúng, không exception thoát ra ngoài.

---

## Phase 5 — Presentation: REST Admin API

**Đổi hướng so với kế hoạch gốc** ("Bootstrap: seed 4 job thật" qua migration) — sau khi rà lại nghiệp vụ quản trị (Create/List/Detail/Start/Stop/Edit qua UI Admin, xem `service.md` §Commands "Đã đảo ngược quyết định trước đó"), 4 job thật nên được tạo qua chính REST API này (`POST` + `start`) thay vì migration/seed script riêng — thực dụng hơn (exercise đúng luồng thật) và không cần file seed. Seed migration/script cụ thể **dời xuống mục riêng bên dưới**, không còn là nội dung chính của Phase 5.

**Chặn bởi**: Phase 1-2 (domain + persistence). Không phụ thuộc Phase 3-4 (poll/fire) — REST chỉ đọc/ghi `scheduled_job`, không đụng `IndexPoller`/`IndexReconciler`.

**Status:** `DONE` (2026-09-15) — unit/integration test vẫn `TODO`, xem Verify.

- [x] Dependencies: `spring-boot-starter-validation`, `spring-boot-starter-oauth2-resource-server`, `vn.t3nexus:common-web` (cho `ApiResponse`/`GlobalExceptionHandler`) thêm vào `pom.xml`
- [x] `application.properties` — `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8004/oauth2/jwks` (cùng issuer `oauth2-service` với các service khác)
- [x] `SecurityConfig` (`infrastructure/crosscutting/config/`) — `anyRequest().authenticated()` (không có nhánh public nào nên không cần danh sách `permitAll` như `catalog-service`), CSRF tắt (stateless JWT bearer), `oauth2ResourceServer().jwt()`
- [x] `WebConfig` (`infrastructure/crosscutting/config/`) — `@Import(GlobalExceptionHandler.class)`, cùng pattern mọi service khác (class nằm ngoài base package quét mặc định)
- [x] **`ScheduledJob.jobName`** (mới, 2026-09-15) — nhãn hiển thị Admin tự đặt, khác `taskType` (routing tag, immutable): `jobName` sửa được qua `edit()`. Migration `V4__scheduled_job_job_name.sql` (`job_name VARCHAR(200) NOT NULL`, không index — search dùng `LIKE '%...%'` wildcard 2 đầu, btree thường không giúp gì). Lan xuống `ScheduledJob.create()`/`edit()`, `ScheduledJobJpaEntity`, `ScheduledJobMapper`, `CreateScheduledJob`/`EditScheduledJob` Command, `ScheduledJobView`, request/response DTO
- [x] **`ScheduledJobQueryPort`** (`domain/scheduled_job/`) — port đọc RIÊNG cho Admin List/Search, tách khỏi `ScheduledJobRepository` (đảo ngược thiết kế ban đầu sau góp ý: phân trang/đếm/lọc là read-model concern, không phải "quản lý vòng đời aggregate"). `search(filter, page, size)` + `count(filter)`, kèm `ScheduledJobQueryFilter(jobName, status, taskType)` — `jobName` search substring (`LOWER()+LIKE`, JPQL, portable — không dùng `ILIKE` của Postgres); `status`/`taskType` exact match, `String` thô không parse enum (sai giá trị → 0 kết quả, không throw). `findById` đơn lẻ (`GetScheduledJob`) vẫn ở `ScheduledJobRepository` — load 1 aggregate để hiển thị vẫn đúng bản chất repository
- [x] `ScheduledJobQueryAdapter` (`infrastructure/adapter/query/scheduled_job/`, đối xứng `adapter/repository/`) — impl `ScheduledJobQueryPort`, dùng CHUNG `ScheduledJobJpaRepository` với `ScheduledJobRepositoryAdapter` (cùng bảng/entity, chỉ tách port domain — không tách hạ tầng persistence, khác trường hợp read DB ≠ write DB)
- [x] `ScheduledJobJpaRepository` — thêm `search(status, taskType, pageable)` + `countSearch(status, taskType)`, JPQL `WHERE (:x IS NULL OR e.x = :x)` cho filter tuỳ chọn AND-combined; `Pageable` tự thêm `ORDER BY`, không khai tay
- [x] `GetScheduledJob` (`application/scheduled_job/get/`, `QueryHandler<Query, ScheduledJobView>`) — `findById` thuần qua `ScheduledJobRepository`, không lock
- [x] `ListScheduledJobs` (`application/scheduled_job/list/`, `QueryHandler<Query, Result>`) — phân trang qua `ScheduledJobQueryPort`, 3 filter tuỳ chọn: `jobName` (search, dành cho Admin), `status`/`taskType` (exact, kỹ thuật) — đủ dùng ở cardinality hiện tại, thêm khi Admin UI thật sự cần
- [x] `ScheduleView` + `ScheduledJobView` (`application/scheduled_job/shared/`) — làm phẳng `Schedule` đa hình thành field rời rạc cho response, dùng chung cho Get + List (tránh lặp mapping)
- [x] `ScheduledJobController` (`presentation/scheduled_job/`) — 6 endpoint dưới `/api/admin/scheduled-jobs`: `POST` (create, 201), `GET` (list, phân trang), `GET /{id}` (detail), `PUT /{id}` (edit), `POST /{id}/start`, `POST /{id}/stop`. Không có `DELETE` — vòng đời qua `stop()`, không "xoá vĩnh viễn" ở domain
- [x] Request/Response DTO (`presentation/scheduled_job/model/`) — `CreateScheduledJobRequest`/`EditScheduledJobRequest` (chỉ `@NotBlank` cơ bản, tổ hợp field theo `scheduleType` để domain tự validate qua `ScheduleRequestMapper`/`Schedule` VO), `ScheduledJobResponse` + `PagedResponse` nested
- [x] `service/scheduler-service/api.yaml` — OpenAPI 3.0.3 đầy đủ 6 path, schema, error response 400/404/422

**Gap đã biết, chưa fix (ghi lại để không quên)**:
- `misfireInstruction` sai giá trị (không phải `FIRE_NOW`/`DO_NOTHING`) → `MisfireInstruction.valueOf()` throw `IllegalArgumentException` trong `CreateScheduledJob`/`EditScheduledJob` — KHÔNG phải `DomainException` nên rơi vào nhánh `Exception` chung của `GlobalExceptionHandler` → 500 thay vì 400. Trước đây không lộ ra vì chưa có caller thật nào truyền input tuỳ ý. Cần 1 `ScheduledJobException`/`ScheduleException` mới bọc lại `IllegalArgumentException` này — chưa làm ở lượt này.
- `SecurityConfig` chỉ check JWT hợp lệ (`authenticated()`), KHÔNG phân biệt role ADMIN — khác dự định "Admin actor" ban đầu. Không có cơ chế `hasAuthority`/`hasRole` nào khác trong toàn hệ thống để tham chiếu (rà lại thấy chỉ `web-gateway` có `SCOPE_webgw.internal`), nên tạm để `authenticated()` là đủ theo đúng mức các service khác đang làm — nếu cần phân role thật, đây là điểm cần quay lại.

**Verify (TODO — chưa viết):** Tạo job qua `POST`, `start` để activate, `GET` list/detail thấy đúng `nextFireAt`; `edit` khi đang RUNNING → 422; `start` khi đã RUNNING → 422; `start` khi schedule quá hạn (one-off `dueAt` trong quá khứ) → 422 `SCHEDULED_JOB_ALREADY_DUE`; `stop` gọi lại nhiều lần → không lỗi (idempotent); Bean Validation thiếu `taskType`/`scheduleType` → 400.

---

## Phase 5b — Seed 4 job thật (dời từ Phase 5 gốc, chưa làm)

**Status:** `TODO`

- [ ] Tạo 4 `ScheduledJob` recurring qua chính REST API Phase 5 (`POST` + `start`), không qua migration nữa: `LOYALTY_POINT_EXPIRY`, `CART_CLEANUP`, `SELLER_PAYOUT`, `OUTBOX_CLEANUP` — cron expression theo đúng yêu cầu nghiệp vụ từng loại (xác nhận lại với `requirement.md`/`bounded-contexts.md` §Scheduler BC trước khi chốt lịch cụ thể). `misfireInstruction = FIRE_NOW` cho cả 4 job
- [ ] `OUTBOX_CLEANUP` job — consumer chạy nội bộ ngay trong `scheduler-service` (tự dọn bảng `outbox_events` của chính mình, không publish ra ngoài — xem `service.md`)

**Verify:** `GET /api/admin/scheduled-jobs` sau khi tạo → đúng 4 row, `status=RUNNING`, `nextFireAt` hợp lý theo cron.

---

## Checklist hoàn thành (Execution phase)

- [ ] Happy path chạy end-to-end (bootstrap → fire → event xuất hiện Kafka)
- [ ] Outbox hoạt động — không mất event khi restart giữa chừng
- [ ] Idempotency — `canProcess()` + `SELECT...FOR UPDATE` + `@Version` chặn double-fire khi test giả lập race (2 instance HA, hoặc `IndexReconciler` nạp lại đúng lúc `IndexPoller` đang xử lý)
- [ ] Redis mất 1 entry giữa chừng — `IndexReconciler` tự bắt kịp, không mất job; Redis outage toàn phần — fire tạm dừng có chủ đích tới khi phục hồi (không phải bug, xem `design.md` §Failure Scenarios)
- [ ] Misfire — job trễ quá `misfireThreshold` với `misfireInstruction=DO_NOTHING` dời lịch đúng, không phát event; `FIRE_NOW` vẫn fire bù như hành vi cũ
- [ ] Observability — đủ 5 metric ở Phase 4.5 xuất hiện trên Prometheus, tăng đúng khi test
- [ ] `ScheduledJobInstance` tạo đúng lúc fire (status `DISPATCHED`), callback cập nhật đúng outcome, idempotent khi redelivery, **không** ảnh hưởng `ScheduledJob.status`/`nextFireAt`
- [ ] `service.md`/`data.md` cập nhật đúng thực tế nếu có sai khác lúc code
- [ ] `event-catalog.md` cập nhật — thêm `ScheduledJobFiredEvent`, sửa dòng mô tả sai (không còn "internal API call")
- [ ] `tech-stack.md` cập nhật hoặc ADR mới — phản ánh đúng quyết định không dùng Quartz

---

## Session Log

_{Chỉ ghi khi có blocker/deviation thật so với plan — Format: `- YYYY-MM-DD: {1-2 dòng}`.}_

- 2026-08-27: Rà lại nghiệp vụ quản trị (Create/List/Detail/Start/Stop/Edit qua UI Admin) lộ ra model 2-status cũ không đủ diễn tả — thêm Phase 1.5, đổi `PENDING|FIRED|CANCELLED` → `PENDING|RUNNING|COMPLETED` (pattern K8s CronJob `suspend`, không có "hủy vĩnh viễn"). Đảo ngược quyết định "không REST public" — cần Admin actor + `oauth2-resource-server`. Executor Phase 1.5 = `User` (module học tập trọng yếu, xem `feedback_learning_critical_modules` memory).
- 2026-08-27 (tiếp): Chuỗi review/pair dài quanh guard của `start()`/`fire()` — kết luận cuối: `start()` cần 2 guard (đã-RUNNING → throw, rỗng → throw `alreadyDue`); `fire()` cần re-verify `nextFireAt<=now` (không phải status-guard — status không đổi giữa các lần fire liên tiếp của recurring job nên không đủ chặn double-fire khi 2 poller đua nhau, đây là "guard quan trọng nhất" theo Knowledge base). Phát hiện thêm: `ScheduledJobFiredEvent` đặt sai chỗ (ở `ScheduledJob.fire()` thay vì `ScheduledJobInstance.dispatch()`) — đã dời. Tranh luận double-dispatch (`CronCalculator` truyền thẳng vào aggregate) vs pre-compute-rồi-truyền-value (kiểu `CollaboratorService`) — verify qua nguồn DDD thật (Vaughn Vernon "double dispatch" pattern, Jimmy Bogard, gist so sánh Domain Service/Factory/Double Dispatch), tìm ra tiêu chí quyết định rõ ràng hơn hẳn suy luận ban đầu ("kết quả có persist thành field của aggregate không") — đã ghi thành convention chính thức ở `ddd-structure.md`. Agent viết code trực tiếp lần này theo yêu cầu tường minh của user (ngoại lệ Executor, xem Phase 1.5).
- 2026-08-28: User tự tay sửa `ScheduledJobInstance.dispatch()` sang nhận thẳng `ULIDGenerator` (Double Dispatch) thay vì `ScheduledJobInstanceId` pre-compute như agent viết ban đầu — đảo ngược 1 phần kết luận double-dispatch cho ID (vẫn giữ `ScheduledJob.create()` ở dạng pre-compute). Lý do: nhất quán cách gọi 2 port cùng inject trong `ScheduledJobFireService` (`CronCalculator` đã double-dispatch), không phải vì `ULIDGenerator` thoả điều kiện 2 (nó không thoả — sinh ID vẫn không cần field nào của aggregate). Cập nhật `ddd-structure.md` §Double Dispatch: làm rõ 2 điều kiện gốc là điều kiện **đủ** chứ không phải điều kiện **cần** — thiếu điều kiện 2 thì pre-compute vẫn là mặc định hợp lý hơn (đa số service), nhưng double dispatch vẫn chấp nhận được nếu có lý do nhất quán cụ thể, không phải rule cứng tuyệt đối.
- 2026-08-28 (tiếp): Chạy Phase 2 (Persistence) — Agent viết trực tiếp (module hạ tầng JPA/mapping, không phải core domain, đúng nhánh mặc định `Agent` của Executor convention). Thiếu `common-utils` dependency trong `pom.xml` (cần cho `JsonUtils`/`ReflectionUtils` dùng trong 2 Mapper) — phát hiện lúc viết code, thêm ngay trước khi compile. `mvn compile -pl scheduler-service -am` pass sạch lần đầu.
- 2026-09-05: Rà lại kiến trúc poll/Redis qua đối chiếu Knowledge base (`distributed-systems/components/job-scheduling/`). Phát hiện: (1) thiết kế "2 loop độc lập cùng chạy Claim+Trigger" (Lớp 1 Redis + Lớp 2 Postgres) không khớp shape thật của case neo Profile 2 nào — Mayil không dùng finder, Aniket dùng pipeline (batch processor chỉ nạp cache, publisher là nơi duy nhất fire) — chỉ là suy rộng nguyên tắc chung ("≥1 poller dùng ScheduleStore song song") thành 1 shape cụ thể hơn mức cần thiết, tạo trùng lặp business flow poll+fire không cần thiết; (2) ở model lõi tier-agnostic, `DueItemFinder` là component **có điều kiện** (kích hoạt khi query `ScheduleStore` trực tiếp thành bottleneck `poll_time`), không phải mặc định — ở cardinality 4 job, điều kiện đó chưa từng xảy ra, nên việc giữ Redis là lựa chọn có chủ đích để rehearse Profile 2 (portfolio/học tập), không phải nhu cầu performance. **Quyết định**: đổi Phase 3 thành model lõi thuần (Poller đọc thẳng `ScheduleStore`, đủ đúng + chạy được thật, không cần Redis); Phase 4 tái cấu trúc theo pipeline Aniket-style — `SchedulerPoller` (Phase 3) refactor thành `IndexReconciler` (chỉ nạp index, cadence chậm) + `IndexPoller` mới (nguồn duy nhất claim+fire, đọc `DueItemFinder`). Đánh đổi chấp nhận: nếu Redis outage toàn phần, fire dừng hẳn tới khi phục hồi (khác model 2-loop cũ, nơi DB tự fire được kể cả khi Redis chết) — ghi vào `design.md` §Failure Scenarios như rủi ro đã cân nhắc, chấp nhận được ở scale hiện tại. Tạo `deferred.md` gom các mở rộng đã bàn nhưng không build (circuit-breaker fallback, time-bucket+segment sharding cho cardinality cao, audit log, retry-on-fail).
- 2026-09-09: Bỏ Phase 3 (`SchedulerPoller` poll DB thuần) theo yêu cầu — đi thẳng pipeline Phase 4. Viết mới 2 khung `@Scheduled` trong `infrastructure/scheduling/`: `IndexPoller` (nhịp Poll, `fixedDelay`, `pollDue` atomic claim, nuốt lỗi Redis/skip tick) + `IndexReconciler` (sweep chậm, `findDueBefore(now+lookahead)`, chỉ nạp index). Cả 2 dừng ở bước poll — vòng claim+fire / ghi index để comment kèm giải thích, chờ bật sau. Đổi tên config key `backstop-poll`/`fastpath-poll` → `index-reconciler`/`index-poller`. `mvn compile -pl scheduler-service -am` pass sạch. Chưa test runtime (chưa có Redis chạy + chưa seed job).
- 2026-09-09 (tiếp): Sync index vào 4 Handler (`Start`/`Stop`/`Edit`/`Fire`). Ban đầu tách helper `DueIndexSync` ở `application/scheduled_job/shared/`, sau đó **bỏ** — nó làm `DueItemFinder` có 2 class `implements` (helper + adapter), trong khi chủ ý là đúng 1 impl. Chốt: hoãn-sau-commit + best-effort thành **guarantee của contract `DueItemFinder`**, gộp vào impl duy nhất `RedisDueItemFinderAdapter` (`registerSynchronization(afterCommit)` khi trong tx, chạy ngay khi ngoài tx; nuốt `RuntimeException`). Handler chỉ inject `DueItemFinder`, gọi `index`/`deindex` như dòng lệnh thường; không import `org.springframework.transaction.*` ở application. `pollDue` không hoãn. Cơ chế thay thế `@TransactionalEventListener` (tránh thêm domain event + lẫn với `EventDispatcher` nội-transaction). `CreateScheduledJob` không sync (chỉ comment). Compile sạch.
- 2026-09-16 (tiếp): User chạy thử app thật lần đầu → boot fail `BeanCreationException` cho `outboxEventRepository`, root cause `IllegalArgumentException: Not a managed type: class vn.t3nexus.lib.outbox.OutboxEvent`. Đối chiếu các service khác dùng `outbox-starter` → phát hiện scheduler-service thiếu hẳn `JpaConfig` (`@EntityScan({"vn.t3nexus.scheduler", "vn.t3nexus.lib.outbox"})`) — sót từ Phase 2 bootstrap, không lộ ra lúc `mvn compile` (chỉ lộ khi Spring context thực sự khởi tạo `EntityManagerFactory`). Thêm file, `mvn compile -pl scheduler-service -am` pass sạch — user cần chạy lại app để xác nhận qua khỏi bước boot này.
- 2026-09-16: Chuẩn bị hạ tầng test — phát hiện `postgres-scheduler` CHƯA từng có trong `infra/docker-compose.yml` (application.properties trỏ `localhost:5439` từ trước nhưng không có service nào map port đó — gap có từ lúc bootstrap module, giờ mới lộ ra khi chuẩn bị chạy thật). Thêm service (port 5439, `wal_level=logical`, khớp `scheduler_db`/`t3nexus`/`t3nexus` đã cấu hình sẵn). Đối chiếu toàn bộ `application.properties` với compose — không có tham số nào lệch. Tạo `infra/debezium/connector-scheduler-outbox.json` (copy mẫu `connector-order-outbox.json`, đổi hostname/dbname/prefix), cập nhật `infra/README.md` (đăng ký/status/cleanup + bảng topics). Đăng ký connector thật + verify RUNNING chưa chạy (cần docker sống) — để user tự chạy tay.
- 2026-09-15 (tiếp, tiếp): User góp ý `taskType` không nên dùng làm filter chính cho Admin browse (opaque routing tag, không whitelist, không thân thiện) — đề xuất thêm `jobName` (nhãn Admin tự đặt) làm filter chủ lực (search substring), giữ `taskType`/`status` làm filter phụ (exact match). Thêm field mới `ScheduledJob.jobName` (migration `V4__scheduled_job_job_name.sql`, NOT NULL vì bảng đang rỗng) — lan xuống toàn bộ layer (`create()`/`edit()` domain, JPA entity/mapper, Command, `ScheduledJobView`, request/response DTO, `ScheduledJobQueryFilter` + JPQL `LOWER()+LIKE`). Kéo theo đổi số migration đã dự kiến trước đó cho Phase 4.6: `V4__scheduled_job_instance_version.sql` → `V5__...` (V4 nay thuộc về `job_name`). `mvn compile -pl scheduler-service -am` pass sạch. Cập nhật `api.yaml` (field `jobName` trong 3 schema + query param mới).
- 2026-09-15 (tiếp): Xây REST Admin API (Phase 5, đổi hướng từ "seed migration" sang "REST quản trị thật" đã dự định trước đó nhưng chưa viết lại). Thêm `common-web`/`spring-boot-starter-oauth2-resource-server`/`spring-boot-starter-validation`, `SecurityConfig` (`anyRequest().authenticated()`, không có endpoint public nào nên đơn giản hơn `catalog-service`), `WebConfig` (`@Import(GlobalExceptionHandler.class)`). Thêm 2 Query Handler mới (`GetScheduledJob`/`ListScheduledJobs`). Bản đầu mở thẳng `findAllPaged`/`countAll` trên `ScheduledJobRepository` (khớp convention `ListSellerProducts` bên catalog-service) — **đảo ngược ngay sau đó theo góp ý**: phân trang/đếm/lọc là read-model concern, không phải "vòng đời aggregate" mà `Repository<T,ID>` đại diện. Tách port riêng `ScheduledJobQueryPort` (`domain/scheduled_job/`) + impl `ScheduledJobQueryAdapter` (`infrastructure/adapter/query/`, đối xứng `adapter/repository/`) — dùng chung `ScheduledJobJpaRepository` (cùng bảng, không tách hạ tầng, chỉ tách port). Nhân dịp thêm 2 filter tuỳ chọn (`status`/`taskType`, `ScheduledJobQueryFilter`) — cố tình để `String` thô, không parse enum, để tránh nhân bản landmine `IllegalArgumentException`/500 y hệt gap đã phát hiện dưới đây. Phát hiện 2 gap chưa fix (ghi vào Phase 5): `MisfireInstruction.valueOf()` throw `IllegalArgumentException` (không phải `DomainException`) khi input sai → 500 thay vì 400; `SecurityConfig` chưa phân role ADMIN (chỉ check JWT hợp lệ) — rà lại thấy toàn hệ thống không có service nào tự làm role-based authorization ngoài `web-gateway`, nên tạm chấp nhận mức "authenticated()" đồng nhất. `mvn compile -pl scheduler-service -am` pass sạch. Tạo `service/scheduler-service/api.yaml` (OpenAPI, 6 path + 2 filter param).
- 2026-09-15: Thêm `ScheduledJobFiredHandler` (log-only) — lấp khoảng trống `EventDispatcher` registry rỗng từ Phase 2 (event raise xong không ai nhận). Chưa wire `OutboxEventStore` — để verify luồng fire trước, publish thật làm sau (TODO ghi thẳng trong javadoc). Compile sạch.
- 2026-09-10: Bật thân `IndexReconciler` + `IndexPoller`. `findDueBefore` đổi trả `List<DueJobRef>` (record id+dueAt) qua interface projection để reconciler có `nextFireAt` mà `ZADD`, không phải load cả aggregate. `IndexPoller` loop `FireScheduledJob.handle()` per-id, bọc `OptimisticLockingFailureException` (benign) + `RuntimeException` (log, đi tiếp — id đã claim bằng `ZREM` nên tick chết giữa chừng thì mất tới sweep kế). Bổ sung javadoc `IndexReconciler` phần "chạy trên mọi instance đồng thời, không guard" (SELECT read thuần + `ZADD` idempotent/commutative + tự lành ≤ 1 sweep). Compile sạch — chưa test runtime (interface projection native query trả `Instant` chưa verify với DB thật).
- 2026-09-05 (tiếp): Rà soát toàn bộ race condition trong pipeline mới — xác nhận mọi cặp race giữa `IndexPoller`/`IndexReconciler`/Admin (kể cả cross-instance HA) đã được chặn bởi 3 lớp sẵn có (Redis Lua atomic pop, row-lock `FOR UPDATE`, re-verify trong `fire()`), không cần thêm gì. Phát hiện 1 gap thật chưa được các lớp đó bao phủ: race **callback-vs-callback** — 2 lần Kafka redeliver cùng 1 message callback được xử lý đồng thời (không đảm bảo tuần tự trừ khi producer key theo `instanceId`, việc này ngoài tầm kiểm soát của `scheduler-service`). **Quyết định**: thêm `@Version` vào `scheduled_job_instance` làm lớp phòng thủ chính (tự `scheduler-service` kiểm soát, không phụ thuộc 3 producer làm đúng convention) + ghi khuyến nghị partition key `instanceId` vào `event-catalog.md` như lớp cộng thêm — cùng triết lý defense-in-depth đã dùng cho race Fire (không đặt cược correctness vào 1 lớp duy nhất, nhất là lớp nằm ngoài tầm kiểm soát).
