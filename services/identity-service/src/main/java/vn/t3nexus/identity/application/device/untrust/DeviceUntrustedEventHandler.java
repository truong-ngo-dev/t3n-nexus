package vn.t3nexus.identity.application.device.untrust;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.device.DeviceUntrustedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class DeviceUntrustedEventHandler implements EventHandler<DeviceUntrustedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(DeviceUntrustedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<DeviceUntrustedEvent> getEventType() {
        return DeviceUntrustedEvent.class;
    }
}
