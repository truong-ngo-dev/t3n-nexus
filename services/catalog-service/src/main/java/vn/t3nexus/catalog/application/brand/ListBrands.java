package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;
import java.util.UUID;

/**
 * Danh sách thương hiệu đang dùng cho mọi người đã đăng nhập (người bán chọn khi khai sản phẩm, người mua lọc): chỉ
 * thương hiệu {@code ACTIVE}, tìm theo tên không phân biệt hoa thường, dấu; phân trang.
 */
@Service
@RequiredArgsConstructor
public class ListBrands implements QueryHandler<ListBrands.Query, ListBrands.Result> {

    private final BrandRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        String key = BrandKeyword.of(query.keyword());
        List<Item> items = repository.search(key, BrandStatus.ACTIVE, query.page(), query.size()).stream()
                .map(b -> new Item(b.getId().getValue(), b.getName()))
                .toList();
        return new Result(items, repository.count(key, BrandStatus.ACTIVE));
    }

    public record Query(String keyword, int page, int size) {}

    public record Result(List<Item> items, long total) {}

    public record Item(UUID id, String name) {}
}
