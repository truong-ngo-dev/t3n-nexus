package vn.t3nexus.oauth2.application.session.revoke_session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.utils.lang.Assert;
import vn.t3nexus.oauth2.domain.session.OAuthSession;
import vn.t3nexus.oauth2.domain.session.OAuthSessionException;
import vn.t3nexus.oauth2.domain.session.OAuthSessionId;
import vn.t3nexus.oauth2.domain.session.OAuthSessionRepository;
import vn.t3nexus.oauth2.domain.session.SessionRevokedEvent;
import vn.t3nexus.oauth2.infrastructure.adapter.http.WebGatewayRevocationClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevokeSession implements CommandHandler<RevokeSession.Command, Void> {

    private final OAuthSessionRepository        oAuthSessionRepository;
    private final OAuth2AuthorizationService    oauth2AuthorizationService;
    private final EventDispatcher               eventDispatcher;
    private final WebGatewayRevocationClient    webGatewayRevocationClient;
    private final SessionRepository<? extends Session> sessionRepository;

    @Override
    @Transactional
    public Void handle(Command command) {
        oAuthSessionRepository.findByAuthorizationId(command.currentAuthorizationId())
                .filter(s -> s.getId().getValueAsString().equals(command.ossId()))
                .ifPresent(s -> { throw OAuthSessionException.cannotRevokeCurrent(); });

        OAuthSession session = oAuthSessionRepository.findById(new OAuthSessionId(command.ossId()))
                .orElseThrow(OAuthSessionException::notFound);

        if (!session.belongsTo(UserId.of(command.userId()))) {
            throw OAuthSessionException.notBelongToUser();
        }

        String authorizationId = session.getAuthorizationId();
        String ossId           = session.getId().getValueAsString();
        String userId          = session.getUserId().getValueAsString();
        String idpSessionId    = session.getIdpSessionId();

        oAuthSessionRepository.delete(session.getId());

        OAuth2Authorization authorization = oauth2AuthorizationService.findById(authorizationId);
        if (authorization != null) oauth2AuthorizationService.remove(authorization);

        // Khác với oidcLogoutHandler (dùng SecurityContextLogoutHandler trên chính request đang
        // chạy) — ở đây revoke session của 1 request KHÁC (VD user tự đăng xuất thiết bị khác từ
        // "Quản lý thiết bị"), không có HttpServletRequest nào đại diện cho nó để dùng
        // SecurityContextLogoutHandler. Phải xoá thẳng Spring Session theo idpSessionId — thiếu
        // bước này thì IDP session vẫn sống, browser đó silent-SSO lại ngay lần request kế tiếp,
        // revoke coi như vô tác dụng.
        sessionRepository.deleteById(idpSessionId);
        log.info("[RevokeSession] terminated: ossId={}, idpSessionId={}, userId={}", ossId, idpSessionId, userId);

        eventDispatcher.dispatch(new SessionRevokedEvent(idpSessionId, List.of(ossId), userId));

        webGatewayRevocationClient.revoke(ossId);

        return null;
    }

    public record Command(String userId, String ossId, String currentAuthorizationId) {
        public Command {
            Assert.notNull(userId,                 "userId must not be null");
            Assert.notNull(ossId,                  "ossId must not be null");
            Assert.notNull(currentAuthorizationId, "currentAuthorizationId must not be null");
        }
    }
}
