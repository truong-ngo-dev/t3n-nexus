package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Đăng nhập thất bại, chỉ phát khi tìm ra tài khoản (INV-IDN-03; email không tồn tại thì không có chủ để ghi).
 * Mã thiết bị là bản băm, không phải mã thô (ADR-0005).
 */
public class LoginFailedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String userAccountId;
    private final String reason;
    private final String deviceKey;
    private final String ipAddress;
    private final String userAgent;

    public LoginFailedEvent(UserAccountId userAccountId, String reason, DeviceKeyHash deviceKey, LoginContext context,
                       Instant occurredOn) {
        super(UUID.randomUUID().toString(), occurredOn, userAccountId.getValueAsString(), "UserAccount");
        this.userAccountId = userAccountId.getValueAsString();
        this.reason        = reason;
        this.deviceKey     = deviceKey == null ? null : deviceKey.value();
        this.ipAddress     = context == null ? null : context.ipAddress();
        this.userAgent     = context == null ? null : context.userAgent();
    }

    @Override
    public String getRoutingKey() {
        return "oauth2.login.failed";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userAccountId", userAccountId);
        payload.put("reason",        reason);
        payload.put("deviceKey",     deviceKey);
        payload.put("ipAddress",     ipAddress);
        payload.put("userAgent",     userAgent);
        return payload;
    }
}
