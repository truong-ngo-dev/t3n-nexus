package vn.t3nexus.oauth2.domain.user_credential;

import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface UserCredentialRepository extends Repository<UserCredential, UserId> {
    Optional<UserCredential> findByEmail(String email);
}
