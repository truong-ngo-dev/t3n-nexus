# Data Schema — Scheduler Service

## Database

**Engine:** PostgreSQL
**Lý do:** Nguồn sự thật cho `scheduled_job` cần ACID — status transition (`PENDING`→`RUNNING`→`COMPLETED`) + row-lock (`FOR UPDATE`) + optimistic lock (`@Version`) chống double-fire khi 2 poller độc lập cùng chạm 1 row. Outbox Pattern (ADR-005) bắt buộc DB quan hệ để Debezium CDC hoạt động — nhất quán với các service khác trong hệ thống.

**Redis** (bổ sung **tuỳ chọn**, KHÔNG thay thế PostgreSQL — mất dữ liệu ở đây không sao, reconciliation sweep định kỳ trên Postgres luôn bắt kịp; xem `design.md` §NFR Assessment cho lý do thêm component này ở cardinality thấp):
- `scheduler:due` — 1 ZSET dùng chung cho toàn bộ `scheduled_job`, member = `id`, score = `next_fire_at` (epoch giây)

---

## Tables

### `scheduled_job`

Mỗi row là 1 job — với cardinality hiện tại (4 job cố định: loyalty expiry, cart cleanup, payout, outbox cleanup), bảng này gần như không bao giờ vượt vài chục row. Thiết kế theo mô hình **pipeline**: Postgres là nguồn sự thật duy nhất; Redis (ZSET) là index tăng tốc **tuỳ chọn**, được nạp lại định kỳ từ Postgres (reconciliation sweep) — **không phải 2 nguồn cùng tự fire độc lập**. Ở quy mô hiện tại lớp Redis chưa thật sự cần thiết cho performance — được thêm có chủ đích để rehearse pattern scale, xem `design.md` §NFR Assessment.

| Column                | Type           | Nullable | Notes                                                                                                                                                                                                  |
|-----------------------|----------------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`                  | `varchar(26)`  | NO       | PK — **ULID**, không phải UUID (đổi sau khi rà lại convention thật toàn hệ — xem `IdGenerator`/`ULIDGenerator` ở `implementation.md` §ULID). 26 ký tự, sinh ở Handler qua `ULIDGenerator` (pre-compute, không phải Double Dispatch), không phụ thuộc DB |
| `job_name`            | `varchar(200)` | NO       | **Mới (V4, 2026-09-15)** — nhãn hiển thị Admin tự đặt lúc tạo, sửa được qua `edit()`. Khác `task_type`: đây cho **con người** tìm/nhận diện job (search `LIKE`), `task_type` cho **routing kỹ thuật** (exact match, immutable) |
| `task_type`           | `varchar(100)` | NO       | Opaque với chính service — domain tiêu thụ tự định nghĩa giá trị (VD `LOYALTY_POINT_EXPIRY`)                                                                                                           |
| `schedule_type`       | `varchar(20)`  | NO       | `ONE_OFF` \| `RECURRING`                                                                                                                                                                               |
| `cron_expression`     | `varchar(100)` | YES      | NULL nếu `ONE_OFF`; bắt buộc nếu `RECURRING`                                                                                                                                                           |
| `timezone`            | `varchar(50)`  | YES      | NULL nếu `ONE_OFF`; default `Asia/Ho_Chi_Minh` khi `RECURRING`                                                                                                                                         |
| `due_at`              | `timestamptz`  | YES      | NULL nếu `RECURRING`; bắt buộc nếu `ONE_OFF`                                                                                                                                                           |
| `payload`             | `jsonb`        | NO       | Config gắn lúc tạo — opaque, consumer tự parse theo `task_type`; default `{}`                                                                                                                          |
| `status`              | `varchar(20)`  | NO       | `PENDING` \| `RUNNING` \| `COMPLETED` — đổi từ `PENDING\|FIRED\|CANCELLED` sau khi rà lại nghiệp vụ quản trị, xem `service.md` §Domain Model                                                           |
| `next_fire_at`        | `timestamptz`  | **YES**  | **NULL khi `PENDING`/`COMPLETED`** — bất biến `PENDING ⟺ next_fire_at IS NULL`. Cache từ `Schedule.nextFireTime()` khi `RUNNING` — cột index chính, DB không index được VO polymorphic trực tiếp       |
| `misfire_instruction` | `varchar(20)`  | NO       | `FIRE_NOW` \| `DO_NOTHING`; default `FIRE_NOW`. Chỉ thật sự có hiệu lực với job `RECURRING` — job `ONE_OFF` overdue luôn hành xử như `FIRE_NOW` bất kể giá trị này (xem `service.md` §`skipMisfire()`) |
| `version`             | `bigint`       | NO       | Default 0; JPA `@Version` — optimistic lock, backup cho `SELECT...FOR UPDATE` khi 2 nguồn cùng chạm 1 row (2 instance HA, hoặc reconciliation sweep race với Index Poller)                             |
| `created_at`          | `timestamptz`  | NO       |                                                                                                                                                                                                        |
| `updated_at`          | `timestamptz`  | NO       |                                                                                                                                                                                                        |

**Indexes:**
- `idx_scheduled_job_next_fire` on `(next_fire_at) WHERE status='RUNNING'` — **đổi điều kiện từ `status='PENDING'`** (RUNNING giờ mới là tập cần poll, PENDING chỉ là draft chưa activate) — partial index, dùng bởi Poller (Phase 3, poll trực tiếp) và reconciliation sweep (Phase 4, chỉ nạp index); tập này tự nhỏ và tự rỗng dần (giống pattern `inventory_reply_deadline` của order-service)
- `idx_scheduled_job_task_type` on `(task_type)` — tra cứu quản trị/debug theo loại job
- **`job_name` KHÔNG có index** — search dùng `LOWER(job_name) LIKE LOWER('%...%')` (wildcard 2 đầu), btree thường không tận dụng được. Ở cardinality hiện tại (vài chục job) sequential scan đủ rẻ; cần GIN + `pg_trgm` nếu mở rộng sau

**Locking:**
`FireScheduledJob.handle()` load row bằng `SELECT ... FOR UPDATE` (JPA `@Lock(PESSIMISTIC_WRITE)`) — kết hợp Claim + Re-verify thành đúng 1 round-trip DB, không cần bảng lock riêng kiểu `QRTZ_LOCKS` của Quartz. Row-lock tự nhả khi transaction commit/rollback — không cần cơ chế release tường minh.

**2 tầng phòng thủ độc lập, không phải 1**: row-lock ở trên chỉ đảm bảo 2 transaction không đọc/ghi đồng thời cùng 1 row — **không** tự động chặn được double-fire cho recurring job (vì `status` vẫn giữ `RUNNING` xuyên suốt các lần fire liên tiếp). Guard thật sự chặn double-fire nằm **trong chính `ScheduledJob.fire()`** (application code, không phải SQL) — re-verify `next_fire_at <= now` tại đúng thời điểm gọi, xem `service.md` §`ScheduledJob` domain methods.

---

### `outbox_events`

**Quản lý bởi `outbox-starter` (lib có sẵn, xem `services/libs/outbox-starter`) — KHÔNG tự định nghĩa entity riêng.** Schema dưới đây copy đúng từ `OutboxEvent.java` của lib (rà lại lúc bootstrap module — bản trước của file này tự vẽ khác, đã sửa cho khớp thật). Debezium đọc CDC từ bảng này, push `ScheduledJobFiredEvent` lên topic `scheduler.job.fired`.

| Column           | Type           | Nullable | Notes                                                              |
|------------------|----------------|----------|---------------------------------------------------------------------|
| `id`             | `bigint`       | NO       | PK, `GENERATED ALWAYS AS IDENTITY` — KHÔNG phải uuid                |
| `event_id`       | `varchar(100)` | NO       | Logical event id — Debezium EventRouter dùng để publish idempotent |
| `aggregate_type` | `varchar(100)` | NO       | `ScheduledJobInstance` — **không phải** `ScheduledJob` (event dời sang `ScheduledJobInstance.dispatch()`, xem `service.md` §`ScheduledJobInstance`) |
| `aggregate_id`   | `varchar(36)`  | NO       | ID (ULID, 26 ký tự) của `ScheduledJobInstance` phát event — cột `varchar(36)` do `outbox-starter` định nghĩa sẵn, dư chỗ so với 26 ký tự thật, không cần đổi |
| `event_type`     | `varchar(100)` | NO       | Tên class event                                                    |
| `routing_key`    | `varchar(255)` | NO       | `scheduler.job.fired`                                              |
| `payload`        | `text`         | NO       | Event payload serialized                                           |
| `occurred_on`    | `timestamptz`  | NO       | Thời điểm domain event thật sự xảy ra                              |
| `created_at`     | `timestamptz`  | NO       | Thời điểm ghi vào outbox                                           |
| `trace_id`       | `varchar(64)`  | YES      | OTel trace id, capture từ MDC lúc ghi                              |
| `span_id`        | `varchar(64)`  | YES      | OTel span id, capture từ MDC lúc ghi                               |

**Indexes:**
- `idx_outbox_events_created_at` on `(created_at)` — đúng index `outbox-starter` khai báo sẵn trên entity

Cleanup: chính scheduler-service tự dọn bảng này bằng 1 `ScheduledJob` nội bộ (`taskType=OUTBOX_CLEANUP`) — ăn ngay cơ chế của chính mình (đúng ADR-005: "scheduler-service chạy job định kỳ xóa outbox rows đã processed").

---

### `scheduled_job_instance`

1 row cho mỗi lần `ScheduledJobFireService.fire()` chạy thành công — tách khỏi `scheduled_job` (aggregate riêng, xem `service.md` §`ScheduledJobInstance`). `dispatch()` tự raise `ScheduledJobFiredEvent` (không phải `ScheduledJob.fire()`). Ghi lúc dispatch (`DISPATCHED`), consumer callback cập nhật `SUCCEEDED`/`FAILED` ở transaction hoàn toàn riêng, sau đó bất kỳ lúc nào.

| Column             | Type           | Nullable | Notes                                                                 |
|--------------------|----------------|----------|-------------------------------------------------------------------------|
| `id`               | `varchar(26)`  | NO       | PK — ULID, `ScheduledJobInstance.dispatch()` tự generate qua `ULIDGenerator` nhận thẳng làm tham số (Double Dispatch — khác `scheduled_job.id` là pre-compute; xem `ddd-structure.md` §Double Dispatch); chính là `instanceId` mang trong `ScheduledJobFiredEvent` |
| `scheduled_job_id` | `varchar(26)`  | NO       | FK logic tới `scheduled_job.id` (cùng kiểu ULID) — không cần FK constraint DB cứng (2 aggregate độc lập, đúng aggregate boundary) |
| `task_type`        | `varchar(100)` | NO       | Snapshot tại thời điểm fire — độc lập với `scheduled_job.task_type` sau đó |
| `payload`          | `jsonb`        | NO       | Snapshot tại thời điểm fire, opaque như `scheduled_job.payload`        |
| `status`           | `varchar(20)`  | NO       | `DISPATCHED` \| `SUCCEEDED` \| `FAILED`                                |
| `fired_at`         | `timestamptz`  | NO       | = T_emit                                                                |
| `completed_at`     | `timestamptz`  | YES      | NULL nếu còn `DISPATCHED`; set khi nhận callback                       |
| `failure_reason`   | `text`         | YES      | Chỉ có khi `status='FAILED'`                                            |
| `version`          | `bigint`       | NO       | Default 0; JPA `@Version` — xem §Locking                                |

**Indexes:**
- `idx_scheduled_job_instance_scheduled_job_id` on `(scheduled_job_id)` — phục vụ query lịch sử run của 1 job (audit/UI sau này, qua `adapter/query/`, chưa implement)

**Locking (2026-09-05, đảo ngược quyết định trước):** **Cần `@Version`** — lý do cũ ("Handler tạo lúc dispatch; callback update sau đó, không chồng lấp thời điểm") chỉ loại được race dispatch-vs-callback, **không** loại được race callback-vs-callback: Kafka at-least-once có thể redeliver cùng 1 message callback, và nếu 2 lần redeliver đó được xử lý đồng thời bởi 2 consumer thread/instance (không có gì đảm bảo tuần tự trừ khi producer key theo `instanceId` — xem `event-catalog.md` §scheduler-service), cả 2 có thể cùng đọc thấy `DISPATCHED` trước khi bên kia kịp ghi terminal. `@Version` chặn: bên thua `UPDATE ... WHERE version=X` ảnh hưởng 0 row → JPA throw `ObjectOptimisticLockingFailureException` → `CompleteScheduledJobInstanceHandler` catch, coi là benign skip (đúng nội dung — 2 message redeliver mang cùng outcome, không mất gì khi bỏ qua 1 trong 2), cùng idiom đã dùng cho `SchedulerPoller`/`IndexPoller`. Đây là lớp phòng thủ **chính, tự scheduler-service kiểm soát** — không phụ thuộc producer có key đúng `instanceId` hay không (partition key là khuyến nghị cộng thêm, xem `event-catalog.md`).

---

## Redis key design

`scheduler:due` — ZSET dùng chung cho toàn bộ job (không tách theo `task_type`, vì cardinality hiện tại quá nhỏ để cần sharding). Chỉ tồn tại từ Phase 4 (`implementation.md`) — trước đó Poller đọc thẳng `scheduled_job`.

**Ghi index — event-driven, best-effort:**
- `ZADD scheduler:due <nextFireAtEpoch> <scheduledJobId>` — lúc `start()` thành công (KHÔNG phải lúc `create()` — job `PENDING` chưa có `nextFireAt`, chưa vào ZSET) hoặc sau mỗi lần fire thành công của recurring job (tái lập lịch)
- `ZREM scheduler:due <scheduledJobId>` — lúc `stop()`/`edit()` (cả 2 đều đưa job về `PENDING`, `nextFireAt = null`), hoặc khi one-off chuyển `COMPLETED` — dọn sớm tránh worker xử lý rác về sau

**Reconciliation sweep — độc lập, cadence chậm (2-5 phút), CHỈ nạp lại index, không tự fire:**
- `SELECT id, next_fire_at FROM scheduled_job WHERE status='RUNNING' AND next_fire_at < now() + lookahead_window` → `ZADD` lại từng id — bù các lần `ZADD`/`ZREM` bị lỗi ở nhánh event-driven phía trên, hoặc entry bị Redis evict. **Không gọi `FireScheduledJob`** — sweep chỉ ghi index, việc claim+fire nằm hoàn toàn ở Index Poller bên dưới.

**Poll + Fire — nguồn DUY NHẤT chạy Claim + Trigger:**
- `ZRANGEBYSCORE scheduler:due 0 <now>` + Lua atomic pop — Index Poller, chu kỳ vài giây

Ghi Redis là **best-effort, không atomic** với ghi Postgres — mất 1 write không sao, reconciliation sweep (2-5 phút) tự nạp lại. **Lưu ý**: nếu Redis down **hoàn toàn** (không chỉ mất 1 entry), Index Poller không còn gì để đọc → fire tạm dừng tới khi Redis phục hồi — rủi ro chấp nhận được ở scale hiện tại, xem `design.md` §Failure Scenarios.

---

## Flyway Migration Plan

| Version | File                          | Nội dung                                                     |
|---------|--------------------------------|-------------------------------------------------------------------|
| V1      | `V1__init_schema.sql`         | `outbox_events`, indexes — đã tạo lúc bootstrap module            |
| V2      | `V2__scheduled_job.sql`       | `scheduled_job` (bao gồm `misfire_instruction`), indexes — đã tạo Phase 2 |
| V3      | `V3__scheduled_job_instance.sql`   | `scheduled_job_instance`, indexes — đã tạo Phase 2                       |
| V4      | `V4__scheduled_job_job_name.sql`  | Thêm cột `job_name VARCHAR(200) NOT NULL` vào `scheduled_job` — nhãn Admin đặt, search được, khác `task_type` (đã tạo 2026-09-15, xem §Constraint Summary) |
| V5      | `V5__scheduled_job_instance_version.sql` | Thêm cột `version bigint NOT NULL DEFAULT 0` vào `scheduled_job_instance` — chặn race callback-vs-callback, xem §Locking (TODO, quyết định 2026-09-05; đổi số từ V4 → V5 sau khi V4 dùng cho `job_name`) |

---

## Constraint Summary

```
scheduled_job.due_at                NOT NULL WHEN schedule_type='ONE_OFF'       (CHECK constraint)
scheduled_job.cron_expression       NOT NULL WHEN schedule_type='RECURRING'     (CHECK constraint)
scheduled_job.status                IN ('PENDING','RUNNING','COMPLETED')        (CHECK constraint)
scheduled_job.next_fire_at          NULL WHEN status IN ('PENDING','COMPLETED') (CHECK constraint)
scheduled_job.misfire_instruction   IN ('FIRE_NOW','DO_NOTHING')                (CHECK constraint)
```

**`misfireThreshold`** (ngưỡng "trễ quá xa" quyết định 1 candidate có bị coi là misfire hay không) là **application property**, không phải cột DB — cấu hình `scheduler.misfire-threshold-seconds`, đề xuất default = 2× chu kỳ poll (Phase 3: Poller đọc thẳng DB, VD 10s → threshold 20s; Phase 4: Index Poller đọc Redis, cadence tương tự — reconciliation sweep không tính vào đây vì nó không quyết định misfire). Cấp scheduler, không phải per-job — khớp đúng model Quartz.
