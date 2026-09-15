package vn.t3nexus.scheduler.application.scheduled_job.get;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.scheduler.application.scheduled_job.shared.ScheduledJobView;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobException;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobRepository;

/**
 * Detail 1 job cho Admin UI — {@code findById} thuần (không lock). KHÔNG đi qua
 * {@code findByIdForUpdate}: đây là read cho hiển thị, không dẫn tới mutation nào, không cần serialize
 * với Fire/Start/Stop/Edit đang chạm cùng row.
 */
@Service
@RequiredArgsConstructor
public class GetScheduledJob implements QueryHandler<GetScheduledJob.Query, ScheduledJobView> {

    private final ScheduledJobRepository scheduledJobRepository;

    @Override
    public ScheduledJobView handle(Query query) {
        ScheduledJob job = scheduledJobRepository.findById(ScheduledJobId.of(query.scheduledJobId()))
                .orElseThrow(ScheduledJobException::notFound);
        return ScheduledJobView.from(job);
    }

    public record Query(String scheduledJobId) {}
}
