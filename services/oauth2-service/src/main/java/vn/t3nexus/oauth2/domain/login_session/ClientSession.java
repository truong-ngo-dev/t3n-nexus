package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.model.AbstractEntity;
import vn.t3nexus.lib.utils.lang.Assert;

import java.time.Instant;

/**
 * Phiên ứng dụng: thực thể con của {@link LoginSession}, mỗi ứng dụng (client) người dùng vào một phiên.
 * Không có hạn riêng và không giữ mốc hoạt động. {@code authorizationId} trỏ tới bản ghi cấp token và đổi được.
 */
public class ClientSession extends AbstractEntity<ClientSessionId> {

    private final ClientId clientId;
    private       String   authorizationId;
    private final Instant  issuedAt;

    ClientSession(ClientSessionId id, ClientId clientId, String authorizationId, Instant issuedAt) {
        Assert.notNull(id, "id is required");
        Assert.notNull(clientId, "clientId is required");
        Assert.notNull(authorizationId, "authorizationId is required");
        Assert.notNull(issuedAt, "issuedAt is required");
        setId(id);
        this.clientId        = clientId;
        this.authorizationId = authorizationId;
        this.issuedAt        = issuedAt;
    }

    public ClientId clientId()        { return clientId; }
    public String   authorizationId() { return authorizationId; }
    public Instant  issuedAt()        { return issuedAt; }

    /** Dựng lại từ kho (không phát event, không kiểm tra nghiệp vụ). */
    public static ClientSession reconstitute(ClientSessionId id, ClientId clientId, String authorizationId, Instant issuedAt) {
        return new ClientSession(id, clientId, authorizationId, issuedAt);
    }

    /** Đổi liên kết sang bản ghi cấp token mới (xác thực im lặng, làm mới). Mã phiên ứng dụng giữ nguyên. */
    void relink(String newAuthorizationId) {
        Assert.notNull(newAuthorizationId, "newAuthorizationId is required");
        this.authorizationId = newAuthorizationId;
    }
}
