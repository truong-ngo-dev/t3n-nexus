package vn.t3nexus.identity.domain.login_activity;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.time.Instant;
import java.util.List;

public interface LoginActivityRepository extends Repository<LoginActivity, LoginActivityId> {

    void endBySessionIds(List<String> sessionIds, Instant endedAt);

    default void delete(LoginActivityId id) {
        throw new UnsupportedOperationException("LoginActivity is append-only");
    }
}
