package vn.t3nexus.identity.application.user_account.verify_email;

import lombok.RequiredArgsConstructor;
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
import vn.t3nexus.lib.utils.lang.Assert;

@Service
@RequiredArgsConstructor
public class VerifyEmail implements CommandHandler<VerifyEmail.Command, VerifyEmail.Reply> {

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserAccountRepository       userAccountRepository;
    private final EventDispatcher             eventDispatcher;

    @Override
    @Transactional
    public Reply handle(Command command) {
        EmailVerification emailVerification = emailVerificationRepository.findByToken(command.token())
                .orElseThrow(EmailVerificationException::notFound);
        UserAccount userAccount = userAccountRepository.findById(emailVerification.getUserId())
                .orElseThrow(UserAccountException::notFound);
        emailVerification.verify(userAccount.getFullName());
        emailVerificationRepository.save(emailVerification);
        userAccount.active();
        userAccountRepository.save(userAccount);
        eventDispatcher.dispatchAll(emailVerification.getDomainEvents());
        return new Reply(userAccount.getId().getValue());
    }

    public record Command(String token) {
        public Command {
            Assert.notNull(token, "token must not be null");
        }
    }

    public record Reply(String userId) {}
}
