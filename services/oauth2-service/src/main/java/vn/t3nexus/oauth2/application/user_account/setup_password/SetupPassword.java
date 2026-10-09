package vn.t3nexus.oauth2.application.user_account.setup_password;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.PasswordHasher;
import vn.t3nexus.oauth2.domain.user_account.RawPassword;
import vn.t3nexus.oauth2.domain.user_account.PasswordSetupTokenService;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;

@Service
@RequiredArgsConstructor
public class SetupPassword implements CommandHandler<SetupPassword.Command, Void> {

    public record Command(String setupToken, String newPassword) {}

    private final UserAccountRepository  credentialRepository;
    private final PasswordSetupTokenService passwordSetupTokenService;
    private final PasswordHasher           passwordHasher;

    @Override
    @Transactional
    public Void handle(Command command) {
        String userId = passwordSetupTokenService.verify(command.setupToken());

        UserAccount credential = credentialRepository.findById(UserAccountId.of(userId))
                .orElseThrow(UserAccountException::notFound);

        if (credential.hasPassword()) {
            throw UserAccountException.passwordAlreadySet();
        }

        PasswordHash newPassword = passwordHasher.hash(RawPassword.of(command.newPassword()));
        credential.setInitialPassword(newPassword);
        credentialRepository.save(credential);
        return null;
    }
}
