package vn.t3nexus.oauth2.application.user_account.request_password_setup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.oauth2.domain.user_account.PasswordSetupResentEvent;
import vn.t3nexus.oauth2.domain.user_account.PasswordSetupTokenService;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;

@Service
@RequiredArgsConstructor
public class RequestPasswordSetup implements CommandHandler<RequestPasswordSetup.Command, Void> {

    public record Command(String userId) {}

    private final UserAccountRepository userAccountRepository;
    private final PasswordSetupTokenService passwordSetupTokenService;
    private final OutboxEventStore          outboxEventStore;

    @Override
    @Transactional
    public Void handle(Command command) {
        UserAccount credential = userAccountRepository.findById(UserAccountId.of(command.userId()))
                .orElseThrow(UserAccountException::notFound);

        if (credential.hasPassword()) {
            throw UserAccountException.passwordAlreadySet();
        }

        String setupToken = passwordSetupTokenService.generateForResend(command.userId());

        outboxEventStore.store(new PasswordSetupResentEvent(
                command.userId(),
                credential.getEmail().value(),
                setupToken
        ));
        return null;
    }
}
