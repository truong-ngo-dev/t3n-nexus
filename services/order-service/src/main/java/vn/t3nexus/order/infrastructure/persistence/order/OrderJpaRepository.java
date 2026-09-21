package vn.t3nexus.order.infrastructure.persistence.order;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, String> {

    @Query("SELECT o.id FROM OrderJpaEntity o WHERE o.status = 'CREATED' AND o.inventoryReplyDeadline < :asOf")
    List<String> findCreatedWithExpiredDeadline(@Param("asOf") Instant asOf, Limit limit);
}
