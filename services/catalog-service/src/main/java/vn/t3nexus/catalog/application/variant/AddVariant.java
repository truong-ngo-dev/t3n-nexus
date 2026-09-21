package vn.t3nexus.catalog.application.variant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttributeValue;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.product.ProductStatus;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;
import vn.t3nexus.catalog.domain.variant.VariantCombination;
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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddVariant implements CommandHandler<AddVariant.Command, AddVariant.Result> {

    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final EventDispatcher eventDispatcher;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCT_VARIANTS, key = "#command.productId()")
    public Result handle(Command command) {
        Product product = productRepository.findById(ProductId.of(command.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

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

        // Cùng transaction với save Variant — bump usageCount cho mỗi option vừa dùng trong combination,
        // dùng để guard OPTION_IN_USE lúc deactivate sau này (xem AttributeOption.deactivate()).
        List<AttributeOptionId> optionIds = pairs.stream().map(VariantAttributePair::optionId).toList();
        attributeTemplateRepository.incrementOptionUsage(optionIds);

        VariantCreatedEvent event = new VariantCreatedEvent(
                variantId.getValue(), command.productId(), product.getSellerId(),
                variant.isActive(), product.getStatus() == ProductStatus.PUBLISHED);
        eventDispatcher.dispatch(event);

        cacheInvalidationPublisher.evict(CacheNames.PRODUCT_VARIANTS, command.productId());

        log.info("[AddVariant] added: variantId={}, productId={}, traceId={}",
                variantId.getValue(), command.productId(), MDC.get("traceId"));

        return new Result(variantId.getValue());
    }

    // Combination chỉ dựa vào cái Product đã khai — KHÔNG fallback về master data nữa. Product chưa
    // khai gì cho templateId này thì reject thẳng (VARIANT_OPTION_NOT_DECLARED_BY_PRODUCT) — muốn dùng
    // attribute nào để tách SKU, Product phải khai tường minh trước, không có "mặc định ngầm". So sánh
    // thuần theo AttributeOptionId (Product giờ tham chiếu option bằng ID, không copy value string).
    //
    // Trục nào BẮT BUỘC dùng để tách SKU do chính Product tự khai (isVariantDefining, Seller quyết định
    // per-listing — KHÔNG dựa vào `required` của category, vì required chỉ là chính sách completeness của
    // Admin, không phải quyết định "attribute này có tách SKU hay không" — 1 attribute category bắt buộc
    // khai (VD Xuất xứ) hoàn toàn có thể KHÔNG phải trục biến thể). Các attribute variant-defining tạo
    // thành 1 ma trận (Cartesian) — mọi Variant là 1 điểm trong không gian đó nên phải khai ĐỦ mọi trục,
    // không được thiếu 1 trục nào, khớp Shopify (mọi variant bắt buộc đủ giá trị cho mọi product option)
    // và Amazon (mọi ASIN con phải khai đủ attribute trong variation theme) — validateProductAttributes đã
    // đảm bảo isVariantDefining=true chỉ tồn tại trên template SELECT, nên không cần check lại ở đây.
    private void validateCombinationAgainstProduct(Product product, List<VariantAttributePair> pairs) {
        List<AttributeTemplateId> templateIds = pairs.stream()
                .map(VariantAttributePair::templateId)
                .distinct()
                .toList();
        Map<String, AttributeTemplate> templatesById = attributeTemplateRepository.findAllByIds(templateIds).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), t -> t));

        Map<String, ProductAttributeValue> declaredByTemplate = product.getAttributeValues().stream()
                .collect(Collectors.toMap(av -> av.templateId().getValue(), av -> av));

        for (VariantAttributePair pair : pairs) {
            AttributeTemplate template = templatesById.get(pair.templateId().getValue());
            if (template == null) {
                throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_NOT_FOUND);
            }

            // optionId phải thực sự thuộc template này — input validation thuần, không phải business
            // decision dựa theo trạng thái hiện tại (không check ACTIVE ở đây, chỉ check tồn tại).
            boolean optionBelongsToTemplate = template.getOptions().stream()
                    .anyMatch(o -> o.getId().equals(pair.optionId()));
            if (!optionBelongsToTemplate) {
                throw new DomainException(AttributeTemplateErrorCode.OPTION_NOT_FOUND);
            }

            ProductAttributeValue declared = declaredByTemplate.get(pair.templateId().getValue());
            boolean allowed = declared != null && declared.values().contains(pair.optionId().getValue());

            if (!allowed) {
                throw new DomainException(ProductErrorCode.VARIANT_OPTION_NOT_DECLARED_BY_PRODUCT);
            }
        }

        Set<String> pairTemplateIds = pairs.stream()
                .map(p -> p.templateId().getValue())
                .collect(Collectors.toSet());
        boolean missingVariantDefiningAttribute = product.getAttributeValues().stream()
                .filter(ProductAttributeValue::isVariantDefining)
                .anyMatch(av -> !pairTemplateIds.contains(av.templateId().getValue()));
        if (missingVariantDefiningAttribute) {
            throw new DomainException(ProductErrorCode.VARIANT_MISSING_VARIANT_DEFINING_ATTRIBUTE);
        }
    }

    public record Command(
            String productId,
            List<CombinationItemDto> combination,
            String skuCode,
            long price
    ) {}

    public record CombinationItemDto(String templateId, String optionId) {}

    public record Result(String skuId) {}
}
