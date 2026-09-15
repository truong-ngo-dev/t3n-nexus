package vn.t3nexus.scheduler.application.scheduled_job.list;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.scheduler.application.scheduled_job.shared.ScheduledJobView;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobQueryFilter;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobQueryPort;

import java.util.List;

/**
 * List phân trang + filter cho Admin UI, sort {@code createdAt} giảm dần (mới nhất trước) — đi qua
 * {@link ScheduledJobQueryPort} (KHÔNG phải {@code ScheduledJobRepository}, tách port 2026-09-15: phân
 * trang/lọc là read-model concern, không phải aggregate life-cycle).
 *
 * <p>{@code jobName} (search substring, dành cho Admin — nhãn tự đặt), {@code status}/{@code taskType}
 * (exact match, kỹ thuật) — 3 filter đủ dùng ở cardinality hiện tại (4 job cố định). Xem javadoc
 * {@code ScheduledJobQueryFilter} cho lý do 2 kiểu lọc khác nhau.
 */
@Service
@RequiredArgsConstructor
public class ListScheduledJobs implements QueryHandler<ListScheduledJobs.Query, ListScheduledJobs.Result> {

    private final ScheduledJobQueryPort scheduledJobQueryPort;

    @Override
    public Result handle(Query query) {
        ScheduledJobQueryFilter filter = new ScheduledJobQueryFilter(
                query.jobName(), query.status(), query.taskType());
        List<ScheduledJob> jobs = scheduledJobQueryPort.search(filter, query.page(), query.size());
        long total = scheduledJobQueryPort.count(filter);
        List<ScheduledJobView> items = jobs.stream().map(ScheduledJobView::from).toList();
        return new Result(items, total, query.page(), query.size());
    }

    public record Query(int page, int size, String jobName, String status, String taskType) {}

    public record Result(List<ScheduledJobView> items, long total, int page, int size) {}
}
