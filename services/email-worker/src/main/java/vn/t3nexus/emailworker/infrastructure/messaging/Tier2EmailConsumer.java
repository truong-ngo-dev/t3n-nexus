package vn.t3nexus.emailworker.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vn.t3nexus.emailworker.application.email.EmailDispatchHandler;
import vn.t3nexus.lib.ratelimiter.RateLimiter;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class Tier2EmailConsumer {

    private final EmailDispatchEventParser parser;
    private final EmailDispatchHandler     handler;
    private final RateLimiter              rateLimiter;

    @Value("${app.rate-limiter.email.bulk.rate-per-second}")
    private int bulkRatePerSecond;

    @KafkaListener(
            topics           = "${app.kafka.topic.email-bulk}",
            groupId          = "${app.kafka.consumer-group.tier2}",
            containerFactory = "tier2KafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment ack) {
        while (!rateLimiter.tryAcquire("email:bulk", bulkRatePerSecond, Duration.ofSeconds(1))) {
            LockSupport.parkNanos(Duration.ofMillis(50).toNanos());
        }
        handler.handle(parser.parse(message));
        ack.acknowledge();
    }
}
