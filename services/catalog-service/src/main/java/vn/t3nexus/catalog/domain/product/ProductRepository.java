package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface ProductRepository extends Repository<Product, ProductId> {

    boolean existsByBrandId(String brandId);

    List<Product> findBySellerIdPaged(String sellerId, int page, int size);

    long countBySellerId(String sellerId);
}
