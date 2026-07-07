package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public final class ReservationException {

    private ReservationException() {}

    public static DomainException notFound() {
        return new DomainException(ReservationErrorCode.RESERVATION_NOT_FOUND);
    }

    public static DomainException alreadyExists() {
        return new DomainException(ReservationErrorCode.RESERVATION_ALREADY_EXISTS);
    }

    public static DomainException notPending() {
        return new DomainException(ReservationErrorCode.RESERVATION_NOT_PENDING);
    }
}
