package vn.t3nexus.identity.application.login_activity.record_login_failure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.device.DeviceFingerprint;
import vn.t3nexus.identity.domain.login_activity.LoginActivity;
import vn.t3nexus.identity.domain.login_activity.LoginActivityId;
import vn.t3nexus.identity.domain.login_activity.LoginActivityRepository;
import vn.t3nexus.identity.domain.login_activity.LoginResult;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.lib.common.domain.vo.UserId;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordLoginFailure implements CommandHandler<RecordLoginFailure.Command, Void> {

    public record Command(
            String userId,
            String loginIdentifier,
            String result,
            String deviceHash,
            String acceptLanguage,
            String ipAddress,
            String userAgent,
            String provider
    ) {}

    private final LoginActivityRepository loginActivityRepository;
    private final ULIDGenerator           ulidGenerator;

    @Override
    @Transactional
    public Void handle(Command command) {
        DeviceFingerprint fingerprint = DeviceFingerprint.of(
                command.deviceHash(), command.userAgent(), command.acceptLanguage()
        );
        LoginActivity activity = LoginActivity.recordFailure(
                LoginActivityId.of(ulidGenerator.generate()),
                UserId.of(command.userId()),
                command.loginIdentifier(),
                LoginResult.valueOf(command.result()),
                fingerprint.getCompositeHash(),
                command.ipAddress(),
                command.userAgent(),
                LoginActivity.LoginProvider.valueOf(command.provider())
        );
        loginActivityRepository.save(activity);

        log.info("[RecordLoginFailure] userId={}, result={}", command.userId(), command.result());

        return null;
    }
}
