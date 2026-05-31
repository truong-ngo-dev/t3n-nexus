package vn.t3nexus.identity.infrastructure.adapter.repository.user_account;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.user_account.EmailVerification;
import vn.t3nexus.identity.domain.user_account.EmailVerificationId;
import vn.t3nexus.identity.domain.user_account.EmailVerificationRepository;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.identity.infrastructure.persistence.user_account.EmailVerificationJpaRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmailVerificationRepositoryAdapter implements EmailVerificationRepository {

    private final EmailVerificationJpaRepository jpaRepository;
    private final EmailVerificationMapper mapper;

    @Override
    public void save(EmailVerification verification) {
        jpaRepository.upsert(
                verification.getId().getValue(),
                verification.getUserId().getValue(),
                verification.getEmail(),
                verification.getToken(),
                verification.getExpiresAt(),
                verification.getStatus().name(),
                verification.getVerifiedAt(),
                verification.getCreatedAt()
        );
    }

    @Override
    public Optional<EmailVerification> findById(EmailVerificationId id) {
        return jpaRepository.findByVerificationId(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<EmailVerification> findByToken(String token) {
        return jpaRepository.findByToken(token)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<EmailVerification> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public void delete(EmailVerificationId id) {
        jpaRepository.findByVerificationId(id.getValue())
                .ifPresent(jpaRepository::delete);
    }
}
