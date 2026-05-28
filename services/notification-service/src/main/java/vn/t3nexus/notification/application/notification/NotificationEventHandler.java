package vn.t3nexus.notification.application.notification;

public interface NotificationEventHandler<E> {

    /** Matches EventEnvelope.eventType — equals event class simple name from the producer. */
    String supportedEventType();

    Class<E> payloadType();

    NotificationResult handle(NotificationTrigger trigger, E payload);
}
