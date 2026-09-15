package vn.t3nexus.scheduler.domain.scheduled_job;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.scheduler.domain.schedule.CronCalculator;
import vn.t3nexus.scheduler.domain.schedule.Schedule;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * State machine — {@code PENDING ⟺ nextFireAt == null}. Không có trạng thái "hủy vĩnh viễn" — khớp
 * pattern Kubernetes CronJob {@code suspend}:
 * <pre>
 * PENDING --(start)--> RUNNING --(fire, recurring)--> RUNNING (tự lặp)
 *    ^                     |
 *    +-------(stop)--------+
 *                          +--(fire, one-off)--> COMPLETED (terminal, nhưng edit() vẫn mở lại được)
 *
 * edit(): PENDING hoặc COMPLETED (status != RUNNING) -> luôn về PENDING, nextFireAt = null
 * </pre>
 * Không còn biết {@code ScheduledJobInstance} tồn tại — {@code fire()} không nhận/tạo instanceId nào,
 * việc tạo {@code ScheduledJobInstance} + raise {@code ScheduledJobFiredEvent} hoàn toàn thuộc về
 * {@link ScheduledJobFireService} sau khi gọi {@code fire()} xong.
 */
@SuppressWarnings("all")
public class ScheduledJob extends AbstractAggregateRoot<ScheduledJobId> {

    private String jobName;
    private String taskType;
    private Schedule schedule;
    private Map<String, Object> payload;
    private ScheduledJobStatus status;
    private Instant nextFireAt;
    private MisfireInstruction misfireInstruction;
    private Instant createdAt;
    private Instant updatedAt;

    private ScheduledJob() {}

    /**
     * Không nhận {@code CronCalculator} — không tính {@code nextFireAt}, để null. Guard "đã quá hạn"
     * dời sang {@code start()}. Nhận {@code id} từ ngoài (Handler tự inject {@code ULIDGenerator},
     * generate rồi truyền vào) — pre-compute, không phải Double Dispatch (sinh ID không cần đọc field
     * nào của aggregate, xem ddd-structure.md §Double Dispatch), khớp đúng convention thật toàn hệ.
     */
    public static ScheduledJob create(ScheduledJobId id, String jobName, String taskType, Schedule schedule,
                                      Map<String, Object> payload, MisfireInstruction misfireInstruction) {
        Instant now = Instant.now();
        ScheduledJob job = new ScheduledJob();
        job.setId(id);
        job.jobName = jobName;
        job.taskType = taskType;
        job.schedule = schedule;
        job.payload = Optional.ofNullable(payload).orElse(Map.of());
        job.status = ScheduledJobStatus.PENDING;
        job.misfireInstruction = Optional.ofNullable(misfireInstruction).orElse(MisfireInstruction.FIRE_NOW);
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    /** Reconstitute từ persistence — dùng bởi repository, không fire event. */
    public static ScheduledJob reconstitute(ScheduledJobId id, String jobName, String taskType, Schedule schedule,
                                            Map<String, Object> payload, ScheduledJobStatus status,
                                            Instant nextFireAt, MisfireInstruction misfireInstruction,
                                            Instant createdAt, Instant updatedAt) {
        ScheduledJob job = new ScheduledJob();
        job.setId(id);
        job.jobName = jobName;
        job.taskType = taskType;
        job.schedule = schedule;
        job.payload = payload;
        job.status = status;
        job.nextFireAt = nextFireAt;
        job.misfireInstruction = misfireInstruction;
        job.createdAt = createdAt;
        job.updatedAt = updatedAt;
        return job;
    }

    /**
     * Activate lịch trình — PENDING/COMPLETED → RUNNING. Gọi trực tiếp từ {@code StartScheduledJobHandler}
     * (Double Dispatch — {@code CronCalculator} truyền thẳng vào, không qua domain service vì không
     * đụng {@code ScheduledJobInstance}, xem ddd-structure.md §Double Dispatch).
     * <br>Guard "đã RUNNING → throw": khác {@code stop()} (idempotent có chủ đích), đây là request có
     * chủ đích rõ ràng — không nên âm thầm no-op khi vô nghĩa (giống {@code Order.confirm()}).
     * <br>Guard "rỗng → throw alreadyDue": rỗng ở đây là LỖI thật (activate 1 job không còn tương lai
     * nào để chạy) — khác hẳn ý nghĩa "rỗng" trong {@code fire()}.
     */
    public void start(Instant now, CronCalculator cronCalculator) {
        if (status == ScheduledJobStatus.RUNNING) throw ScheduledJobException.invalidTransition(status, "start");
        Optional<Instant> next = schedule.nextFireTime(now, cronCalculator);
        if (next.isEmpty()) throw ScheduledJobException.alreadyDue();
        this.nextFireAt = next.get();
        this.status = ScheduledJobStatus.RUNNING;
        this.updatedAt = now;
    }

    /**
     * Poller gọi khi job đến hạn — recurring tự tái lập {@code nextFireAt} (giữ RUNNING), one-off hết
     * lịch → COMPLETED. Double Dispatch giống {@code start()} — cùng lý do (kết quả là field thật sự
     * lưu lại của aggregate, xem ddd-structure.md §Double Dispatch).
     * <br>Guard "còn due thật không" — re-verify chống double-fire khi 2 poller (Redis fast-path +
     * Postgres backstop) đua nhau tìm cùng 1 job. Khác {@code canProcess()}/status-guard: status vẫn
     * giữ RUNNING xuyên suốt các lần fire liên tiếp của recurring job, nên check status không đủ —
     * phải re-verify đúng {@code nextFireAt} (đúng "guard quan trọng nhất" theo Knowledge base
     * {@code 5. core-components.md}, Flow D bước 2).
     * <br>Không nhận/tạo {@code instanceId}, không raise event — {@link ScheduledJobFireService} lo
     * việc tạo {@code ScheduledJobInstance} (nơi thật sự raise {@code ScheduledJobFiredEvent}) sau khi
     * gọi method này.
     */
    public void fire(Instant now, CronCalculator cronCalculator) {
        if (this.nextFireAt == null || this.nextFireAt.isAfter(now)) {
            throw ScheduledJobException.invalidTransition(status, "fire");
        }
        Optional<Instant> next = schedule.nextFireTime(now, cronCalculator);
        this.nextFireAt = next.orElse(null);
        this.status = next.isPresent() ? ScheduledJobStatus.RUNNING : ScheduledJobStatus.COMPLETED;
        this.updatedAt = now;
    }

    /**
     * Nhánh misfire-skip (design.md, {@code misfireInstruction=DO_NOTHING} + trễ quá
     * {@code misfireThreshold}) — dời lịch như {@link #fire} nhưng KHÔNG check lại re-verify (Handler
     * đã tự load + lock trước khi quyết định gọi nhánh này), KHÔNG tạo {@code ScheduledJobInstance}.
     * Chỉ thật sự có ý nghĩa với recurring — one-off overdue rơi thẳng vào nhánh rỗng (COMPLETED), tự
     * nhiên hành xử như {@code FIRE_NOW} bất kể cấu hình, không cần nhánh code riêng.
     */
    public void skipMisfire(Instant now, CronCalculator cronCalculator) {
        if (!canProcess()) throw ScheduledJobException.invalidTransition(status, "skipMisfire");
        Optional<Instant> next = schedule.nextFireTime(now, cronCalculator);
        this.nextFireAt = next.orElse(null);
        this.status = next.isPresent() ? ScheduledJobStatus.RUNNING : ScheduledJobStatus.COMPLETED;
        this.updatedAt = now;
    }

    /**
     * Tạm dừng — RUNNING → PENDING, {@code nextFireAt = null}. KHÔNG phải hủy vĩnh viễn — {@code start()}
     * lại được sau. Idempotent no-op nếu đã PENDING/COMPLETED — an toàn gọi lại nhiều lần, không cần
     * caller biết trước trạng thái hiện tại (khác {@code start()}).
     */
    public void stop() {
        if (status != ScheduledJobStatus.RUNNING) return;
        this.status = ScheduledJobStatus.PENDING;
        this.nextFireAt = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Sửa jobName/schedule/payload/misfireInstruction — chỉ khi {@code status != RUNNING} (PENDING hoặc
     * COMPLETED). Luôn đưa về PENDING + {@code nextFireAt = null} bất kể trạng thái trước đó — không
     * tự activate, vẫn cần {@code start()} lại. Cho phép sửa job {@code COMPLETED} để dùng lại (one-off
     * job không nhất thiết dùng đúng 1 lần). {@code taskType} không sửa được — đổi taskType gần như
     * "xoá tạo lại" hơn là edit; {@code jobName} thì sửa được vô tư — chỉ là nhãn hiển thị, không mang
     * ý nghĩa định tuyến như {@code taskType} (xem javadoc field).
     */
    public void edit(String jobName, Schedule schedule, Map<String, Object> payload, MisfireInstruction misfireInstruction) {
        if (status == ScheduledJobStatus.RUNNING) throw ScheduledJobException.invalidTransition(status, "edit");
        this.jobName = jobName;
        this.schedule = schedule;
        this.payload = Optional.ofNullable(payload).orElse(Map.of());
        this.misfireInstruction = Optional.ofNullable(misfireInstruction).orElse(MisfireInstruction.FIRE_NOW);
        this.status = ScheduledJobStatus.PENDING;
        this.nextFireAt = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Dùng bởi cả 2 lớp poll (Redis fast-path + Postgres backstop) để lọc candidate trước khi gọi
     * {@link #fire}/{@link #skipMisfire}.
     */
    public boolean canProcess() {
        return status == ScheduledJobStatus.RUNNING;
    }

    public String getJobName() { return jobName; }
    public String getTaskType() { return taskType; }
    public Schedule getSchedule() { return schedule; }
    public Map<String, Object> getPayload() { return Map.copyOf(payload); }
    public ScheduledJobStatus getStatus() { return status; }
    public Instant getNextFireAt() { return nextFireAt; }
    public MisfireInstruction getMisfireInstruction() { return misfireInstruction; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
