package vn.t3nexus.identity.domain.login_activity;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.time.Instant;

/**
 * Bản ghi audit cho mỗi lần thử đăng nhập — immutable sau khi tạo, chỉ append.
 * Chỉ ghi nhận khi user tồn tại trong hệ thống (userId luôn non-null).
 */
public class LoginActivity extends AbstractAggregateRoot<LoginActivityId> implements AggregateRoot<LoginActivityId> {

    private final UserId        userId;
    private final String        username;       // submitted login identifier (email)
    private final LoginResult   result;
    private final String        ipAddress;
    private final String        userAgent;
    private final String        compositeHash;  // device fingerprint — for correlation with Device
    private final String        deviceId;       // nullable — null when login fails (device not yet resolved)
    private final String        sessionId;      // FK to OAuthSession — nullable when login fails
    private final LoginProvider provider;
    private final Instant       createdAt;
    private final Instant       endedAt;        // nullable — set when session is closed (logout/revoke)

    public enum LoginProvider {
        LOCAL, GOOGLE
    }

    private LoginActivity(
            LoginActivityId id,
            UserId          userId,
            String          username,
            LoginResult     result,
            String          ipAddress,
            String          userAgent,
            String          compositeHash,
            String          deviceId,
            String          sessionId,
            LoginProvider   provider,
            Instant         createdAt,
            Instant         endedAt) {
        setId(id);
        this.userId        = userId;
        this.username      = username;
        this.result        = result;
        this.ipAddress     = ipAddress;
        this.userAgent     = userAgent;
        this.compositeHash = compositeHash;
        this.deviceId      = deviceId;
        this.sessionId     = sessionId;
        this.provider      = provider;
        this.createdAt     = createdAt;
        this.endedAt       = endedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static LoginActivity recordSuccess(
            LoginActivityId id,
            UserId          userId,
            String          username,
            String          compositeHash,
            String          deviceId,
            String          sessionId,
            String          ipAddress,
            String          userAgent,
            LoginProvider   provider) {
        return new LoginActivity(id, userId, username, LoginResult.SUCCESS,
                ipAddress, userAgent, compositeHash, deviceId, sessionId, provider, Instant.now(), null);
    }

    public static LoginActivity recordFailure(
            LoginActivityId id,
            UserId          userId,
            String          username,
            LoginResult     result,
            String          compositeHash,
            String          ipAddress,
            String          userAgent,
            LoginProvider   provider) {
        return new LoginActivity(id, userId, username, result,
                ipAddress, userAgent, compositeHash, null, null, provider, Instant.now(), null);
    }

    public static LoginActivity reconstitute(
            LoginActivityId id,
            UserId          userId,
            String          username,
            LoginResult     result,
            String          ipAddress,
            String          userAgent,
            String          compositeHash,
            String          deviceId,
            String          sessionId,
            LoginProvider   provider,
            Instant         createdAt,
            Instant         endedAt) {
        return new LoginActivity(id, userId, username, result,
                ipAddress, userAgent, compositeHash, deviceId, sessionId, provider, createdAt, endedAt);
    }

    // ───────────── Queries ─────────────

    public boolean isSuccess() { return this.result == LoginResult.SUCCESS; }

    // ───────────── Getters ─────────────

    public UserId        getUserId()        { return userId; }
    public String        getUsername()      { return username; }
    public LoginResult   getResult()        { return result; }
    public String        getIpAddress()     { return ipAddress; }
    public String        getUserAgent()     { return userAgent; }
    public String        getCompositeHash() { return compositeHash; }
    public String        getDeviceId()      { return deviceId; }
    public String        getSessionId()     { return sessionId; }
    public LoginProvider getProvider()      { return provider; }
    public Instant       getCreatedAt()     { return createdAt; }
    public Instant       getEndedAt()       { return endedAt; }
}
