package vn.t3nexus.oauth2.domain.login_session;

/** Bộ băm mã thiết bị (SHA-256, tất định để nhóm theo thiết bị được). */
public interface DeviceKeyHasher {

    DeviceKeyHash hash(String rawDeviceKey);
}
