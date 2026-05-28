package vn.t3nexus.notification.application.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.notification.domain.notification.NotificationInboxRepository;
import vn.t3nexus.notification.domain.notification.NotificationLogRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationHandlerRegistry registry;
    private final NotificationLogRepository   logRepository;
    private final NotificationInboxRepository inboxRepository;
    private final ObjectMapper                objectMapper;

    @Transactional
    public void dispatch(NotificationTrigger trigger) {
        NotificationEventHandler<?> handler = registry.get(trigger.eventType());
        if (handler == null) {
            log.debug("[Notification] no handler for eventType={}, skipping", trigger.eventType());
            return;
        }
        dispatch(trigger, handler);
    }

    private <E> void dispatch(NotificationTrigger trigger, NotificationEventHandler<E> handler) {
        E payload = objectMapper.convertValue(trigger.payload(), handler.payloadType());
        NotificationResult result = handler.handle(trigger, payload);

        result.logs().forEach(logRepository::save);
        result.inboxes().forEach(inboxRepository::save);

        log.info("[Notification] dispatched eventType={} eventId={} logs={} inboxes={}",
                trigger.eventType(), trigger.eventId(),
                result.logs().size(), result.inboxes().size());
    }
}
