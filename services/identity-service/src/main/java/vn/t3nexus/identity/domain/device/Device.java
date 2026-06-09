package vn.t3nexus.identity.domain.device;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.time.Instant;

public class Device extends AbstractAggregateRoot<DeviceId> implements AggregateRoot<DeviceId> {

    private final UserId            userId;
    private final DeviceFingerprint fingerprint;

    private DeviceName   name;
    private boolean      trusted;
    private DeviceStatus status;

    private final Instant registeredAt;
    private       Instant lastSeenAt;
    private       String  lastIpAddress;
    private       String  lastHistoryId;

    private Device(
            DeviceId          id,
            UserId            userId,
            DeviceFingerprint fingerprint,
            DeviceName        name,
            String            ipAddress) {
        setId(id);
        this.userId        = userId;
        this.fingerprint   = fingerprint;
        this.name          = name;
        this.trusted       = false;
        this.status        = DeviceStatus.ACTIVE;
        this.registeredAt  = Instant.now();
        this.lastSeenAt    = this.registeredAt;
        this.lastIpAddress = ipAddress;
        this.lastHistoryId = null;
    }

    private Device(
            DeviceId          id,
            UserId            userId,
            DeviceFingerprint fingerprint,
            DeviceName        name,
            boolean           trusted,
            DeviceStatus      status,
            Instant           registeredAt,
            Instant           lastSeenAt,
            String            lastIpAddress,
            String            lastHistoryId) {
        setId(id);
        this.userId        = userId;
        this.fingerprint   = fingerprint;
        this.name          = name;
        this.trusted       = trusted;
        this.status        = status;
        this.registeredAt  = registeredAt;
        this.lastSeenAt    = lastSeenAt;
        this.lastIpAddress = lastIpAddress;
        this.lastHistoryId = lastHistoryId;
    }

    // ───────────── Factory Methods ─────────────

    public static Device register(
            DeviceId          id,
            UserId            userId,
            DeviceFingerprint fingerprint,
            DeviceName        name,
            String            ipAddress) {
        return new Device(id, userId, fingerprint, name, ipAddress);
    }

    public static Device reconstitute(
            DeviceId          id,
            UserId            userId,
            DeviceFingerprint fingerprint,
            DeviceName        name,
            boolean           trusted,
            DeviceStatus      status,
            Instant           registeredAt,
            Instant           lastSeenAt,
            String            lastIpAddress,
            String            lastHistoryId) {
        return new Device(id, userId, fingerprint, name, trusted, status, registeredAt, lastSeenAt, lastIpAddress, lastHistoryId);
    }

    // ───────────── Behavior ─────────────

    /** Đánh dấu device là trusted — chỉ trusted device mới có thể revoke device khác. */
    public void trust() {
        assertActive();
        this.trusted = true;
        addDomainEvent(new DeviceTrustedEvent(getId().getValueAsString(), userId.getValueAsString()));
    }

    /** Revoke device — thường từ xa, sau khi revoke không thể login cho đến khi đăng ký lại. */
    public void revoke() {
        assertActive();
        this.trusted = false;
        this.status  = DeviceStatus.REVOKED;
        addDomainEvent(new DeviceRevokedEvent(getId().getValueAsString(), userId.getValueAsString()));
    }

    /** Cập nhật lastSeenAt và IP khi user đăng nhập lại trên device này. */
    public void recordActivity(String ipAddress) {
        assertActive();
        this.lastSeenAt    = Instant.now();
        this.lastIpAddress = ipAddress;
    }

    /** Cập nhật pointer đến login_activity record mới nhất. */
    public void recordLoginHistory(String historyId) {
        this.lastHistoryId = historyId;
    }

    /** Cập nhật tên device — system detect lại từ User-Agent mới. */
    public void updateName(DeviceName newName) {
        assertActive();
        this.name = newName;
    }

    // ───────────── Queries ─────────────

    public boolean isActive()        { return status == DeviceStatus.ACTIVE; }
    public boolean isTrusted()       { return trusted; }
    public boolean canRevokeOthers() { return isActive() && isTrusted(); }
    public boolean belongsTo(UserId uid) { return this.userId.equals(uid); }

    // ───────────── Guards ─────────────

    private void assertActive() {
        if (!isActive()) throw DeviceException.notActive();
    }

    // ───────────── Getters ─────────────

    public UserId            getUserId()        { return userId; }
    public DeviceFingerprint getFingerprint()   { return fingerprint; }
    public DeviceName        getName()          { return name; }
    public boolean           getTrusted()       { return trusted; }
    public DeviceStatus      getStatus()        { return status; }
    public Instant           getRegisteredAt()  { return registeredAt; }
    public Instant           getLastSeenAt()    { return lastSeenAt; }
    public String            getLastIpAddress() { return lastIpAddress; }
    public String            getLastHistoryId() { return lastHistoryId; }
}
