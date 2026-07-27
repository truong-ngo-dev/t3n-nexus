package vn.t3nexus.lib.eventsourcing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for {@link EventStoreEntity} entities.
 */
public interface EventStoreJpaRepository extends JpaRepository<EventStoreEntity, Long> {
    List<EventStoreEntity> findByAggregateIdOrderByRevisionAsc(String aggregateId);
}
