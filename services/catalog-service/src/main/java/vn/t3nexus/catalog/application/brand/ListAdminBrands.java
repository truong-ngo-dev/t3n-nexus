package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

/** Danh sách thương hiệu cho quản trị: mọi trạng thái, có lọc theo tên và trạng thái, phân trang. */
@Service
@RequiredArgsConstructor
public class ListAdminBrands implements QueryHandler<ListAdminBrands.Query, ListAdminBrands.Result> {

    private final BrandRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        query.actor().requireAdmin();
        String key = BrandKeyword.of(query.keyword());
        List<BrandView> items = repository.search(key, query.status(), query.page(), query.size()).stream()
                .map(b -> BrandView.of(b, b.version()))
                .toList();
        return new Result(items, repository.count(key, query.status()));
    }

    public record Query(CatalogActor actor, String keyword, BrandStatus status, int page, int size) {}

    public record Result(List<BrandView> items, long total) {}
}
