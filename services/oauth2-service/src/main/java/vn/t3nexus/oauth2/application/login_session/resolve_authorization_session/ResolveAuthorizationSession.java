package vn.t3nexus.oauth2.application.login_session.resolve_authorization_session;

import lombok.extern.slf4j.Slf4j;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.oauth2.domain.login_session.ClientId;
import vn.t3nexus.oauth2.domain.login_session.ClientSession;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionException;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Cửa kiểm hạn ở {@code /oauth2/authorize}, lúc cấp mã ủy quyền (ADR-0003 mục 2, 3): xác nhận phiên của ta còn hiệu lực
 * dù cookie của Spring còn sống, cập nhật {@code idp_session_id} khi Spring đã đổi mã session, và chốt {@code sid} cho lần
 * cấp token sắp tới. Kết quả được ghi vào thuộc tính của bản ghi ủy quyền; bộ tùy biến JWT đặt {@code sid} và cắt hạn từ đó.
 *
 * <p><b>Chốt {@code sid}:</b> JWT được ký trước khi điểm móc lúc token được cấp chạy, nên {@code sid} phải biết từ đây. Phiên
 * ứng dụng đã có cho client này (xác thực im lặng) thì dùng lại mã đó; chưa có thì sinh trước một mã mới (UUID v7), điểm móc
 * dùng mã này khi cấp phiên ứng dụng và so lại (ADR-0003 mục 3, 9).
 *
 * <p><b>Từ chối thay vì cho qua:</b> phiên không còn (đã đăng xuất, đã dọn) hoặc quá hạn thì {@code SESSION_EXPIRED}; tài khoản
 * không còn hoạt động (bị khóa giữa chừng) thì {@code ACCOUNT_NOT_ACTIVE}; lỗi cơ sở dữ liệu cứ lan ra (không nuốt), để không
 * cấp mã khi không đọc được phiên.
 *
 * <p>Chưa gắn {@code @Service}: gắn khi nối lớp bọc {@code OAuth2AuthorizationService} (bước 9a).
 */
@Slf4j
public class ResolveAuthorizationSession
        implements CommandHandler<ResolveAuthorizationSession.Command, ResolveAuthorizationSession.Result> {

    /**
     * @param loginSessionId       mã phiên của ta, lấy từ thuộc tính session Spring (ghi lúc mở phiên)
     * @param currentIdpSessionId  mã session Spring hiện tại ({@code session.getId()})
     * @param clientId             client đang xin mã
     */
    public record Command(String loginSessionId, String currentIdpSessionId, String clientId) {}

    /**
     * @param sid        mã phiên ứng dụng cho lần cấp token sắp tới (đã có, hoặc sinh trước)
     * @param expiresAt  hạn phiên (bất biến), dùng để cắt hạn token
     * @param deviceKey  bản băm mã thiết bị của phiên, có thể {@code null}
     */
    public record Result(String loginSessionId, String sid, Instant expiresAt, Role role, String deviceKey) {}

    private final LoginSessionRepository loginSessionRepository;
    private final UserAccountRepository  userAccountRepository;
    private final IdGenerator            idGenerator;
    private final Clock                  clock;

    public ResolveAuthorizationSession(LoginSessionRepository loginSessionRepository,
                                       UserAccountRepository userAccountRepository, IdGenerator idGenerator, Clock clock) {
        this.loginSessionRepository = loginSessionRepository;
        this.userAccountRepository  = userAccountRepository;
        this.idGenerator            = idGenerator;
        this.clock                  = clock;
    }

    @Override
    public Result handle(Command command) {
        Instant now = clock.instant();

        LoginSession session = loginSessionRepository.findById(LoginSessionId.of(command.loginSessionId()))
                .orElseThrow(LoginSessionException::sessionExpired);
        if (!session.confirm(now)) throw LoginSessionException.sessionExpired();

        UserAccount account = userAccountRepository.findById(session.userAccountId())
                .orElseThrow(UserAccountException::notFound);
        if (!account.canLogin()) throw UserAccountException.accountNotActive();

        if (!session.idpSessionId().equals(command.currentIdpSessionId())) {
            // act as a guard prevent mismatch data when rotate session, not gonna happen in 99% case.
            session.updateIdpSessionId(command.currentIdpSessionId());
            loginSessionRepository.save(session);
        }

        ClientSessionId sid = existingClientSession(session, new ClientId(command.clientId()))
                .map(ClientSession::getId)
                .orElseGet(() -> ClientSessionId.of(idGenerator.generate()));

        return new Result(session.getId().getValueAsString(), sid.getValueAsString(), session.expiresAt(),
                account.getRole(), session.deviceKey() == null ? null : session.deviceKey().value());
    }

    private static Optional<ClientSession> existingClientSession(LoginSession session, ClientId clientId) {
        return session.clientSessions().stream().filter(c -> c.clientId().equals(clientId)).findFirst();
    }
}
