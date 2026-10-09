package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

/** Danh sách quản trị: mọi trạng thái, có lọc + phân trang. */
@Service
@RequiredArgsConstructor
public class ListAdminBrands implements QueryHandler<ListAdminBrands.Query, ListAdminBrands.Result> {

    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        List<BrandSummary> items = brandRepository
                .search(query.keyword(), query.status(), query.page(), query.size()).stream()
                .map(b -> new BrandSummary(b.getId().getValue(), b.getName(), b.getSlug(), b.getStatus()))
                .toList();
        long total = brandRepository.count(query.keyword(), query.status());
        return new Result(items, total);
    }

    public record Query(String keyword, BrandStatus status, int page, int size) {}

    public record Result(List<BrandSummary> items, long total) {}

    public record BrandSummary(String id, String name, String slug, BrandStatus status) {}
}
