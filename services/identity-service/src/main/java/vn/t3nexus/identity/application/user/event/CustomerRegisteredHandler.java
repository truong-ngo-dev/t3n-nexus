package vn.t3nexus.identity.application.user.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user.CustomerRegisteredEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.lib.utils.JsonUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerRegisteredHandler implements EventHandler<CustomerRegisteredEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(CustomerRegisteredEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<CustomerRegisteredEvent> getEventType() {
        return CustomerRegisteredEvent.class;
    }
}
