package vn.t3nexus.identity.domain.device;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class DeviceOtpRequested extends AbstractDomainEvent implements DomainEvent {

    private final String userId;
    private final String deviceId;
    private final String email;
    private final String fullName;
    private final String otp;

    public DeviceOtpRequested(String userId, String deviceId, String email, String fullName, String otp) {
        super(UUID.randomUUID().toString(), Instant.now(), userId, "Device");
        this.userId   = userId;
        this.deviceId = deviceId;
        this.email    = email;
        this.fullName = fullName;
        this.otp      = otp;
    }

    public String getUserId()   { return userId; }
    public String getDeviceId() { return deviceId; }
    public String getEmail()    { return email; }
    public String getFullName() { return fullName; }
    public String getOtp()      { return otp; }

    @Override
    public String getRoutingKey() {
        return "identity.device-trust-otp.requested";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "userId",   userId,
                "deviceId", deviceId,
                "email",    email,
                "fullName", fullName,
                "otp",      otp
        );
    }
}
