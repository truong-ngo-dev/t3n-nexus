package vn.t3nexus.oauth2.domain.user_account;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface UserAccountRepository extends Repository<UserAccount, UserAccountId> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(Email email);

    /**
     * Lưu tài khoản.
     *
     * @throws UserAccountException {@code EMAIL_TAKEN} khi vi phạm ràng buộc duy nhất của email
     */
    @Override
    void save(UserAccount aggregate);
}
