package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class SessionIssuedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String oauthSessionId;
    private final String userId;
    private final String idpSessionId;
    private final String loginIdentifier;
    private final String deviceHash;
    private final String userAgent;
    private final String acceptLanguage;
    private final String ipAddress;
    private final String provider;

    public SessionIssuedEvent(
            String authorizationId,
            String oauthSessionId,
            String idpSessionId,
            String userId,
            String loginIdentifier,
            String deviceHash,
            String userAgent,
            String acceptLanguage,
            String ipAddress,
            String provider) {
        super(UUID.randomUUID().toString(), Instant.now(), oauthSessionId, "OAuthSession");
        this.oauthSessionId  = oauthSessionId;
        this.userId          = userId;
        this.idpSessionId    = idpSessionId;
        this.loginIdentifier = loginIdentifier;
        this.deviceHash      = deviceHash;
        this.userAgent       = userAgent;
        this.acceptLanguage  = acceptLanguage;
        this.ipAddress       = ipAddress;
        this.provider        = provider;
    }

    @Override
    public String getRoutingKey() {
        return "oauth2.session.issued";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "authorizationId", getAggregateId(),
                "oauthSessionId",  oauthSessionId,
                "idpSessionId",    idpSessionId,
                "userId",          userId,
                "loginIdentifier", loginIdentifier,
                "deviceHash",      deviceHash,
                "userAgent",       userAgent,
                "acceptLanguage",  acceptLanguage,
                "ipAddress",       ipAddress,
                "provider",        provider
        );
    }

    public String getOauthSessionId()  { return oauthSessionId; }
    public String getUserId()          { return userId; }
    public String getIdpSessionId()    { return idpSessionId; }
    public String getLoginIdentifier() { return loginIdentifier; }
    public String getDeviceHash()      { return deviceHash; }
    public String getUserAgent()       { return userAgent; }
    public String getAcceptLanguage()  { return acceptLanguage; }
    public String getIpAddress()       { return ipAddress; }
    public String getProvider()        { return provider; }
}
