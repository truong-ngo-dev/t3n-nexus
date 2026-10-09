package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

/** Seller xem sản phẩm của chính mình — mọi trạng thái, nhưng chỉ sản phẩm do chính seller đó tạo (analysis.md §4). */
@Service
@RequiredArgsConstructor
public class GetSellerProduct implements QueryHandler<GetSellerProduct.Query, GetProduct.Result> {

    private final GetProduct getProduct;

    @Override
    public GetProduct.Result handle(Query query) {
        GetProduct.Result result = getProduct.handle(new GetProduct.Query(query.productId()));
        if (!result.sellerId().equals(query.sellerId())) {
            throw new DomainException(ProductErrorCode.NOT_PRODUCT_OWNER);
        }
        return result;
    }

    public record Query(String sellerId, String productId) {}
}
