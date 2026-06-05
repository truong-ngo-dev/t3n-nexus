package vn.t3nexus.identity.infrastructure.persistence.login_activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "login_activities")
@Getter
@Setter
@NoArgsConstructor
public class LoginActivityJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "username", length = 200, nullable = false)
    private String username;

    @Column(name = "result", length = 30, nullable = false)
    private String result;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "composite_hash", length = 64)
    private String compositeHash;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "provider", length = 20, nullable = false)
    private String provider;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
