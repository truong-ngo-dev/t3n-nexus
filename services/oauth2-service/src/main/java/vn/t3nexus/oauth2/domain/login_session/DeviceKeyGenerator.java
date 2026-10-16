package vn.t3nexus.oauth2.domain.login_session;

/** Cổng sinh mã thiết bị: chuỗi ngẫu nhiên 256 bit, khác mã định danh thực thể (ADR-0005). */
public interface DeviceKeyGenerator {

    /** Sinh mã thô để đặt vào cookie. */
    String generate();
}
