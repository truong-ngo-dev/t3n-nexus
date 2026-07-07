package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum ReservationErrorCode implements ErrorCode {

    RESERVATION_NOT_FOUND        ("31001", "Reservation not found",             "error.reservation.not_found",        404),
    RESERVATION_ALREADY_EXISTS   ("31002", "Reservation already exists",        "error.reservation.already_exists",   409),
    RESERVATION_NOT_PENDING      ("31003", "Reservation is not in PENDING state","error.reservation.not_pending",     422);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    ReservationErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
        this.code           = code;
        this.defaultMessage = defaultMessage;
        this.messageKey     = messageKey;
        this.httpStatus     = httpStatus;
    }

    @Override public String code()           { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public String messageKey()     { return messageKey; }
    @Override public int httpStatus()        { return httpStatus; }
}
