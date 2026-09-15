package vn.t3nexus.scheduler.application.scheduled_job.create;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.scheduler.application.scheduled_job.shared.ScheduleRequestMapper;
import vn.t3nexus.scheduler.domain.schedule.Schedule;
import vn.t3nexus.scheduler.domain.scheduled_job.MisfireInstruction;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Đăng ký lịch — chỉ ghi definition, KHÔNG activate. {@link ScheduledJob#create} không nhận
 * {@code CronCalculator}, không tính {@code nextFireAt} — job tạo xong ở {@code PENDING}, cần gọi
 * {@code StartScheduledJob} riêng để chạy (xem service.md §Domain Model). Cú pháp cron sai chỉ lộ ra ở
 * lần {@code start()} đầu tiên, không phải ở đây — {@code RecurringSchedule} cố tình không tự validate
 * (xem javadoc của nó).
 *
 * <p>Không cần {@code EventDispatcher} — {@code create()} không raise domain event nào (khác
 * {@code ScheduledJobInstance.dispatch()}), xem service.md §Domain Events: chỉ có đúng 1 event
 * ({@code ScheduledJobFiredEvent}), phát lúc fire, không phải lúc tạo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateScheduledJob implements CommandHandler<CreateScheduledJob.Command, CreateScheduledJob.Result> {

    private final ScheduledJobRepository scheduledJobRepository;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    public Result handle(Command command) {
        Schedule schedule = ScheduleRequestMapper.toSchedule(command.scheduleType(), command.dueAt(),
                command.cronExpression(), command.timezone());
        MisfireInstruction misfireInstruction = Optional.ofNullable(command.misfireInstruction())
                .map(MisfireInstruction::valueOf)
                .orElse(null);

        ScheduledJobId id = ScheduledJobId.of(ulidGenerator.generate());
        ScheduledJob job = ScheduledJob.create(id, command.jobName(), command.taskType(), schedule,
                command.payload(), misfireInstruction);
        scheduledJobRepository.save(job);
        // Không sync DueItemFinder ở đây: job PENDING chưa có nextFireAt, chưa vào index. Chỉ
        // StartScheduledJob mới index (xem DueItemFinder, data.md §Redis key design).

        log.info("[CreateScheduledJob] created: scheduledJobId={}, jobName={}, taskType={}, scheduleType={}, traceId={}",
                id.getValue(), command.jobName(), command.taskType(), command.scheduleType(), MDC.get("traceId"));

        return new Result(id.getValue());
    }

    public record Command(String jobName, String taskType, String scheduleType, Instant dueAt, String cronExpression,
                          String timezone, Map<String, Object> payload, String misfireInstruction) {}

    public record Result(String id) {}
}
