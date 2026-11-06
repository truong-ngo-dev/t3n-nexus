package vn.t3nexus.seller.domain.sellerprofile;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng kho của miền cho hồ sơ người bán (đọc theo mã kế thừa từ {@link Repository}).
 * <br>Việc tìm kiếm có bộ lọc và phân trang thuộc cổng đọc ở tầng ứng dụng, không đặt ở đây.
 */
public interface SellerProfileRepository extends Repository<SellerProfile, SellerProfileId> {

    Optional<SellerProfile> findByOwner(UUID ownerAccountId);

    /**
     * Đọc để quyết định, khóa ghi dòng ({@code SELECT ... FOR UPDATE}) đến hết giao dịch của bên gọi.
     * Chỉ gọi trong giao dịch.
     */
    Optional<SellerProfile> findByIdForUpdate(SellerProfileId id);

    boolean existsByOwner(UUID ownerAccountId);

    boolean existsShopNameKeyUsedByOther(String shopNameKey, UUID ownerAccountId);

    boolean existsEffectiveIdentityKeyUsedByOther(String identityKey, UUID ownerAccountId);

    @Override
    default void delete(SellerProfileId id) {
        throw new UnsupportedOperationException("Hồ sơ người bán không xóa được ở C1a");
    }
}
