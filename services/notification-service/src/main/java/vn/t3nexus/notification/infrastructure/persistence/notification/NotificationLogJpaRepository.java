package vn.t3nexus.notification.infrastructure.persistence.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface NotificationLogJpaRepository extends JpaRepository<NotificationLogJpaEntity, String> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notification_log (id, event_id, notification_type, channel, tier, user_id, recipient, payload, created_at)
            VALUES (:id, :eventId, :notificationType, :channel, :tier, :userId, :recipient, CAST(:payloadJson AS jsonb), :createdAt)
            ON CONFLICT (event_id, channel) DO NOTHING
            """)
    void insertIgnoreConflict(
            @Param("id")               String id,
            @Param("eventId")          String eventId,
            @Param("notificationType") String notificationType,
            @Param("channel")          String channel,
            @Param("tier")             String tier,
            @Param("userId")           String userId,
            @Param("recipient")        String recipient,
            @Param("payloadJson")      String payloadJson,
            @Param("createdAt")        Instant createdAt
    );
}
