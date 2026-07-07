package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class ReservationItemId extends AbstractId<String> implements Id<String> {

    private ReservationItemId(String value) {
        super(value);
    }

    public static ReservationItemId of(String id) {
        return new ReservationItemId(id);
    }
}
