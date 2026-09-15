package vn.t3nexus.scheduler.infrastructure.persistence.scheduled_job_instance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduledJobInstanceJpaRepository extends JpaRepository<ScheduledJobInstanceJpaEntity, String> {
}
