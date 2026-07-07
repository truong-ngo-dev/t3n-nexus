package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.model.AbstractEntity;
import vn.t3nexus.lib.common.domain.model.Entity;

public class ReservationItem extends AbstractEntity<ReservationItemId> implements Entity<ReservationItemId> {

    private final String skuId;
    private final int qty;

    private ReservationItem(ReservationItemId id, String skuId, int qty) {
        setId(id);
        this.skuId = skuId;
        this.qty   = qty;
    }

    public static ReservationItem create(ReservationItemId id, String skuId, int qty) {
        return new ReservationItem(id, skuId, qty);
    }

    public static ReservationItem reconstitute(ReservationItemId id, String skuId, int qty) {
        return new ReservationItem(id, skuId, qty);
    }

    public String getSkuId() { return skuId; }
    public int getQty()      { return qty; }
}
