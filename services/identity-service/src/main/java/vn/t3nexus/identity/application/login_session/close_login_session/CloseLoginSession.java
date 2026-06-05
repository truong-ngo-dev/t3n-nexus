package vn.t3nexus.identity.application.login_session.close_login_session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.identity.domain.login_activity.LoginActivityRepository;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloseLoginSession implements CommandHandler<CloseLoginSession.Command, Void> {

    private final LoginActivityRepository loginActivityRepository;

    public record Command(String ossId) {}

    @Override
    @Transactional
    public Void handle(Command command) {
        loginActivityRepository.endBySessionId(command.ossId(), Instant.now());
        log.info("[CloseLoginSession] ended login activity for ossId={}", command.ossId());
        return null;
    }
}
