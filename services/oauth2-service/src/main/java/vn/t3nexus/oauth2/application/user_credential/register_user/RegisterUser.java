package vn.t3nexus.oauth2.application.user_credential.register_user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.ratelimiter.RateLimit;
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

    public record Command(String email, String rawPassword, String role, String fullName, String clientIp) {}
    public record Result(String userId) {}

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordService          passwordService;
    private final ULIDGenerator            ulidGenerator;
    private final EventDispatcher          eventDispatcher;

    @Override
    @Transactional
    @RateLimit(key = "'register:' + #command.clientIp()", limit = 5, windowSeconds = 3600,
            message = "Too many registration attempts, please try again later")
    public Result handle(Command command) {
        if (userCredentialRepository.findByEmail(command.email()).isPresent()) {
            throw UserCredentialException.emailAlreadyExists();
        }

        UserId         userId     = UserId.of(ulidGenerator.generate());
        Role           role       = Role.valueOf(command.role());
        UserCredential credential = UserCredential.registerWithCredential(
                userId,
                command.email(),
                CredentialPassword.ofHashed(passwordService.hash(command.rawPassword())),
                role,
                command.fullName());

        // findByEmail ở trên chỉ chặn happy path — dưới concurrent request cùng email
        // (double-click, 2 tab, retry sau timeout), cả 2 có thể qua check trước khi request nào
        // insert xong. uq_user_credentials_email tại DB là chốt chặn cuối; bắt riêng ở đây để
        // trả 409 domain-meaningful thay vì rơi vào GlobalExceptionHandler generic → 500.
        try {
            userCredentialRepository.save(credential);
        } catch (DataIntegrityViolationException e) {
            throw UserCredentialException.emailAlreadyExists();
        }
        eventDispatcher.dispatchAll(credential.getDomainEvents());

        log.info("[RegisterUser] registered: userId={}, traceId={}", userId.getValue(), MDC.get("traceId"));

        return new Result(userId.getValue());
    }
}
