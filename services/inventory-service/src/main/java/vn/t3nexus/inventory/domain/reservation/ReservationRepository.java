package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface ReservationRepository extends Repository<Reservation, ReservationId> {

    Optional<Reservation> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);
}
