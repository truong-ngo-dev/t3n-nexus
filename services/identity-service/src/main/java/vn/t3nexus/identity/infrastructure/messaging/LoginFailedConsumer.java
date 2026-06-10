package vn.t3nexus.identity.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.identity.application.login_activity.record_login_failure.RecordLoginFailure;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginFailedConsumer {

    private final ObjectMapper         objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final RecordLoginFailure   recordLoginFailure;

    @KafkaListener(
            topics  = "${app.kafka.topic.login-failed}",
            groupId = "${app.kafka.consumer-group}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());
        try {
            LoginFailedPayload payload = decoder.decode(event, LoginFailedPayload.class);
            recordLoginFailure.handle(new RecordLoginFailure.Command(
                    payload.userId(),
                    payload.loginIdentifier(),
                    payload.result(),
                    payload.deviceHash(),
                    payload.acceptLanguage(),
                    payload.ipAddress(),
                    payload.userAgent(),
                    payload.provider()
            ));
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }
}
