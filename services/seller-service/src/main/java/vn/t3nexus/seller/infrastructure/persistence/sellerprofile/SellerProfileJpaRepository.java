package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Kho Spring Data JPA cho thực thể hồ sơ người bán.
 */
@Repository
public interface SellerProfileJpaRepository extends JpaRepository<SellerProfileJpaEntity, UUID> {

    Optional<SellerProfileJpaEntity> findByOwnerAccountId(UUID ownerAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM SellerProfileJpaEntity p WHERE p.id = :id")
    Optional<SellerProfileJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByOwnerAccountId(UUID ownerAccountId);

    boolean existsByShopNameKeyAndOwnerAccountIdNot(String shopNameKey, UUID ownerAccountId);

    @Query("SELECT COUNT(p) > 0 FROM SellerProfileJpaEntity p WHERE p.identityKey = :identityKey AND p.ownerAccountId <> :ownerAccountId AND p.status <> :rejected")
    boolean existsEffectiveIdentityKeyUsedByOther(
            @Param("identityKey") String identityKey,
            @Param("ownerAccountId") UUID ownerAccountId,
            @Param("rejected") SellerProfileStatus rejected
    );

    record PendingRow(UUID id, SellerType sellerType, String shopName, Instant submittedAt) {}

    @Query(
            value = "SELECT new vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileJpaRepository$PendingRow(p.id, p.sellerType, p.shopName, p.submittedAt) FROM SellerProfileJpaEntity p WHERE p.status = :status ORDER BY p.submittedAt ASC, p.id ASC",
            countQuery = "SELECT COUNT(p) FROM SellerProfileJpaEntity p WHERE p.status = :status"
    )
    Page<PendingRow> findPending(@Param("status") SellerProfileStatus status, Pageable pageable);
}
