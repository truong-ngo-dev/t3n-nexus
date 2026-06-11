package vn.t3nexus.identity.application.device.trust_otp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.device.DeviceOtpRequested;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class DeviceOtpRequestedHandler implements EventHandler<DeviceOtpRequested> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(DeviceOtpRequested event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<DeviceOtpRequested> getEventType() {
        return DeviceOtpRequested.class;
    }
}
