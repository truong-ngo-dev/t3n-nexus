package vn.t3nexus.oauth2.domain.user_account;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Tài khoản vừa đăng ký. Chỉ mang mã, email chuẩn hóa, vai; không mang mật khẩu hay tên (INV-ATH-02). */
public class UserAccountRegistered extends AbstractDomainEvent implements DomainEvent {

    private final String userAccountId;
    private final String email;
    private final String role;

    public UserAccountRegistered(UserAccountId userAccountId, Email email, Role role, Instant occurredOn) {
        super(UUID.randomUUID().toString(), occurredOn, userAccountId.getValueAsString(), "UserAccount");
        this.userAccountId = userAccountId.getValueAsString();
        this.email         = email.value();
        this.role          = role.name();
    }

    public String getUserAccountId() { return userAccountId; }
    public String getEmail()         { return email; }
    public String getRole()          { return role; }

    @Override
    public String getRoutingKey() {
        return "oauth2.user-account.registered";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userAccountId", userAccountId);
        payload.put("email",         email);
        payload.put("role",          role);
        return payload;
    }
}
