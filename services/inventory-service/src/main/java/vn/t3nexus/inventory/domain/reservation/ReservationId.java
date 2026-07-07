package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class ReservationId extends AbstractId<String> implements Id<String> {

    private ReservationId(String value) {
        super(value);
    }

    public static ReservationId of(String id) {
        return new ReservationId(id);
    }
}
