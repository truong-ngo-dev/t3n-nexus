package vn.t3nexus.identity.application.device.untrust;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.device.Device;
import vn.t3nexus.identity.domain.device.DeviceException;
import vn.t3nexus.identity.domain.device.DeviceFingerprint;
import vn.t3nexus.identity.domain.device.DeviceId;
import vn.t3nexus.identity.domain.device.DeviceRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.utils.lang.Assert;

@Service
@RequiredArgsConstructor
public class UntrustDevice implements CommandHandler<UntrustDevice.Command, Void> {

    private final DeviceRepository deviceRepository;
    private final EventDispatcher  eventDispatcher;

    @Override
    @Transactional
    public Void handle(Command command) {
        UserId userId = UserId.of(command.userId());

        String compositeHash = DeviceFingerprint.of(
                command.deviceHash(), command.userAgent(), command.acceptLanguage()
        ).getCompositeHash();

        Device callerDevice = deviceRepository.findByUserIdAndCompositeHash(userId, compositeHash)
                .orElseThrow(DeviceException::notFound);

        if (!callerDevice.isTrusted()) {
            throw DeviceException.notTrusted();
        }

        Device target = deviceRepository.findById(DeviceId.of(command.targetDeviceId()))
                .orElseThrow(DeviceException::notFound);

        if (!target.belongsTo(userId)) {
            throw DeviceException.notBelongToUser();
        }

        target.unTrust();
        deviceRepository.save(target);
        eventDispatcher.dispatchAll(target.getDomainEvents());
        return null;
    }

    public record Command(
            String userId,
            String targetDeviceId,
            String deviceHash,
            String userAgent,
            String acceptLanguage
    ) {
        public Command {
            Assert.notNull(userId,         "userId must not be null");
            Assert.notNull(targetDeviceId, "targetDeviceId must not be null");
            Assert.notNull(deviceHash,     "deviceHash must not be null");
            Assert.notNull(userAgent,      "userAgent must not be null");
        }
    }
}
