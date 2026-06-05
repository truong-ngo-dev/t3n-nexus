package vn.t3nexus.identity.domain.device;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class DeviceTrustedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String userId;

    public DeviceTrustedEvent(String deviceId, String userId) {
        super(UUID.randomUUID().toString(), Instant.now(), deviceId, "Device");
        this.userId = userId;
    }

    public String getUserId() { return userId; }

    @Override
    public String getRoutingKey() {
        return "identity.device.trusted";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "deviceId", getAggregateId(),
                "userId",   userId
        );
    }
}
