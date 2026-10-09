package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.*;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetProduct implements QueryHandler<GetProduct.Query, GetProduct.Result> {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;

    @Override
    @Cacheable(value = CacheNames.PRODUCT, key = "#query.productId()")
    public Result handle(Query query) {
        Product product = productRepository.findById(ProductId.of(query.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        Brand brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));
        Category category = categoryRepository.findById(product.getCategoryId())
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        return toResult(product, brand, category);
    }

    private Result toResult(Product product, Brand brand, Category category) {
        List<AttributeTemplateId> templateIds = product.getAttributeValues().stream()
                .map(ProductAttributeValue::templateId)
                .distinct()
                .toList();
        Map<String, AttributeTemplate> templatesById = attributeTemplateRepository.findAllByIds(templateIds).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), t -> t));

        List<AttributeValueDto> attrDtos = product.getAttributeValues().stream()
                .map(av -> {
                    AttributeTemplate template = templatesById.get(av.templateId().getValue());
                    List<AttributeValueItemDto> items = av.values().stream()
                            .map(v -> resolveItem(template, v))
                            .toList();
                    return new AttributeValueDto(
                            av.templateId().getValue(),
                            template != null ? template.getName() : null,
                            template != null ? template.getDisplayName() : null,
                            items,
                            av.isVariantDefining());
                })
                .toList();

        List<ImageDto> imageDtos = product.getImages().stream()
                .map(img -> new ImageDto(img.getId().getValue(), img.getObjectKey(), img.getDisplayOrder()))
                .toList();

        WarrantyDto warrantyDto = product.getWarrantyInfo() == null ? null
                : new WarrantyDto(
                        product.getWarrantyInfo().months(),
                        product.getWarrantyInfo().type(),
                        product.getWarrantyInfo().coverage());

        return new Result(
                product.getId().getValue(),
                product.getSellerId(),
                product.getCategoryId().getValue(),
                category.getName(),
                product.getBrandId().getValue(),
                brand.getName(),
                product.getName(),
                product.getDescription(),
                product.getStatus().name(),
                product.isAdminBlocked(),
                product.isPubliclyVisible(),
                warrantyDto,
                attrDtos,
                imageDtos
        );
    }

    // Product lưu optionId (không copy value string nữa — xem service.md § Copy vs reference). Với
    // SELECT, resolve cả 2: business value (VD "black") + displayValue (VD "Đen") từ đúng option. Với
    // TEXT/NUMBER/BOOLEAN, "stored" chính là raw value, không có option để resolve. Không tìm thấy
    // option khớp (không nên xảy ra, data integrity) thì fallback trả raw stored, không throw lỗi — đây
    // là read path, không nên vỡ cả trang chỉ vì 1 label không resolve được.
    private static AttributeValueItemDto resolveItem(AttributeTemplate template, String stored) {
        if (template == null || !template.getInputType().isSelect()) {
            return new AttributeValueItemDto(stored, stored);
        }
        return template.getOptions().stream()
                .filter(o -> o.getId().getValue().equals(stored))
                .findFirst()
                .map(o -> new AttributeValueItemDto(o.getValue(), o.getDisplayValue()))
                .orElse(new AttributeValueItemDto(stored, stored));
    }

    public record Query(String productId) {}

    public record Result(
            String id,
            String sellerId,
            String categoryId,
            String categoryName,
            String brandId,
            String brandName,
            String name,
            String description,
            String status,
            boolean adminBlocked,
            // INV-CAT-044 — tính từ Product.isPubliclyVisible(), không tự suy lại từ status ở nơi đọc.
            boolean publiclyVisible,
            WarrantyDto warrantyInfo,
            List<AttributeValueDto> attributeValues,
            List<ImageDto> images
    ) {}

    public record AttributeValueDto(
            String templateId,
            String templateName,
            String templateDisplayName,
            List<AttributeValueItemDto> values,
            boolean isVariantDefining
    ) {}

    public record AttributeValueItemDto(String value, String displayValue) {}

    public record ImageDto(String imageId, String objectKey, int displayOrder) {}

    public record WarrantyDto(int months, String type, String coverage) {}
}
