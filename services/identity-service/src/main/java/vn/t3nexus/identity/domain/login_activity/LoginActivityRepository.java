package vn.t3nexus.identity.domain.login_activity;

import vn.t3nexus.lib.common.domain.service.Repository;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface LoginActivityRepository extends Repository<LoginActivity, LoginActivityId> {

    void endBySessionIds(List<String> sessionIds, Instant endedAt);

    List<LoginActivity> findPageByUserId(UserId userId, int page, int size);

    List<LoginActivity> findAllByIds(Set<String> ids);

    default void delete(LoginActivityId id) {
        throw new UnsupportedOperationException("LoginActivity is append-only");
    }
}
