package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListActiveBrands implements QueryHandler<ListActiveBrands.Query, ListActiveBrands.Result> {

    private final BrandRepository brandRepository;

    @Override
    @Cacheable(CacheNames.BRANDS_ACTIVE)
    public Result handle(Query query) {
        List<BrandSummary> brands = brandRepository.findAllByStatus(BrandStatus.ACTIVE).stream()
                .map(brand -> new BrandSummary(brand.getId().getValue(), brand.getName(), brand.getSlug()))
                .toList();
        return new Result(brands);
    }

    public record Query() {}

    public record Result(List<BrandSummary> brands) {}

    public record BrandSummary(String id, String name, String slug) {}
}
