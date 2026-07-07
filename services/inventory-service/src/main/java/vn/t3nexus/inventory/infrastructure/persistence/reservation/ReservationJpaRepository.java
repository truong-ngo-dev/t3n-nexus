package vn.t3nexus.inventory.infrastructure.persistence.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, String> {

    Optional<ReservationJpaEntity> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);
}
