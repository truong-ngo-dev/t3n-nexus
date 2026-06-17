package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateDomainService;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.*;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateProduct implements CommandHandler<CreateProduct.Command, CreateProduct.Result> {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final AttributeTemplateDomainService attributeTemplateDomainService;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    public Result handle(Command command) {
        categoryRepository.findById(CategoryId.of(command.categoryId()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        brandRepository.findById(BrandId.of(command.brandId()))
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));

        List<ProductAttributeValue> attributeValues = command.attributeValues().stream()
                .map(dto -> new ProductAttributeValue(
                        AttributeTemplateId.of(dto.templateId()),
                        dto.value()))
                .toList();

        attributeTemplateDomainService.validateProductAttributes(
                CategoryId.of(command.categoryId()), attributeValues);

        WarrantyInfo warrantyInfo = command.warrantyType() == null ? null
                : new WarrantyInfo(command.warrantyMonths(), command.warrantyType(), command.warrantyCoverage());

        ProductId id = ProductId.of(ulidGenerator.generate());
        Product product = Product.create(
                id,
                command.sellerId(),
                CategoryId.of(command.categoryId()),
                BrandId.of(command.brandId()),
                command.name(),
                command.description(),
                warrantyInfo,
                attributeValues
        );

        productRepository.save(product);

        log.info("[CreateProduct] created: productId={}, sellerId={}, traceId={}",
                id.getValue(), command.sellerId(), MDC.get("traceId"));

        return new Result(id.getValue());
    }

    public record Command(
            String sellerId,
            String categoryId,
            String brandId,
            String name,
            String description,
            Integer warrantyMonths,
            String warrantyType,
            String warrantyCoverage,
            List<AttributeValueDto> attributeValues
    ) {}

    public record AttributeValueDto(String templateId, String value) {}

    public record Result(String id) {}
}
