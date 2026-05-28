package vn.t3nexus.identity.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface EmailVerificationJpaRepository extends JpaRepository<EmailVerificationJpaEntity, Long> {

    Optional<EmailVerificationJpaEntity> findByVerificationId(String verificationId);

    Optional<EmailVerificationJpaEntity> findByToken(String token);

    Optional<EmailVerificationJpaEntity> findByUserId(String userId);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO email_verifications (id, user_id, email, token, expires_at, status, verified_at, created_at)
            VALUES (:verificationId, :userId, :email, :token, :expiresAt, :status, :verifiedAt, :createdAt)
            ON CONFLICT (id) DO UPDATE SET
                token       = EXCLUDED.token,
                expires_at  = EXCLUDED.expires_at,
                status      = EXCLUDED.status,
                verified_at = EXCLUDED.verified_at
            """)
    void upsert(
            @Param("verificationId") String verificationId,
            @Param("userId")         String userId,
            @Param("email")          String email,
            @Param("token")          String token,
            @Param("expiresAt")      Instant expiresAt,
            @Param("status")         String status,
            @Param("verifiedAt")     Instant verifiedAt,
            @Param("createdAt")      Instant createdAt
    );
}
