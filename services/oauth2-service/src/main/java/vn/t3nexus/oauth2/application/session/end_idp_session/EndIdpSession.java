package vn.t3nexus.oauth2.application.session.end_idp_session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.oauth2.domain.session.OAuthSession;
import vn.t3nexus.oauth2.domain.session.OAuthSessionRepository;
import vn.t3nexus.oauth2.domain.session.SessionRevokedEvent;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EndIdpSession implements CommandHandler<EndIdpSession.Command, EndIdpSession.Result> {

    public record Command(String idpSessionId) {}

    public record Result(List<String> ossIds) {}

    private final OAuthSessionRepository     oAuthSessionRepository;
    private final OAuth2AuthorizationService oauth2AuthorizationService;
    private final EventDispatcher            eventDispatcher;

    @Transactional
    public Result handle(Command command) {
        List<OAuthSession> sessions = oAuthSessionRepository.findAllByIdpSessionId(command.idpSessionId());
        if (sessions.isEmpty()) {
            log.debug("[EndIdpSession] no OAuthSession for idpSessionId={}", command.idpSessionId());
            return new Result(List.of());
        }

        List<String> ossIds = new ArrayList<>();
        String userId = sessions.getFirst().getUserId().getValueAsString();

        for (OAuthSession session : sessions) {
            String ossId           = session.getId().getValueAsString();
            String authorizationId = session.getAuthorizationId();

            oAuthSessionRepository.delete(session.getId());

            OAuth2Authorization authorization = oauth2AuthorizationService.findById(authorizationId);
            if (authorization != null) oauth2AuthorizationService.remove(authorization);

            ossIds.add(ossId);
            log.info("[EndIdpSession] revoked ossId={}, idpSessionId={}", ossId, command.idpSessionId());
        }

        eventDispatcher.dispatchAll(List.of(new SessionRevokedEvent(command.idpSessionId(), ossIds, userId)));

        return new Result(ossIds);
    }
}
