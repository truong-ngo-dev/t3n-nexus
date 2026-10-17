package vn.t3nexus.oauth2.application.login_session.end_login_session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.observability.logging.LogContext;
import vn.t3nexus.oauth2.domain.login_session.ClientSession;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.EndReason;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tự đăng xuất: kết thúc phiên người dùng của lần đăng nhập hiện tại và mọi phiên ứng dụng bên dưới nó (CAP-ATH-04, INV-ATH-05,
 * 08). Chạy ở bộ xử lý đăng xuất của Spring, sau khi Spring đã kiểm {@code id_token_hint}, client và trang đích.
 *
 * <p><b>Khóa hàng:</b> phiên được nạp bằng {@code findActiveByIdpSessionIdForUpdate} nên chạy nối tiếp với cấp phiên ứng dụng và làm mới trên cùng phiên (R7); phải gọi trong giao dịch.
 *
 * <p><b>Luôn thành công.</b> Không có cookie, không tìm ra phiên còn sống theo cookie (chưa đăng nhập, đã hết hạn, đã kết thúc),
 * hoặc hint không thuộc phiên đó thì kết quả rỗng, không ném lỗi và không đổi gì.
 *
 * <p><b>Ràng buộc hint với cookie:</b> phiên tìm theo cookie phải chứa phiên ứng dụng {@code hintSid} (claim {@code sid} của hint).
 * Hint của thiết bị khác hoặc của phiên khác thì không kết thúc phiên nào. Thiếu {@code hintSid} cũng là không kết thúc (đóng cửa).
 * Không có cookie thì cũng không kết thúc theo mỗi hint: hint không chứng minh được người gọi đang cầm phiên (chốt tạm,
 * {@code feature 03 flow.md} mục 4).
 *
 * <p><b>Chụp lại trước khi {@code end}:</b> {@link LoginSession#end} xóa danh sách phiên ứng dụng, nên mã phiên ứng dụng và mã bản
 * ghi ủy quyền được lấy ra trước. Bên gọi dùng kết quả để xóa các bản ghi ủy quyền (cùng giao dịch với việc xóa phiên ở đây) và báo
 * từng ứng dụng sau khi giao dịch xong; việc đó nằm ở hạ tầng, như điểm móc lúc cấp token ({@code RecordClientSessionIssued}).
 *
 * <p>Không có {@code @Transactional}: giao dịch do {@code LoginSessionLogout} (hạ tầng) mở, bao cả use case này và việc xóa
 * bản ghi ủy quyền.
 */
@Slf4j
@Service
public class EndLoginSession implements CommandHandler<EndLoginSession.Command, EndLoginSession.Result> {

    /**
     * @param idpSessionId mã session Spring của yêu cầu (cookie đăng nhập); {@code null} hoặc rỗng nếu không có
     * @param hintSid      claim {@code sid} (mã phiên ứng dụng) của {@code id_token_hint}
     */
    public record Command(String idpSessionId, String hintSid) {}

    /** Một phiên ứng dụng vừa kết thúc: đủ để xóa bản ghi ủy quyền và báo ứng dụng giữ mã. */
    public record EndedClientSession(String sid, String clientId, String authorizationId) {}

    /** @param loginSessionId mã phiên người dùng vừa kết thúc, {@code null} nếu không kết thúc gì */
    public record Result(String loginSessionId, List<EndedClientSession> endedClientSessions) {

        public static Result nothingEnded() {
            return new Result(null, List.of());
        }

        public boolean ended() {
            return loginSessionId != null;
        }
    }

    private final LoginSessionRepository loginSessionRepository;
    private final Clock                  clock;

    public EndLoginSession(LoginSessionRepository loginSessionRepository, Clock clock) {
        this.loginSessionRepository = loginSessionRepository;
        this.clock                  = clock;
    }

    @Override
    public Result handle(Command command) {
        Instant now = clock.instant();

        if (isBlank(command.idpSessionId()) || isBlank(command.hintSid())) return Result.nothingEnded();
        ClientSessionId hintSid = parse(command.hintSid());
        if (hintSid == null) return Result.nothingEnded();

        LoginSession session = loginSessionRepository.findActiveByIdpSessionIdForUpdate(command.idpSessionId(), now).orElse(null);
        if (session == null) return Result.nothingEnded();
        if (session.clientSessions().stream().noneMatch(c -> c.getId().equals(hintSid))) {
            log.info("logout hint does not belong to the session of the cookie: loginSessionId={}", session.getId());
            return Result.nothingEnded();
        }

        List<EndedClientSession> ended = session.clientSessions().stream().map(EndLoginSession::toEnded).toList();
        session.end(EndReason.SELF_LOGOUT, now);
        loginSessionRepository.delete(session.getId());

        LogContext.user(session.userAccountId().getValueAsString());
        log.info("login session ended loginSessionId={} clientSessions={}", session.getId(), ended.size());
        return new Result(session.getId().getValueAsString(), ended);
    }

    private static EndedClientSession toEnded(ClientSession c) {
        return new EndedClientSession(c.getId().getValueAsString(), c.clientId().value(), c.authorizationId());
    }

    private static ClientSessionId parse(String value) {
        try {
            return ClientSessionId.of(UUID.fromString(value));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
