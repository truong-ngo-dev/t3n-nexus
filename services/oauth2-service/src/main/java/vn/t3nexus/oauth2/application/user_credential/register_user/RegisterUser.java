package vn.t3nexus.oauth2.application.user_credential.register_user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.oauth2.domain.user_credential.CredentialPassword;
import vn.t3nexus.oauth2.domain.user_credential.PasswordService;
import vn.t3nexus.oauth2.domain.user_credential.Role;
import vn.t3nexus.oauth2.domain.user_credential.UserCredential;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialException;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterUser implements CommandHandler<RegisterUser.Command, RegisterUser.Result> {

    public record Command(String email, String rawPassword, String role, String fullName, String registrationMethod) {}
    public record Result(String userId) {}

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordService          passwordService;
    private final ULIDGenerator            ulidGenerator;
    private final EventDispatcher          eventDispatcher;

    @Override
    @Transactional
    public Result handle(Command command) {
        if (userCredentialRepository.findByEmail(command.email()).isPresent()) {
            throw UserCredentialException.emailAlreadyExists();
        }

        UserId userId = UserId.of(ulidGenerator.generate());
        Role   role   = Role.valueOf(command.role());

        UserCredential credential = "CREDENTIAL".equals(command.registrationMethod())
                ? UserCredential.registerWithCredential(
                        userId,
                        command.email(),
                        CredentialPassword.ofHashed(passwordService.hash(command.rawPassword())),
                        role,
                        command.fullName())
                : UserCredential.registerWithOAuth(userId, command.email(), role, command.fullName());

        userCredentialRepository.save(credential);
        eventDispatcher.dispatchAll(credential.getDomainEvents());

        log.info("[RegisterUser] registered (method={}): userId={}, traceId={}",
                command.registrationMethod(), userId.getValue(), MDC.get("traceId"));

        return new Result(userId.getValue());
    }
}
