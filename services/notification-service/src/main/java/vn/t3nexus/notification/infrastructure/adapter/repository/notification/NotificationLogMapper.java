package vn.t3nexus.notification.infrastructure.adapter.repository.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.notification.domain.notification.*;
import vn.t3nexus.notification.infrastructure.persistence.notification.NotificationLogJpaEntity;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationLogMapper {

    private final ObjectMapper objectMapper;

    public NotificationLog toDomain(NotificationLogJpaEntity entity) {
        return NotificationLog.reconstitute(
                NotificationLogId.of(entity.getId()),
                entity.getEventId(),
                NotificationType.valueOf(entity.getNotificationType()),
                NotificationChannel.valueOf(entity.getChannel()),
                NotificationTier.valueOf(entity.getTier()),
                entity.getUserId(),
                entity.getRecipient(),
                payloadFromJson(entity.getPayloadJson()),
                entity.getCreatedAt()
        );
    }

    public NotificationLogJpaEntity toEntity(NotificationLog log) {
        NotificationLogJpaEntity entity = new NotificationLogJpaEntity();
        entity.setId(log.getId().getValue());
        entity.setEventId(log.getEventId());
        entity.setNotificationType(log.getNotificationType().name());
        entity.setChannel(log.getChannel().name());
        entity.setTier(log.getTier().name());
        entity.setUserId(log.getUserId());
        entity.setRecipient(log.getRecipient());
        entity.setPayloadJson(payloadToJson(log.getPayload()));
        entity.setCreatedAt(log.getCreatedAt());
        return entity;
    }

    String payloadToJson(NotificationPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize NotificationPayload", e);
        }
    }

    private NotificationPayload payloadFromJson(String json) {
        try {
            PayloadJson data = objectMapper.readValue(json, PayloadJson.class);
            return NotificationPayload.of(data.title(), data.body(), data.attributes());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize NotificationPayload", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PayloadJson(
            String title,
            String body,
            Map<String, Object> attributes
    ) {}
}
