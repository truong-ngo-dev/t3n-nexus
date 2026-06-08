package vn.t3nexus.oauth2.application.user_credential.resolve_social_user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.oauth2.domain.user_credential.Role;
import vn.t3nexus.oauth2.domain.user_credential.UserCredential;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveSocialUser implements CommandHandler<ResolveSocialUser.Command, ResolveSocialUser.Result> {

    public record Command(String email, String fullName) {}

    public record Result(String userId, boolean locked, boolean newAccount, boolean mfaEnabled) {}

    private final UserCredentialRepository userCredentialRepository;
    private final ULIDGenerator            ulidGenerator;
    private final EventDispatcher          eventDispatcher;

    @Override
    @Transactional
    public Result handle(Command command) {
        return userCredentialRepository.findByEmail(command.email())
                .map(existing -> {
                    log.debug("[ResolveSocialUser] existing user: userId={}", existing.getId().getValue());
                    return new Result(existing.getId().getValue(), !existing.canLogin(), false, existing.isMfaEnabled());
                })
                .orElseGet(() -> {
                    UserId         userId     = UserId.of(ulidGenerator.generate());
                    UserCredential credential = UserCredential.registerWithOAuth(
                            userId, command.email(), Role.CUSTOMER, command.fullName());
                    userCredentialRepository.save(credential);
                    eventDispatcher.dispatchAll(credential.getDomainEvents());
                    log.info("[ResolveSocialUser] new OAuth account persisted: userId={}", userId.getValue());
                    return new Result(userId.getValue(), false, true, false);
                });
    }
}
