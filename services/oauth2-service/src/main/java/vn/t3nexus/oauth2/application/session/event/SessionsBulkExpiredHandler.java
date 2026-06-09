package vn.t3nexus.oauth2.application.session.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.oauth2.domain.session.SessionsBulkExpiredEvent;

@Component
@RequiredArgsConstructor
public class SessionsBulkExpiredHandler implements EventHandler<SessionsBulkExpiredEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(SessionsBulkExpiredEvent event) {
        if (event.getOauthSessionIds().isEmpty()) return;
        outboxEventStore.store(event);
    }

    @Override
    public Class<SessionsBulkExpiredEvent> getEventType() {
        return SessionsBulkExpiredEvent.class;
    }
}
