package vn.t3nexus.identity.application.device.trust_otp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.device.Device;
import vn.t3nexus.identity.domain.device.DeviceException;
import vn.t3nexus.identity.domain.device.DeviceId;
import vn.t3nexus.identity.domain.device.DeviceRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.utils.lang.Assert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TrustDevice implements CommandHandler<TrustDevice.Command, Void> {

    private static final int MAX_ATTEMPTS = 3;

    private final DeviceRepository    deviceRepository;
    private final DeviceTrustOtpStore otpStore;
    private final EventDispatcher     eventDispatcher;

    @Override
    @Transactional
    public Void handle(Command command) {
        String storedHash = otpStore.findHash(command.userId(), command.deviceId())
                .orElseThrow(DeviceException::otpExpired);

        if (!storedHash.equals(hashOtp(command.otp()))) {
            int attempts = otpStore.incrementAttempts(command.userId(), command.deviceId());
            if (attempts >= MAX_ATTEMPTS) {
                otpStore.delete(command.userId(), command.deviceId());
            }
            throw DeviceException.otpInvalid();
        }

        otpStore.delete(command.userId(), command.deviceId());

        Device device = deviceRepository.findById(DeviceId.of(command.deviceId()))
                .orElseThrow(DeviceException::notFound);

        if (!device.belongsTo(UserId.of(command.userId()))) {
            throw DeviceException.notBelongToUser();
        }

        device.trust();
        deviceRepository.save(device);
        eventDispatcher.dispatchAll(device.getDomainEvents());
        return null;
    }

    private static String hashOtp(String otp) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record Command(String userId, String deviceId, String otp) {
        public Command {
            Assert.notNull(userId,   "userId must not be null");
            Assert.notNull(deviceId, "deviceId must not be null");
            Assert.notNull(otp,      "otp must not be null");
        }
    }
}
