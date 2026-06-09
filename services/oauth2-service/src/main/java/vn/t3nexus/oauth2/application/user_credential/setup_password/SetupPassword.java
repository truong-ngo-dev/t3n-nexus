package vn.t3nexus.oauth2.application.user_credential.setup_password;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.oauth2.domain.user_credential.CredentialPassword;
import vn.t3nexus.oauth2.domain.user_credential.PasswordService;
import vn.t3nexus.oauth2.domain.user_credential.PasswordSetupTokenService;
import vn.t3nexus.oauth2.domain.user_credential.UserCredential;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialException;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;

@Service
@RequiredArgsConstructor
public class SetupPassword implements CommandHandler<SetupPassword.Command, Void> {

    public record Command(String setupToken, String newPassword) {}

    private final UserCredentialRepository  credentialRepository;
    private final PasswordSetupTokenService passwordSetupTokenService;
    private final PasswordService           passwordService;

    @Override
    @Transactional
    public Void handle(Command command) {
        String userId = passwordSetupTokenService.verify(command.setupToken());

        UserCredential credential = credentialRepository.findById(UserId.of(userId))
                .orElseThrow(UserCredentialException::notFound);

        if (credential.hasPassword()) {
            throw UserCredentialException.passwordAlreadySet();
        }

        CredentialPassword newPassword = CredentialPassword.ofHashed(
                passwordService.hash(command.newPassword())
        );
        credential.setInitialPassword(newPassword);
        credentialRepository.save(credential);
        return null;
    }
}
