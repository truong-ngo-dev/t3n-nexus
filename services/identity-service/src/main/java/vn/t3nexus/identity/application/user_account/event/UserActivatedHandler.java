package vn.t3nexus.identity.application.user_account.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user_account.UserActivatedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class UserActivatedHandler implements EventHandler<UserActivatedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(UserActivatedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<UserActivatedEvent> getEventType() {
        return UserActivatedEvent.class;
    }
}
