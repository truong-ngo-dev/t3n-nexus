package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends Repository<Product, ProductId> {

    boolean existsByBrandId(String brandId);

    List<Product> findBySellerIdPaged(String sellerId, int page, int size);

    long countBySellerId(String sellerId);

    /** Như {@code findById} nhưng khoá dòng tới hết transaction — dùng khi thay đổi quan hệ Product ↔ Variant (C-07). */
    Optional<Product> findByIdForUpdate(ProductId id);
}
