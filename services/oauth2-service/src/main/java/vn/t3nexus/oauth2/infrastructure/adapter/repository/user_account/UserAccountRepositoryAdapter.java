package vn.t3nexus.oauth2.infrastructure.adapter.repository.user_account;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;
import vn.t3nexus.oauth2.infrastructure.persistence.user_account.UserAccountJpaRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserAccountRepositoryAdapter implements UserAccountRepository {

    /** Tên ràng buộc duy nhất của email, xem migration V12. */
    static final String EMAIL_UNIQUE_CONSTRAINT = "uq_user_accounts_email";

    private final UserAccountJpaRepository jpaRepository;
    private final UserAccountMapper        mapper;

    @Override
    public Optional<UserAccount> findById(UserAccountId id) {
        return jpaRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.value());
    }

    @Override
    public void save(UserAccount account) {
        try {
            jpaRepository.upsert(
                    account.getId().getValue(),
                    account.getEmail().value(),
                    account.getPasswordHash() != null ? account.getPasswordHash().getHashedValue() : null,
                    account.getRole().name(),
                    account.getRegistrationMethod().name(),
                    account.getStatus().name(),
                    account.getCreatedAt(),
                    account.getUpdatedAt()
            );
        } catch (DataIntegrityViolationException e) {
            // Chốt cuối của INV-ATH-01: hai yêu cầu cùng email cùng qua kiểm trước thì một bên vi phạm ràng buộc duy nhất.
            // Dịch ở đây để tầng ứng dụng không biết kiểu lỗi của hạ tầng.
            if (isEmailUniqueViolation(e)) throw UserAccountException.emailTaken();
            throw e;
        }
    }

    @Override
    public void delete(UserAccountId id) {
        jpaRepository.deleteById(id.getValue());
    }

    private static boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        return cause instanceof ConstraintViolationException cve
                && EMAIL_UNIQUE_CONSTRAINT.equals(cve.getConstraintName());
    }
}
