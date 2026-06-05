package vn.t3nexus.oauth2.application.session.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.oauth2.domain.session.SessionRevokedEvent;

@Component
@RequiredArgsConstructor
public class SessionRevokedHandler implements EventHandler<SessionRevokedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(SessionRevokedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<SessionRevokedEvent> getEventType() {
        return SessionRevokedEvent.class;
    }
}
