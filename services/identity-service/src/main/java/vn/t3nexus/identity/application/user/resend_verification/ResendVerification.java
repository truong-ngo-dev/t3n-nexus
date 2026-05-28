package vn.t3nexus.identity.application.user.resend_verification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.user.*;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.ratelimiter.RateLimiter;
import vn.t3nexus.lib.utils.lang.Assert;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ResendVerification implements CommandHandler<ResendVerification.Command, Void> {

    private static final int    RESEND_LIMIT  = 3;
    private static final Duration RESEND_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EventDispatcher eventDispatcher;
    private final RateLimiter rateLimiter;

    @Override
    @Transactional
    public Void handle(Command command) {
        if (!rateLimiter.tryAcquire("resend:" + command.email(), RESEND_LIMIT, RESEND_WINDOW)) {
            throw UserException.rateLimitExceeded();
        }

        // filter(isPending) ensures uniform 400 for both non-existent email and already-active user
        User user = userRepository.findByEmail(command.email())
                .filter(User::isPending)
                .orElseThrow(UserException::resendNotAllowed);

        EmailVerification emailVerification = emailVerificationRepository
                .findByUserId(user.getId())
                .orElseThrow(EmailVerificationException::notFound);

        emailVerification.reissue(user.getFullName());
        emailVerificationRepository.save(emailVerification);
        eventDispatcher.dispatchAll(emailVerification.getDomainEvents());
        return null;
    }

    public record Command(String email) {
        public Command {
            Assert.notNull(email, "email must not be null");
        }
    }
}
