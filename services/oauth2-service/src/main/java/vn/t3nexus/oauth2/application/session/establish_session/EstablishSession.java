package vn.t3nexus.oauth2.application.session.establish_session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.oauth2.application.session.issue_session.IssueSession;
import vn.t3nexus.oauth2.domain.session.OAuthSession;
import vn.t3nexus.oauth2.domain.session.OAuthSessionId;
import vn.t3nexus.oauth2.domain.session.OAuthSessionRepository;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EstablishSession implements CommandHandler<EstablishSession.Command, EstablishSession.Result> {

    public record Command(
            String oauthSessionId,
            String userId,
            String idpSessionId,
            String authorizationId,
            String ipAddress,
            String registeredClientId,
            String loginIdentifier,
            String deviceHash,
            String userAgent,
            String acceptLanguage,
            String provider
    ) {}

    public record Result(String sessionId) {}

    private final OAuthSessionRepository oAuthSessionRepository;
    private final IssueSession           issueSession;

    @Autowired @Lazy
    private OAuth2AuthorizationService oauth2AuthorizationService;

    @Override
    @Transactional
    public Result handle(Command command) {
        if (StringUtils.hasText(command.oauthSessionId())) {
            Optional<OAuthSession> existing = oAuthSessionRepository.findById(
                    new OAuthSessionId(command.oauthSessionId()));
            if (existing.isPresent()) {
                OAuthSession session = existing.get();
                String oldAuthorizationId = session.getAuthorizationId();
                if (oldAuthorizationId.equals(command.authorizationId())) {
                    // refresh token: same OAuth2Authorization ID — nothing to do
                    log.debug("[EstablishSession] refresh no-op — ossId={}", command.oauthSessionId());
                    return new Result(session.getId().getValueAsString());
                }
                // silent SSO: new OAuth2Authorization (new code flow) — rotate
                OAuth2Authorization oldAuth = oauth2AuthorizationService.findById(oldAuthorizationId);
                if (oldAuth != null) oauth2AuthorizationService.remove(oldAuth);
                session.onTokenRotated(command.authorizationId());
                oAuthSessionRepository.save(session);
                log.debug("[EstablishSession] authorization rotated — ossId={}", command.oauthSessionId());
                return new Result(session.getId().getValueAsString());
            }
        }

        IssueSession.Result issued = issueSession.handle(new IssueSession.Command(
                command.oauthSessionId(), command.userId(), command.idpSessionId(),
                command.authorizationId(), command.ipAddress(), command.registeredClientId(),
                command.loginIdentifier(), command.deviceHash(), command.userAgent(),
                command.acceptLanguage(), command.provider()
        ));
        return new Result(issued.sessionId());
    }
}
