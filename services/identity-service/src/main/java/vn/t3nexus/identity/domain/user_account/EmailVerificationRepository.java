package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface EmailVerificationRepository extends Repository<EmailVerification, EmailVerificationId> {

    Optional<EmailVerification> findByToken(String token);

    Optional<EmailVerification> findByUserId(UserId userId);
}
