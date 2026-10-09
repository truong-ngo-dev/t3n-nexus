package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

/**
 * RM-CAT-02 — buyer/guest xem chi tiết. Chỉ sản phẩm được xem công khai (INV-CAT-044); còn lại trả 404 như không tồn
 * tại (không để lộ bản nháp hay sản phẩm bị chặn). Đọc qua {@link GetProduct} để dùng chung cache.
 */
@Service
@RequiredArgsConstructor
public class GetPublishedProduct implements QueryHandler<GetPublishedProduct.Query, GetProduct.Result> {

    private final GetProduct getProduct;

    @Override
    public GetProduct.Result handle(Query query) {
        GetProduct.Result result = getProduct.handle(new GetProduct.Query(query.productId()));
        if (!result.publiclyVisible()) {
            throw new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        return result;
    }

    public record Query(String productId) {}
}
