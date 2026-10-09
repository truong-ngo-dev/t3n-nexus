package vn.t3nexus.oauth2.application.user_account.resolve_social_user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.PasswordSetupTokenService;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveSocialUser implements CommandHandler<ResolveSocialUser.Command, ResolveSocialUser.Result> {

    public record Command(String email, String fullName) {}

    public record Result(String userId, boolean locked, boolean newAccount, boolean mfaEnabled, String role) {}

    private final UserAccountRepository userAccountRepository;
    private final IdGenerator               idGenerator;
    private final EventDispatcher          eventDispatcher;
    private final PasswordSetupTokenService passwordSetupTokenService;

    @Override
    @Transactional
    public Result handle(Command command) {
        return userAccountRepository.findByEmail(command.email())
                .map(existing -> {
                    log.debug("[ResolveSocialUser] existing user: userId={}", existing.getId().getValueAsString());
                    return new Result(existing.getId().getValueAsString(), !existing.canLogin(), false,
                            existing.isMfaEnabled(), existing.getRole().name());
                })
                .orElseGet(() -> {
                    UserAccountId userId = UserAccountId.of(idGenerator.generate());
                    String setupToken = passwordSetupTokenService.generate(userId.getValueAsString());
                    UserAccount credential = UserAccount.registerWithOAuth(
                            userId, Email.of(command.email()), Role.USER, command.fullName(), setupToken);
                    userAccountRepository.save(credential);
                    eventDispatcher.dispatchAll(credential.getDomainEvents());
                    log.info("[ResolveSocialUser] new OAuth account persisted: userId={}", userId);
                    return new Result(userId.getValueAsString(), false, true, false, Role.USER.name());
                });
    }
}
