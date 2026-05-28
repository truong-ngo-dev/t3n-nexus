package vn.t3nexus.identity.infrastructure.adapter.repository.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user.User;
import vn.t3nexus.identity.domain.user.UserId;
import vn.t3nexus.identity.domain.user.UserRepository;
import vn.t3nexus.identity.infrastructure.persistence.user.UserJpaRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserMapper userMapper;

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.getValue())
                .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
                .map(userMapper::toDomain);
    }

    @Override
    public void save(User user) {
        jpaRepository.upsert(
                user.getId().getValue(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getHashedPassword() != null ? user.getHashedPassword().getHashedValue() : null,
                user.getRole().name(),
                user.getStatus().name(),
                user.getLockedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    @Override
    public void delete(UserId id) {
        jpaRepository.deleteById(id.getValue());
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }
}
