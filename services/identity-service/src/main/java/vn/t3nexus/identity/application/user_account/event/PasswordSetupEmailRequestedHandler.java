package vn.t3nexus.identity.application.user_account.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user_account.PasswordSetupEmailRequested;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class PasswordSetupEmailRequestedHandler implements EventHandler<PasswordSetupEmailRequested> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(PasswordSetupEmailRequested event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<PasswordSetupEmailRequested> getEventType() {
        return PasswordSetupEmailRequested.class;
    }
}
