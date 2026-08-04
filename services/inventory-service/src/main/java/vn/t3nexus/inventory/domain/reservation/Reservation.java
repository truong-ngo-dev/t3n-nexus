package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Reservation extends AbstractAggregateRoot<ReservationId> implements AggregateRoot<ReservationId> {

    private final String orderId;
    private ReservationStatus status;
    private final List<ReservationItem> items;
    private final Instant createdAt;
    private Instant updatedAt;

    private Reservation(ReservationId id, String orderId, ReservationStatus status,
                        List<ReservationItem> items,
                        Instant createdAt, Instant updatedAt) {
        setId(id);
        this.orderId   = orderId;
        this.status    = status;
        this.items     = new ArrayList<>(items);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    /**
     * Success path (T1): creates PENDING reservation and raises InventoryReservedEvent.
     * <br>Không có TTL/expiresAt — release chỉ xảy ra qua {@link #release()} khi order-service
     * publish {@code OrderCancelled} (kể cả khi tự huỷ do đơn kẹt ở CREATED quá lâu — order-service
     * là chủ sở hữu quyết định đó, không phải Reservation tự dò hạn).
     */
    public static Reservation create(ReservationId id, String orderId, List<ReservationItem> items) {
        Instant now = Instant.now();
        Reservation reservation = new Reservation(id, orderId, ReservationStatus.PENDING, items, now, now);
        reservation.addDomainEvent(new InventoryReservedEvent(id.getValue(), orderId, items));
        return reservation;
    }

    /** Failure path (T2): creates CANCELLED reservation and raises InventoryReservationFailedEvent. */
    public static Reservation createFailed(ReservationId id, String orderId,
                                           List<ReservationItem> items,
                                           String failedSkuId, String reason) {
        Instant now = Instant.now();
        Reservation reservation = new Reservation(id, orderId, ReservationStatus.CANCELLED, items, now, now);
        reservation.addDomainEvent(
                new InventoryReservationFailedEvent(id.getValue(), orderId, failedSkuId, reason));
        return reservation;
    }

    /** Reconstitute from persistence — no events raised. */
    public static Reservation reconstitute(ReservationId id, String orderId, ReservationStatus status,
                                           List<ReservationItem> items,
                                           Instant createdAt, Instant updatedAt) {
        return new Reservation(id, orderId, status, items, createdAt, updatedAt);
    }

    // ───────────── Behaviour ─────────────

    /** Compensation for OrderCancelled — releases reserved stock back. */
    public void release() {
        if (status != ReservationStatus.PENDING) {
            throw new DomainException(ReservationErrorCode.RESERVATION_NOT_PENDING);
        }
        this.status    = ReservationStatus.RELEASED;
        this.updatedAt = Instant.now();
        addDomainEvent(new InventoryReleasedEvent(getId().getValue(), orderId, items));
    }

    // ───────────── Getters ─────────────

    public String getOrderId()               { return orderId; }
    public ReservationStatus getStatus()     { return status; }
    public List<ReservationItem> getItems()  { return List.copyOf(items); }
    public Instant getCreatedAt()            { return createdAt; }
    public Instant getUpdatedAt()            { return updatedAt; }

    public boolean isPending() { return status == ReservationStatus.PENDING; }
}
