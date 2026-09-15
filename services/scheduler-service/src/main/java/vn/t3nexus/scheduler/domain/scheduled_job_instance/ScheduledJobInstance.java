package vn.t3nexus.scheduler.domain.scheduled_job_instance;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Aggregate riêng — KHÔNG phải child entity của {@code ScheduledJob}. 1 lần fire (T_emit) tạo đúng 1
 * instance, ghi lại "đã dispatch" rồi chờ consumer báo lại (Kafka, xem service.md §Callback).
 * <br>Cố tình KHÔNG cùng aggregate với {@code ScheduledJob} — 2 lý do: (1) volume khác hẳn — 1 job lặp
 * lại tạo N instance theo thời gian, load chung sẽ phình aggregate cha vô ích mỗi lần chỉ cần đọc lịch;
 * (2) transaction khác nhau — tạo lúc {@code ScheduledJobFireService.fire()} (transaction Poll/
 * Trigger), còn {@code succeed()}/{@code fail()} ghi ở transaction hoàn toàn riêng, xảy ra sau đó bất
 * kỳ lúc nào (đợi consumer xử lý xong), không có lý do gì để 2 việc này khoá chung 1 row.
 * <br><b>Quan trọng</b>: outcome ở đây (SUCCEEDED/FAILED) KHÔNG bao giờ ảnh hưởng ngược lại
 * {@code ScheduledJob.status}/{@code nextFireAt} — đúng nguyên tắc "status không mang outcome"
 * (Knowledge base {@code 5. core-components.md}). Retry-khi-fail (nếu triển khai) là 1 cơ chế tách
 * biệt, đọc dữ liệu từ đây — KHÔNG fuse vào chu trình lên lịch, xem "Retry — vì sao cố tình không phải
 * bước thứ 6" cùng file — đang cố tình để ngỏ (seam), chưa quyết định triển khai.
 */
public class ScheduledJobInstance extends AbstractAggregateRoot<ScheduledJobInstanceId> {

    private ScheduledJobId scheduledJobId;
    private String taskType;
    private Map<String, Object> payload;
    private ScheduledJobInstanceStatus status;
    private Instant firedAt;
    private Instant completedAt;
    private String failureReason;

    private ScheduledJobInstance() {}

    /**
     * Tạo lúc {@code ScheduledJobFireService.fire()} publish signal (T_emit) — {@code taskType}/
     * {@code payload} là snapshot tại thời điểm fire, độc lập với {@code ScheduledJob} sau đó (job có
     * thể đổi payload cho lần fire kế tiếp, không ảnh hưởng instance đã tạo). Raise
     * {@link ScheduledJobFiredEvent} ngay tại đây — không phải {@code ScheduledJob.fire()} — vì nội
     * dung event hoàn toàn là field của chính aggregate này.
     * <br>Nhận {@code ULIDGenerator} (Double Dispatch), tự generate id bên trong — khác đa số aggregate
     * khác trong hệ (thường nhận {@code Id} đã pre-compute sẵn), xem ddd-structure.md §Double Dispatch
     * mục "Trường hợp ranh giới: sinh ID" cho lý do chọn ở đây.
     */
    public static ScheduledJobInstance dispatch(ULIDGenerator ulidGenerator, ScheduledJobId scheduledJobId,
                                                String taskType, Map<String, Object> payload, Instant firedAt) {
        ScheduledJobInstance instance = new ScheduledJobInstance();
        instance.setId(ScheduledJobInstanceId.of(ulidGenerator.generate()));
        instance.scheduledJobId = scheduledJobId;
        instance.taskType = taskType;
        instance.payload = Optional.ofNullable(payload).orElse(Map.of());
        instance.status = ScheduledJobInstanceStatus.DISPATCHED;
        instance.firedAt = firedAt;
        instance.addDomainEvent(new ScheduledJobFiredEvent(instance.getId().getValue(), scheduledJobId.getValue(), taskType, instance.payload));
        return instance;
    }

    /** Reconstitute từ persistence — dùng bởi repository. */
    public static ScheduledJobInstance reconstitute(ScheduledJobInstanceId id, ScheduledJobId scheduledJobId,
                                                     String taskType, Map<String, Object> payload,
                                                     ScheduledJobInstanceStatus status, Instant firedAt,
                                                     Instant completedAt, String failureReason) {
        ScheduledJobInstance instance = new ScheduledJobInstance();
        instance.setId(id);
        instance.scheduledJobId = scheduledJobId;
        instance.taskType = taskType;
        instance.payload = payload;
        instance.status = status;
        instance.firedAt = firedAt;
        instance.completedAt = completedAt;
        instance.failureReason = failureReason;
        return instance;
    }

    /**
     * Consumer callback báo thành công. Idempotent no-op nếu đã terminal — Kafka at-least-once có thể
     * redeliver cùng 1 completion event, không được ghi đè outcome đã chốt.
     */
    public void succeed(Instant completedAt) {
        if (isTerminal()) return;
        this.status = ScheduledJobInstanceStatus.SUCCEEDED;
        this.completedAt = completedAt;
    }

    /** Consumer callback báo lỗi — idempotent no-op nếu đã terminal, cùng lý do như {@link #succeed}. */
    public void fail(String reason, Instant completedAt) {
        if (isTerminal()) return;
        this.status = ScheduledJobInstanceStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = completedAt;
    }

    private boolean isTerminal() {
        return status == ScheduledJobInstanceStatus.SUCCEEDED || status == ScheduledJobInstanceStatus.FAILED;
    }

    public ScheduledJobId getScheduledJobId() { return scheduledJobId; }
    public String getTaskType() { return taskType; }
    public Map<String, Object> getPayload() { return Map.copyOf(payload); }
    public ScheduledJobInstanceStatus getStatus() { return status; }
    public Instant getFiredAt() { return firedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureReason() { return failureReason; }
}
