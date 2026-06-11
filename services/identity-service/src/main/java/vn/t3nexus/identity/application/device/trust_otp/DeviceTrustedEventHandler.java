package vn.t3nexus.identity.application.device.trust_otp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.device.DeviceTrustedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class DeviceTrustedEventHandler implements EventHandler<DeviceTrustedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(DeviceTrustedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<DeviceTrustedEvent> getEventType() {
        return DeviceTrustedEvent.class;
    }
}
