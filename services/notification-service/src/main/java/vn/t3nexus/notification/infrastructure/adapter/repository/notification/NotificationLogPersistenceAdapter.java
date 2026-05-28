package vn.t3nexus.notification.infrastructure.adapter.repository.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.notification.domain.notification.NotificationLog;
import vn.t3nexus.notification.domain.notification.NotificationLogId;
import vn.t3nexus.notification.domain.notification.NotificationLogRepository;
import vn.t3nexus.notification.infrastructure.persistence.notification.NotificationLogJpaRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NotificationLogPersistenceAdapter implements NotificationLogRepository {

    private final NotificationLogJpaRepository jpaRepository;
    private final NotificationLogMapper        mapper;

    @Override
    public Optional<NotificationLog> findById(NotificationLogId id) {
        return jpaRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public void save(NotificationLog log) {
        // NotificationLog is immutable — always INSERT, silently ignore duplicate (event_id, channel)
        jpaRepository.insertIgnoreConflict(
                log.getId().getValue(),
                log.getEventId(),
                log.getNotificationType().name(),
                log.getChannel().name(),
                log.getTier().name(),
                log.getUserId(),
                log.getRecipient(),
                mapper.payloadToJson(log.getPayload()),
                log.getCreatedAt()
        );
    }

    @Override
    public void delete(NotificationLogId id) {
        jpaRepository.deleteById(id.getValue());
    }
}
