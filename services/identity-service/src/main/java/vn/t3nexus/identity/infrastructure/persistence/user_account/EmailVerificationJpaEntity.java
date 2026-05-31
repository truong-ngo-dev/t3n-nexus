package vn.t3nexus.identity.infrastructure.persistence.user_account;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.t3nexus.identity.domain.user_account.EmailVerificationStatus;

import java.time.Instant;

@Entity
@Table(name = "email_verifications")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq")
    private Long seq;

    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private String verificationId;

    @Column(name = "user_id", unique = true, nullable = false, updatable = false)
    private String userId;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "token", nullable = false)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmailVerificationStatus status;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
