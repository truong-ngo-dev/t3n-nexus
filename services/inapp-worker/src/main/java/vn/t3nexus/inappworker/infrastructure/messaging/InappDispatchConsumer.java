package vn.t3nexus.inappworker.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vn.t3nexus.inappworker.application.inapp.InappDispatchHandler;

@Component
@RequiredArgsConstructor
public class InappDispatchConsumer {

    private final InappDispatchEventParser parser;
    private final InappDispatchHandler     handler;

    @KafkaListener(
            topics           = "${app.kafka.topic.inapp-dispatch}",
            groupId          = "${app.kafka.consumer-group.inapp}",
            containerFactory = "inappKafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment ack) {
        handler.handle(parser.parse(message));
        ack.acknowledge();
    }
}
