package vn.t3nexus.scheduler.application.scheduled_job.stop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.scheduler.domain.scheduled_job.DueItemFinder;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobException;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobRepository;

/**
 * Tạm dừng — RUNNING → PENDING, KHÔNG phải hủy vĩnh viễn (khớp pattern K8s CronJob {@code suspend}, xem
 * service.md §Domain Model). {@code job.stop()} tự idempotent (no-op nếu đã PENDING/COMPLETED) nên
 * Handler không cần tự check trạng thái trước.
 *
 * <p><b>{@code findByIdForUpdate}</b>, không phải {@code findById} — Stop phải serialize đúng với
 * {@code FireScheduledJob} đang chạm cùng row (Admin bấm Stop đúng lúc Poller đang fire job đó). Row lock
 * đảm bảo 1 trong 2 transaction đợi bên kia commit trước rồi mới đọc state thật, tự re-verify/no-op
 * đúng — không cần 2 nguồn "biết nhau".
 *
 * <p>{@link DueItemFinder#deindex} — job về PENDING, {@code nextFireAt = null}, phải rời index. Gọi vô
 * điều kiện kể cả khi {@code stop()} là no-op (đã PENDING/COMPLETED): {@code deindex} idempotent, xoá id
 * không tồn tại không phải lỗi. Hoãn-sau-commit + best-effort do port lo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StopScheduledJob implements CommandHandler<StopScheduledJob.Command, Void> {

    private final ScheduledJobRepository scheduledJobRepository;
    private final DueItemFinder dueItemFinder;

    @Override
    @Transactional
    public Void handle(Command command) {
        ScheduledJobId id = ScheduledJobId.of(command.scheduledJobId());
        ScheduledJob job = scheduledJobRepository.findByIdForUpdate(id)
                .orElseThrow(ScheduledJobException::notFound);

        job.stop();
        scheduledJobRepository.save(job);
        dueItemFinder.deindex(id);   // hoãn-sau-commit + best-effort: guarantee của port

        log.info("[StopScheduledJob] stopped: scheduledJobId={}, status={}, traceId={}",
                command.scheduledJobId(), job.getStatus(), MDC.get("traceId"));

        return null;
    }

    public record Command(String scheduledJobId) {}
}
