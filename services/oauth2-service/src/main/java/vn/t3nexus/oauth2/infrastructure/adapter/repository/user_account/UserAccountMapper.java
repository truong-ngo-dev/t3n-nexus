package vn.t3nexus.oauth2.infrastructure.adapter.repository.user_account;

import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.infrastructure.persistence.user_account.UserAccountJpaEntity;

@Component
public class UserAccountMapper {

    public UserAccount toDomain(UserAccountJpaEntity entity) {
        PasswordHash password = entity.getPasswordHash() != null
                ? PasswordHash.ofHashed(entity.getPasswordHash())
                : null;

        return UserAccount.reconstitute(
                UserAccountId.of(entity.getId()),
                Email.of(entity.getEmail()),
                password,
                entity.getRole(),
                entity.getRegistrationMethod(),
                entity.getStatus(),
                entity.isMfaEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
