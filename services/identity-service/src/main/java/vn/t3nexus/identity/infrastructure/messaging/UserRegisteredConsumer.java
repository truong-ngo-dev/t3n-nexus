package vn.t3nexus.identity.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.identity.application.user_account.create_user_account.CreateUserAccount;
import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final ObjectMapper           objectMapper;
    private final EventEnvelopeDecoder   decoder;
    private final CreateUserAccount createUserAccount;

    @KafkaListener(
            topics  = "${app.kafka.topic.user-registered}",
            groupId = "${app.kafka.consumer-group}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelope envelope = event.payload();
        EventEnvelopeMdcPropagator.propagate(envelope);
        try {
            UserRegisteredPayload payload = decoder.decode(event, UserRegisteredPayload.class);
            createUserAccount.handle(new CreateUserAccount.Command(
                    envelope.aggregateId(),
                    payload.email(),
                    payload.fullName(),
                    payload.role(),
                    payload.registrationMethod()
            ));
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }
}
