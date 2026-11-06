package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.product.SellerStateRepository;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.upload.UploadSlots;

import java.util.List;
import java.util.UUID;

/**
 * Chi tiết sản phẩm của tôi, kèm điều kiện còn thiếu để đăng (RM-CAT-04). Chỉ chủ xem được; của người khác như không có.
 * Không ghi, không khóa dòng.
 */
@Service
public class GetMyProduct implements QueryHandler<GetMyProduct.Query, ProductView> {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final VariantRepository variants;
    private final ProductAssembler assembler;

    public GetMyProduct(ProductRepository products, CategoryRepository categories,
                        AttributeTemplateRepository templates, VariantRepository variants, UploadSlots uploads,
                        SellerStateRepository sellerStates) {
        this.products = products;
        this.categories = categories;
        this.variants = variants;
        this.assembler = new ProductAssembler(templates, uploads, sellerStates, categories);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductView handle(Query query) {
        query.actor().requireSeller();
        ProductId id = ProductId.of(query.productId());
        Product product = products.findOwned(id, query.actor().accountId())
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));
        Category category = categories.findById(product.getCategoryId())
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        List<Variant> productVariants = variants.findByProductId(id);
        long version = product.version() == null ? 0L : product.version();
        return assembler.assemble(product, version, category.getAssignments(), productVariants);
    }

    public record Query(CatalogActor actor, UUID productId) {
    }
}
