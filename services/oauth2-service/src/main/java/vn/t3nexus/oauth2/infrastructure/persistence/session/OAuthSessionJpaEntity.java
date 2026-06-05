package vn.t3nexus.oauth2.infrastructure.persistence.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.oauth2.domain.session.SessionStatus;

import java.time.Instant;

@Entity
@Table(name = "oauth_sessions")
@Getter
@Setter
@NoArgsConstructor
public class OAuthSessionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "idp_session_id", nullable = false)
    private String idpSessionId;

    @Column(name = "authorization_id", nullable = false, unique = true)
    private String authorizationId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
