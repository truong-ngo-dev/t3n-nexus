package vn.t3nexus.emailworker.application.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.idempotency.IdempotencyGuard;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatchHandler {

    private final IdempotencyGuard idempotencyGuard;
    private final EmailSender emailSender;

    @Value("${app.idempotency.ttl-hours}")
    private long idempotencyTtlHours;

    public void handle(EmailDispatchEvent event) {
        String key = "email:" + event.id();

        if (!idempotencyGuard.tryAcquire(key, Duration.ofHours(idempotencyTtlHours))) {
            log.info("Duplicate email skipped: notificationLogId={}, eventId={}, userId={}", event.id(), event.eventId(), event.userId());
            return;
        }

        try {
            emailSender.send(event);
        } catch (Exception e) {
            idempotencyGuard.release(key);
            throw e;
        }
    }
}
