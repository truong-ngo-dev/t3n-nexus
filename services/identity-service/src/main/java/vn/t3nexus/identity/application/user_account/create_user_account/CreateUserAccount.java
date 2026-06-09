package vn.t3nexus.identity.application.user_account.create_user_account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.user_account.EmailVerification;
import vn.t3nexus.identity.domain.user_account.EmailVerificationId;
import vn.t3nexus.identity.domain.user_account.EmailVerificationRepository;
import vn.t3nexus.identity.domain.user_account.UserAccount;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.identity.domain.user_account.UserAccountRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateUserAccount implements CommandHandler<CreateUserAccount.Command, Void> {

    public record Command(String userId, String email, String fullName, String role, String registrationMethod, String setupToken) {}

    private final UserAccountRepository       userAccountRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final ULIDGenerator               ulidGenerator;
    private final EventDispatcher             eventDispatcher;

    @Override
    @Transactional
    public Void handle(Command command) {
        UserId userId = UserId.of(command.userId());

        if (userAccountRepository.findById(userId).isPresent()) {
            log.info("[CreateUserAccount] userId already exists, skip. userId={}", command.userId());
            return null;
        }

        if ("CUSTOMER".equals(command.role())) {
            handleCustomer(command, userId);
        } else {
            // TODO: SELLER   → registerPendingVerification → SellerAccountCreated   → seller-service starts Temporal onboarding
            //       SHIPPER  → registerPendingVerification → ShipperAccountCreated  → shipper-service creates ShipperApplication{PENDING}
            //       ADMIN    → registerActivated (active immediately, no approval flow)
            // Candidate for domain service — each role has different initial status and downstream event.
            throw new UnsupportedOperationException("UserAccount creation for role=" + command.role() + " is not yet implemented");
        }

        return null;
    }

    private void handleCustomer(Command command, UserId userId) {
        if ("CREDENTIAL".equals(command.registrationMethod())) {
            UserAccount userAccount = UserAccount.registerCustomerPendingVerification(userId, command.email(), command.fullName());
            EmailVerification emailVerification = EmailVerification.issue(
                    EmailVerificationId.of(ulidGenerator.generate()), userId, command.email(), command.fullName());
            userAccountRepository.save(userAccount);
            emailVerificationRepository.save(emailVerification);
            eventDispatcher.dispatchAll(userAccount.getDomainEvents());
            eventDispatcher.dispatchAll(emailVerification.getDomainEvents());
        } else {
            UserAccount userAccount = UserAccount.registerCustomerActivated(userId, command.email(), command.fullName(), command.setupToken());
            userAccountRepository.save(userAccount);
            eventDispatcher.dispatchAll(userAccount.getDomainEvents());
        }
        log.info("[CreateUserAccount] UserAccount created (CUSTOMER, method={}): userId={}, traceId={}",
                command.registrationMethod(), command.userId(), MDC.get("traceId"));
    }
}
