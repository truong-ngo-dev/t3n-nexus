package vn.t3nexus.catalog.application.variant;

import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.lib.common.domain.exception.DomainException;

/**
 * Bước đầu chung của mọi lệnh biến thể: kiểm vai, rồi đọc <b>khóa dòng</b> sản phẩm của chính chủ (ADR-0012). Sản phẩm
 * của người khác hoặc không có: cùng {@code PRODUCT_NOT_FOUND}.
 */
final class VariantLocator {

    private VariantLocator() {
    }

    static Product lockOwnProduct(ProductRepository products, CatalogActor actor, java.util.UUID productId) {
        actor.requireSeller();
        return products.findOwnedForUpdate(ProductId.of(productId), actor.accountId())
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
