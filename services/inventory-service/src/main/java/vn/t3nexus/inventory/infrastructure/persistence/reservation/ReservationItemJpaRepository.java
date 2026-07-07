package vn.t3nexus.inventory.infrastructure.persistence.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationItemJpaRepository extends JpaRepository<ReservationItemJpaEntity, String> {

    List<ReservationItemJpaEntity> findByReservationId(String reservationId);

    List<ReservationItemJpaEntity> findByReservationIdIn(List<String> reservationIds);
}
