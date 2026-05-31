package vn.t3nexus.identity.infrastructure.adapter.repository.user_account;

import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user_account.EmailVerification;
import vn.t3nexus.identity.domain.user_account.EmailVerificationId;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.identity.infrastructure.persistence.user_account.EmailVerificationJpaEntity;

@Component
public class EmailVerificationMapper {

    public EmailVerification toDomain(EmailVerificationJpaEntity entity) {
        return EmailVerification.reconstitute(
                EmailVerificationId.of(entity.getVerificationId()),
                UserId.of(entity.getUserId()),
                entity.getEmail(),
                entity.getToken(),
                entity.getExpiresAt(),
                entity.getStatus(),
                entity.getVerifiedAt(),
                entity.getCreatedAt()
        );
    }
}
