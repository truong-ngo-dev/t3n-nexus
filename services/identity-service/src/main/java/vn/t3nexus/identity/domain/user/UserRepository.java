package vn.t3nexus.identity.domain.user;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface UserRepository extends Repository<User, UserId> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
