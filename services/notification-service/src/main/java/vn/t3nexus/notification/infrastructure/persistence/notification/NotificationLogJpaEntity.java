package vn.t3nexus.notification.infrastructure.persistence.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
public class NotificationLogJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "notification_type", nullable = false, updatable = false)
    private String notificationType;

    @Column(name = "channel", nullable = false, updatable = false)
    private String channel;

    @Column(name = "tier", nullable = false, updatable = false)
    private String tier;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "recipient", updatable = false)
    private String recipient;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
