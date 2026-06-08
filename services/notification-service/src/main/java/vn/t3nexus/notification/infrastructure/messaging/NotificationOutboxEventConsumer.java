package vn.t3nexus.notification.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;
import vn.t3nexus.notification.application.notification.NotificationDispatchService;
import vn.t3nexus.notification.application.notification.NotificationTrigger;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxEventConsumer {

    private final ObjectMapper                objectMapper;
    private final NotificationDispatchService dispatchService;

    @KafkaListener(
            topics  = {"${app.kafka.topic.password-setup-requested}",
                       "${app.kafka.topic.login-otp-requested}",
                       "${app.kafka.topic.email-verification-requested}",
                       "${app.kafka.topic.email-verification-reissued}",
                       "${app.kafka.topic.email-verification-verified}"},
            groupId = "${app.kafka.consumer-group}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelope envelope = event.payload();
        EventEnvelopeMdcPropagator.propagate(envelope);
        try {
            dispatchService.dispatch(NotificationTrigger.from(envelope));
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }
}
