package vn.t3nexus.notification.domain.notification;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface NotificationInboxRepository extends Repository<NotificationInbox, NotificationInboxId> {
        List<NotificationInbox> findByUserId(String userId, int limit, int offset);
    long countUnreadByUserId(String userId);
    void markAllAsReadByUserId(String userId);
}
