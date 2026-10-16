package vn.t3nexus.oauth2.infrastructure.persistence.login_session;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Đọc theo JPQL (kèm phiên ứng dụng bằng một câu truy vấn), ghi bằng upsert gốc để không có chu kỳ merge. Mọi truy vấn
 * "đang sống" lọc theo hạn ({@code expires_at > :now}): không dựa vào việc dòng quá hạn đã được dọn (INV-ATH-08).
 */
@Repository
public interface LoginSessionJpaRepository extends JpaRepository<LoginSessionJpaEntity, UUID> {

    @Query("select cs.authorizationId from ClientSessionJpaEntity cs where cs.id = :sid")
    Optional<String> findAuthorizationIdByClientSessionId(@Param("sid") UUID sid);

    @Query("select ls from LoginSessionJpaEntity ls left join fetch ls.clientSessions where ls.id = :id")
    Optional<LoginSessionJpaEntity> findWithClientSessionsById(@Param("id") UUID id);

    /** Tra theo {@code sid} bằng một câu truy vấn: phiên chứa phiên ứng dụng đó, kèm mọi phiên ứng dụng của nó. */
    @Query("""
            select ls from LoginSessionJpaEntity ls left join fetch ls.clientSessions
            where ls.id in (select cs.loginSessionId from ClientSessionJpaEntity cs where cs.id = :sid)
            """)
    Optional<LoginSessionJpaEntity> findWithClientSessionsByClientSessionId(@Param("sid") UUID sid);

    @Query("""
            select ls from LoginSessionJpaEntity ls left join fetch ls.clientSessions
            where ls.userAccountId = :accountId and ls.expiresAt > :now
            order by ls.issuedAt
            """)
    List<LoginSessionJpaEntity> findActiveByAccount(@Param("accountId") UUID accountId, @Param("now") Instant now);

    @Query("""
            select ls from LoginSessionJpaEntity ls left join fetch ls.clientSessions
            where ls.userAccountId = :accountId and ls.deviceKey = :deviceKey and ls.expiresAt > :now
            order by ls.issuedAt
            """)
    List<LoginSessionJpaEntity> findActiveByAccountAndDevice(@Param("accountId") UUID accountId,
                                                             @Param("deviceKey") String deviceKey,
                                                             @Param("now") Instant now);

    /** Mã các phiên đã quá hạn (cũ nhất trước), giới hạn theo lô; nạp phiên ứng dụng ở bước sau. */
    @Query("select ls.id from LoginSessionJpaEntity ls where ls.expiresAt <= :now order by ls.expiresAt")
    List<UUID> findExpiredIds(@Param("now") Instant now, Pageable limit);

    @Query("select ls from LoginSessionJpaEntity ls left join fetch ls.clientSessions where ls.id in :ids order by ls.expiresAt")
    List<LoginSessionJpaEntity> findAllWithClientSessionsByIdIn(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO login_sessions (id, user_account_id, device_key, idp_session_id, issued_at, expires_at)
            VALUES (:id, :userAccountId, :deviceKey, :idpSessionId, :issuedAt, :expiresAt)
            ON CONFLICT (id) DO UPDATE SET
                idp_session_id = EXCLUDED.idp_session_id
            """)
    void upsertLoginSession(
            @Param("id")            UUID    id,
            @Param("userAccountId") UUID    userAccountId,
            @Param("deviceKey")     String  deviceKey,
            @Param("idpSessionId")  String  idpSessionId,
            @Param("issuedAt")      Instant issuedAt,
            @Param("expiresAt")     Instant expiresAt
    );

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO client_sessions (id, login_session_id, client_id, authorization_id, issued_at)
            VALUES (:id, :loginSessionId, :clientId, :authorizationId, :issuedAt)
            ON CONFLICT (id) DO UPDATE SET
                authorization_id = EXCLUDED.authorization_id
            """)
    void upsertClientSession(
            @Param("id")              UUID    id,
            @Param("loginSessionId")  UUID    loginSessionId,
            @Param("clientId")        String  clientId,
            @Param("authorizationId") String  authorizationId,
            @Param("issuedAt")        Instant issuedAt
    );

    /** Xóa phiên người dùng; phiên ứng dụng xóa theo nhờ {@code ON DELETE CASCADE}. */
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM login_sessions WHERE id = :id")
    void deleteLoginSessionById(@Param("id") UUID id);
}
