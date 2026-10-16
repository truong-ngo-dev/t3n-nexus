package vn.t3nexus.oauth2.infrastructure.persistence.login_session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Hàng phiên ứng dụng. Chỉ dùng để đọc; ghi đi qua upsert của {@link LoginSessionJpaRepository}. */
@Entity
@Table(name = "client_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ClientSessionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "login_session_id", nullable = false, insertable = false, updatable = false)
    private UUID loginSessionId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "authorization_id", nullable = false)
    private String authorizationId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
}
