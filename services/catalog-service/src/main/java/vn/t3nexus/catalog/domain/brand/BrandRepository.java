package vn.t3nexus.catalog.domain.brand;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface BrandRepository extends Repository<Brand, BrandId> {

    boolean existsBySlug(String slug);

    List<Brand> findAllByStatus(BrandStatus status);
}
