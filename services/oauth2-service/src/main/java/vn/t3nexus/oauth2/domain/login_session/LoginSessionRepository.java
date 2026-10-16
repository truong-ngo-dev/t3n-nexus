package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.service.Repository;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Kho phiên đăng nhập (cổng của miền, hạ tầng cài). Truy vấn "đang sống" luôn lọc theo hạn. */
public interface LoginSessionRepository extends Repository<LoginSession, LoginSessionId> {

    /** Tìm phiên chứa phiên ứng dụng {@code sid}; dùng khi làm mới token (tra theo khóa chính, ADR-0003 mục 8). */
    Optional<LoginSession> findByClientSessionId(ClientSessionId sid);

    /**
     * Chỉ lấy {@code authorization_id} hiện hành của phiên ứng dụng {@code sid} (một truy vấn theo khóa chính, không nạp aggregate).
     * Dùng khi tra bản ghi ủy quyền lúc làm mới và lúc userinfo (ADR-0003 mục 8).
     */
    Optional<String> findAuthorizationIdByClientSessionId(ClientSessionId sid);

    /** Các phiên người dùng còn hiệu lực của tài khoản tại {@code now}. */
    List<LoginSession> findActiveByAccount(UserAccountId accountId, Instant now);

    /** Các phiên còn hiệu lực của tài khoản trên một thiết bị tại {@code now}. */
    List<LoginSession> findActiveByAccountAndDevice(UserAccountId accountId, DeviceKeyHash deviceKey, Instant now);

    /** Các phiên đã quá hạn (dọn theo lô, ADR-0003 mục 7). */
    List<LoginSession> findExpired(Instant now, int limit);

    /**
     * Lưu phiên.
     *
     * @throws LoginSessionException {@code CLIENT_SESSION_CONFLICT} khi vi phạm ràng buộc duy nhất (phiên người dùng, client)
     *                               hoặc của {@code authorizationId}; bên thua nạp lại phiên rồi thử lại
     */
    @Override
    void save(LoginSession aggregate);
}
