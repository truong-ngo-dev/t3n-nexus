package vn.t3nexus.scheduler.application.scheduled_job.start;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.scheduler.domain.schedule.CronCalculator;
import vn.t3nexus.scheduler.domain.scheduled_job.DueItemFinder;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobException;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobRepository;

import java.time.Instant;

/**
 * Activate lịch trình — PENDING/COMPLETED → RUNNING. Khác {@code StopScheduledJob}: {@code job.start()}
 * KHÔNG idempotent, cố tình throw nếu đã RUNNING hoặc nếu schedule không còn tương lai nào để chạy
 * (request có chủ đích rõ ràng từ Admin — không nên âm thầm no-op khi vô nghĩa, xem service.md
 * §ScheduledJob domain methods). 2 exception này ({@code invalidTransition}/{@code alreadyDue})
 * <b>KHÔNG</b> bị catch ở đây — phải propagate ra REST layer thành lỗi thật (422), khác nhánh race
 * benign trong {@code FireScheduledJob}.
 *
 * <p>{@code findByIdForUpdate}, không phải {@code findById} — cùng lý do {@code StopScheduledJob}/
 * {@code EditScheduledJob}: serialize đúng nếu 2 request Start cùng job race nhau (2 admin double-click).
 * Không có race thật với {@code FireScheduledJob} — Fire chỉ chạm job đang RUNNING, Start chỉ chạm job
 * đang PENDING/COMPLETED, 2 tập rời nhau.
 *
 * <p>{@link DueItemFinder#index} — đây là điểm duy nhất job từ {@code PENDING} có {@code nextFireAt} và
 * được đưa vào index. Việc hoãn tới sau commit + nuốt lỗi Redis là guarantee của port (xem javadoc
 * {@link DueItemFinder}); Handler gọi như dòng lệnh thường trong flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartScheduledJob implements CommandHandler<StartScheduledJob.Command, Void> {

    private final ScheduledJobRepository scheduledJobRepository;
    private final CronCalculator cronCalculator;
    private final DueItemFinder dueItemFinder;

    @Override
    @Transactional
    public Void handle(Command command) {
        ScheduledJobId id = ScheduledJobId.of(command.scheduledJobId());
        ScheduledJob job = scheduledJobRepository.findByIdForUpdate(id)
                .orElseThrow(ScheduledJobException::notFound);

        job.start(Instant.now(), cronCalculator);
        scheduledJobRepository.save(job);
        dueItemFinder.index(id, job.getNextFireAt());   // hoãn-sau-commit + best-effort: guarantee của port

        log.info("[StartScheduledJob] started: scheduledJobId={}, nextFireAt={}, traceId={}",
                command.scheduledJobId(), job.getNextFireAt(), MDC.get("traceId"));

        return null;
    }

    public record Command(String scheduledJobId) {}
}
