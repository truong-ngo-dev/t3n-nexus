package vn.t3nexus.notification.infrastructure.adapter.repository.notification;

import org.springframework.stereotype.Component;
import vn.t3nexus.notification.domain.notification.NotificationInbox;
import vn.t3nexus.notification.domain.notification.NotificationInboxId;
import vn.t3nexus.notification.domain.notification.NotificationLogId;
import vn.t3nexus.notification.infrastructure.persistence.notification.NotificationInboxJpaEntity;

@Component
public class NotificationInboxMapper {

    public NotificationInbox toDomain(NotificationInboxJpaEntity entity) {
        return NotificationInbox.reconstitute(
                NotificationInboxId.of(entity.getId()),
                NotificationLogId.of(entity.getNotificationLogId()),
                entity.getUserId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getActionUrl(),
                entity.isRead(),
                entity.getCreatedAt()
        );
    }
}
