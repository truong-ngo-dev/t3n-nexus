package vn.t3nexus.inventory.infrastructure.persistence.reservation;

import vn.t3nexus.inventory.domain.reservation.Reservation;
import vn.t3nexus.inventory.domain.reservation.ReservationId;
import vn.t3nexus.inventory.domain.reservation.ReservationItem;
import vn.t3nexus.inventory.domain.reservation.ReservationItemId;

import java.util.List;

public final class ReservationMapper {

    private ReservationMapper() {}

    public static Reservation toDomain(ReservationJpaEntity e, List<ReservationItemJpaEntity> itemEntities) {
        List<ReservationItem> items = itemEntities.stream()
                .map(ReservationMapper::toItemDomain)
                .toList();
        return Reservation.reconstitute(
                ReservationId.of(e.getId()),
                e.getOrderId(),
                e.getStatus(),
                items,
                e.getExpiresAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    public static ReservationJpaEntity toJpaEntity(Reservation reservation) {
        ReservationJpaEntity e = new ReservationJpaEntity();
        e.setId(reservation.getId().getValue());
        e.setOrderId(reservation.getOrderId());
        e.setStatus(reservation.getStatus());
        e.setExpiresAt(reservation.getExpiresAt());
        e.setCreatedAt(reservation.getCreatedAt());
        e.setUpdatedAt(reservation.getUpdatedAt());
        return e;
    }

    public static ReservationItemJpaEntity toItemJpaEntity(String reservationId, ReservationItem item) {
        ReservationItemJpaEntity e = new ReservationItemJpaEntity();
        e.setId(item.getId().getValue());
        e.setReservationId(reservationId);
        e.setSkuId(item.getSkuId());
        e.setQty(item.getQty());
        return e;
    }

    private static ReservationItem toItemDomain(ReservationItemJpaEntity e) {
        return ReservationItem.reconstitute(
                ReservationItemId.of(e.getId()),
                e.getSkuId(),
                e.getQty()
        );
    }
}
