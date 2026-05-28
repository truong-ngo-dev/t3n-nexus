package vn.t3nexus.customer.infrastructure.adapter.repository.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.customer.domain.customer.CustomerProfile;
import vn.t3nexus.customer.domain.customer.CustomerProfileId;
import vn.t3nexus.customer.domain.customer.CustomerProfileRepository;
import vn.t3nexus.customer.infrastructure.persistence.customer.CustomerProfileJpaRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomerProfilePersistenceAdapter implements CustomerProfileRepository {

    private final CustomerProfileJpaRepository jpaRepository;
    private final CustomerProfileMapper mapper;

    @Override
    public Optional<CustomerProfile> findById(CustomerProfileId id) {
        return jpaRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<CustomerProfile> findByUserId(String userId) {
        return jpaRepository.findByUserId(userId)
                .map(mapper::toDomain);
    }

    @Override
    public void save(CustomerProfile profile) {
        jpaRepository.insertIgnoreConflict(
                profile.getId().getValue(),
                profile.getUserId(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    @Override
    public void delete(CustomerProfileId id) {
        jpaRepository.deleteById(id.getValue());
    }
}
