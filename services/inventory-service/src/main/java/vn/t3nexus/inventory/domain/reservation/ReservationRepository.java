package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface ReservationRepository extends Repository<Reservation, ReservationId> {

    Optional<Reservation> findByOrderId(String orderId);

    /** Acquires pessimistic write lock (SELECT FOR UPDATE). Must be called within a transaction. */
    Optional<Reservation> findByOrderIdForUpdate(String orderId);

    boolean existsByOrderId(String orderId);
}
