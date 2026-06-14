package vn.t3nexus.catalog.infrastructure.persistence.brand;

import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandId;

public final class BrandMapper {

    private BrandMapper() {}

    public static Brand toDomain(BrandJpaEntity entity) {
        return Brand.reconstitute(
                BrandId.of(entity.getId()),
                entity.getName(),
                entity.getSlug(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static BrandJpaEntity toJpaEntity(Brand brand) {
        BrandJpaEntity entity = new BrandJpaEntity();
        entity.setId(brand.getId().getValue());
        entity.setName(brand.getName());
        entity.setSlug(brand.getSlug());
        entity.setStatus(brand.getStatus());
        entity.setCreatedAt(brand.getCreatedAt());
        entity.setUpdatedAt(brand.getUpdatedAt());
        return entity;
    }
}
