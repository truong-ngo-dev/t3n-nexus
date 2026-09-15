package vn.t3nexus.scheduler.infrastructure.adapter.repository.scheduled_job_instance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobInstance;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobInstanceId;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobInstanceRepository;
import vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job_instance.ScheduledJobInstanceJpaRepository;
import vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job_instance.ScheduledJobInstanceMapper;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ScheduledJobInstanceRepositoryAdapter implements ScheduledJobInstanceRepository {

    private final ScheduledJobInstanceJpaRepository jpaRepository;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional(readOnly = true)
    public Optional<ScheduledJobInstance> findById(ScheduledJobInstanceId id) {
        return jpaRepository.findById(id.getValue()).map(ScheduledJobInstanceMapper::toDomain);
    }

    @Override
    @Transactional
    public void save(ScheduledJobInstance instance) {
//        jpaRepository.save(ScheduledJobInstanceMapper.toJpaEntity(instance));
        List<DomainEvent> pending = List.copyOf(instance.getDomainEvents());
        eventDispatcher.dispatchAll(pending);
        instance.clearDomainEvents();
    }

    @Override
    public void delete(ScheduledJobInstanceId id) {
        jpaRepository.deleteById(id.getValue());
    }
}
