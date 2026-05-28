package vn.t3nexus.identity.application.user.customer_register;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.user.*;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.lib.utils.lang.Assert;
import vn.t3nexus.lib.utils.lang.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerRegister implements CommandHandler<CustomerRegister.Command, CustomerRegister.Reply> {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final ULIDGenerator ulidGenerator;
    private final EventDispatcher eventDispatcher;
    private final EmailVerificationRepository emailVerificationRepository;

    @Override
    @Transactional
    public CustomerRegister.Reply handle(CustomerRegister.Command command) {
        if (userRepository.existsByEmail(command.email())) {
            throw UserException.emailAlreadyExists();
        }
        UserId userId = UserId.of(ulidGenerator.generate());
        String hashPassword = passwordService.hash(command.rawPassword());
        UserPassword userPassword = UserPassword.ofHashed(hashPassword);
        EmailVerification emailVerification = EmailVerification.issue(EmailVerificationId.of(ulidGenerator.generate()), userId, command.email());
        User user = User.registerAsCustomer(userId, command.email(), command.fullName(), userPassword, emailVerification.getToken());
        userRepository.save(user);
        emailVerificationRepository.save(emailVerification);
        eventDispatcher.dispatchAll(user.getDomainEvents());
        log.info("[CustomerRegister] Customer registered: userId={}, email={}, traceId={}", userId.getValue(), command.email(), MDC.get("traceId"));
        return new Reply(user.getId().getValue());
    }

    public record Command(String email, String rawPassword, String fullName) {
        public Command {
            Assert.notNull(email, "email must not be null");
            Assert.isTrue(StringUtils.isEmail(email), "email must be a valid email");
            Assert.notNull(rawPassword, "rawPassword must not be null");
        }
    }

    public record Reply(String userId) {}
}
