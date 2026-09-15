# Scheduler Service

## Trách nhiệm

Trigger job định kỳ/delayed cho các domain khác cần tự động hoá theo thời gian — hiện tại: loyalty point expiry, cart cleanup, seller payout, outbox cleanup. Chỉ quyết định **"khi nào"** — publish 1 tín hiệu trigger tối giản qua Kafka; domain service tiêu thụ tự quyết **"làm gì"** và tự query dữ liệu mới nhất của chính nó.

**Không làm:**
- Không gọi API trực tiếp vào service đích — không nằm trong 4 cặp sync đã duyệt (`global/2.architecture/4. communication.md`). Mọi trigger đều qua Kafka.
- Không diễn giải/thực thi business logic của consumer — `payload` mang theo là opaque, scheduler không đọc nội dung.
- Không quản lý auto-cancel đơn hàng — deadline nằm ngay trên bảng `orders` của `order-service`, tự polling nội bộ, không băng qua ranh giới service nên không cần scheduler-service.

---

## Domain Model

### Aggregates

| Aggregate              | Root Entity            | Invariants                                                                                                                                           |
|------------------------|------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ScheduledJob`         | `ScheduledJob`         | `PENDING ⟺ nextFireAt == null`; chỉ fire khi `status=RUNNING`; one-off sau khi fire → `COMPLETED`, recurring giữ `RUNNING` (tự tái lập `nextFireAt`) |
| `ScheduledJobInstance` | `ScheduledJobInstance` | 1 record cho mỗi lần fire; `DISPATCHED` → `SUCCEEDED`/`FAILED` (terminal, idempotent no-op nếu callback tới 2 lần)                                   |

**Bổ sung sau khi rà lại nghiệp vụ quản trị (Create/List/Detail/Start/Stop/Edit qua UI)**: `ScheduledJobStatus` đổi thành **3 giá trị `PENDING | RUNNING | COMPLETED`** (bỏ `FIRED`/`CANCELLED` cũ). Không có trạng thái "hủy vĩnh viễn" — `stop()` luôn đưa về `PENDING` (tạm dừng, khởi động lại được), khớp đúng pattern Kubernetes CronJob `suspend: true/false`. State machine đầy đủ:

```
PENDING ──(start)──> RUNNING ──(fire, recurring)──> RUNNING (tự lặp)
   ↑                     │
   └───────(stop)────────┘
                         └──(fire, one-off)──> COMPLETED (terminal — nhưng edit() vẫn mở lại được)

edit(): PENDING hoặc COMPLETED (status != RUNNING) → set schedule/payload/misfireInstruction mới,
        LUÔN đưa về PENDING, nextFireAt = null — không tự activate, vẫn cần bấm start() lại
```

`ScheduledJobInstance` là **aggregate riêng, KHÔNG phải child entity của `ScheduledJob`** — volume khác hẳn (1 recurring job tạo N instance theo thời gian) và transaction khác nhau (`fire()` ghi lúc Poll/Trigger; `succeed()`/`fail()` ghi ở transaction hoàn toàn riêng, xảy ra sau đó khi consumer callback). Outcome của `ScheduledJobInstance` **không bao giờ** ảnh hưởng ngược lại `ScheduledJob.status`/`nextFireAt` — xem Business Rules.

### ScheduledJob

```
ScheduledJob
├── ScheduledJobId
├── jobName              string    — nhãn hiển thị Admin tự đặt lúc tạo, SỬA ĐƯỢC qua edit() (mới, 2026-09-15)
├── taskType             string    — opaque với chính scheduler, domain tiêu thụ tự định nghĩa (VD "LOYALTY_POINT_EXPIRY")
├── schedule             Schedule (VO)  — OneOffSchedule | RecurringSchedule
├── payload              JSONB     — config gắn lúc tạo, opaque, consumer tự parse theo taskType
├── status               PENDING | RUNNING | COMPLETED
├── nextFireAt           Instant, nullable  — null khi PENDING/COMPLETED; cache từ schedule.nextFireTime() khi RUNNING
├── misfireInstruction   FIRE_NOW | DO_NOTHING  — hành vi khi 1 candidate bị phát hiện trễ quá `misfireThreshold` (config cấp scheduler, xem Business Rules); default FIRE_NOW
├── createdAt
└── updatedAt
```

Domain methods:
- `ScheduledJob.create(id, jobName, taskType, schedule, payload, misfireInstruction)` — static factory; **không nhận `CronCalculator`, không tính `nextFireAt`** (để `null`) — status = `PENDING`. `id` (`ScheduledJobId`) do Handler generate trước rồi truyền vào — **pre-compute, không phải Double Dispatch** (sinh ID không cần đọc field nào của aggregate, khác `CronCalculator` cần `this.schedule`; xem `ddd-structure.md` §Double Dispatch)
- `ScheduledJob.start(now, cronCalculator)` — **Double Dispatch**: guard 1 — `status == RUNNING` → throw `invalidTransition` (request có chủ đích rõ ràng, không nên âm thầm no-op khi vô nghĩa, khác `stop()`); guard 2 — `next = schedule.nextFireTime(now, cronCalculator)` rỗng → throw `alreadyDue` (rỗng ở đây LÀ lỗi thật — activate 1 job không còn tương lai nào để chạy, khác ý nghĩa "rỗng" trong `fire()`); còn giá trị → `nextFireAt = next`, `status = RUNNING`
- `ScheduledJob.fire(now, cronCalculator)` — **Double Dispatch, KHÔNG nhận `instanceId`, KHÔNG raise event** (khác thiết kế ban đầu — xem `ScheduledJobInstance` bên dưới cho lý do dời event sang đó). Guard **không phải status-guard** — status không đổi qua các lần fire liên tiếp của recurring job (luôn `RUNNING`) nên không đủ chặn double-fire; guard thật là **re-verify `nextFireAt == null || nextFireAt.isAfter(now)` → throw `invalidTransition`** — chặn đúng race 2-poller (Redis fast-path + Postgres backstop) cùng tìm ra 1 candidate gần như đồng thời, khớp "guard quan trọng nhất toàn hệ thống" theo Knowledge base `5. core-components.md` Flow D bước 2. Qua guard rồi: tính `next = schedule.nextFireTime(now, cronCalculator)`; còn giá trị → `nextFireAt = next`, `status` giữ `RUNNING`; rỗng → `status = COMPLETED`, `nextFireAt = null`
- `ScheduledJob.skipMisfire(now, cronCalculator)` — guard: `canProcess()` (đủ dùng ở đây — Handler đã tự `findByIdForUpdate` trước khi rẽ vào nhánh này, không cần re-verify riêng như `fire()`); tính lại `next`; còn giá trị → `nextFireAt = next`, giữ `RUNNING`, **không** raise event, **không** tạo `ScheduledJobInstance`; rỗng → `status = COMPLETED`, `nextFireAt = null`. Chỉ thật sự có ý nghĩa với `RecurringSchedule` — one-off overdue tự nhiên rơi vào `COMPLETED`, hành xử như `FIRE_NOW` bất kể cấu hình
- `ScheduledJob.stop()` — rename từ `cancel()`; guard: chỉ tác dụng khi `RUNNING` → `status = PENDING`, `nextFireAt = null`; **idempotent no-op** nếu đã `PENDING`/`COMPLETED` — an toàn gọi lại nhiều lần, không cần caller biết trước trạng thái (khác `start()`, có chủ đích rõ). **Không phải "hủy vĩnh viễn"** — `start()` lại được sau, đúng pattern K8s CronJob `suspend`
- `ScheduledJob.edit(jobName, schedule, payload, misfireInstruction)` — guard: `status == RUNNING` → throw; set 4 field mới (thay hoàn toàn, VO immutable); **luôn** đưa `status = PENDING`, `nextFireAt = null` bất kể trạng thái trước đó — không tự activate, cần `start()` lại. Cho sửa cả job `COMPLETED` (cho phép "chạy lại 1 lần" — one-off không nhất thiết dùng đúng 1 lần). `taskType` KHÔNG sửa được; `jobName` sửa được thoải mái (chỉ là nhãn hiển thị)
- `ScheduledJob.canProcess()` — `status == RUNNING`; dùng để **lọc candidate** ở tầng poll (không phải guard correctness chính — đó là việc của re-verify trong `fire()`)

`cronCalculator` (`CronCalculator`) — port kỹ thuật thuần, Double Dispatch, xem mục `CronCalculator` bên dưới. Truyền qua tham số ở `start()`/`fire()`/`skipMisfire()` — không phải field của `ScheduledJob`/`Schedule`. `create()`/`edit()` không cần vì không tính `nextFireAt`.

### ScheduledJobInstance

```
ScheduledJobInstance
├── ScheduledJobInstanceId
├── scheduledJobId   ScheduledJobId  — ref cùng BC, dùng typed Id (không lưu cả ScheduledJob)
├── taskType         string          — snapshot tại thời điểm fire
├── payload          JSONB           — snapshot tại thời điểm fire, độc lập với payload hiện tại của ScheduledJob
├── status           DISPATCHED | SUCCEEDED | FAILED
├── firedAt          Instant         — = T_emit
├── completedAt      Instant, null   — set khi nhận callback
└── failureReason    string, null    — chỉ có khi FAILED
```

Domain methods:
- `ScheduledJobInstance.dispatch(ulidGenerator, scheduledJobId, taskType, payload, firedAt)` — static factory, gọi bởi `ScheduledJobFireService` ngay sau `ScheduledJob.fire()`, cùng transaction. **Raise `ScheduledJobFiredEvent` tại đây** (không phải `ScheduledJob.fire()` như thiết kế ban đầu) — nội dung event (`taskType`/`payload`/`instanceId`) toàn bộ là field của chính aggregate này, đúng convention "Domain Event đặt trong package của aggregate phát ra event". `aggregateId`/`aggregateType` của event = `instanceId`/"ScheduledJobInstance", không phải `scheduledJobId`/"ScheduledJob" — `scheduledJobId` chỉ mang theo trong payload. Nhận thẳng `ULIDGenerator` (Double Dispatch) — tự generate `id` bên trong, khác `ScheduledJob.create()` (nhận `id` đã pre-compute sẵn); xem `ddd-structure.md` §Double Dispatch, mục "Trường hợp ranh giới: sinh ID" cho lý do chọn khác nhau ở 2 chỗ
- `ScheduledJobInstance.succeed(completedAt)` / `ScheduledJobInstance.fail(reason, completedAt)` — set outcome, idempotent no-op nếu đã terminal (Kafka at-least-once có thể redeliver cùng 1 completion event)

### ScheduledJobFireService (Domain Service — cross-aggregate)

```
ScheduledJobFireService.fire(scheduledJob: ScheduledJob, now: Instant) -> ScheduledJobInstance
```

Điều phối `ScheduledJob.fire()` với việc tạo `ScheduledJobInstance` tương ứng — 2 aggregate, 1 bất biến nghiệp vụ chung ("fire luôn tạo đúng 1 instance, snapshot đúng `taskType`/`payload` tại thời điểm fire"). Đúng tiêu chí dùng Domain Service theo `ddd-structure.md` ("Logic cần phối hợp nhiều aggregate") — không đặt trong `ScheduledJob` (vi phạm aggregate boundary, không được tạo thẳng aggregate khác) và không để Handler tự lặp lại đúng 2 bước này mỗi nơi cần fire.

- Không gọi Repository, không biết persistence tồn tại — Handler vẫn là nơi duy nhất `save()` cả 2 aggregate (cùng transaction) + dispatch event, lấy từ **`instance.pullEvents()`** — **không phải** `scheduledJob.pullEvents()` (event thuộc `ScheduledJobInstance` từ sau khi dời, xem mục đó).
- Không tự check `canProcess()` — tin caller (Handler) đã verify trước khi gọi (Flow D re-verify tầng Handler). `ScheduledJob.fire()` bên trong vẫn tự có guard re-verify riêng (`nextFireAt<=now`) — 2 tầng phòng thủ độc lập, không phải service này lặp lại việc của aggregate.
- Đánh dấu `@Service` (Spring) dù `ddd-structure.md` nói `domain/` không import Spring framework — theo đúng precedent thật đã có trong repo (`AttributeTemplateDomainService` ở `catalog-service`), không phải ngoại lệ riêng của scheduler-service.
- Inject **2 port** qua DI, cả 2 đều truyền thẳng vào aggregate method (Double Dispatch): `CronCalculator` vào `ScheduledJob.fire()` (bắt buộc — aggregate cần đọc field `schedule` của chính nó); `ULIDGenerator` vào `ScheduledJobInstance.dispatch()` (không bắt buộc theo tiêu chí chung — sinh ID không cần field nào của aggregate, đa số service khác pre-compute — nhưng chọn double dispatch ở đây để nhất quán 1 kiểu gọi cho cả 2 port cùng inject trong class này, không phải rule cứng; xem `ddd-structure.md` §Double Dispatch, mục "Trường hợp ranh giới: sinh ID").

### Schedule (Value Object — domain concept riêng, dùng chung nếu BC khác cần)

```
Schedule (sealed interface):
  nextFireTime(after: Instant, cronCalculator: CronCalculator) -> Optional<Instant>
  isRecurring() -> boolean

OneOffSchedule(dueAt: Instant)                  — trả dueAt nếu after < dueAt, rỗng nếu đã qua; bỏ qua cronCalculator
RecurringSchedule(cron: String, zone: ZoneId)   — delegate hoàn toàn cho cronCalculator, không bao giờ rỗng
```

1 method `fire()` xử lý được cả 2 loại nhờ tính đa hình ở `Schedule` — không cần nhánh `if (recurring)` trong aggregate. `RecurringSchedule` không tự parse cron nữa (không import Spring) — validate cú pháp cron xảy ra ở lần gọi `nextFireTime()` đầu tiên, qua `CronCalculator` (xem mục dưới), không phải trong constructor của record.

### CronCalculator (port — không thuộc aggregate nào)

```
CronCalculator (functional interface, domain/schedule/):
  nextFireTime(cron: String, zone: ZoneId, after: Instant) -> Optional<Instant>
```

Tách khỏi `RecurringSchedule` để domain không phụ thuộc trực tiếp thư viện parse cron cụ thể — implementation thật (`SpringCronCalculatorAdapter`, dùng `org.springframework.scheduling.support.CronExpression`) nằm ở `infrastructure/adapter/service/cron/`, đây là **duy nhất** chỗ trong service còn import package `org.springframework.scheduling.*`.

Truyền thẳng port vào `Schedule.nextFireTime()` (không resolve trước ở Handler rồi truyền value, khác pattern `CollaboratorService`/`AssigneeService`) — vì đây là Strategy thuần: deterministic, không I/O, không state, không băng qua bounded-context nào. Giữ polymorphism của `Schedule` nguyên vẹn (`OneOffSchedule` bỏ qua tham số hoàn toàn), tránh tái sinh nhánh `if (recurring)` ở tầng gọi. `ScheduledJobFireService` giữ instance thật qua DI, truyền tiếp xuống `ScheduledJob.fire()`/`skipMisfire()`/`create()`.

### Commands

| Command                   | Handler                          | Publishes                                                             |
|----------------------------|-------------------------------------|--------------------------------------------------------------------------|
| `CreateScheduledJob`      | `CreateScheduledJobHandler`      | —                                                                      |
| `StartScheduledJob`       | `StartScheduledJobHandler`       | —                                                                      |
| `StopScheduledJob`        | `StopScheduledJobHandler`        | — *(rename từ `CancelScheduledJob`)*                                    |
| `EditScheduledJob`        | `EditScheduledJobHandler`        | —                                                                      |
| `FireScheduledJob`        | `FireScheduledJobHandler`        | `ScheduledJobFiredEvent` *(chỉ nếu `canProcess()==true`)*              |
| `CompleteScheduledJobInstance` | `CompleteScheduledJobInstanceHandler` | — *(reactive, trigger bởi callback event từ consumer, xem §Callback)* |

### Queries

| Query                | Handler               | Ghi chú                                                                 |
|-----------------------|------------------------|--------------------------------------------------------------------------|
| `GetScheduledJob`    | `GetScheduledJob`     | `findById` thuần (không lock, qua `ScheduledJobRepository`) — read cho hiển thị, không dẫn tới mutation |
| `ListScheduledJobs`  | `ListScheduledJobs`   | Phân trang + filter (`jobName` search, `status`/`taskType` exact) qua `ScheduledJobQueryPort` (**không phải** `ScheduledJobRepository`), sort `createdAt` giảm dần |

**`ScheduledJobQueryPort`** (`domain/scheduled_job/`, impl `ScheduledJobQueryAdapter` ở `infrastructure/adapter/query/`) — tách khỏi `ScheduledJobRepository` (2026-09-15): phân trang/đếm/lọc cho Admin List UI là read-model concern, không phải "quản lý vòng đời aggregate" mà `Repository<T,ID>` đại diện — không flow nghiệp vụ nào (Start/Stop/Edit/Fire) cần `search`, chỉ Admin UI liệt kê job mới cần. Dùng chung `ScheduledJobJpaRepository` với `ScheduledJobRepositoryAdapter` (cùng bảng, cùng entity) — chỉ tách port domain, không tách hạ tầng persistence.

**Đã đảo ngược quyết định trước đó** — trước đây "Không expose REST endpoint public" vì `CreateScheduledJob` chỉ dùng nội bộ (bootstrap seed, 4 job tĩnh). Sau khi rà lại nghiệp vụ quản trị (Create/List/Detail/Start/Stop/Edit qua UI admin), **cần REST endpoint public thật** — kéo theo: thêm actor Admin, thêm lại `spring-boot-starter-oauth2-resource-server` vào `pom.xml` (đã cố tình bỏ lúc bootstrap module vì tưởng không cần).

**Đã implement (2026-09-15)** — `ScheduledJobController` (`presentation/scheduled_job/`), toàn bộ dưới `/api/admin/scheduled-jobs/**`, xem `api.yaml`: `POST` (create), `GET` list (phân trang + filter qua `ScheduledJobQueryPort` — xem trên), `GET /{id}` (detail, qua `ScheduledJobRepository`), `PUT /{id}` (edit), `POST /{id}/start`, `POST /{id}/stop`. `SecurityConfig` chỉ `anyRequest().authenticated()` — chưa phân role ADMIN riêng (bất kỳ JWT hợp lệ nào cũng gọi được), xem Open questions ở `implementation.md`. `Create`/`Edit` nay có thêm field `jobName` bắt buộc (nhãn hiển thị, xem §Domain Model).

**Còn treo — chưa thiết kế chi tiết**: audit log lịch sử thay đổi job (ai `start`/`stop`/`edit` lúc nào) — khuyến nghị 1 aggregate riêng `ScheduledJobAuditLog` (append-only, ghi qua Event Handler phản ứng theo domain event của `ScheduledJob`, không phải trong chính Handler chính) — chưa thiết kế field/event cụ thể.

### Domain Events

| Event                    | Trigger                                                        | Consumers (dự kiến theo `taskType`)                                                       |
|--------------------------|------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `ScheduledJobFiredEvent` | `ScheduledJobInstance.dispatch()` — gọi bởi `ScheduledJobFireService` sau khi `ScheduledJob.fire()` thành công | `customer-service` (loyalty expiry), `cart-service` (cleanup), `payment-service` (payout) |

`outbox_events` cleanup (ADR-005) chạy nội bộ scheduler-service (tự dọn bảng của chính mình), không publish event ra ngoài. `ScheduledJobInstance.succeed()`/`fail()` cũng không phát event ra ngoài — pure sink, chỉ cập nhật chính nó (xem §Callback).

### Business Rules

- `ScheduledJobFiredEvent` payload tối giản — chỉ mang `instanceId`, `taskType` + `payload` đã cấu hình lúc tạo, **không** tra cứu thêm dữ liệu nghiệp vụ nào khác. Consumer tự query dữ liệu mới nhất của chính nó (DB isolation + async-by-default).
- Không tier nào đạt exactly-once ở tầng trigger — consumer bắt buộc tự idempotent (dedup theo `eventId`, giống mọi Kafka consumer khác trong hệ thống).
- Chỉ **1 nguồn** thực sự chạy Claim + Trigger tại một thời điểm kiến trúc: Index Poller — đọc thẳng `ScheduleStore` trước khi có Redis (Phase 3), đọc `DueItemFinder` sau khi có Redis (Phase 4). Reconciliation sweep định kỳ trên Postgres (chỉ tồn tại từ Phase 4) chỉ nạp lại index, **không tự fire** — khác thiết kế "2 loop độc lập cùng fire" đã cân nhắc và bỏ, xem `design.md`. Dù vậy race vẫn có thể xảy ra (2 instance `scheduler-service` chạy HA, hoặc index vừa được nạp lại đúng lúc đang xử lý) — chặn tại `ScheduledJob.fire()`, qua **3 lớp** kết hợp: `SELECT...FOR UPDATE` (row-lock DB, chặn 2 transaction đọc-ghi đồng thời), `@Version` (optimistic lock, backup nếu không dùng row-lock), và **re-verify `nextFireAt<=now` bên trong chính `fire()`** (guard thật sự chặn double-fire cho recurring job — vì `status` không đổi qua các lần fire liên tiếp nên tự nó không đủ, xem domain method `fire()` ở trên).
- `payload` là JSONB opaque — schema bên trong do consumer sở hữu (VD `taskType=LOYALTY_POINT_EXPIRY` có thể không cần payload gì, chỉ cần tín hiệu "tới giờ rồi").
- `misfireThreshold` (ngưỡng "trễ quá xa" so với `nextFireAt`) là **application property cấp scheduler** (`scheduler.misfire-threshold-seconds`), không phải cột DB, không phải per-job — khớp đúng model Quartz (`misfireThreshold` per-scheduler-instance, không phải per-trigger). Default đề xuất = 2× chu kỳ poll (Phase 3: chu kỳ Poller đọc thẳng DB; Phase 4: chu kỳ Index Poller đọc Redis).
- Nhánh misfire-skip (`skipMisfire()`) được check ngay trong `FireScheduledJobHandler`, ngay sau khi load row bằng `findByIdForUpdate` — cùng 1 round-trip DB với check `canProcess()`, không tách thành command/lượt lock riêng.

### Callback — `ScheduledJobInstance` outcome

- Mỗi lần `ScheduledJobFireService.fire()` chạy thành công (`ScheduledJob.fire()` qua được guard re-verify) tạo đúng 1 `ScheduledJobInstance` (status `DISPATCHED`) ngay sau đó — Handler `save()` cả 2 aggregate cùng transaction (xem `implementation.md`).
- Consumer (`customer-service`/`cart-service`/`payment-service`) xử lý xong `ScheduledJobFiredEvent` → publish 1 event callback riêng (qua Outbox của chính nó, topic do từng producer tự đặt tên theo convention của BC đó — chưa chốt, xem `event-catalog.md` §scheduler-service) mang `instanceId` + `outcome` (SUCCEEDED/FAILED) + `completedAt`.
- `scheduler-service` consume event đó → `CompleteScheduledJobInstance` → load `ScheduledJobInstance` theo `instanceId` → `succeed()`/`fail()` → save. **Không** động tới `ScheduledJob` — đúng nguyên tắc "status không mang outcome" (Knowledge base `distributed-systems/components/job-scheduling/5. core-components.md`): tiến độ lịch trình (`ScheduledJob`) và outcome lần chạy (`ScheduledJobInstance`) là 2 khái niệm tách bạch hoàn toàn.
- Retry-khi-fail: **cố tình để ngỏ** (seam), chưa quyết định triển khai. Nếu làm sau này, phải là 1 cơ chế tách biệt đọc `ScheduledJobInstance.status`/`failureReason` — không fuse vào `ScheduledJob.fire()` (đúng khuyến nghị Knowledge base "Retry — vì sao cố tình không phải bước thứ 6": policy biến thiên mạnh, không có 1 hình dạng đúng để cố định thành primitive).
- Callback không bắt buộc phải tới — nếu consumer không publish (hoặc service đó chưa implement phần callback), `ScheduledJobInstance` giữ nguyên `DISPATCHED` vô thời hạn. Đây là gap chấp nhận được ở scope hiện tại (chỉ ảnh hưởng observability/audit, không ảnh hưởng correctness của lịch trigger).

---

## Use Cases — tham gia

| Feature                                                           | Role                            | Handles | Publishes                |
|-------------------------------------------------------------------|---------------------------------|---------|--------------------------|
| [scheduler-service](../../feature/08-scheduler-service/design.md) | Coordinator — trigger theo lịch | —       | `ScheduledJobFiredEvent` |

---

## Integration Contract

### Publishes (Kafka)

| Topic                 | Event                    | Partition Key                                                             |
|-----------------------|--------------------------|---------------------------------------------------------------------------|
| `scheduler.job.fired` | `ScheduledJobFiredEvent` | `taskType` — cùng loại job vào cùng partition, giữ order nếu consumer cần |

### Consumes (Kafka)

Callback outcome từ `customer-service`/`cart-service`/`payment-service` — cập nhật `ScheduledJobInstance`, xem §Callback + `event-catalog.md` §scheduler-service cho contract tối thiểu (`instanceId`, `outcome`, `failureReason`, `completedAt`). **Tên topic cụ thể của từng producer chưa chốt** — cần xác định khi implement Phase 3+ của từng consumer (nằm ngoài scope hiện tại, chỉ scheduler-service side).

### Sync Calls

Không có. `scheduler-service` không nằm trong 4 cặp sync đã duyệt (`4. communication.md`) — mọi trigger đều qua Kafka.

---

## Dependencies

- **Services cần chạy cùng:** không service nào bắt buộc lúc khởi động — scheduler tự chạy độc lập. `customer-service`/`cart-service`/`payment-service` cần chạy để tiêu thụ `scheduler.job.fired`, nhưng scheduler-service vẫn đúng đắn (publish thành công) dù consumer đang down — Kafka giữ message.
- **Infrastructure:**
  - PostgreSQL — nguồn sự thật (`scheduled_job`, `outbox_events`)
  - Redis — tăng tốc tìm job đến hạn (ZSET), **không phải nguồn sự thật**, **component tuỳ chọn** ở cardinality hiện tại (thêm có chủ đích để rehearse pattern scale, xem `design.md` §NFR Assessment) — xem `data.md`
  - Kafka — publish `scheduler.job.fired` qua Debezium CDC từ `outbox_events`

## Tài liệu liên quan

- [`data.md`](data.md) — DB schema, Redis key design
- [scheduler-service (feature)](../../feature/08-scheduler-service/design.md) — implementation plan, phase checklist
