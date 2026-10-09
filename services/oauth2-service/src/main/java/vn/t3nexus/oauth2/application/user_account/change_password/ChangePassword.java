package vn.t3nexus.oauth2.application.user_account.change_password;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.PasswordHasher;
import vn.t3nexus.oauth2.domain.user_account.RawPassword;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;

@Service
@RequiredArgsConstructor
public class ChangePassword implements CommandHandler<ChangePassword.Command, Void> {

    public record Command(String userId, String currentPassword, String newPassword) {}

    private final UserAccountRepository credentialRepository;
    private final PasswordHasher          passwordHasher;

    @Override
    @Transactional
    public Void handle(Command command) {
        UserAccount credential = credentialRepository.findById(UserAccountId.of(command.userId()))
                .orElseThrow(UserAccountException::notFound);

        if (!credential.verifyPassword(command.currentPassword(), passwordHasher)) {
            throw UserAccountException.wrongPassword();
        }

        PasswordHash newPassword = passwordHasher.hash(RawPassword.of(command.newPassword()));
        credential.changePassword(newPassword);
        credentialRepository.save(credential);
        return null;
    }
}
