package vn.t3nexus.identity.application.login_session.record_login_session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.device.Device;
import vn.t3nexus.identity.domain.device.DeviceFingerprint;
import vn.t3nexus.identity.domain.device.DeviceId;
import vn.t3nexus.identity.domain.device.DeviceName;
import vn.t3nexus.identity.domain.device.DeviceNameDetector;
import vn.t3nexus.identity.domain.device.DeviceRepository;
import vn.t3nexus.identity.domain.login_activity.LoginActivity;
import vn.t3nexus.identity.domain.login_activity.LoginActivityId;
import vn.t3nexus.identity.domain.login_activity.LoginActivityRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.lib.common.domain.vo.UserId;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordLoginSession implements CommandHandler<RecordLoginSession.Command, Void> {

    public record Command(
            String oauthSessionId,
            String authorizationId,
            String idpSessionId,
            String userId,
            String loginIdentifier,
            String deviceHash,
            String userAgent,
            String acceptLanguage,
            String ipAddress,
            String provider
    ) {}

    private final DeviceRepository        deviceRepository;
    private final LoginActivityRepository loginActivityRepository;
    private final DeviceNameDetector      deviceNameDetector;
    private final ULIDGenerator           ulidGenerator;

    @Override
    @Transactional
    public Void handle(Command command) {
        UserId            userId      = UserId.of(command.userId());
        DeviceFingerprint fingerprint = DeviceFingerprint.of(
                command.deviceHash(), command.userAgent(), command.acceptLanguage()
        );

        Device device = resolveDevice(userId, fingerprint, command.ipAddress());

        // Insert LoginActivity TRƯỚC — ON CONFLICT (session_id) DO NOTHING tự dedup nếu
        // Kafka redeliver cùng SessionIssuedEvent. Chỉ trỏ Device.lastHistoryId vào activityId
        // này nếu row thật sự được insert — trỏ vào activityId bị skip sẽ tạo dangling reference
        // (Device.lastHistoryId point tới 1 LoginActivity không tồn tại trong DB).
        LoginActivityId activityId = LoginActivityId.of(ulidGenerator.generate());
        LoginActivity activity = LoginActivity.recordSuccess(
                activityId,
                userId,
                command.loginIdentifier(),
                fingerprint.getCompositeHash(),
                device.getId().getValueAsString(),
                command.oauthSessionId(),
                command.ipAddress(),
                command.userAgent(),
                LoginActivity.LoginProvider.valueOf(command.provider())
        );
        boolean inserted = loginActivityRepository.tryRecord(activity);

        if (inserted) {
            device.recordLoginHistory(activityId.getValueAsString());
        } else {
            log.debug("[RecordLoginSession] duplicate SessionIssuedEvent, oauthSessionId={} — " +
                    "LoginActivity đã tồn tại, skip cập nhật lastHistoryId", command.oauthSessionId());
        }
        deviceRepository.save(device);

        log.info("[RecordLoginSession] userId={}, deviceId={}, oauthSessionId={}, historyRecorded={}",
                command.userId(), device.getId().getValueAsString(), command.oauthSessionId(), inserted);

        return null;
    }

    private Device resolveDevice(UserId userId, DeviceFingerprint fingerprint, String ipAddress) {
        return deviceRepository
                .findByUserIdAndCompositeHash(userId, fingerprint.getCompositeHash())
                .map(existing -> {
                    existing.recordActivity(ipAddress);
                    return existing;
                })
                .orElseGet(() -> {
                    DeviceName name = deviceNameDetector.detect(fingerprint.getUserAgent());
                    return Device.register(DeviceId.of(ulidGenerator.generate()), userId, fingerprint, name, ipAddress);
                });
    }
}
