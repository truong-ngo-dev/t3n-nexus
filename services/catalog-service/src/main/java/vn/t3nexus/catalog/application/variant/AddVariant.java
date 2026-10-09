package vn.t3nexus.catalog.application.variant;

import lombok.RequiredArgsConstructor;
import vn.t3nexus.catalog.application.product.search_sync.PublishProductSearchSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.product.ProductStatus;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;
import vn.t3nexus.catalog.domain.variant.VariantCombination;
import vn.t3nexus.catalog.domain.variant.VariantCombinationPolicy;
import vn.t3nexus.catalog.domain.variant.VariantCreatedEvent;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddVariant implements CommandHandler<AddVariant.Command, AddVariant.Result> {

    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final PublishProductSearchSnapshot publishProductSearchSnapshot;
    private final EventDispatcher eventDispatcher;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCT_VARIANTS, key = "#command.productId()")
    public Result handle(Command command) {
        Product product = productRepository.findByIdForUpdate(ProductId.of(command.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.assertOwnedBy(command.sellerId());

        List<VariantAttributePair> pairs = command.combination().stream()
                .map(dto -> new VariantAttributePair(
                        AttributeTemplateId.of(dto.templateId()),
                        AttributeOptionId.of(dto.optionId())))
                .toList();

        validateCombinationAgainstProduct(product, pairs);

        VariantCombination combination = new VariantCombination(pairs);
        String combinationHash = combination.toHash();

        if (variantRepository.existsByProductIdAndCombinationHash(command.productId(), combinationHash)) {
            throw new DomainException(ProductErrorCode.VARIANT_COMBINATION_EXISTS);
        }

        VariantId variantId = VariantId.of(ulidGenerator.generate());
        Variant variant = Variant.create(
                variantId,
                command.productId(),
                combination,
                command.skuCode(),
                command.price());

        variantRepository.save(variant);

        VariantCreatedEvent event = new VariantCreatedEvent(
                variantId.getValue(), command.productId(), product.getSellerId(),
                variant.isActive(), product.getStatus() == ProductStatus.PUBLISHED);
        eventDispatcher.dispatch(event);

        publishProductSearchSnapshot.publish(command.productId());
        cacheInvalidationPublisher.evict(CacheNames.PRODUCT_VARIANTS, command.productId());

        log.info("[AddVariant] added: variantId={}, productId={}, traceId={}",
                variantId.getValue(), command.productId(), MDC.get("traceId"));

        return new Result(variantId.getValue());
    }

    // Quy tắc tổ hợp (đúng 1 giá trị/trục, giá trị còn dùng được, trong tập Product đã khai) nằm ở
    // VariantCombinationPolicy — INV-CAT-97, INV-CAT-99.
    private void validateCombinationAgainstProduct(Product product, List<VariantAttributePair> pairs) {
        List<AttributeTemplateId> templateIds = pairs.stream()
                .map(VariantAttributePair::templateId)
                .distinct()
                .toList();
        Map<String, AttributeTemplate> templatesById = attributeTemplateRepository.findAllByIds(templateIds).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), t -> t));

        VariantCombinationPolicy.validate(product, pairs, templatesById);
    }

    public record Command(
            String sellerId,
            String productId,
            List<CombinationItemDto> combination,
            String skuCode,
            long price
    ) {}

    public record CombinationItemDto(String templateId, String optionId) {}

    public record Result(String skuId) {}
}
