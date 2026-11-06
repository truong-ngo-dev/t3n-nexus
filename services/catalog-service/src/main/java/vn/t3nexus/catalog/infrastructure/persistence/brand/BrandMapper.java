package vn.t3nexus.catalog.infrastructure.persistence.brand;

import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandId;

public final class BrandMapper {

    private BrandMapper() {}

    public static Brand toDomain(BrandJpaEntity entity) {
        return Brand.reconstitute(
                BrandId.of(entity.getId()),
                entity.getName(),
                entity.getNameKey(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /** Thương hiệu mới (chưa lưu) có {@code version} null để Hibernate nhận biết bản ghi mới. */
    public static BrandJpaEntity toJpaEntity(Brand brand) {
        BrandJpaEntity entity = new BrandJpaEntity();
        entity.setId(brand.getId().getValue());
        entity.setName(brand.getName());
        entity.setNameKey(brand.getNameKey());
        entity.setStatus(brand.getStatus());
        entity.setVersion(brand.version());
        entity.setCreatedAt(brand.getCreatedAt());
        entity.setUpdatedAt(brand.getUpdatedAt());
        return entity;
    }
}
