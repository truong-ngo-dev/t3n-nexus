package vn.t3nexus.oauth2.domain.login_session;

/**
 * Bản băm của mã thiết bị (cookie {@code DEVICE_KEY}). Mã thô không rời Xác thực: phiên và event chỉ mang bản băm
 * (ADR-0005, {@code service.md} mục 2).
 */
public record DeviceKeyHash(String value) {

    public DeviceKeyHash {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("DeviceKeyHash value is required");
    }
}
