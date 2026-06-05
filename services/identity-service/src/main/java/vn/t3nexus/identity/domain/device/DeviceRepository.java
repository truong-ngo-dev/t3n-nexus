package vn.t3nexus.identity.domain.device;

import vn.t3nexus.lib.common.domain.service.Repository;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends Repository<Device, DeviceId> {

    List<Device> findActiveByUserId(UserId userId);

    List<Device> findAllByUserId(UserId userId);

    Optional<Device> findByUserIdAndCompositeHash(UserId userId, String compositeHash);
}
