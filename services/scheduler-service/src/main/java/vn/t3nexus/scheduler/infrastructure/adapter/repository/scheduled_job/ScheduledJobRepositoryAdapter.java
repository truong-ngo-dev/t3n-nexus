package vn.t3nexus.scheduler.infrastructure.adapter.repository.scheduled_job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.scheduler.domain.scheduled_job.DueJobRef;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJob;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobRepository;
import vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job.ScheduledJobJpaRepository;
import vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job.ScheduledJobMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ScheduledJobRepositoryAdapter implements ScheduledJobRepository {

    private final ScheduledJobJpaRepository jpaRepository;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional(readOnly = true)
    public Optional<ScheduledJob> findById(ScheduledJobId id) {
        return jpaRepository.findById(id.getValue()).map(ScheduledJobMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<ScheduledJob> findByIdForUpdate(ScheduledJobId id) {
        return jpaRepository.findByIdForUpdate(id.getValue()).map(ScheduledJobMapper::toDomain);
    }

    @Override
    @Transactional
    public void save(ScheduledJob job) {
        jpaRepository.save(ScheduledJobMapper.toJpaEntity(job));
        List<DomainEvent> pending = List.copyOf(job.getDomainEvents());
        eventDispatcher.dispatchAll(pending);
        job.clearDomainEvents();
    }

    @Override
    public void delete(ScheduledJobId id) {
        jpaRepository.deleteById(id.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DueJobRef> findDueBefore(Instant asOf, int limit) {
        return jpaRepository.findDueBefore(asOf, limit).stream()
                .map(r -> new DueJobRef(ScheduledJobId.of(r.getId()), r.getNextFireAt()))
                .toList();
    }
}
