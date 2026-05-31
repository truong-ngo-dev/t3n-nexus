package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface UserAccountRepository extends Repository<UserAccount, UserId> {
    Optional<UserAccount> findByEmail(String email);
}
