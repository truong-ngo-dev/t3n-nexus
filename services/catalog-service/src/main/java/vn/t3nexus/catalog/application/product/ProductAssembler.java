package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.application.variant.VariantView;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.MissingPublishCondition;
import vn.t3nexus.catalog.domain.product.MissingPublishConditions;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.catalog.domain.product.SellerState;
import vn.t3nexus.catalog.domain.product.SellerStateRepository;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.lib.upload.UploadSlots;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dựng {@link ProductView} và nạp mẫu thuộc tính cho các use case sản phẩm (một lần đọc mẫu cho mỗi lệnh). */
@Component
public class ProductAssembler {

    private final AttributeTemplateRepository templates;
    private final UploadSlots uploads;
    private final SellerStateRepository sellerStates;
    private final CategoryRepository categories;

    public ProductAssembler(AttributeTemplateRepository templates, UploadSlots uploads,
                            SellerStateRepository sellerStates, CategoryRepository categories) {
        this.templates = templates;
        this.uploads = uploads;
        this.sellerStates = sellerStates;
        this.categories = categories;
    }

    public Map<AttributeTemplateId, AttributeTemplate> load(Collection<AttributeTemplateId> ids) {
        Map<AttributeTemplateId, AttributeTemplate> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            templates.findAllByIds(new LinkedHashSet<>(ids)).forEach(t -> byId.put(t.getId(), t));
        }
        return byId;
    }

    /** Tính các điều kiện còn thiếu để sản phẩm có thể đăng (CAP-CAT-09). */
    public List<MissingPublishCondition> missingConditions(Product product, List<Variant> variants) {
        Category category = categories.findById(product.getCategoryId()).orElse(null);
        List<CategoryAttributeAssignment> assignments = category == null ? List.of() : category.getAssignments();

        Set<AttributeTemplateId> needed = new LinkedHashSet<>();
        product.getAttributes().forEach(a -> needed.add(a.templateId()));
        assignments.stream().filter(CategoryAttributeAssignment::isRequired)
                .forEach(a -> needed.add(a.getAttributeTemplateId()));
        Map<AttributeTemplateId, AttributeTemplate> byId = load(needed);

        boolean hasActiveVariant = variants != null && variants.stream().anyMatch(Variant::isActive);
        boolean sellerApproved = sellerStates.find(product.getSellerId())
                .map(SellerState::isApproved)
                .orElse(false);
        boolean categoryUsable = category != null && category.isLeaf() && category.isActive()
                && !categories.hasInactiveAncestor(category.getId());

        return MissingPublishConditions.of(product, assignments, byId, hasActiveVariant, sellerApproved, categoryUsable);
    }

    /** {@code version}: phiên bản sau thay đổi (hoặc hiện tại nếu không ghi). */
    public ProductView assemble(Product product, long version, List<CategoryAttributeAssignment> assignments,
                         List<Variant> variants) {
        Set<AttributeTemplateId> needed = new LinkedHashSet<>();
        product.getAttributes().forEach(a -> needed.add(a.templateId()));
        assignments.stream().filter(CategoryAttributeAssignment::isRequired)
                .forEach(a -> needed.add(a.getAttributeTemplateId()));
        Map<AttributeTemplateId, AttributeTemplate> byId = load(needed);

        List<AttributeView> attributes = product.getAttributes().stream()
                .filter(a -> byId.containsKey(a.templateId()))
                .map(a -> AttributeView.of(a, byId.get(a.templateId())))
                .toList();
        List<MissingPublishCondition> missing = missingConditions(product, variants);

        List<ImageView> images = product.getImages().stream()
                .map(img -> new ImageView(
                        img.getId().getValue(),
                        uploads.urlOf("product-image", img.getObjectKey()),
                        img.getPosition(),
                        img.getContentType(),
                        img.getSizeBytes()
                ))
                .toList();

        return new ProductView(product.getId().getValue(), product.getCategoryId().getValue(),
                product.getBrandId() == null ? null : product.getBrandId().getValue(), product.getTitle(),
                product.getDescription(), product.getStatus(), version, attributes, images,
                variants.stream().map(VariantView::of).toList(), missing, product.getPublishedAt(),
                product.getCreatedAt(), product.getUpdatedAt());
    }

    public static Set<AttributeTemplateId> templateIds(Collection<ProductAttribute> attributes) {
        Set<AttributeTemplateId> ids = new LinkedHashSet<>();
        attributes.forEach(a -> ids.add(a.templateId()));
        return ids;
    }
}
