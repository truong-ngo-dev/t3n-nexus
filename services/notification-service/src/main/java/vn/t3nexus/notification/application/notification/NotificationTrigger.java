package vn.t3nexus.notification.application.notification;

import vn.t3nexus.lib.events.EventEnvelope;

public record NotificationTrigger(
        String eventId,
        String eventType,
        String aggregateId,
        Object payload
) {
    public static NotificationTrigger from(EventEnvelope envelope) {
        return new NotificationTrigger(
                envelope.eventId(),
                envelope.eventType(),
                envelope.aggregateId(),
                envelope.payload()
        );
    }
}
