package vn.t3nexus.oauth2.application.login_session.record_client_session_issued;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.oauth2.domain.login_session.ClientId;
import vn.t3nexus.oauth2.domain.login_session.ClientSession;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.IssuedClientSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionException;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Ghi nhận một token vừa được cấp cho phiên ứng dụng: chạy ở điểm móc lúc token được cấp (lớp bọc
 * {@code OAuth2AuthorizationService.save()}, ADR-0003 mục 9), <b>cùng giao dịch</b> với bản ghi ủy quyền. Chọn hành vi của
 * aggregate theo tình huống:
 * <ul>
 *   <li>Phiên ứng dụng {@code sid} đã có và đã trỏ tới đúng bản ghi ủy quyền này: <b>làm mới</b> ({@code refreshClientSession}).</li>
 *   <li>Ngược lại (chưa có phiên ứng dụng, hoặc xác thực im lặng sang bản ghi ủy quyền mới): <b>cấp phiên ứng dụng</b>
 *       ({@code issueClientSession}, tra theo client, tạo mới hoặc dùng lại và đổi liên kết).</li>
 * </ul>
 *
 * <p><b>Đối chiếu {@code sid}:</b> {@code sid} trong token đã chốt từ lúc cấp mã (JWT ký trước điểm móc). Nếu aggregate trả mã
 * khác (đua nhau: một yêu cầu khác vừa tạo phiên ứng dụng cho client này) thì token đã phát ra mang {@code sid} sai và không
 * hòa giải được, nên thất bại để giao dịch quay lui. Vì vậy không có vòng thử lại khi vi phạm ràng buộc duy nhất: bên thua chỉ
 * có thể đăng nhập lại.
 *
 * <p><b>Khóa hàng:</b> phiên được nạp bằng {@code findByIdForUpdate} (trong giao dịch của điểm móc), nên một đăng xuất chạy đồng thời không thể xóa phiên rồi để bên này ghi lại và tạo lại phiên đã đăng xuất (R7); phiên đã bị xóa thì "hết hạn".
 *
 * <p>Kết quả báo mã {@code authorization_id} cũ (nếu có đổi liên kết) để bên gọi xóa bản ghi ủy quyền cũ trong cùng giao dịch.
 */
@Slf4j
@Service
public class RecordClientSessionIssued
        implements CommandHandler<RecordClientSessionIssued.Command, RecordClientSessionIssued.Result> {

    /**
     * @param sid      mã phiên ứng dụng đã nằm trong token (chốt lúc cấp mã)
     * @param tokenId  {@code jti} của access token, dùng làm mã event để bên nhận bỏ qua trùng
     */
    public record Command(String loginSessionId, String clientId, String sid, String authorizationId, String tokenId) {}

    public record Result(Optional<String> replacedAuthorizationId) {}

    private final LoginSessionRepository loginSessionRepository;
    private final Clock                  clock;

    public RecordClientSessionIssued(LoginSessionRepository loginSessionRepository, Clock clock) {
        this.loginSessionRepository = loginSessionRepository;
        this.clock                  = clock;
    }

    @Override
    public Result handle(Command command) {
        Instant now = clock.instant();
        ClientSessionId sid = ClientSessionId.of(command.sid());

        LoginSession session = loginSessionRepository.findByIdForUpdate(LoginSessionId.of(command.loginSessionId()))
                .orElseThrow(LoginSessionException::sessionExpired);

        if (alreadyLinked(session, sid, command.authorizationId())) {
            session.refreshClientSession(sid, command.tokenId(), now);
            return new Result(Optional.empty());
        }

        IssuedClientSession issued = session.issueClientSession(new ClientId(command.clientId()), sid,
                command.authorizationId(), command.tokenId(), now);
        if (!issued.sid().equals(sid)) {
            log.warn("token sid does not match the client session of the login session (race): loginSessionId={}",
                    command.loginSessionId());
            throw LoginSessionException.clientSessionMismatch();
        }
        loginSessionRepository.save(session);
        return new Result(issued.replacedAuthorizationId());
    }

    private static boolean alreadyLinked(LoginSession session, ClientSessionId sid, String authorizationId) {
        return session.clientSessions().stream()
                .filter(c -> c.getId().equals(sid))
                .map(ClientSession::authorizationId)
                .anyMatch(authorizationId::equals);
    }
}
