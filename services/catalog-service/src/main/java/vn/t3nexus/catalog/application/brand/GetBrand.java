package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.UUID;

/** Chi tiết thương hiệu cho quản trị: mọi trạng thái. */
@Service
@RequiredArgsConstructor
public class GetBrand implements QueryHandler<GetBrand.Query, BrandView> {

    private final BrandRepository repository;

    @Override
    @Transactional(readOnly = true)
    public BrandView handle(Query query) {
        query.actor().requireAdmin();
        Brand brand = repository.findById(BrandId.of(query.brandId()))
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));
        return BrandView.of(brand, brand.version());
    }

    public record Query(CatalogActor actor, UUID brandId) {}
}
