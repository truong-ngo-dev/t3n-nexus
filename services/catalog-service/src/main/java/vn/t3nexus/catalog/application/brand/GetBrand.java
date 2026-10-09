package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Instant;

/** Chi tiết quản trị. */
@Service
@RequiredArgsConstructor
public class GetBrand implements QueryHandler<GetBrand.Query, GetBrand.Result> {

    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        Brand b = brandRepository.findById(BrandId.of(query.id()))
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));

        return new Result(b.getId().getValue(), b.getName(), b.getSlug(), b.getStatus(),
                b.getCreatedAt(), b.getUpdatedAt());
    }

    public record Query(String id) {}

    public record Result(String id, String name, String slug, BrandStatus status,
                         Instant createdAt, Instant updatedAt) {}
}
