package vn.t3nexus.identity.application.user_account.resend_verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.user_account.EmailVerification;
import vn.t3nexus.identity.domain.user_account.EmailVerificationException;
import vn.t3nexus.identity.domain.user_account.EmailVerificationRepository;
import vn.t3nexus.identity.domain.user_account.UserAccount;
import vn.t3nexus.identity.domain.user_account.UserAccountException;
import vn.t3nexus.identity.domain.user_account.UserAccountRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.ratelimiter.RateLimit;
import vn.t3nexus.lib.utils.lang.Assert;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendVerification implements CommandHandler<ResendVerification.Command, Void> {

    private final UserAccountRepository       userAccountRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EventDispatcher             eventDispatcher;

    @Override
    @Transactional
    @RateLimit(key = "'resend:' + #command.email()", limit = 3, windowSeconds = 3600,
            message = "Please wait before requesting another link")
    public Void handle(Command command) {
        // filter(isPending) ensures uniform 400 for both non-existent email and already-active user
        UserAccount userAccount = userAccountRepository.findByEmail(command.email())
                .filter(UserAccount::isPending)
                .orElseThrow(UserAccountException::resendNotAllowed);

        EmailVerification emailVerification = emailVerificationRepository
                .findByUserId(userAccount.getId())
                .orElseThrow(EmailVerificationException::notFound);

        emailVerification.reissue(userAccount.getFullName());
        emailVerificationRepository.save(emailVerification);
        eventDispatcher.dispatchAll(emailVerification.getDomainEvents());

        log.info("[ResendVerification] reissued: userId={}, traceId={}", userAccount.getId().getValue(), MDC.get("traceId"));
        return null;
    }

    public record Command(String email) {
        public Command {
            Assert.notNull(email, "email must not be null");
        }
    }
}
