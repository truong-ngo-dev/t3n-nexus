package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phiên người dùng vừa được mở (chỉ khi nhập mật khẩu thành công; không phát khi cấp phiên ứng dụng hay làm mới).
 * Không mang {@code SESSION_ID} của Spring (ADR-0003 mục 4); mã thiết bị là bản băm.
 */
public class SessionIssuedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String loginSessionId;
    private final String userAccountId;
    private final String deviceKey;
    private final String ipAddress;
    private final String userAgent;

    public SessionIssuedEvent(LoginSessionId loginSessionId, UserAccountId userAccountId, DeviceKeyHash deviceKey,
                         LoginContext context, Instant occurredOn) {
        super(UUID.randomUUID().toString(), occurredOn, loginSessionId.getValueAsString(), "LoginSession");
        this.loginSessionId = loginSessionId.getValueAsString();
        this.userAccountId  = userAccountId.getValueAsString();
        this.deviceKey      = deviceKey == null ? null : deviceKey.value();
        this.ipAddress      = context == null ? null : context.ipAddress();
        this.userAgent      = context == null ? null : context.userAgent();
    }

    @Override
    public String getRoutingKey() {
        return "oauth2.login-session.issued";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loginSessionId", loginSessionId);
        payload.put("userAccountId",  userAccountId);
        payload.put("deviceKey",      deviceKey);
        payload.put("ipAddress",      ipAddress);
        payload.put("userAgent",      userAgent);
        return payload;
    }
}
