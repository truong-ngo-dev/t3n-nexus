package vn.t3nexus.oauth2.application.session.revoke_session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.oauth2.domain.session.OAuthSessionId;
import vn.t3nexus.oauth2.domain.session.OAuthSessionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevokeSession implements CommandHandler<RevokeSession.Command, Void> {

    private final OAuthSessionRepository oAuthSessionRepository;
    private final EventDispatcher        eventDispatcher;

    public record Command(String ossId) {}

    @Override
    @Transactional
    public Void handle(Command command) {
        oAuthSessionRepository.findById(new OAuthSessionId(command.ossId()))
                .ifPresentOrElse(
                        session -> {
                            session.revoke();
                            oAuthSessionRepository.save(session);
                            eventDispatcher.dispatchAll(session.getDomainEvents());
                            log.info("[RevokeSession] OAuthSession revoked: ossId={}", command.ossId());
                        },
                        () -> log.warn("[RevokeSession] OAuthSession not found for ossId={}", command.ossId())
                );
        return null;
    }
}
