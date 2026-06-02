package vn.t3nexus.oauth2.infrastructure.adapter.repository.user_credential;

import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.oauth2.domain.user_credential.CredentialPassword;
import vn.t3nexus.oauth2.domain.user_credential.UserCredential;
import vn.t3nexus.oauth2.infrastructure.persistence.user_credential.UserCredentialJpaEntity;

@Component
public class UserCredentialMapper {

    public UserCredential toDomain(UserCredentialJpaEntity entity) {
        CredentialPassword password = entity.getPasswordHash() != null
                ? CredentialPassword.ofHashed(entity.getPasswordHash())
                : null;

        return UserCredential.reconstitute(
                UserId.of(entity.getId()),
                entity.getEmail(),
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
