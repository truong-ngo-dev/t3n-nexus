package vn.t3nexus.seller.domain.sellerprofile;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lớp cài đặt giả lập SellerProfileRepository trong bộ nhớ phục vụ kiểm thử.
 * Lưu trữ theo ownerAccountId.
 */
public class FakeSellerProfileRepository implements SellerProfileRepository {

    private final Map<UUID, SellerProfile> storeByOwner = new ConcurrentHashMap<>();

    @Override
    public void save(SellerProfile profile) {
        storeByOwner.put(profile.ownerAccountId(), profile);
    }

    @Override
    public Optional<SellerProfile> findById(SellerProfileId id) {
        return storeByOwner.values().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();
    }

    @Override
    public Optional<SellerProfile> findByIdForUpdate(SellerProfileId id) {
        return findById(id);
    }

    @Override
    public Optional<SellerProfile> findByOwner(UUID ownerAccountId) {
        return Optional.ofNullable(storeByOwner.get(ownerAccountId));
    }

    @Override
    public boolean existsByOwner(UUID ownerAccountId) {
        return storeByOwner.containsKey(ownerAccountId);
    }

    @Override
    public boolean existsShopNameKeyUsedByOther(String shopNameKey, UUID ownerAccountId) {
        return storeByOwner.values().stream()
                .filter(p -> !p.ownerAccountId().equals(ownerAccountId))
                .anyMatch(p -> p.shopInfo().shopName().key().equals(shopNameKey));
    }

    @Override
    public boolean existsEffectiveIdentityKeyUsedByOther(String identityKey, UUID ownerAccountId) {
        return storeByOwner.values().stream()
                .filter(p -> !p.ownerAccountId().equals(ownerAccountId))
                .filter(p -> p.status() != SellerProfileStatus.REJECTED)
                .anyMatch(p -> p.legalInfo().identityKey().equals(identityKey));
    }
}
