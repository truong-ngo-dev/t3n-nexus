package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter triển khai cổng kho {@link SellerProfileRepository} của miền.
 */
@Repository
public class SellerProfileRepositoryAdapter implements SellerProfileRepository {

    private final SellerProfileJpaRepository jpaRepository;

    public SellerProfileRepositoryAdapter(SellerProfileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<SellerProfile> findById(SellerProfileId id) {
        return jpaRepository.findById(id.getValue())
                .map(SellerProfileMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SellerProfile> findByIdForUpdate(SellerProfileId id) {
        return jpaRepository.findByIdForUpdate(id.getValue())
                .map(SellerProfileMapper::toDomain);
    }

    @Override
    public Optional<SellerProfile> findByOwner(UUID ownerAccountId) {
        return jpaRepository.findByOwnerAccountId(ownerAccountId)
                .map(SellerProfileMapper::toDomain);
    }

    @Override
    public boolean existsByOwner(UUID ownerAccountId) {
        return jpaRepository.existsByOwnerAccountId(ownerAccountId);
    }

    @Override
    public boolean existsShopNameKeyUsedByOther(String shopNameKey, UUID ownerAccountId) {
        return jpaRepository.existsByShopNameKeyAndOwnerAccountIdNot(shopNameKey, ownerAccountId);
    }

    @Override
    public boolean existsEffectiveIdentityKeyUsedByOther(String identityKey, UUID ownerAccountId) {
        return jpaRepository.existsEffectiveIdentityKeyUsedByOther(identityKey, ownerAccountId, SellerProfileStatus.REJECTED);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(SellerProfile profile) {
        SellerProfileJpaEntity entity = jpaRepository.findById(profile.getId().getValue())
                .map(existing -> {
                    SellerProfileMapper.copyInto(existing, profile);
                    return existing;
                })
                .orElseGet(() -> SellerProfileMapper.toNewEntity(profile));

        jpaRepository.saveAndFlush(entity);
    }
}
