package vn.t3nexus.inventory.infrastructure.persistence.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, String> {

    Optional<ReservationJpaEntity> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReservationJpaEntity r WHERE r.orderId = :orderId")
    Optional<ReservationJpaEntity> findByOrderIdForUpdate(@Param("orderId") String orderId);

    boolean existsByOrderId(String orderId);

    @Query("SELECT r.orderId FROM ReservationJpaEntity r WHERE r.status = 'PENDING' AND r.expiresAt < :asOf")
    List<String> findOrderIdsPendingWithExpiredDeadline(@Param("asOf") Instant asOf, Limit limit);
}
