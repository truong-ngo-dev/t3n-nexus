package vn.t3nexus.oauth2.infrastructure.adapter.service;

import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Băm mã thiết bị bằng SHA-256, ra 64 ký tự hex (cột {@code device_key VARCHAR(64)}). Tất định để nhóm được theo thiết bị. Mã
 * thiết bị là 256 bit ngẫu nhiên nên không cần băm chậm hay muối (ADR-0005); chỉ bản băm được lưu và đi trong event.
 */
@Component
public class Sha256DeviceKeyHasher implements DeviceKeyHasher {

    @Override
    public DeviceKeyHash hash(String rawDeviceKey) {
        if (rawDeviceKey == null || rawDeviceKey.isBlank()) throw new IllegalArgumentException("rawDeviceKey is required");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawDeviceKey.getBytes(StandardCharsets.UTF_8));
            return new DeviceKeyHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
