package vn.t3nexus.identity.domain.device;

public enum DeviceStatus {

    /** Thiết bị đang hoạt động bình thường. */
    ACTIVE,

    /** Thiết bị bị revoke — không thể dùng để login cho đến khi đăng ký lại. */
    REVOKED
}
