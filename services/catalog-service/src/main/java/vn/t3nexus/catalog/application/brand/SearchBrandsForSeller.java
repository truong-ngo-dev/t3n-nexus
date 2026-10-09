package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

/** Droplist khi seller khai brand cho sản phẩm — chỉ ACTIVE, gõ tới đâu gợi ý tới đó, giới hạn kết quả. */
@Service
@RequiredArgsConstructor
public class SearchBrandsForSeller implements QueryHandler<SearchBrandsForSeller.Query, SearchBrandsForSeller.Result> {

    private static final int MAX_RESULTS = 20;

    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        List<BrandOption> options = brandRepository.search(query.keyword(), BrandStatus.ACTIVE, 0, MAX_RESULTS)
                .stream()
                .map(b -> new BrandOption(b.getId().getValue(), b.getName()))
                .toList();
        return new Result(options);
    }

    public record Query(String keyword) {}

    public record Result(List<BrandOption> options) {}

    public record BrandOption(String id, String name) {}
}
