package vn.t3nexus.scheduler.application.scheduled_job.fire;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.scheduler.domain.schedule.CronCalculator;
import vn.t3nexus.scheduler.domain.scheduled_job.DueItemFinder;
import vn.t3nexus.scheduler.domain.scheduled_job.MisfireInstruction;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobErrorCode;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobFireService;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobRepository;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobInstance;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobInstanceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Flow C+D (Claim/Re-verify/Trigger, Knowledge base {@code 5. core-components.md}) — gọi bởi
 * {@code SchedulerPoller}/{@code IndexPoller}, mỗi id 1 lần gọi, 1 transaction riêng (không batch, xem
 * {@code implementation.md} Phase 3 — 1 job optimistic-lock-fail/race không được rollback job khác).
 *
 * <p><b>Inject {@link CronCalculator} trực tiếp</b> — khác ghi chú cũ trong {@code implementation.md}
 * ("không cần tự inject, ScheduledJobFireService đã giữ sẵn"), sai vì nhánh {@code skipMisfire()} gọi
 * thẳng aggregate, KHÔNG qua {@link ScheduledJobFireService} (service đó chỉ bọc {@code fire()}) — Handler
 * vẫn cần {@link CronCalculator} của chính mình cho nhánh đó.
 *
 * <p>Sync {@link DueItemFinder}: recurring vừa fire hoặc {@code skipMisfire()} →
 * {@code index(id, nextFireAt mới)}; one-off vừa {@code COMPLETED} → {@code deindex(id)}. Việc hoãn tới
 * sau commit + nuốt lỗi Redis là guarantee của port (xem javadoc {@link DueItemFinder}) — Handler gọi
 * như dòng lệnh thường. Nhánh <i>lost race</i> (catch {@code INVALID_TRANSITION}) KHÔNG đụng index —
 * nguồn thắng đã tự sync; {@code pollDue()} cũng đã pop id khỏi ZSET trước đó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FireScheduledJob implements CommandHandler<FireScheduledJob.Command, Void> {

    private final ScheduledJobRepository scheduledJobRepository;
    private final ScheduledJobInstanceRepository scheduledJobInstanceRepository;
    private final ScheduledJobFireService scheduledJobFireService;
    private final CronCalculator cronCalculator;
    private final DueItemFinder dueItemFinder;

    @Value("${scheduler.misfire-threshold-seconds}")
    private long misfireThresholdSeconds;

    @Override
    @Transactional
    public Void handle(Command command) {
        ScheduledJobId id = ScheduledJobId.of(command.scheduledJobId());
        Optional<ScheduledJob> maybeJob = scheduledJobRepository.findByIdForUpdate(id);
        if (maybeJob.isEmpty()) {
            log.debug("[FireScheduledJob] not found, skip. scheduledJobId={}", command.scheduledJobId());
            return null;
        }

        ScheduledJob job = maybeJob.get();
        // guard theo status
        if (!job.canProcess()) {
            log.debug("[FireScheduledJob] not RUNNING, skip (đã bị xử lý ở nơi khác). scheduledJobId={}", command.scheduledJobId());
            return null;
        }

        Instant now = Instant.now();
        if (isMisfire(job, now)) {
            job.skipMisfire(now, cronCalculator);
            scheduledJobRepository.save(job);
            // skipMisfire chỉ chạy cho recurring (isMisfire == false với one-off) → luôn có nextFireAt mới.
            dueItemFinder.index(id, job.getNextFireAt());   // hoãn-sau-commit + best-effort: guarantee của port
            log.info("[FireScheduledJob] misfire skipped: scheduledJobId={}, newNextFireAt={}, traceId={}", command.scheduledJobId(), job.getNextFireAt(), MDC.get("traceId"));
            return null;
        }

        try {
            ScheduledJobInstance instance = scheduledJobFireService.fire(job, now);
            // save() của cả 2 repository tự dispatch domain event bên trong (xem
            // ScheduledJob{,Instance}RepositoryAdapter) — không cần tự gọi EventDispatcher ở đây.
            scheduledJobRepository.save(job);
            scheduledJobInstanceRepository.save(instance);
            // recurring vừa fire → nextFireAt = lần kế tiếp, index lại; one-off → COMPLETED, nextFireAt
            // null, rời index. Port lo phần hoãn-sau-commit + best-effort.
            if (job.getNextFireAt() != null) {
                dueItemFinder.index(id, job.getNextFireAt());
            } else {
                dueItemFinder.deindex(id);
            }
            log.info("[FireScheduledJob] fired: scheduledJobId={}, instanceId={}, traceId={}",
                    command.scheduledJobId(), instance.getId().getValue(), MDC.get("traceId"));
        } catch (DomainException e) {
            if (e.getErrorCode() != ScheduledJobErrorCode.SCHEDULED_JOB_INVALID_TRANSITION) throw e;
            // Race re-verify trong ScheduledJob.fire() phát hiện job đã bị nguồn khác fire mất giữa lúc
            // candidate được tìm thấy và lúc handle() này chạy tới (2 instance HA, hoặc IndexReconciler
            // nạp lại đúng lúc IndexPoller đang xử lý — xem design.md §Failure Scenarios). Benign, không
            // phải lỗi thật — không throw ra ngoài, không rollback.
            log.error("[FireScheduledJob] lost race, skip. scheduledJobId={}, reason={}",
                    command.scheduledJobId(), e.getMessage());
        }

        return null;
    }

    /**
     * {@code misfireInstruction} chỉ có hiệu lực với {@code RecurringSchedule} — one-off overdue PHẢI
     * luôn hành xử như FIRE_NOW bất kể giá trị này (xem {@link MisfireInstruction} javadoc). Guard
     * {@code !isRecurring()} bắt buộc đứng trước: thiếu nó thì one-off + {@code DO_NOTHING} + trễ quá
     * ngưỡng sẽ rơi vào {@code skipMisfire()} → {@code schedule.nextFireTime()} rỗng (one-off đã quá hạn)
     * → COMPLETED mà KHÔNG hề fire, KHÔNG publish event — sai với "luôn hành xử như FIRE_NOW" (COMPLETED
     * giống nhau bề ngoài, nhưng 1 đằng có fire thật, 1 đằng thì không).
     */
    private boolean isMisfire(ScheduledJob job, Instant now) {
        if (!job.getSchedule().isRecurring()) return false;
        return job.getMisfireInstruction() == MisfireInstruction.DO_NOTHING
               && Duration.between(job.getNextFireAt(), now).compareTo(Duration.ofSeconds(misfireThresholdSeconds)) > 0;
    }

    public record Command(String scheduledJobId) {}
}
