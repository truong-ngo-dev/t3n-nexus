package vn.t3nexus.identity.domain.login_activity;

import vn.t3nexus.lib.common.domain.service.Repository;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface LoginActivityRepository extends Repository<LoginActivity, LoginActivityId> {

    /**
     * Insert nếu chưa tồn tại record cho cùng sessionId (ON CONFLICT (session_id) DO NOTHING).
     *
     * @return true nếu row mới thật sự được insert, false nếu bị skip do duplicate (Kafka redelivery
     *         của SessionIssuedEvent). Caller phải dùng giá trị này để quyết định có update
     *         Device.lastHistoryId hay không — nếu insert bị skip, activityId vừa sinh KHÔNG tồn tại
     *         trong DB, trỏ Device.lastHistoryId vào nó sẽ tạo dangling reference.
     */
    boolean tryRecord(LoginActivity activity);

    void endBySessionIds(List<String> sessionIds, Instant endedAt);

    List<LoginActivity> findPageByUserId(UserId userId, int page, int size);

    long countByUserId(UserId userId);

    List<LoginActivity> findAllByIds(Set<String> ids);

    default void delete(LoginActivityId id) {
        throw new UnsupportedOperationException("LoginActivity is append-only");
    }
}
