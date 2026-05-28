package vn.t3nexus.identity.infrastructure.adapter.repository.user;

import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user.User;
import vn.t3nexus.identity.domain.user.UserId;
import vn.t3nexus.identity.domain.user.UserPassword;
import vn.t3nexus.identity.infrastructure.persistence.user.UserJpaEntity;

@Component
public class UserMapper {

    public User toDomain(UserJpaEntity entity) {
        UserPassword password = entity.getHashedPassword() != null
                ? UserPassword.ofHashed(entity.getHashedPassword())
                : null;
        return User.reconstitute(
                UserId.of(entity.getId()),
                entity.getEmail(),
                entity.getPhoneNumber(),
                entity.getFullName(),
                password,
                entity.getStatus(),
                entity.getRole(),
                entity.getLockedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

}
