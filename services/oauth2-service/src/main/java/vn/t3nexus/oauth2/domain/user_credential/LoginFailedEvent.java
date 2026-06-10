package vn.t3nexus.oauth2.domain.user_credential;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginFailedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String loginIdentifier;
    private final String result;
    private final String deviceHash;
    private final String acceptLanguage;
    private final String ipAddress;
    private final String userAgent;
    private final String provider;

    public LoginFailedEvent(
            String userId,
            String loginIdentifier,
            String result,
            String deviceHash,
            String acceptLanguage,
            String ipAddress,
            String userAgent,
            String provider) {
        super(UUID.randomUUID().toString(), Instant.now(), userId, "UserCredential");
        this.loginIdentifier = loginIdentifier;
        this.result          = result;
        this.deviceHash      = deviceHash;
        this.acceptLanguage  = acceptLanguage;
        this.ipAddress       = ipAddress;
        this.userAgent       = userAgent;
        this.provider        = provider;
    }

    public String getLoginIdentifier() { return loginIdentifier; }
    public String getResult()          { return result; }
    public String getDeviceHash()      { return deviceHash; }
    public String getAcceptLanguage()  { return acceptLanguage; }
    public String getIpAddress()       { return ipAddress; }
    public String getUserAgent()       { return userAgent; }
    public String getProvider()        { return provider; }

    @Override
    public String getRoutingKey() {
        return "oauth2.login.failed";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId",          getAggregateId());
        payload.put("loginIdentifier", loginIdentifier);
        payload.put("result",          result);
        payload.put("deviceHash",      deviceHash);
        payload.put("acceptLanguage",  acceptLanguage);
        payload.put("ipAddress",       ipAddress);
        payload.put("userAgent",       userAgent);
        payload.put("provider",        provider);
        return payload;
    }
}
