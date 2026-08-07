package vn.t3nexus.oauth2.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;
import vn.t3nexus.oauth2.application.user_credential.activate_user_credential.ActivateUserCredential;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivatedConsumer {

    private final ObjectMapper           objectMapper;
    private final EventEnvelopeDecoder   decoder;
    private final ActivateUserCredential activateUserCredential;

    @KafkaListener(
            topics  = "${app.kafka.topic.user-activated}",
            groupId = "${app.kafka.consumer-group}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());
        try {
            UserActivatedPayload payload = decoder.decode(event, UserActivatedPayload.class);
            activateUserCredential.handle(new ActivateUserCredential.Command(payload.userId()));
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }
}
