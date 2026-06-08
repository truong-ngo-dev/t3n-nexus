package vn.t3nexus.oauth2.application.session.issue_session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.oauth2.domain.session.OAuthSession;
import vn.t3nexus.oauth2.domain.session.OAuthSessionId;
import vn.t3nexus.oauth2.domain.session.OAuthSessionRepository;
import vn.t3nexus.oauth2.domain.session.SessionIssuanceContext;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueSession implements CommandHandler<IssueSession.Command, IssueSession.Result> {

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
    private final ULIDGenerator          ulidGenerator;
    private final EventDispatcher        eventDispatcher;

    private OAuth2AuthorizationService oauth2AuthorizationService;

    @Autowired
    public void setOauth2AuthorizationService(@Lazy OAuth2AuthorizationService oauth2AuthorizationService) {
        this.oauth2AuthorizationService = oauth2AuthorizationService;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        // Phase 6 — SSO invariant: revoke + delete any existing ACTIVE session for same (idpSessionId, registeredClientId)
        if (StringUtils.hasText(command.idpSessionId()) && StringUtils.hasText(command.registeredClientId())) {
            oAuthSessionRepository.findActiveByIdpSessionAndClient(
                    command.idpSessionId(), command.registeredClientId())
                    .ifPresent(stale -> {
                        stale.revoke();
                        oAuthSessionRepository.delete(stale.getId());
                        OAuth2Authorization staleAuth = oauth2AuthorizationService.findById(stale.getAuthorizationId());
                        if (staleAuth != null) oauth2AuthorizationService.remove(staleAuth);
                        eventDispatcher.dispatchAll(stale.getDomainEvents());
                        log.info("[IssueSession] revoked stale SSO session: ossId={}", stale.getId().getValueAsString());
                    });
        }

        SessionIssuanceContext context = new SessionIssuanceContext(
                command.loginIdentifier(),
                command.deviceHash(),
                command.userAgent(),
                command.acceptLanguage(),
                command.provider()
        );

        OAuthSessionId sessionId = StringUtils.hasText(command.oauthSessionId())
                ? new OAuthSessionId(command.oauthSessionId())
                : new OAuthSessionId(ulidGenerator.generate());

        OAuthSession session = OAuthSession.issue(
                sessionId,
                UserId.of(command.userId()),
                command.idpSessionId(),
                command.authorizationId(),
                command.ipAddress(),
                command.registeredClientId(),
                context
        );

        oAuthSessionRepository.save(session);
        eventDispatcher.dispatchAll(session.getDomainEvents());

        log.info("[IssueSession] sessionId={}, userId={}, authorizationId={}, provider={}",
                session.getId().getValueAsString(),
                command.userId(),
                command.authorizationId(),
                command.provider());

        return new Result(session.getId().getValueAsString());
    }
}
