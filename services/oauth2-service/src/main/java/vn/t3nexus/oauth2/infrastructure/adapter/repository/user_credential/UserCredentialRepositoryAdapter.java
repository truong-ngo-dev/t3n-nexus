package vn.t3nexus.oauth2.infrastructure.adapter.repository.user_credential;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.oauth2.domain.user_credential.UserCredential;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;
import vn.t3nexus.oauth2.infrastructure.persistence.user_credential.UserCredentialJpaRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserCredentialRepositoryAdapter implements UserCredentialRepository {

    private final UserCredentialJpaRepository jpaRepository;
    private final UserCredentialMapper        mapper;

    @Override
    public Optional<UserCredential> findById(UserId id) {
        return jpaRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<UserCredential> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
                .map(mapper::toDomain);
    }

    @Override
    public void save(UserCredential credential) {
        jpaRepository.upsert(
                credential.getId().getValue(),
                credential.getEmail(),
                credential.getPassword() != null ? credential.getPassword().getHashedValue() : null,
                credential.getRole().name(),
                credential.getRegistrationMethod().name(),
                credential.getStatus().name(),
                credential.getCreatedAt(),
                credential.getUpdatedAt()
        );
    }

    @Override
    public void delete(UserId id) {
        jpaRepository.deleteById(id.getValue());
    }
}
