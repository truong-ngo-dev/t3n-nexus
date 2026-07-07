package vn.t3nexus.inventory.domain.reservation;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class InventoryReservedEvent extends AbstractDomainEvent {

    private final String orderId;
    private final List<ItemEntry> items;

    public InventoryReservedEvent(String reservationId, String orderId, List<ReservationItem> items) {
        super(UUID.randomUUID().toString(), Instant.now(), reservationId, "Reservation");
        this.orderId = orderId;
        this.items   = items.stream()
                .map(i -> new ItemEntry(i.getSkuId(), i.getQty()))
                .toList();
    }

    @Override
    public String getRoutingKey() { return "inventory.reservation.created"; }

    @Override
    public Object getPayload() {
        return new Payload(getAggregateId(), orderId, items);
    }

    public String getOrderId()        { return orderId; }
    public List<ItemEntry> getItems() { return items; }

    public record ItemEntry(String skuId, int qty) {}

    public record Payload(String reservationId, String orderId, List<ItemEntry> items) {}
}
