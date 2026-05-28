package vn.t3nexus.customer.infrastructure.adapter.repository.customer;

import org.springframework.stereotype.Component;
import vn.t3nexus.customer.domain.customer.CustomerProfile;
import vn.t3nexus.customer.domain.customer.CustomerProfileId;
import vn.t3nexus.customer.infrastructure.persistence.customer.CustomerProfileJpaEntity;

@Component
public class CustomerProfileMapper {

    public CustomerProfile toDomain(CustomerProfileJpaEntity entity) {
        return CustomerProfile.reconstitute(
                CustomerProfileId.of(entity.getId()),
                entity.getUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public CustomerProfileJpaEntity toEntity(CustomerProfile profile) {
        CustomerProfileJpaEntity entity = new CustomerProfileJpaEntity();
        entity.setId(profile.getId().getValue());
        entity.setUserId(profile.getUserId());
        entity.setCreatedAt(profile.getCreatedAt());
        entity.setUpdatedAt(profile.getUpdatedAt());
        return entity;
    }

}
