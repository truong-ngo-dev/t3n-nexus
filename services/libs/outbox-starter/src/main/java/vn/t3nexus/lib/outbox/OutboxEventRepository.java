package vn.t3nexus.lib.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for {@link OutboxEvent} entities.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
