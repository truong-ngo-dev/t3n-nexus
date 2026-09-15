package vn.t3nexus.scheduler.application.scheduled_job.edit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.scheduler.application.scheduled_job.shared.ScheduleRequestMapper;
import vn.t3nexus.scheduler.domain.schedule.Schedule;
import vn.t3nexus.scheduler.domain.scheduled_job.DueItemFinder;
import vn.t3nexus.scheduler.domain.scheduled_job.MisfireInstruction;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobException;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Sửa schedule/payload/misfireInstruction — chỉ hợp lệ khi {@code status != RUNNING}
 * ({@code ScheduledJob.edit()} tự throw {@code invalidTransition} nếu đang RUNNING, xem service.md
 * §Domain Model). Luôn đưa job về PENDING + {@code nextFireAt = null}, kể cả sửa job đã COMPLETED (cho
 * phép "chạy lại 1 lần" cho one-off) — không tự activate, vẫn cần {@code StartScheduledJob} riêng.
 * {@code taskType} không sửa được — không có trong Command. {@code jobName} thì sửa được (nhãn hiển
 * thị, không mang ý nghĩa định tuyến) — có trong Command, xem javadoc {@code ScheduledJob.edit()}.
 *
 * <p><b>{@code findByIdForUpdate}</b>, không phải {@code findById} — cùng lý do {@code StopScheduledJob}:
 * serialize đúng với {@code FireScheduledJob} nếu Admin sửa job đúng lúc Poller đang fire.
 *
 * <p>{@link DueItemFinder#deindex} — {@code edit()} luôn đưa job về PENDING, {@code nextFireAt = null},
 * nên rời index giống {@code stop()}. Muốn chạy lại phải {@code StartScheduledJob} (chính nó sẽ
 * {@code index} lại). Hoãn-sau-commit + best-effort do port lo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EditScheduledJob implements CommandHandler<EditScheduledJob.Command, Void> {

    private final ScheduledJobRepository scheduledJobRepository;
    private final DueItemFinder dueItemFinder;

    @Override
    @Transactional
    public Void handle(Command command) {
        ScheduledJobId id = ScheduledJobId.of(command.scheduledJobId());
        ScheduledJob job = scheduledJobRepository.findByIdForUpdate(id)
                .orElseThrow(ScheduledJobException::notFound);

        Schedule schedule = ScheduleRequestMapper.toSchedule(command.scheduleType(), command.dueAt(),
                command.cronExpression(), command.timezone());
        MisfireInstruction misfireInstruction = Optional.ofNullable(command.misfireInstruction())
                .map(MisfireInstruction::valueOf)
                .orElse(null);

        job.edit(command.jobName(), schedule, command.payload(), misfireInstruction);
        scheduledJobRepository.save(job);
        dueItemFinder.deindex(id);   // hoãn-sau-commit + best-effort: guarantee của port

        log.info("[EditScheduledJob] edited: scheduledJobId={}, jobName={}, scheduleType={}, traceId={}",
                command.scheduledJobId(), command.jobName(), command.scheduleType(), MDC.get("traceId"));

        return null;
    }

    public record Command(String scheduledJobId, String jobName, String scheduleType, Instant dueAt,
                          String cronExpression, String timezone, Map<String, Object> payload,
                          String misfireInstruction) {}
}
