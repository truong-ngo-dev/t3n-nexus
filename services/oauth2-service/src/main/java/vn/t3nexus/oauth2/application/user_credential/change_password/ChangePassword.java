package vn.t3nexus.oauth2.application.user_credential.change_password;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.oauth2.domain.user_credential.CredentialPassword;
import vn.t3nexus.oauth2.domain.user_credential.PasswordService;
import vn.t3nexus.oauth2.domain.user_credential.UserCredential;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialException;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;

@Service
@RequiredArgsConstructor
public class ChangePassword implements CommandHandler<ChangePassword.Command, Void> {

    public record Command(String userId, String currentPassword, String newPassword) {}

    private final UserCredentialRepository credentialRepository;
    private final PasswordService          passwordService;

    @Override
    @Transactional
    public Void handle(Command command) {
        UserCredential credential = credentialRepository.findById(UserId.of(command.userId()))
                .orElseThrow(UserCredentialException::notFound);

        if (!credential.verifyPassword(command.currentPassword(), passwordService)) {
            throw UserCredentialException.invalidPassword();
        }

        CredentialPassword newPassword = CredentialPassword.ofHashed(
                passwordService.hash(command.newPassword())
        );
        credential.changePassword(newPassword);
        credentialRepository.save(credential);
        return null;
    }
}
