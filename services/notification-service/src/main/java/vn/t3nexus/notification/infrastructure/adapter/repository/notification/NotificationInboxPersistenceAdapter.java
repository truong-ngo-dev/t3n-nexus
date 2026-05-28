package vn.t3nexus.notification.infrastructure.adapter.repository.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.notification.domain.notification.NotificationInbox;
import vn.t3nexus.notification.domain.notification.NotificationInboxId;
import vn.t3nexus.notification.domain.notification.NotificationInboxRepository;
import vn.t3nexus.notification.infrastructure.persistence.notification.NotificationInboxJpaRepository;

import java.util.List;
import java.util.Optional;


@Component
@RequiredArgsConstructor
public class NotificationInboxPersistenceAdapter implements NotificationInboxRepository {

    private final NotificationInboxJpaRepository jpaRepository;
    private final NotificationInboxMapper        mapper;

    @Override
    public Optional<NotificationInbox> findById(NotificationInboxId id) {
        return jpaRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public void save(NotificationInbox inbox) {
        jpaRepository.upsert(
                inbox.getId().getValue(),
                inbox.getNotificationLogId().getValue(),
                inbox.getUserId(),
                inbox.getTitle(),
                inbox.getBody(),
                inbox.getActionUrl(),
                inbox.isRead(),
                inbox.getCreatedAt()
        );
    }

    @Override
    public void delete(NotificationInboxId id) {
        jpaRepository.deleteById(id.getValue());
    }

    @Override
    public List<NotificationInbox> findByUserId(String userId, int limit, int offset) {
        return jpaRepository.findByUserId(userId, limit, offset).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countUnreadByUserId(String userId) {
        return jpaRepository.countByUserIdAndIsRead(userId, false);
    }

    @Override
    public void markAllAsReadByUserId(String userId) {
        jpaRepository.markAllAsReadByUserId(userId);
    }
}
