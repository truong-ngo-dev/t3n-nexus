package vn.t3nexus.identity.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.identity.application.login_session.close_login_session.CloseLoginSession;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionExpiredBulkConsumer {

    private final ObjectMapper         objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final CloseLoginSession    closeLoginSession;

    @KafkaListener(
            topics  = "${app.kafka.topic.session-expired-bulk}",
            groupId = "${app.kafka.consumer-group}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());
        try {
            SessionExpiredBulkPayload payload = decoder.decode(event, SessionExpiredBulkPayload.class);
            if (payload.oauthSessionIds().isEmpty()) return;
            closeLoginSession.handle(new CloseLoginSession.Command(payload.oauthSessionIds()));
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }
}
