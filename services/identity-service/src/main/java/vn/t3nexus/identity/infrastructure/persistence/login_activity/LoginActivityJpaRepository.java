package vn.t3nexus.identity.infrastructure.persistence.login_activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Repository
public interface LoginActivityJpaRepository extends JpaRepository<LoginActivityJpaEntity, String> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO login_activities (
                id, user_id, username, result, ip_address, user_agent,
                composite_hash, device_id, session_id, provider, created_at
            ) VALUES (
                :id, :userId, :username, :result, :ipAddress, :userAgent,
                :compositeHash, :deviceId, :sessionId, :provider, :createdAt
            )
            ON CONFLICT (session_id) WHERE session_id IS NOT NULL DO NOTHING
            """)
    void insert(
            @Param("id")            String id,
            @Param("userId")        String userId,
            @Param("username")      String username,
            @Param("result")        String result,
            @Param("ipAddress")     String ipAddress,
            @Param("userAgent")     String userAgent,
            @Param("compositeHash") String compositeHash,
            @Param("deviceId")      String deviceId,
            @Param("sessionId")     String sessionId,
            @Param("provider")      String provider,
            @Param("createdAt")     Instant createdAt
    );

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            UPDATE login_activities
               SET ended_at = :endedAt
             WHERE session_id IN (:sessionIds)
               AND ended_at IS NULL
            """)
    void endBySessionIds(@Param("sessionIds") List<String> sessionIds, @Param("endedAt") Instant endedAt);

    @Query(nativeQuery = true, value = """
            SELECT * FROM login_activities
             WHERE user_id = :userId
             ORDER BY created_at DESC
             LIMIT :size OFFSET :offset
            """)
    List<LoginActivityJpaEntity> findPageByUserId(
            @Param("userId") String userId,
            @Param("size")   int    size,
            @Param("offset") int    offset
    );

    @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM login_activities WHERE user_id = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT la FROM LoginActivityJpaEntity la WHERE la.id IN :ids")
    List<LoginActivityJpaEntity> findAllByIds(@Param("ids") Set<String> ids);
}
