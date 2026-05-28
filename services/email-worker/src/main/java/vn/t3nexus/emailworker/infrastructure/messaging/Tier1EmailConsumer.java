package vn.t3nexus.emailworker.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vn.t3nexus.emailworker.application.email.EmailDispatchHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class Tier1EmailConsumer {

    private final EmailDispatchEventParser parser;
    private final EmailDispatchHandler     handler;

    @KafkaListener(
            topics           = "${app.kafka.topic.email-transactional}",
            groupId          = "${app.kafka.consumer-group.tier1}",
            containerFactory = "tier1KafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment ack) {
        handler.handle(parser.parse(message));
        ack.acknowledge();
    }
}
