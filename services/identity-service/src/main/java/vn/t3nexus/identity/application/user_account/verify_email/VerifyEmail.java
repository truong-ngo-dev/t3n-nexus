package vn.t3nexus.identity.application.user_account.verify_email;

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
import vn.t3nexus.lib.utils.lang.Assert;

@Slf4j
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
        eventDispatcher.dispatchAll(userAccount.getDomainEvents());

        // Bước quan trọng nhất của cả luồng registration (account activation) — trước đây class
        // này 0 log, kể cả thành công. Lỗi (token not found/expired/already verified) đã có
        // GlobalExceptionHandler log warn chung, không cần lặp lại ở đây.
        log.info("[VerifyEmail] activated: userId={}, traceId={}", userAccount.getId().getValue(), MDC.get("traceId"));

        return new Reply(userAccount.getId().getValue());
    }

    public record Command(String token) {
        public Command {
            Assert.notNull(token, "token must not be null");
        }
    }

    public record Reply(String userId) {}
}
