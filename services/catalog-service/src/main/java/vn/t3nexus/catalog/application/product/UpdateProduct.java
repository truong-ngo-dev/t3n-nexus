package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateDomainService;
import vn.t3nexus.catalog.domain.product.*;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProduct implements CommandHandler<UpdateProduct.Command, UpdateProduct.Result> {

    private final ProductRepository productRepository;
    private final AttributeTemplateDomainService attributeTemplateDomainService;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCT, key = "#command.productId()")
    public Result handle(Command command) {
        Product product = productRepository.findById(ProductId.of(command.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        List<ProductAttributeValue> attributeValues = command.attributeValues().stream()
                .map(dto -> new ProductAttributeValue(
                        AttributeTemplateId.of(dto.templateId()),
                        dto.value()))
                .toList();

        attributeTemplateDomainService.validateProductAttributes(
                product.getCategoryId(), attributeValues);

        WarrantyInfo warrantyInfo = command.warrantyType() == null ? null
                : new WarrantyInfo(command.warrantyMonths(), command.warrantyType(), command.warrantyCoverage());

        product.update(command.name(), command.description(), warrantyInfo, attributeValues);
        productRepository.save(product);
        eventDispatcher.dispatchAll(product.getDomainEvents());
        product.clearDomainEvents();
        cacheInvalidationPublisher.evict(CacheNames.PRODUCT, command.productId());

        log.info("[UpdateProduct] updated: productId={}, traceId={}", command.productId(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(
            String productId,
            String name,
            String description,
            Integer warrantyMonths,
            String warrantyType,
            String warrantyCoverage,
            List<AttributeValueDto> attributeValues
    ) {}

    public record AttributeValueDto(String templateId, String value) {}

    public record Result() {}
}
