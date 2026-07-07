package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class InventoryReservationFailedEvent extends AbstractDomainEvent {

    private final String orderId;
    private final String failedSkuId;
    private final String reason;

    public InventoryReservationFailedEvent(String reservationId, String orderId,
                                           String failedSkuId, String reason) {
        super(UUID.randomUUID().toString(), Instant.now(), reservationId, "Reservation");
        this.orderId     = orderId;
        this.failedSkuId = failedSkuId;
        this.reason      = reason;
    }

    @Override
    public String getRoutingKey() { return "inventory.reservation.failed"; }

    @Override
    public Object getPayload() {
        return new Payload(orderId, failedSkuId, reason);
    }

    public String getOrderId()     { return orderId; }
    public String getFailedSkuId() { return failedSkuId; }
    public String getReason()      { return reason; }

    public record Payload(String orderId, String failedSkuId, String reason) {}
}
