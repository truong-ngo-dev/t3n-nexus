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
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.AttributeInput;
import vn.t3nexus.catalog.domain.product.AttributeValueChecker;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.product.SellerState;
import vn.t3nexus.catalog.domain.product.SellerStateRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.upload.UploadSlots;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Người bán tạo sản phẩm nháp (CAP-CAT-04). Danh mục phải là lá và dùng được (INV-CAT-97), thương hiệu tùy chọn nhưng nếu có
 * phải đang dùng; chủ là người gọi, lấy từ token. Yêu cầu người bán đã được duyệt (feature 09). Không phát event.
 */
@Service
public class CreateProduct implements CommandHandler<CreateProduct.Command, ProductView> {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final SellerStateRepository sellerStates;
    private final IdGenerator ids;
    private final Clock clock;
    private final ProductLimits limits;
    private final ProductAssembler assembler;

    public CreateProduct(ProductRepository products, CategoryRepository categories, BrandRepository brands,
                         AttributeTemplateRepository templates, SellerStateRepository sellerStates,
                         IdGenerator ids, Clock clock, ProductLimits limits, UploadSlots uploads) {
        this.products = products;
        this.categories = categories;
        this.brands = brands;
        this.sellerStates = sellerStates;
        this.ids = ids;
        this.clock = clock;
        this.limits = limits;
        this.assembler = new ProductAssembler(templates, uploads, sellerStates, categories);
    }

    @Override
    @Transactional
    public ProductView handle(Command command) {
        command.actor().requireSeller();

        UUID sellerId = command.actor().accountId();
        SellerState sellerState = sellerStates.find(sellerId).orElse(null);
        if (sellerState == null || !sellerState.isApproved()) {
            throw new DomainException(ProductErrorCode.SELLER_NOT_APPROVED);
        }

        Category category = categories.findById(CategoryId.of(command.categoryId()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        if (!category.isLeaf() || !category.isActive() || categories.hasInactiveAncestor(category.getId())) {
            throw new DomainException(ProductErrorCode.PRODUCT_CATEGORY_NOT_USABLE);
        }
        BrandId brandId = command.brandId() == null ? null : usableBrand(command.brandId());

        List<AttributeInput> submitted = command.attributes() == null ? List.of() : command.attributes();
        Map<AttributeTemplateId, AttributeTemplate> templates = assembler.load(
                submitted.stream().map(AttributeInput::templateId).toList());
        List<ProductAttribute> attributes = AttributeValueChecker.check(submitted, List.of(),
                category.getAssignments(), templates, limits, LocalDate.now(clock));

        Product product = Product.create(ProductId.of(ids.generate()), sellerId, category.getId(),
                brandId, command.title(), command.description(), attributes, limits, clock.instant());
        long version = product.nextVersion();
        products.save(product);
        return assembler.assemble(product, version, category.getAssignments(), List.of());
    }

    private BrandId usableBrand(UUID id) {
        var brand = brands.findById(BrandId.of(id))
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));
        if (!brand.isActive()) {
            throw new DomainException(ProductErrorCode.PRODUCT_BRAND_NOT_USABLE);
        }
        return brand.getId();
    }

    public record Command(CatalogActor actor, String title, String description, UUID categoryId, UUID brandId,
                          List<AttributeInput> attributes) {
    }
}
