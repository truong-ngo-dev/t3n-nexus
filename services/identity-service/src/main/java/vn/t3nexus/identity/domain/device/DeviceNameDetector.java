package vn.t3nexus.identity.domain.device;

public interface DeviceNameDetector {
    DeviceName detect(String userAgent);
}
