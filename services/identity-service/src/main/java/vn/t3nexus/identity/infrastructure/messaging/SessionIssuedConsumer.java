package vn.t3nexus.identity.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.identity.application.login_session.record_login_session.RecordLoginSession;
import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionIssuedConsumer {

    private final ObjectMapper         objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final RecordLoginSession   recordLoginSession;

    @KafkaListener(
            topics  = "${app.kafka.topic.session-issued}",
            groupId = "${app.kafka.consumer-group}"
    )
    public void consume(String message) {
        OutboxEventData event    = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelope   envelope = event.payload();
        EventEnvelopeMdcPropagator.propagate(envelope);
        try {
            SessionIssuedPayload payload = decoder.decode(event, SessionIssuedPayload.class);
            recordLoginSession.handle(new RecordLoginSession.Command(
                    payload.oauthSessionId(),
                    payload.authorizationId(),
                    payload.idpSessionId(),
                    payload.userId(),
                    payload.loginIdentifier(),
                    payload.deviceHash(),
                    payload.userAgent(),
                    payload.acceptLanguage(),
                    payload.ipAddress(),
                    payload.provider()
            ));
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }
}
