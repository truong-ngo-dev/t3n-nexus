package vn.t3nexus.oauth2.infrastructure.persistence.login_session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Hàng phiên người dùng. Chỉ dùng để <b>đọc</b>: ghi đi qua câu lệnh upsert của {@link LoginSessionJpaRepository} nên
 * không có chu kỳ merge hay chọn trước khi chèn. {@code clientSessions} là ánh xạ chỉ đọc (cột khóa ngoại do phiên ứng dụng sở hữu).
 */
@Entity
@Table(name = "login_sessions")
@Getter
@Setter
@NoArgsConstructor
public class LoginSessionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_account_id", nullable = false)
    private UUID userAccountId;

    @Column(name = "device_key")
    private String deviceKey;

    @Column(name = "idp_session_id", nullable = false)
    private String idpSessionId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "login_session_id", insertable = false, updatable = false)
    private Set<ClientSessionJpaEntity> clientSessions = new LinkedHashSet<>();
}
