package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Token vừa được cấp cho một phiên ứng dụng (đổi mã lấy token sau đăng nhập hoặc xác thực im lặng, làm mới). Mã event lấy từ {@code jti} của access token. Tên chờ chốt
 * (`service.md` mục 5). Không mang địa chỉ mạng: lời gọi làm mới đến từ BFF.
 */
public class ClientSessionIssuedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String loginSessionId;
    private final String clientSessionId;
    private final String userAccountId;
    private final String clientId;
    private final String deviceKey;

    public ClientSessionIssuedEvent(String eventId, LoginSessionId loginSessionId, ClientSessionId clientSessionId,
                               UserAccountId userAccountId, ClientId clientId, DeviceKeyHash deviceKey,
                               Instant occurredOn) {
        super(eventId, occurredOn, loginSessionId.getValueAsString(), "LoginSession");
        this.loginSessionId  = loginSessionId.getValueAsString();
        this.clientSessionId = clientSessionId.getValueAsString();
        this.userAccountId   = userAccountId.getValueAsString();
        this.clientId        = clientId.value();
        this.deviceKey       = deviceKey == null ? null : deviceKey.value();
    }

    @Override
    public String getRoutingKey() {
        return "oauth2.client-session.issued";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loginSessionId",  loginSessionId);
        payload.put("clientSessionId", clientSessionId);
        payload.put("userAccountId",   userAccountId);
        payload.put("clientId",        clientId);
        payload.put("deviceKey",       deviceKey);
        return payload;
    }
}
