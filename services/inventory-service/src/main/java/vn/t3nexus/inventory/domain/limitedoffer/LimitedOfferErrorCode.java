package vn.t3nexus.inventory.domain.limitedoffer;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum LimitedOfferErrorCode implements ErrorCode {

    LIMITED_OFFER_NOT_FOUND   ("32001", "Limited offer not found",          "error.limited_offer.not_found",   404),
    LIMITED_OFFER_ALREADY_ACTIVE("32002","Limited offer already active",    "error.limited_offer.already_active", 409),
    LIMITED_OFFER_NOT_ACTIVE  ("32003", "Limited offer is not active",      "error.limited_offer.not_active",  422),
    SLOT_EXHAUSTED            ("32004", "Limited offer slots exhausted",     "error.limited_offer.exhausted",   422);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    LimitedOfferErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
