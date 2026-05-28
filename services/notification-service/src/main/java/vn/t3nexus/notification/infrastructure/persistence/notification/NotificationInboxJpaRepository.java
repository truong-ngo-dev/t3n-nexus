package vn.t3nexus.notification.infrastructure.persistence.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationInboxJpaRepository extends JpaRepository<NotificationInboxJpaEntity, String> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notification_inbox (id, notification_log_id, user_id, title, body, action_url, is_read, created_at)
            VALUES (:id, :notificationLogId, :userId, :title, :body, :actionUrl, :isRead, :createdAt)
            ON CONFLICT (id) DO UPDATE SET
                is_read = EXCLUDED.is_read
            """)
    void upsert(
            @Param("id")                String id,
            @Param("notificationLogId") String notificationLogId,
            @Param("userId")            String userId,
            @Param("title")             String title,
            @Param("body")              String body,
            @Param("actionUrl")         String actionUrl,
            @Param("isRead")            boolean isRead,
            @Param("createdAt")         Instant createdAt
    );

    @Query(nativeQuery = true, value = """
            SELECT * FROM notification_inbox
            WHERE user_id = :userId
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<NotificationInboxJpaEntity> findByUserId(
            @Param("userId") String userId,
            @Param("limit")  int limit,
            @Param("offset") int offset
    );

    long countByUserIdAndIsRead(String userId, boolean isRead);

    @Modifying
    @Query("UPDATE NotificationInboxJpaEntity e SET e.isRead = true WHERE e.userId = :userId AND e.isRead = false")
    void markAllAsReadByUserId(@Param("userId") String userId);
}
