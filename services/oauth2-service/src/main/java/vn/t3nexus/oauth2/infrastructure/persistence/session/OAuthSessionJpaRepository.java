package vn.t3nexus.oauth2.infrastructure.persistence.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OAuthSessionJpaRepository extends JpaRepository<OAuthSessionJpaEntity, String> {

    Optional<OAuthSessionJpaEntity> findByAuthorizationId(String authorizationId);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO oauth_sessions (id, user_id, idp_session_id, authorization_id, ip_address, status, created_at)
            VALUES (:id, :userId, :idpSessionId, :authorizationId, :ipAddress, :status, :createdAt)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status
            """)
    void upsert(
            @Param("id")              String  id,
            @Param("userId")          String  userId,
            @Param("idpSessionId")    String  idpSessionId,
            @Param("authorizationId") String  authorizationId,
            @Param("ipAddress")       String  ipAddress,
            @Param("status")          String  status,
            @Param("createdAt")       Instant createdAt
    );
}
