package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Reservation extends AbstractAggregateRoot<ReservationId> implements AggregateRoot<ReservationId> {

    private static final long TIMEOUT_MINUTES = 5;

    private final String orderId;
    private ReservationStatus status;
    private final List<ReservationItem> items;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Reservation(ReservationId id, String orderId, ReservationStatus status,
                        List<ReservationItem> items, Instant expiresAt,
                        Instant createdAt, Instant updatedAt) {
        setId(id);
        this.orderId   = orderId;
        this.status    = status;
        this.items     = new ArrayList<>(items);
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    /**
     * Success path (T1): creates PENDING reservation and raises InventoryReservedEvent.
     * <br>{@code expiresAt} — TTL tự guard, Lớp backstop cuối cùng, không phụ thuộc order-service phải
     * publish {@code OrderCancelled} đúng, đủ, đúng lúc. Đảo ngược quyết định cũ (V5 migration — từng
     * bỏ hẳn {@code expires_at} vì lúc đó chưa có state {@link ReservationStatus#COMMITTED}, TTL sweep
     * ngây thơ sẽ release nhầm cả đơn đã confirm). Nay thêm {@code commit()} trước nên an toàn để làm lại.
     * <br><b>5 phút (2026-09-23, hạ từ 10 phút)</b> — không cần dài hơn hẳn 3 phút deadline của
     * {@code Order}: chuỗi compensate bình thường (`OrderCancelled` → `ReleaseReservation`, kể cả
     * `republishCancellation`) chỉ mất vài giây bất kể Order tự huỷ sau 3 giây hay 3 phút — TTL chỉ là
     * backstop cho compounding-failure (hiếm), không phải để né race với luồng bình thường. TTL dài hơn
     * = giữ tồn kho oan lâu hơn cho khách khác trong lúc chờ (chi phí kinh doanh thật, đặc biệt SKU hot/
     * flash-sale) mà không giảm thêm rủi ro release-nhầm.
     */
    public static Reservation create(ReservationId id, String orderId, List<ReservationItem> items) {
        Instant now = Instant.now();
        Reservation reservation = new Reservation(id, orderId, ReservationStatus.PENDING, items,
                now.plus(TIMEOUT_MINUTES, java.time.temporal.ChronoUnit.MINUTES), now, now);
        reservation.addDomainEvent(new InventoryReservedEvent(id.getValue(), orderId, items));
        return reservation;
    }

    /** Failure path (T2): creates CANCELLED reservation and raises InventoryReservationFailedEvent. */
    public static Reservation createFailed(ReservationId id, String orderId,
                                           List<ReservationItem> items,
                                           String failedSkuId, String reason) {
        Instant now = Instant.now();
        Reservation reservation = new Reservation(id, orderId, ReservationStatus.CANCELLED, items, null, now, now);
        reservation.addDomainEvent(
                new InventoryReservationFailedEvent(id.getValue(), orderId, failedSkuId, reason));
        return reservation;
    }

    /** Reconstitute from persistence — no events raised. */
    public static Reservation reconstitute(ReservationId id, String orderId, ReservationStatus status,
                                           List<ReservationItem> items, Instant expiresAt,
                                           Instant createdAt, Instant updatedAt) {
        return new Reservation(id, orderId, status, items, expiresAt, createdAt, updatedAt);
    }

    // ───────────── Behaviour ─────────────

    /** Compensation for OrderCancelled (hoặc tự phát hiện quá hạn) — releases reserved stock back. */
    public void release() {
        if (status != ReservationStatus.PENDING) {
            throw new DomainException(ReservationErrorCode.RESERVATION_NOT_PENDING);
        }
        this.status    = ReservationStatus.RELEASED;
        this.updatedAt = Instant.now();
        addDomainEvent(new InventoryReleasedEvent(getId().getValue(), orderId, items));
    }

    /**
     * Nhận tín hiệu {@code OrderConfirmed} — chuyển sang trạng thái chung cuộc, dừng đồng hồ TTL vĩnh
     * viễn. Không raise event (không ảnh hưởng Stock, chỉ đánh dấu nội bộ để TTL sweep bỏ qua).
     */
    public void commit() {
        if (status != ReservationStatus.PENDING) {
            throw new DomainException(ReservationErrorCode.RESERVATION_NOT_PENDING);
        }
        this.status    = ReservationStatus.COMMITTED;
        this.updatedAt = Instant.now();
    }

    // ───────────── Getters ─────────────

    public String getOrderId()               { return orderId; }
    public ReservationStatus getStatus()     { return status; }
    public List<ReservationItem> getItems()  { return List.copyOf(items); }
    public Instant getExpiresAt()            { return expiresAt; }
    public Instant getCreatedAt()            { return createdAt; }
    public Instant getUpdatedAt()            { return updatedAt; }

    public boolean isPending() { return status == ReservationStatus.PENDING; }
}
