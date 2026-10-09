package vn.t3nexus.oauth2.application.user_account.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.oauth2.domain.user_account.UserRegisteredEvent;

@Component
@RequiredArgsConstructor
public class UserRegisteredHandler implements EventHandler<UserRegisteredEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(UserRegisteredEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<UserRegisteredEvent> getEventType() {
        return UserRegisteredEvent.class;
    }
}
