package vn.t3nexus.identity.infrastructure.adapter.repository.user_account;

import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user_account.UserAccount;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.identity.infrastructure.persistence.user_account.UserAccountJpaEntity;

@Component
public class UserAccountMapper {

    public UserAccount toDomain(UserAccountJpaEntity entity) {
        return UserAccount.reconstitute(
                UserId.of(entity.getId()),
                entity.getEmail(),
                entity.getPhoneNumber(),
                entity.getFullName(),
                entity.getStatus(),
                entity.getLockedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
