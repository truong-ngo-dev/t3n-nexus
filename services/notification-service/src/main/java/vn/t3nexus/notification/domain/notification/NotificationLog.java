package vn.t3nexus.notification.domain.notification;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;

public class NotificationLog extends AbstractAggregateRoot<NotificationLogId>
        implements AggregateRoot<NotificationLogId> {

    private final String              eventId;
    private final NotificationType    notificationType;
    private final NotificationChannel channel;
    private final NotificationTier    tier;
    private final String              userId;
    private final String              recipient;   // nullable — email address when channel = EMAIL
    private final NotificationPayload payload;
    private final Instant             createdAt;

    private NotificationLog(NotificationLogId id, String eventId,
                             NotificationType notificationType,
                             NotificationChannel channel, NotificationTier tier,
                             String userId, String recipient,
                             NotificationPayload payload, Instant createdAt) {
        setId(id);
        this.eventId          = eventId;
        this.notificationType = notificationType;
        this.channel          = channel;
        this.tier             = tier;
        this.userId           = userId;
        this.recipient        = recipient;
        this.payload          = payload;
        this.createdAt        = createdAt;
    }

    public static NotificationLog create(NotificationLogId id, String eventId,
                                          NotificationType notificationType,
                                          NotificationChannel channel, NotificationTier tier,
                                          String userId, String recipient,
                                          NotificationPayload payload) {
        return new NotificationLog(id, eventId, notificationType, channel, tier,
                                   userId, recipient, payload, Instant.now());
    }

    public static NotificationLog reconstitute(NotificationLogId id, String eventId,
                                                NotificationType notificationType,
                                                NotificationChannel channel, NotificationTier tier,
                                                String userId, String recipient,
                                                NotificationPayload payload, Instant createdAt) {
        return new NotificationLog(id, eventId, notificationType, channel, tier,
                                   userId, recipient, payload, createdAt);
    }

    public String              getEventId()          { return eventId; }
    public NotificationType    getNotificationType() { return notificationType; }
    public NotificationChannel getChannel()          { return channel; }
    public NotificationTier    getTier()             { return tier; }
    public String              getUserId()           { return userId; }
    public String              getRecipient()        { return recipient; }
    public NotificationPayload getPayload()          { return payload; }
    public Instant             getCreatedAt()        { return createdAt; }
}
