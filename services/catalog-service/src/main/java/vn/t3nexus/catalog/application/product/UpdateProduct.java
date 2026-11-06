package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.AttributeInput;
import vn.t3nexus.catalog.domain.product.AttributeValueChecker;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.product.SellerStateRepository;
import vn.t3nexus.catalog.domain.product.VariantUsage;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.upload.UploadSlots;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Người bán sửa sản phẩm của mình (CAP-CAT-04), kiểu {@code PATCH}: trường vắng giữ nguyên. Có {@code attributes} thì thay
 * <b>toàn bộ</b> danh sách thuộc tính, kể cả trục, nên đọc <b>khóa dòng</b> sản phẩm (ADR-0012); chỉ sửa tiêu đề, mô tả,
 * thương hiệu thì đọc thường và dựa vào khóa lạc quan (ADR-0011). Sản phẩm của người khác như không có.
 */
@Service
public class UpdateProduct implements CommandHandler<UpdateProduct.Command, ProductView> {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final VariantRepository variants;
    private final Clock clock;
    private final ProductLimits limits;
    private final ProductAssembler assembler;
    private final ProductEventRecorder recorder;

    public UpdateProduct(ProductRepository products, CategoryRepository categories, BrandRepository brands,
                         AttributeTemplateRepository templates, VariantRepository variants, Clock clock,
                         ProductLimits limits, UploadSlots uploads, SellerStateRepository sellerStates,
                         ProductEventRecorder recorder) {
        this.products = products;
        this.categories = categories;
        this.brands = brands;
        this.variants = variants;
        this.clock = clock;
        this.limits = limits;
        this.assembler = new ProductAssembler(templates, uploads, sellerStates, categories);
        this.recorder = recorder;
    }

    @Override
    @Transactional
    public ProductView handle(Command command) {
        command.actor().requireSeller();

        ProductId id = ProductId.of(command.productId());
        UUID seller = command.actor().accountId();
        Product product = (command.attributes().present()
                ? products.findOwnedForUpdate(id, seller)
                : products.findOwned(id, seller))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        Category category = categories.findById(product.getCategoryId())
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        List<Variant> productVariants = variants.findByProductId(id);
        VariantUsage usage = VariantUsage.of(productVariants);
        Instant now = clock.instant();

        boolean changed = false;
        if (command.title().present()) {
            product.changeTitle(command.title().value(), limits, now);
            changed = true;
        }
        if (command.description().present()) {
            product.changeDescription(command.description().value(), limits, now);
            changed = true;
        }
        if (command.brandId().present()) {
            changeBrand(product, command.brandId().value(), now);
            changed = true;
        }
        if (command.attributes().present()) {
            replaceAttributes(product, category, command.attributes().value(), usage, now);
            changed = true;
        }

        long version = product.version() == null ? 0L : product.version();
        if (changed) {
            version = product.nextVersion();
            products.save(product);
            recorder.onChanged(product, List.of(), ProductEventRecorder.Trigger.EDIT);
        }
        return assembler.assemble(product, version, category.getAssignments(), productVariants);
    }

    /** Chọn thương hiệu là "chọn mới": phải đang dùng. Giữ nguyên thương hiệu cũ đã ngừng thì không kiểm. */
    private void changeBrand(Product product, UUID brandId, Instant now) {
        if (brandId == null) {
            product.changeBrand(null, now);
            return;
        }
        BrandId requested = BrandId.of(brandId);
        if (requested.equals(product.getBrandId())) {
            return;
        }
        var brand = brands.findById(requested)
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));
        if (!brand.isActive()) {
            throw new DomainException(ProductErrorCode.PRODUCT_BRAND_NOT_USABLE);
        }
        product.changeBrand(requested, now);
    }

    private void replaceAttributes(Product product, Category category, List<AttributeInput> value,
                                   VariantUsage usage, Instant now) {
        List<AttributeInput> submitted = value == null ? List.of() : value;
        Set<AttributeTemplateId> needed = new LinkedHashSet<>(ProductAssembler.templateIds(product.getAttributes()));
        submitted.forEach(a -> needed.add(a.templateId()));
        Map<AttributeTemplateId, AttributeTemplate> templates = assembler.load(needed);

        List<ProductAttribute> attributes = AttributeValueChecker.check(submitted, product.getAttributes(),
                category.getAssignments(), templates, limits, LocalDate.now(clock));
        product.replaceAttributes(attributes, usage, now);
    }

    public record Command(CatalogActor actor, UUID productId, Patch<String> title, Patch<String> description,
                          Patch<UUID> brandId, Patch<List<AttributeInput>> attributes) {
    }
}
