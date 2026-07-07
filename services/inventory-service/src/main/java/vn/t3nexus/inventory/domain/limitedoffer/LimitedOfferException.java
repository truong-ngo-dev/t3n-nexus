package vn.t3nexus.inventory.domain.limitedoffer;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public final class LimitedOfferException {

    private LimitedOfferException() {}

    public static DomainException notFound() {
        return new DomainException(LimitedOfferErrorCode.LIMITED_OFFER_NOT_FOUND);
    }

    public static DomainException alreadyActive() {
        return new DomainException(LimitedOfferErrorCode.LIMITED_OFFER_ALREADY_ACTIVE);
    }

    public static DomainException notActive() {
        return new DomainException(LimitedOfferErrorCode.LIMITED_OFFER_NOT_ACTIVE);
    }

    public static DomainException slotExhausted() {
        return new DomainException(LimitedOfferErrorCode.SLOT_EXHAUSTED);
    }
}
