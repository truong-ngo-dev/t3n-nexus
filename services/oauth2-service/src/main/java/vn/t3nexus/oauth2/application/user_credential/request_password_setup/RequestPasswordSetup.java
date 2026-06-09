package vn.t3nexus.oauth2.application.user_credential.request_password_setup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.oauth2.domain.user_credential.PasswordSetupResentEvent;
import vn.t3nexus.oauth2.domain.user_credential.PasswordSetupTokenService;
import vn.t3nexus.oauth2.domain.user_credential.UserCredential;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialException;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;

@Service
@RequiredArgsConstructor
public class RequestPasswordSetup implements CommandHandler<RequestPasswordSetup.Command, Void> {

    public record Command(String userId) {}

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordSetupTokenService passwordSetupTokenService;
    private final OutboxEventStore          outboxEventStore;

    @Override
    @Transactional
    public Void handle(Command command) {
        UserCredential credential = userCredentialRepository.findById(UserId.of(command.userId()))
                .orElseThrow(UserCredentialException::notFound);

        if (credential.hasPassword()) {
            throw UserCredentialException.passwordAlreadySet();
        }

        String setupToken = passwordSetupTokenService.generateForResend(command.userId());

        outboxEventStore.store(new PasswordSetupResentEvent(
                command.userId(),
                credential.getEmail(),
                setupToken
        ));
        return null;
    }
}
