package vn.t3nexus.identity.infrastructure.adapter.repository.device;

import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.device.Device;
import vn.t3nexus.identity.domain.device.DeviceFingerprint;
import vn.t3nexus.identity.domain.device.DeviceId;
import vn.t3nexus.identity.domain.device.DeviceName;
import vn.t3nexus.identity.domain.device.DeviceStatus;
import vn.t3nexus.identity.domain.device.DeviceType;
import vn.t3nexus.identity.infrastructure.persistence.device.DeviceJpaEntity;
import vn.t3nexus.lib.common.domain.vo.UserId;

@Component
public class DeviceMapper {

    public Device toDomain(DeviceJpaEntity entity) {
        DeviceFingerprint fingerprint = DeviceFingerprint.of(
                entity.getDeviceHash(),
                entity.getUserAgent(),
                entity.getAcceptLanguage()
        );
        DeviceName name = (entity.getDeviceName() != null && entity.getDeviceType() != null)
                ? DeviceName.of(entity.getDeviceName(), DeviceType.valueOf(entity.getDeviceType()))
                : DeviceName.unknown();

        return Device.reconstitute(
                DeviceId.of(entity.getId()),
                UserId.of(entity.getUserId()),
                fingerprint,
                name,
                entity.isTrusted(),
                DeviceStatus.valueOf(entity.getStatus()),
                entity.getRegisteredAt(),
                entity.getLastSeenAt(),
                entity.getLastIpAddress()
        );
    }
}
