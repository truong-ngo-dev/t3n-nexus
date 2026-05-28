package vn.t3nexus.identity.infrastructure.adapter.repository.user;

import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user.*;
import vn.t3nexus.identity.infrastructure.persistence.user.EmailVerificationJpaEntity;

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
