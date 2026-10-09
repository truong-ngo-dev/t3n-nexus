package vn.t3nexus.catalog.application.product.search_sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOption;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.category.Discovery;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttributeValue;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductImage;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.variant.SkuImage;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.outbox.OutboxEventStore;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dựng + ghi outbox {@link ProductSearchSnapshotEvent} cho 1 sản phẩm. Gọi ở CUỐI mọi use case làm đổi
 * Product hoặc Variant, trong cùng transaction (MANDATORY) — snapshot phản ánh đúng state vừa ghi, và
 * outbox commit/rollback cùng thay đổi gốc.
 * <br>Sản phẩm chưa từng publish ({@code publishedAt == null}) → bỏ qua: search không cần biết DRAFT, và
 * DRAFT xoá cứng được ({@code DeleteProduct}) — không phát thì search không bao giờ có document mồ côi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishProductSearchSnapshot {

    private final ProductRepository productRepository;
    private final VariantRepository variantRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;
    private final ProductSearchSnapshotPort snapshotPort;
    private final OutboxEventStore outboxEventStore;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String productId) {
        Product product = productRepository.findById(ProductId.of(productId)).orElse(null);
        if (product == null || product.getPublishedAt() == null) {
            return;
        }

        long version = snapshotPort.nextVersion(productId);
        List<Variant> variants = variantRepository.findByProductId(productId);

        String brandName = brandRepository.findById(product.getBrandId())
                .map(Brand::getName)
                .orElse(null);

        Map<String, CategoryAttributeAssignment> assignmentByTemplate = categoryRepository
                .findById(product.getCategoryId())
                .map(c -> c.getAssignments().stream().collect(Collectors.toMap(
                        a -> a.getAttributeTemplateId().getValue(), Function.identity())))
                .orElse(Map.of());

        Map<String, AttributeTemplate> templateById = loadTemplates(product, variants);

        ProductSearchSnapshotEvent.Payload payload = new ProductSearchSnapshotEvent.Payload(
                productId,
                version,
                product.getSellerId(),
                product.getName(),
                product.getDescription(),
                product.getBrandId().getValue(),
                brandName,
                product.getCategoryId().getValue(),
                product.getStatus().name(),
                product.isAdminBlocked(),
                product.getPublishedAt(),
                product.getImages().stream()
                        .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
                        .map(ProductImage::getObjectKey)
                        .toList(),
                product.getAttributeValues().stream()
                        .map(av -> toAttribute(av, templateById, assignmentByTemplate))
                        .toList(),
                variants.stream()
                        .map(v -> toVariant(v, templateById))
                        .toList());

        outboxEventStore.store(new ProductSearchSnapshotEvent(payload));

        log.debug("[PublishProductSearchSnapshot] productId={}, version={}, variants={}",
                productId, version, variants.size());
    }

    // 1 round-trip cho mọi template xuất hiện ở cả attribute cấp sản phẩm lẫn combination của Variant.
    private Map<String, AttributeTemplate> loadTemplates(Product product, List<Variant> variants) {
        Set<AttributeTemplateId> ids = new LinkedHashSet<>();
        product.getAttributeValues().forEach(av -> ids.add(av.templateId()));
        variants.forEach(v -> v.getCombination().getPairs().forEach(p -> ids.add(p.templateId())));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return attributeTemplateRepository.findAllByIds(ids).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), Function.identity()));
    }

    private ProductSearchSnapshotEvent.Attribute toAttribute(ProductAttributeValue av,
                                                             Map<String, AttributeTemplate> templateById,
                                                             Map<String, CategoryAttributeAssignment> assignmentByTemplate) {
        String templateId = av.templateId().getValue();
        AttributeTemplate template = templateById.get(templateId);
        CategoryAttributeAssignment assignment = assignmentByTemplate.get(templateId);

        List<ProductSearchSnapshotEvent.Value> values = av.values().stream()
                .map(value -> new ProductSearchSnapshotEvent.Value(value, labelOf(template, value)))
                .toList();

        Discovery discovery = assignment != null ? assignment.getDiscovery() : Discovery.NONE;
        return new ProductSearchSnapshotEvent.Attribute(
                templateId,
                template == null ? null : template.getInputType().name(),
                av.isVariantDefining(),
                discovery.search() == null ? null : discovery.search().name(),
                discovery.filter() != null,
                discovery.sort() != null,
                values);
    }

    private ProductSearchSnapshotEvent.Variant toVariant(Variant variant, Map<String, AttributeTemplate> templateById) {
        List<ProductSearchSnapshotEvent.CombinationItem> combination = variant.getCombination().getPairs().stream()
                .map((VariantAttributePair p) -> new ProductSearchSnapshotEvent.CombinationItem(
                        p.templateId().getValue(),
                        p.optionId().getValue(),
                        labelOf(templateById.get(p.templateId().getValue()), p.optionId().getValue())))
                .toList();

        return new ProductSearchSnapshotEvent.Variant(
                variant.getId().getValue(),
                variant.getPrice(),
                variant.isActive(),
                variant.getImages().stream()
                        .sorted(Comparator.comparingInt(SkuImage::getDisplayOrder))
                        .map(SkuImage::getObjectKey)
                        .toList(),
                combination);
    }

    // SELECT: value là optionId → displayValue của option. Kiểu khác: value chính là giá trị hiển thị.
    private String labelOf(AttributeTemplate template, String value) {
        if (template == null || !template.getInputType().isSelect()) {
            return value;
        }
        return template.getOptions().stream()
                .filter(o -> o.getId().getValue().equals(value))
                .map(AttributeOption::getDisplayValue)
                .findFirst()
                .orElse(null);
    }
}
