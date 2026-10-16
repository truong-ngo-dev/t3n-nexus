package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Phiên người dùng kết thúc, kéo theo mọi phiên ứng dụng của nó. Hết hạn không phát event. Không mang mốc hoạt động. */
public class SessionEndedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String    loginSessionId;
    private final String    userAccountId;
    private final EndReason reason;

    public SessionEndedEvent(LoginSessionId loginSessionId, UserAccountId userAccountId, EndReason reason,
                        Instant occurredOn) {
        super(UUID.randomUUID().toString(), occurredOn, loginSessionId.getValueAsString(), "LoginSession");
        this.loginSessionId = loginSessionId.getValueAsString();
        this.userAccountId  = userAccountId.getValueAsString();
        this.reason         = reason;
    }

    @Override
    public String getRoutingKey() {
        return "oauth2.login-session.ended";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loginSessionId", loginSessionId);
        payload.put("userAccountId",  userAccountId);
        payload.put("reason",         reason.name());
        return payload;
    }
}
