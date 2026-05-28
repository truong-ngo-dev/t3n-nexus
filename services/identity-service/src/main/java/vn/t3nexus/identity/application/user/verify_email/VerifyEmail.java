package vn.t3nexus.identity.application.user.verify_email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.user.*;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.utils.lang.Assert;

@Service
@RequiredArgsConstructor
public class VerifyEmail implements CommandHandler<VerifyEmail.Command, VerifyEmail.Reply> {

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public Reply handle(Command command) {
        EmailVerification emailVerification = emailVerificationRepository.findByToken(command.token()).orElseThrow(EmailVerificationException::notFound);
        User user = userRepository.findById(emailVerification.getUserId()).orElseThrow(UserException::notFound);
        emailVerification.verify(user.getFullName());
        emailVerificationRepository.save(emailVerification);
        user.active();
        userRepository.save(user);
        eventDispatcher.dispatchAll(emailVerification.getDomainEvents());
        return new Reply(user.getId().getValue());
    }

    public record Command(String token) {
        public Command {
            Assert.notNull(token, "token must not be null");
        }
    }

    public record Reply(String userId) {}
}
