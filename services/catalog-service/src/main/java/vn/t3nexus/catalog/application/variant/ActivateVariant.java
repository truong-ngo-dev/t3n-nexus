package vn.t3nexus.catalog.application.variant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.product.Product;
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

/** Người bán bật lại một biến thể đã ngừng. Khóa dòng sản phẩm; tăng phiên bản sản phẩm một. */
@Service
public class ActivateVariant implements CommandHandler<ActivateVariant.Command, VariantView> {

    private final ProductRepository products;
    private final VariantRepository variants;
    private final Clock clock;
    private final vn.t3nexus.catalog.application.product.ProductEventRecorder recorder;

    public ActivateVariant(ProductRepository products, VariantRepository variants, Clock clock,
                           vn.t3nexus.catalog.application.product.ProductEventRecorder recorder) {
        this.products = products;
        this.variants = variants;
        this.clock = clock;
        this.recorder = recorder;
    }

    @Override
    @Transactional
    public VariantView handle(Command command) {
        Product product = VariantLocator.lockOwnProduct(products, command.actor(), command.productId());
        Variant variant = variants.findByIdAndProduct(VariantId.of(command.variantId()), product.getId())
                .orElseThrow(() -> new DomainException(VariantErrorCode.VARIANT_NOT_FOUND));
        Instant now = clock.instant();

        variant.activate(now);
        variants.save(variant);
        product.recordChange(now);
        products.save(product);
        recorder.onChanged(product, java.util.List.of(variant.getId()), vn.t3nexus.catalog.application.product.ProductEventRecorder.Trigger.EDIT);
        return VariantView.of(variant);
    }

    public record Command(CatalogActor actor, UUID productId, UUID variantId) {
    }
}
