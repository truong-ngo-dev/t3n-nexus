package vn.t3nexus.scheduler.infrastructure.adapter.query.scheduled_job;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobQueryFilter;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobQueryPort;
import vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job.ScheduledJobJpaRepository;
import vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job.ScheduledJobMapper;

import java.util.List;

/**
 * Impl {@link ScheduledJobQueryPort} — nằm ở {@code adapter/query/}, đối xứng với
 * {@code adapter/repository/} (nơi {@code ScheduledJobRepositoryAdapter} sống), đúng convention
 * `ddd-structure.md` §presentation/persistence cho read-side. Dùng chung {@link ScheduledJobJpaRepository}
 * với adapter kia — cùng bảng, cùng JPA entity, tách domain port chứ không tách hạ tầng persistence (chỉ
 * cần tách hẳn khi read DB khác loại write DB — không phải trường hợp ở đây, cùng Postgres).
 */
@Component
@RequiredArgsConstructor
public class ScheduledJobQueryAdapter implements ScheduledJobQueryPort {

    private final ScheduledJobJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ScheduledJob> search(ScheduledJobQueryFilter filter, int page, int size) {
        return jpaRepository.search(filter.jobName(), filter.status(), filter.taskType(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(ScheduledJobMapper::toDomain)
                .toList();
    }

    @Override
    public long count(ScheduledJobQueryFilter filter) {
        return jpaRepository.countSearch(filter.jobName(), filter.status(), filter.taskType());
    }
}
