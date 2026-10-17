package vn.t3nexus.oauth2.infrastructure.adapter.repository.login_session;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.oauth2.domain.login_session.ClientSession;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionException;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.infrastructure.persistence.login_session.LoginSessionJpaEntity;
import vn.t3nexus.oauth2.infrastructure.persistence.login_session.LoginSessionJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Kho phiên đăng nhập trên Postgres. Thao tác ghi tham gia giao dịch của bên gọi nếu có (điểm móc lúc cấp token), nếu không thì tự mở một giao dịch (câu lệnh upsert gốc đòi giao dịch), để việc ghi phiên
 * và bản ghi ủy quyền cùng thành công hoặc cùng quay lui (ADR-0003 mục 9).
 */
@Component
@RequiredArgsConstructor
public class LoginSessionRepositoryAdapter implements LoginSessionRepository {

    /** Tên các ràng buộc duy nhất của phiên ứng dụng, xem migration V13. */
    static final Set<String> CLIENT_SESSION_UNIQUE_CONSTRAINTS =
            Set.of("uq_client_sessions_login_client", "uq_client_sessions_authorization");

    private final LoginSessionJpaRepository jpaRepository;
    private final LoginSessionMapper        mapper;

    @Override
    public Optional<LoginSession> findById(LoginSessionId id) {
        return jpaRepository.findWithClientSessionsById(id.getValue()).map(mapper::toDomain);
    }

    @Override
    public Optional<LoginSession> findByClientSessionId(ClientSessionId sid) {
        return jpaRepository.findWithClientSessionsByClientSessionId(sid.getValue()).map(mapper::toDomain);
    }

    @Override
    public Optional<String> findAuthorizationIdByClientSessionId(ClientSessionId sid) {
        return jpaRepository.findAuthorizationIdByClientSessionId(sid.getValue());
    }

    @Override
    public List<LoginSession> findActiveByAccount(UserAccountId accountId, Instant now) {
        return jpaRepository.findActiveByAccount(accountId.getValue(), now).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<LoginSession> findActiveByAccountAndDevice(UserAccountId accountId, DeviceKeyHash deviceKey,
                                                           Instant now) {
        return jpaRepository.findActiveByAccountAndDevice(accountId.getValue(), deviceKey.value(), now).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public Optional<LoginSession> findByIdForUpdate(LoginSessionId id) {
        if (jpaRepository.lockById(id.getValue()).isEmpty()) return Optional.empty();
        return findById(id);   // đọc sau khi đã giữ khóa nên thấy trạng thái đã commit của bên giữ khóa trước
    }

    @Override
    public Optional<LoginSession> findActiveByIdpSessionIdForUpdate(String idpSessionId, Instant now) {
        return jpaRepository.lockActiveByIdpSessionId(idpSessionId, now).stream().findFirst()
                .flatMap(id -> findById(LoginSessionId.of(id)));
    }

    @Override
    public List<LoginSession> findExpired(Instant now, int limit) {
        List<UUID> ids = jpaRepository.findExpiredIds(now, PageRequest.of(0, limit));
        if (ids.isEmpty()) return List.of();
        return jpaRepository.findAllWithClientSessionsByIdIn(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void save(LoginSession session) {
        try {
            jpaRepository.upsertLoginSession(
                    session.getId().getValue(),
                    session.userAccountId().getValue(),
                    session.deviceKey() == null ? null : session.deviceKey().value(),
                    session.idpSessionId(),
                    session.issuedAt(),
                    session.expiresAt());
            for (ClientSession clientSession : session.clientSessions()) {
                jpaRepository.upsertClientSession(
                        clientSession.getId().getValue(),
                        session.getId().getValue(),
                        clientSession.clientId().value(),
                        clientSession.authorizationId(),
                        clientSession.issuedAt());
            }
        } catch (DataIntegrityViolationException e) {
            // Chốt cuối của INV-ATH-10: hai yêu cầu cùng cấp phiên ứng dụng cho một (phiên người dùng, client) thì một bên
            // vi phạm ràng buộc duy nhất; dịch để tầng ứng dụng không biết kiểu lỗi của hạ tầng và có thể nạp lại rồi thử lại.
            if (isClientSessionUniqueViolation(e)) throw LoginSessionException.clientSessionConflict();
            throw e;
        }
    }

    @Override
    @Transactional
    public void delete(LoginSessionId id) {
        jpaRepository.deleteLoginSessionById(id.getValue());
    }

    private static boolean isClientSessionUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        return cause instanceof ConstraintViolationException cve
                && cve.getConstraintName() != null
                && CLIENT_SESSION_UNIQUE_CONSTRAINTS.contains(cve.getConstraintName());
    }
}
