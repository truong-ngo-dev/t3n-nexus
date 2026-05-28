package vn.t3nexus.customer.domain.customer;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface CustomerProfileRepository extends Repository<CustomerProfile, CustomerProfileId> {
    Optional<CustomerProfile> findByUserId(String userId);
}
