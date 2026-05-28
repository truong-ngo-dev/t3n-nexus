package vn.t3nexus.notification.infrastructure.persistence.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notification_inbox")
@Getter
@Setter
@NoArgsConstructor
public class NotificationInboxJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "notification_log_id", nullable = false, updatable = false)
    private String notificationLogId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "title", nullable = false, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, updatable = false)
    private String body;

    @Column(name = "action_url", updatable = false)
    private String actionUrl;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
