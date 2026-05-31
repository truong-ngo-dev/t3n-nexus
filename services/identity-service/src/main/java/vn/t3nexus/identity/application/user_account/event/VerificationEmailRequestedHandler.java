package vn.t3nexus.identity.application.user_account.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user_account.VerificationEmailRequested;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class VerificationEmailRequestedHandler implements EventHandler<VerificationEmailRequested> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(VerificationEmailRequested event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<VerificationEmailRequested> getEventType() {
        return VerificationEmailRequested.class;
    }
}
