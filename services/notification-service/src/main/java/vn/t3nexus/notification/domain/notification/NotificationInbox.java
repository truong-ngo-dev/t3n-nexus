package vn.t3nexus.notification.domain.notification;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;

public class NotificationInbox extends AbstractAggregateRoot<NotificationInboxId>
        implements AggregateRoot<NotificationInboxId> {

    private final NotificationLogId notificationLogId; // cross-aggregate ref by ID
    private final String            userId;
    private final String            title;
    private final String            body;
    private final String            actionUrl;          // nullable
    private boolean                 isRead;
    private final Instant           createdAt;

    private NotificationInbox(NotificationInboxId id, NotificationLogId notificationLogId,
                               String userId, String title, String body,
                               String actionUrl, boolean isRead, Instant createdAt) {
        setId(id);
        this.notificationLogId = notificationLogId;
        this.userId            = userId;
        this.title             = title;
        this.body              = body;
        this.actionUrl         = actionUrl;
        this.isRead            = isRead;
        this.createdAt         = createdAt;
    }

    public static NotificationInbox create(NotificationInboxId id,
                                            NotificationLogId notificationLogId,
                                            String userId, String title, String body,
                                            String actionUrl) {
        return new NotificationInbox(id, notificationLogId, userId,
                                     title, body, actionUrl, false, Instant.now());
    }

    public static NotificationInbox reconstitute(NotificationInboxId id,
                                                  NotificationLogId notificationLogId,
                                                  String userId, String title, String body,
                                                  String actionUrl, boolean isRead,
                                                  Instant createdAt) {
        return new NotificationInbox(id, notificationLogId, userId,
                                     title, body, actionUrl, isRead, createdAt);
    }

    public void markAsRead() {
        this.isRead = true;
    }

    public NotificationLogId getNotificationLogId() { return notificationLogId; }
    public String            getUserId()            { return userId; }
    public String            getTitle()             { return title; }
    public String            getBody()              { return body; }
    public String            getActionUrl()         { return actionUrl; }
    public boolean           isRead()               { return isRead; }
    public Instant           getCreatedAt()         { return createdAt; }
}
