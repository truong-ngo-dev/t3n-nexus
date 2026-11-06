package vn.t3nexus.seller.domain.sellerprofile;

import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.Objects;
import java.util.UUID;

/**
 * Dịch vụ miền kiểm tra tính duy nhất cho hồ sơ người bán.
 */
public class SellerProfileUniqueness {

    private final SellerProfileRepository repository;

    public SellerProfileUniqueness(SellerProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void requireNoProfile(UUID ownerAccountId) {
        if (repository.existsByOwner(ownerAccountId)) {
            throw new DomainException(SellerErrorCode.SELLER_PROFILE_EXISTS);
        }
    }

    public void requireShopNameAvailable(UUID ownerAccountId, ShopName shopName) {
        if (repository.existsShopNameKeyUsedByOther(shopName.key(), ownerAccountId)) {
            throw new DomainException(SellerErrorCode.SHOP_NAME_TAKEN);
        }
    }

    public void requireIdentityAvailable(UUID ownerAccountId, LegalInfo legalInfo) {
        if (repository.existsEffectiveIdentityKeyUsedByOther(legalInfo.identityKey(), ownerAccountId)) {
            throw new DomainException(SellerErrorCode.IDENTITY_ALREADY_REGISTERED);
        }
    }

    public void requireCanSubmit(UUID ownerAccountId, ShopName shopName, LegalInfo legalInfo) {
        requireNoProfile(ownerAccountId);
        requireShopNameAvailable(ownerAccountId, shopName);
        requireIdentityAvailable(ownerAccountId, legalInfo);
    }
}
