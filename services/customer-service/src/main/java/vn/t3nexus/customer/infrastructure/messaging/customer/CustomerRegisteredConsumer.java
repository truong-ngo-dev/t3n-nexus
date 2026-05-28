package vn.t3nexus.customer.infrastructure.messaging.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.customer.application.customer.CreateCustomerProfile;
import vn.t3nexus.customer.domain.customer.CustomerProfileRepository;
import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;
import vn.t3nexus.lib.idempotency.IdempotencyGuard;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerRegisteredConsumer {

    private final ObjectMapper                  objectMapper;
    private final IdempotencyGuard              idempotencyGuard;
    private final CustomerProfileRepository     customerProfileRepository;
    private final CreateCustomerProfile.Handler createCustomerProfile;

    @Value("${app.idempotency.ttl-hours}")
    private long idempotencyTtlHours;

    @KafkaListener(
            topics  = "${app.kafka.topic.customer-registered}",
            groupId = "${app.kafka.consumer-group}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelope envelope = event.payload();
        EventEnvelopeMdcPropagator.propagate(envelope);
        try {
            String key = "customer-profile:" + envelope.eventId();
            if (!idempotencyGuard.tryAcquire(key, Duration.ofHours(idempotencyTtlHours))) {
                log.info("[CustomerRegisteredConsumer] duplicate event, skip. userId={}, eventId={}",
                        envelope.aggregateId(), envelope.eventId());
                return;
            }
            try {
                if (customerProfileRepository.findByUserId(envelope.aggregateId()).isPresent()) {
                    log.info("[CustomerRegisteredConsumer] profile already exists, skip. userId={}, eventId={}",
                            envelope.aggregateId(), envelope.eventId());
                    return;
                }
                createCustomerProfile.handle(new CreateCustomerProfile.Command(envelope.aggregateId()));
            } catch (Exception e) {
                idempotencyGuard.release(key);
                throw e;
            }
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }
}
