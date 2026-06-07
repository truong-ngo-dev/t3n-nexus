package vn.t3nexus.oauth2.infrastructure.persistence.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "registered_client_id", nullable = false)
    private String registeredClientId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
