package vn.t3nexus.inappworker.application.inapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.idempotency.IdempotencyGuard;

import java.time.Duration;

/**
 * Idempotency: cùng pattern {@code email-worker.EmailDispatchHandler} — key {@code inapp:{id}}, TTL
 * khớp cửa sổ redelivery tối đa của Kafka (72h). Publish Redis là best-effort/at-most-once theo bản
 * chất Pub/Sub (không ai subscribe thì mất) — guard này chỉ chặn publish trùng khi Kafka redeliver,
 * không đảm bảo message tới nơi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InappDispatchHandler {

    private final IdempotencyGuard idempotencyGuard;
    private final InappPublisher   inappPublisher;

    @Value("${app.idempotency.ttl-hours}")
    private long idempotencyTtlHours;

    public void handle(InappDispatchEvent event) {
        String key = "inapp:" + event.id();

        if (!idempotencyGuard.tryAcquire(key, Duration.ofHours(idempotencyTtlHours))) {
            log.info("Duplicate inapp dispatch skipped: notificationLogId={}, eventId={}, userId={}", event.id(), event.eventId(), event.userId());
            return;
        }

        try {
            inappPublisher.publish(event.userId(), event.payload());
        } catch (Exception e) {
            idempotencyGuard.release(key);
            throw e;
        }
    }
}
