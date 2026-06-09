package vn.t3nexus.identity.infrastructure.adapter.repository.device;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.device.Device;
import vn.t3nexus.identity.domain.device.DeviceId;
import vn.t3nexus.identity.domain.device.DeviceRepository;
import vn.t3nexus.identity.infrastructure.persistence.device.DeviceJpaRepository;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DeviceRepositoryAdapter implements DeviceRepository {

    private final DeviceJpaRepository jpaRepository;
    private final DeviceMapper        deviceMapper;

    @Override
    public Optional<Device> findById(DeviceId id) {
        return jpaRepository.findById(id.getValueAsString())
                .map(deviceMapper::toDomain);
    }

    @Override
    public List<Device> findAllByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.getValueAsString())
                .stream()
                .map(deviceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Device> findActiveByUserId(UserId userId) {
        return jpaRepository.findByUserIdAndStatus(userId.getValueAsString(), "ACTIVE")
                .stream()
                .map(deviceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Device> findByUserIdAndCompositeHash(UserId userId, String compositeHash) {
        return jpaRepository.findByUserIdAndCompositeHash(userId.getValueAsString(), compositeHash)
                .map(deviceMapper::toDomain);
    }

    @Override
    public void save(Device device) {
        jpaRepository.upsert(
                device.getId().getValueAsString(),
                device.getUserId().getValueAsString(),
                device.getFingerprint().getDeviceHash(),
                device.getFingerprint().getUserAgent(),
                device.getFingerprint().getAcceptLanguage(),
                device.getFingerprint().getCompositeHash(),
                device.getName().getValue(),
                device.getName().getType().name(),
                device.isTrusted(),
                device.getStatus().name(),
                device.getRegisteredAt(),
                device.getLastSeenAt(),
                device.getLastIpAddress(),
                device.getLastHistoryId()
        );
    }

    @Override
    public void delete(DeviceId id) {
        jpaRepository.deleteById(id.getValueAsString());
    }
}
