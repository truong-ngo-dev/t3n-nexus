package vn.t3nexus.catalog.application.variant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.application.product.Patch;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantErrorCode;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Người bán đổi giá hoặc SKU của biến thể (CAP-CAT-05), kiểu {@code PATCH}: trường vắng giữ nguyên; {@code sku} là
 * {@code null} thì bỏ SKU. Khóa dòng sản phẩm; mỗi lệnh có thay đổi tăng phiên bản sản phẩm đúng một. Không có đường
 * sửa tổ hợp (INV-CAT-41).
 */
@Service
public class UpdateVariant implements CommandHandler<UpdateVariant.Command, VariantView> {

    private final ProductRepository products;
    private final VariantRepository variants;
    private final Clock clock;
    private final ProductLimits limits;
    private final vn.t3nexus.catalog.application.product.ProductEventRecorder recorder;

    public UpdateVariant(ProductRepository products, VariantRepository variants, Clock clock, ProductLimits limits,
                         vn.t3nexus.catalog.application.product.ProductEventRecorder recorder) {
        this.products = products;
        this.variants = variants;
        this.clock = clock;
        this.limits = limits;
        this.recorder = recorder;
    }

    @Override
    @Transactional
    public VariantView handle(Command command) {
        Product product = VariantLocator.lockOwnProduct(products, command.actor(), command.productId());
        Variant variant = variants.findByIdAndProduct(VariantId.of(command.variantId()), product.getId())
                .orElseThrow(() -> new DomainException(VariantErrorCode.VARIANT_NOT_FOUND));
        Instant now = clock.instant();

        boolean changed = false;
        if (command.price().present()) {
            if (command.price().value() == null) {
                throw new CatalogFieldException("price");
            }
            variant.changePrice(command.price().value(), limits, now);
            changed = true;
        }
        if (command.sku().present()) {
            variant.changeSku(command.sku().value(), limits, now);
            if (variant.getSku() != null
                    && variants.existsBySellerAndSkuExcluding(variant.getSellerId(), variant.getSku(), variant.getId())) {
                throw new DomainException(VariantErrorCode.VARIANT_SKU_TAKEN);
            }
            changed = true;
        }

        if (changed) {
            variants.save(variant);
            product.recordChange(now);
            products.save(product);
            recorder.onChanged(product, java.util.List.of(), vn.t3nexus.catalog.application.product.ProductEventRecorder.Trigger.EDIT);
        }
        return VariantView.of(variant);
    }

    public record Command(CatalogActor actor, UUID productId, UUID variantId, Patch<Long> price, Patch<String> sku) {
    }
}
