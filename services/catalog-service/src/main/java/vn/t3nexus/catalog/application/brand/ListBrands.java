package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

/** Danh sách công khai: chỉ brand ACTIVE, có tìm theo tên + phân trang. */
@Service
@RequiredArgsConstructor
public class ListBrands implements QueryHandler<ListBrands.Query, ListBrands.Result> {

    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        List<BrandSummary> items = brandRepository
                .search(query.keyword(), BrandStatus.ACTIVE, query.page(), query.size()).stream()
                .map(b -> new BrandSummary(b.getId().getValue(), b.getName(), b.getSlug()))
                .toList();
        long total = brandRepository.count(query.keyword(), BrandStatus.ACTIVE);
        return new Result(items, total);
    }

    public record Query(String keyword, int page, int size) {}

    public record Result(List<BrandSummary> items, long total) {}

    public record BrandSummary(String id, String name, String slug) {}
}
