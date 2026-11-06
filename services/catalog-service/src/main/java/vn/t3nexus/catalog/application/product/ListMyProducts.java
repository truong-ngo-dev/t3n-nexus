package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.product.ProductStatus;
import vn.t3nexus.catalog.domain.product.ProductSummary;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

/** Sản phẩm của tôi: phân trang, mới nhất trước, lọc theo trạng thái; đọc thẳng từ bảng, không qua miền. */
@Service
public class ListMyProducts implements QueryHandler<ListMyProducts.Query, ListMyProducts.Result> {

    private final ProductRepository products;

    public ListMyProducts(ProductRepository products) {
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        query.actor().requireSeller();
        var seller = query.actor().accountId();
        return new Result(products.findSummaries(seller, query.status(), query.page(), query.size()),
                products.countBySeller(seller, query.status()));
    }

    public record Query(CatalogActor actor, ProductStatus status, int page, int size) {
    }

    public record Result(List<ProductSummary> items, long total) {
    }
}
