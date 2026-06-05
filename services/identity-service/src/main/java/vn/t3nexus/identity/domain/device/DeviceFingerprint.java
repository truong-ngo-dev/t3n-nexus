package vn.t3nexus.identity.domain.device;

import vn.t3nexus.lib.common.domain.model.ValueObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class DeviceFingerprint implements ValueObject {

    private final String deviceHash;     // hash từ JavaScript (canvas, screen, timezone...)
    private final String userAgent;      // từ request header
    private final String acceptLanguage; // từ request header
    private final String compositeHash;  // SHA-256(deviceHash|userAgent|acceptLanguage) — dùng để so khớp

    private DeviceFingerprint(String deviceHash, String userAgent, String acceptLanguage) {
        this.deviceHash     = deviceHash;
        this.userAgent      = userAgent;
        this.acceptLanguage = acceptLanguage;
        this.compositeHash  = computeHash(deviceHash, userAgent, acceptLanguage);
    }

    public static DeviceFingerprint of(String deviceHash, String userAgent, String acceptLanguage) {
        return new DeviceFingerprint(deviceHash, userAgent, acceptLanguage);
    }

    public boolean matches(DeviceFingerprint other) {
        return this.compositeHash.equals(other.compositeHash);
    }

    private static String computeHash(String... parts) {
        try {
            String input = String.join("|", parts);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String getDeviceHash()     { return deviceHash; }
    public String getUserAgent()      { return userAgent; }
    public String getAcceptLanguage() { return acceptLanguage; }
    public String getCompositeHash()  { return compositeHash; }
}
