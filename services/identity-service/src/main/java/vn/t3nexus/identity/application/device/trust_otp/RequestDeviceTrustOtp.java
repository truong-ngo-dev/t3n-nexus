package vn.t3nexus.identity.application.device.trust_otp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.device.Device;
import vn.t3nexus.identity.domain.device.DeviceException;
import vn.t3nexus.identity.domain.device.DeviceId;
import vn.t3nexus.identity.domain.device.DeviceOtpRequested;
import vn.t3nexus.identity.domain.device.DeviceRepository;
import vn.t3nexus.identity.domain.user_account.UserAccount;
import vn.t3nexus.identity.domain.user_account.UserAccountException;
import vn.t3nexus.identity.domain.user_account.UserAccountRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.ratelimiter.RateLimiter;
import vn.t3nexus.lib.utils.lang.Assert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RequestDeviceTrustOtp implements CommandHandler<RequestDeviceTrustOtp.Command, Void> {

    private static final int      RATE_LIMIT  = 3;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(5);

    private final DeviceRepository      deviceRepository;
    private final UserAccountRepository userAccountRepository;
    private final DeviceTrustOtpStore   otpStore;
    private final EventDispatcher       eventDispatcher;
    private final RateLimiter           rateLimiter;

    @Override
    @Transactional
    public Void handle(Command command) {
        if (!rateLimiter.tryAcquire("trust_device_otp_rate:" + command.userId(), RATE_LIMIT, RATE_WINDOW)) {
            throw DeviceException.rateLimitExceeded();
        }

        Device device = deviceRepository.findById(DeviceId.of(command.deviceId()))
                .orElseThrow(DeviceException::notFound);

        if (!device.belongsTo(UserId.of(command.userId()))) {
            throw DeviceException.notBelongToUser();
        }

        if (device.isTrusted()) {
            throw DeviceException.alreadyTrusted();
        }

        UserAccount user = userAccountRepository.findById(UserId.of(command.userId()))
                .orElseThrow(UserAccountException::notFound);

        String otp     = generateOtp();
        String otpHash = hashOtp(otp);

        otpStore.save(command.userId(), command.deviceId(), otpHash);

        eventDispatcher.dispatch(new DeviceOtpRequested(
                command.userId(), command.deviceId(),
                user.getEmail(), user.getFullName(), otp
        ));
        return null;
    }

    private static String generateOtp() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
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

    public record Command(String userId, String deviceId) {
        public Command {
            Assert.notNull(userId,   "userId must not be null");
            Assert.notNull(deviceId, "deviceId must not be null");
        }
    }
}
