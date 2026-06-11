package vn.t3nexus.identity.application.device.trust_otp;

import java.util.Optional;

public interface DeviceTrustOtpStore {

    void save(String userId, String deviceId, String otpHash);

    Optional<String> findHash(String userId, String deviceId);

    int incrementAttempts(String userId, String deviceId);

    void delete(String userId, String deviceId);
}
