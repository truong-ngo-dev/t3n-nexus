package vn.t3nexus.identity.infrastructure.adapter.repository.user_account;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user_account.UserAccount;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.identity.domain.user_account.UserAccountRepository;
import vn.t3nexus.identity.infrastructure.persistence.user_account.UserAccountJpaRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserAccountRepositoryAdapter implements UserAccountRepository {

    private final UserAccountJpaRepository jpaRepository;
    private final UserAccountMapper userAccountMapper;

    @Override
    public Optional<UserAccount> findById(UserId id) {
        return jpaRepository.findById(id.getValue())
                .map(userAccountMapper::toDomain);
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
                .map(userAccountMapper::toDomain);
    }

    @Override
    public void save(UserAccount userAccount) {
        jpaRepository.upsert(
                userAccount.getId().getValue(),
                userAccount.getEmail(),
                userAccount.getPhoneNumber(),
                userAccount.getFullName(),
                userAccount.getAvatarUrl(),
                userAccount.getStatus().name(),
                userAccount.getLockedAt(),
                userAccount.getCreatedAt(),
                userAccount.getUpdatedAt()
        );
    }

    @Override
    public void delete(UserId id) {
        jpaRepository.deleteById(id.getValue());
    }
}
