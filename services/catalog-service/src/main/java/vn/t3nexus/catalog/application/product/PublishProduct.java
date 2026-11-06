package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.MissingPublishCondition;
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
 * Người bán đăng bán sản phẩm (CAP-CAT-07). Khóa dòng sản phẩm, kiểm tra các điều kiện để đăng.
 */
@Service
public class PublishProduct implements CommandHandler<PublishProduct.Command, ProductView> {

    private final ProductRepository products;
    private final VariantRepository variantRepository;
    private final CategoryRepository categories;
    private final ProductAssembler assembler;
    private final Clock clock;
    private final ProductEventRecorder recorder;

    public PublishProduct(ProductRepository products, VariantRepository variantRepository,
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

        if (product.isPublished()) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }

        List<Variant> variants = variantRepository.findByProductId(id);
        List<MissingPublishCondition> missing = assembler.missingConditions(product, variants);
        if (!missing.isEmpty()) {
            throw new PublishNotAllowedException(missing);
        }

        product.publish(clock.instant());
        products.save(product);
        recorder.onChanged(product, List.of(), ProductEventRecorder.Trigger.PUBLISH);

        Category category = categories.findById(product.getCategoryId())
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        long version = product.nextVersion();
        return assembler.assemble(product, version, category.getAssignments(), variants);
    }

    public record Command(CatalogActor actor, UUID productId) {
    }
}
