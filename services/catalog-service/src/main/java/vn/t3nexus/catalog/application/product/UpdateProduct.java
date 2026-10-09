package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import vn.t3nexus.catalog.application.product.search_sync.PublishProductSearchSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateDomainService;
import vn.t3nexus.catalog.domain.product.*;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProduct implements CommandHandler<UpdateProduct.Command, UpdateProduct.Result> {

    private final ProductRepository productRepository;
    private final VariantRepository variantRepository;
    private final AttributeTemplateDomainService attributeTemplateDomainService;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final PublishProductSearchSnapshot publishProductSearchSnapshot;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCT, key = "#command.productId()")
    public Result handle(Command command) {
        Product product = productRepository.findByIdForUpdate(ProductId.of(command.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.assertOwnedBy(command.sellerId());

        List<ProductAttributeValue> attributeValues = command.attributeValues().stream()
                .map(dto -> new ProductAttributeValue(
                        AttributeTemplateId.of(dto.templateId()), dto.values(), dto.isVariantDefining()))
                .toList();

        attributeTemplateDomainService.validateProductAttributes(
                product.getCategoryId(), attributeValues, product.getAttributeValues());
        validateVariantConsistency(product, attributeValues);

        WarrantyInfo warrantyInfo = command.warrantyType() == null ? null
                : new WarrantyInfo(command.warrantyMonths(), command.warrantyType(), command.warrantyCoverage());

        product.update(command.name(), command.description(), warrantyInfo, attributeValues);
        productRepository.save(product);
        eventDispatcher.dispatchAll(product.getDomainEvents());
        product.clearDomainEvents();
        publishProductSearchSnapshot.publish(command.productId());
        cacheInvalidationPublisher.evict(CacheNames.PRODUCT, command.productId());

        log.info("[UpdateProduct] updated: productId={}, traceId={}", command.productId(), MDC.get("traceId"));

        return new Result();
    }

    // Chỉ chạy khi Product đã có ít nhất 1 Variant — trước đó Product được sửa attributeValues tự do,
    // không guard gì cả (chưa có SKU nào phụ thuộc).
    private void validateVariantConsistency(Product product, List<ProductAttributeValue> submitted) {
        List<Variant> variants = variantRepository.findByProductId(product.getId().getValue());
        if (variants.isEmpty()) {
            return;
        }

        // isVariantDefining khoá lại NGUYÊN TẬP sau khi Product đã có Variant — thêm mới, gỡ bớt, hay đổi
        // cờ 1 template bất kỳ đều bị chặn như nhau, không chỉ so từng entry trùng cả 2 bản (nếu chỉ so
        // entry trùng sẽ bỏ lọt trường hợp thêm hẳn 1 attribute MỚI với isVariantDefining=true — Variant
        // cũ sẽ "thiếu" đúng trục mới này mà không guard nào bắt được).
        Set<String> previousVariantDefining = product.getAttributeValues().stream()
                .filter(ProductAttributeValue::isVariantDefining)
                .map(v -> v.templateId().getValue())
                .collect(Collectors.toSet());
        Set<String> submittedVariantDefining = submitted.stream()
                .filter(ProductAttributeValue::isVariantDefining)
                .map(v -> v.templateId().getValue())
                .collect(Collectors.toSet());
        if (!previousVariantDefining.equals(submittedVariantDefining)) {
            throw new DomainException(ProductErrorCode.VARIANT_DEFINING_LOCKED_AFTER_VARIANT);
        }

        // Gỡ 1 giá trị mà Variant đang dùng sẽ làm SKU đó mồ côi (Product không còn "công nhận" đặc tính
        // mà SKU vẫn khai) — không phân biệt Variant ACTIVE/INACTIVE (SKU tắt vẫn tồn tại, vẫn
        // tham chiếu giá trị đó).
        Map<String, List<String>> submittedByTemplate = submitted.stream()
                .collect(Collectors.toMap(v -> v.templateId().getValue(), ProductAttributeValue::values));

        for (ProductAttributeValue previous : product.getAttributeValues()) {
            String templateId = previous.templateId().getValue();
            List<String> submittedValues = submittedByTemplate.getOrDefault(templateId, List.of());
            for (String removedValue : previous.values()) {
                if (submittedValues.contains(removedValue)) {
                    continue;
                }
                boolean stillUsed = variants.stream()
                        .flatMap(v -> v.getCombination().getPairs().stream())
                        .anyMatch(pair -> pair.templateId().getValue().equals(templateId)
                                && pair.optionId().getValue().equals(removedValue));
                if (stillUsed) {
                    throw new DomainException(ProductErrorCode.ATTRIBUTE_VALUE_STILL_USED_BY_VARIANT);
                }
            }
        }
    }

    public record Command(
            String sellerId,
            String productId,
            String name,
            String description,
            Integer warrantyMonths,
            String warrantyType,
            String warrantyCoverage,
            List<AttributeValueDto> attributeValues
    ) {}

    public record AttributeValueDto(String templateId, List<String> values, boolean isVariantDefining) {}

    public record Result() {}
}
