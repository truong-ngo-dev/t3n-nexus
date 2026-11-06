package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Người bán gỡ sản phẩm đang bán về trạng thái UNPUBLISHED (CAP-CAT-07). Khóa dòng sản phẩm và cập nhật trạng thái.
 */
@Service
public class UnpublishProduct implements CommandHandler<UnpublishProduct.Command, ProductView> {

    private final ProductRepository products;
    private final VariantRepository variantRepository;
    private final CategoryRepository categories;
    private final ProductAssembler assembler;
    private final Clock clock;
    private final ProductEventRecorder recorder;

    public UnpublishProduct(ProductRepository products, VariantRepository variantRepository,
                            CategoryRepository categories, ProductAssembler assembler, Clock clock,
                            ProductEventRecorder recorder) {
        this.products = products;
        this.variantRepository = variantRepository;
        this.categories = categories;
        this.assembler = assembler;
        this.clock = clock;
        this.recorder = recorder;
    }

    @Override
    @Transactional
    public ProductView handle(Command command) {
        command.actor().requireSeller();

        ProductId id = ProductId.of(command.productId());
        UUID seller = command.actor().accountId();

        Product product = products.findOwnedForUpdate(id, seller)
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        product.unpublish(clock.instant());
        products.save(product);
        recorder.onChanged(product, List.of(), ProductEventRecorder.Trigger.UNPUBLISH);

        List<Variant> variants = variantRepository.findByProductId(id);
        Category category = categories.findById(product.getCategoryId())
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        long version = product.nextVersion();
        return assembler.assemble(product, version, category.getAssignments(), variants);
    }

    public record Command(CatalogActor actor, UUID productId) {
    }
}
