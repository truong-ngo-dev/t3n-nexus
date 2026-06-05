package vn.t3nexus.oauth2.application.user_credential.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.oauth2.domain.session.SessionIssuedEvent;

@Component
@RequiredArgsConstructor
public class SessionIssuedHandler implements EventHandler<SessionIssuedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(SessionIssuedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<SessionIssuedEvent> getEventType() {
        return SessionIssuedEvent.class;
    }
}
