package vn.t3nexus.oauth2.application.login_session.open_login_session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.observability.logging.LogContext;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHasher;
import vn.t3nexus.oauth2.domain.login_session.LoginContext;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;

import java.time.Clock;
import java.time.Instant;

/**
 * Mở phiên người dùng, ở điểm cuối chuỗi xác thực (ADR-0003 mục 6): chạy trong bộ xử lý thành công của Spring, sau mọi lớp
 * xác thực và <b>sau</b> bước chống chiếm phiên của Spring, nên {@code idpSessionId} đã là giá trị cuối. Đọc lại tài khoản
 * theo mã (một lần đọc khóa chính) thay vì tin dữ liệu đã nằm trong phiên HTTP, để tài khoản bị khóa giữa chừng không mở được
 * phiên. Hạn phiên tính theo vai (T1). Chỉ tạo phiên người dùng; phiên ứng dụng cấp lúc đổi mã lấy token (ADR-0003 mục 5).
 *
 * <p>Không có {@code @Transactional}: ghi một lần qua kho, tự bảo đảm giao dịch.
 */
@Slf4j
@Service
public class OpenLoginSession implements CommandHandler<OpenLoginSession.Command, OpenLoginSession.Result> {

    /**
     * @param rawDeviceKey giá trị cookie mã thiết bị, có thể {@code null}; chỉ băm rồi dùng
     * @param idpSessionId mã session Spring hiện tại (đã qua bước chống chiếm phiên); chỉ lưu ở phiên, không ra event
     */
    public record Command(String userAccountId, String rawDeviceKey, String ipAddress, String userAgent,
                          String idpSessionId) {}

    public record Result(String loginSessionId, String userAccountId, Role role, Instant expiresAt) {}

    private final UserAccountRepository  userAccountRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final DeviceKeyHasher        deviceKeyHasher;
    private final SessionLifetimePolicy  lifetimePolicy;
    private final IdGenerator            idGenerator;
    private final Clock                  clock;

    public OpenLoginSession(UserAccountRepository userAccountRepository, LoginSessionRepository loginSessionRepository,
                            DeviceKeyHasher deviceKeyHasher, SessionLifetimePolicy lifetimePolicy,
                            IdGenerator idGenerator, Clock clock) {
        this.userAccountRepository  = userAccountRepository;
        this.loginSessionRepository = loginSessionRepository;
        this.deviceKeyHasher        = deviceKeyHasher;
        this.lifetimePolicy         = lifetimePolicy;
        this.idGenerator            = idGenerator;
        this.clock                  = clock;
    }

    @Override
    public Result handle(Command command) {
        Instant now = clock.instant();

        UserAccount account = userAccountRepository.findById(UserAccountId.of(command.userAccountId()))
                .orElseThrow(UserAccountException::notFound);
        if (!account.canLogin()) throw UserAccountException.accountNotActive();

        DeviceKeyHash deviceKey = (command.rawDeviceKey() == null || command.rawDeviceKey().isBlank())
                ? null : deviceKeyHasher.hash(command.rawDeviceKey());
        LoginSessionId sessionId = LoginSessionId.of(idGenerator.generate());
        Instant expiresAt = lifetimePolicy.sessionExpiresAt(account.getRole(), now);

        LoginSession session = LoginSession.open(sessionId, account.getId(), deviceKey, command.idpSessionId(),
                new LoginContext(command.ipAddress(), command.userAgent()), expiresAt, now);
        loginSessionRepository.save(session);

        LogContext.user(account.getId().getValueAsString());
        log.info("login session opened loginSessionId={}", sessionId);
        return new Result(sessionId.getValueAsString(), account.getId().getValueAsString(), account.getRole(), expiresAt);
    }
}
