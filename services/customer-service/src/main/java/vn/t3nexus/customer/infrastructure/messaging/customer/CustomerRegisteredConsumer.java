package vn.t3nexus.customer.infrastructure.messaging.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.customer.application.customer.CreateCustomerProfile;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerRegisteredConsumer {

    private final ObjectMapper          objectMapper;
    private final EventEnvelopeDecoder  decoder;
    private final CreateCustomerProfile createCustomerProfile;

    @KafkaListener(
            topics  = "${app.kafka.topic.customer-account-created}",
            groupId = "${app.kafka.consumer-group}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());
        try {
            CustomerAccountCreatedPayload payload = decoder.decode(event, CustomerAccountCreatedPayload.class);
            createCustomerProfile.handle(new CreateCustomerProfile.Command(payload.userId()));
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }
}
