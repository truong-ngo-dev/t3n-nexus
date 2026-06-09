package vn.t3nexus.identity.infrastructure.adapter.repository.login_activity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.login_activity.LoginActivity;
import vn.t3nexus.identity.domain.login_activity.LoginActivityId;
import vn.t3nexus.identity.domain.login_activity.LoginActivityRepository;
import vn.t3nexus.identity.infrastructure.persistence.login_activity.LoginActivityJpaRepository;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LoginActivityRepositoryAdapter implements LoginActivityRepository {

    private final LoginActivityJpaRepository jpaRepository;
    private final LoginActivityMapper        loginActivityMapper;

    @Override
    public Optional<LoginActivity> findById(LoginActivityId id) {
        throw new UnsupportedOperationException("LoginActivity lookup by ID is not supported");
    }

    @Override
    public void save(LoginActivity activity) {
        jpaRepository.insert(
                activity.getId().getValueAsString(),
                activity.getUserId().getValueAsString(),
                activity.getUsername(),
                activity.getResult().name(),
                activity.getIpAddress(),
                activity.getUserAgent(),
                activity.getCompositeHash(),
                activity.getDeviceId(),
                activity.getSessionId(),
                activity.getProvider().name(),
                activity.getCreatedAt()
        );
    }

    @Override
    public void endBySessionIds(List<String> sessionIds, Instant endedAt) {
        jpaRepository.endBySessionIds(sessionIds, endedAt);
    }

    @Override
    public List<LoginActivity> findPageByUserId(UserId userId, int page, int size) {
        int offset = page * size;
        return jpaRepository.findPageByUserId(userId.getValueAsString(), size, offset)
                .stream()
                .map(loginActivityMapper::toDomain)
                .toList();
    }

    @Override
    public List<LoginActivity> findAllByIds(Set<String> ids) {
        if (ids.isEmpty()) return List.of();
        return jpaRepository.findAllByIds(ids)
                .stream()
                .map(loginActivityMapper::toDomain)
                .toList();
    }
}
