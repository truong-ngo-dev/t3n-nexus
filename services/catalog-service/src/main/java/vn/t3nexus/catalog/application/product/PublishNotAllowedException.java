package vn.t3nexus.catalog.application.product;

import vn.t3nexus.catalog.domain.product.MissingPublishCondition;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

/**
 * Ném ra khi sản phẩm chưa đủ điều kiện để đăng (CAP-CAT-09).
 */
public class PublishNotAllowedException extends DomainException {

    private final List<MissingPublishCondition> missing;

    public PublishNotAllowedException(List<MissingPublishCondition> missing) {
        super(ProductErrorCode.PRODUCT_NOT_PUBLISHABLE);
        this.missing = missing == null ? List.of() : List.copyOf(missing);
    }

    public List<MissingPublishCondition> missing() {
        return missing;
    }
}
