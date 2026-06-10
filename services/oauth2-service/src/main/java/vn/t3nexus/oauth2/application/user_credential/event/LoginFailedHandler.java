package vn.t3nexus.oauth2.application.user_credential.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.oauth2.domain.user_credential.LoginFailedEvent;

@Component
@RequiredArgsConstructor
public class LoginFailedHandler implements EventHandler<LoginFailedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(LoginFailedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<LoginFailedEvent> getEventType() {
        return LoginFailedEvent.class;
    }
}
