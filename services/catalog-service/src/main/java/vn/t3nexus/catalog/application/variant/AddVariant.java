package vn.t3nexus.catalog.application.variant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;
import vn.t3nexus.catalog.domain.variant.VariantCombinationPolicy;
import vn.t3nexus.catalog.domain.variant.VariantErrorCode;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Người bán thêm biến thể (CAP-CAT-05). Khóa dòng sản phẩm, rồi: đếm (tối đa theo giới hạn, kể cả biến thể đã ngừng,
 * INV-CAT-93), kiểm tổ hợp với trục của sản phẩm (INV-CAT-94), kiểm giá và SKU, kiểm sớm tổ hợp và SKU đã dùng
 * (ràng buộc duy nhất của cơ sở dữ liệu là chốt cuối), ghi biến thể và tăng phiên bản sản phẩm đúng một (INV-CAT-36).
 */
@Service
public class AddVariant implements CommandHandler<AddVariant.Command, VariantView> {

    private final ProductRepository products;
    private final VariantRepository variants;
    private final AttributeTemplateRepository templates;
    private final IdGenerator ids;
    private final Clock clock;
    private final ProductLimits limits;
    private final vn.t3nexus.catalog.application.product.ProductEventRecorder recorder;

    public AddVariant(ProductRepository products, VariantRepository variants, AttributeTemplateRepository templates,
                      IdGenerator ids, Clock clock, ProductLimits limits,
                      vn.t3nexus.catalog.application.product.ProductEventRecorder recorder) {
        this.products = products;
        this.variants = variants;
        this.templates = templates;
        this.ids = ids;
        this.clock = clock;
        this.limits = limits;
        this.recorder = recorder;
    }

    @Override
    @Transactional
    public VariantView handle(Command command) {
        Product product = VariantLocator.lockOwnProduct(products, command.actor(), command.productId());

        if (variants.countByProductId(product.getId()) >= limits.maxVariants()) {
            throw new DomainException(VariantErrorCode.VARIANT_LIMIT_REACHED);
        }
        VariantCombinationPolicy.validate(product, command.combination(), loadTemplates(command.combination()));

        Instant now = clock.instant();
        Variant variant = Variant.create(VariantId.of(ids.generate()), product.getId(), product.getSellerId(),
                command.combination(), command.price(), command.sku(), limits, now);
        if (variants.existsByProductAndCombinationKey(product.getId(), variant.getCombinationKey())) {
            throw new DomainException(VariantErrorCode.VARIANT_COMBINATION_TAKEN);
        }
        if (variant.getSku() != null && variants.existsBySellerAndSku(product.getSellerId(), variant.getSku())) {
            throw new DomainException(VariantErrorCode.VARIANT_SKU_TAKEN);
        }

        variants.save(variant);
        product.recordChange(now);
        products.save(product);
        recorder.onChanged(product, List.of(variant.getId()), vn.t3nexus.catalog.application.product.ProductEventRecorder.Trigger.EDIT);
        return VariantView.of(variant);
    }

    private Map<AttributeTemplateId, AttributeTemplate> loadTemplates(List<VariantAttributePair> pairs) {
        Map<AttributeTemplateId, AttributeTemplate> byId = new HashMap<>();
        var wanted = pairs.stream().map(VariantAttributePair::templateId).distinct().toList();
        if (!wanted.isEmpty()) {
            templates.findAllByIds(wanted).forEach(t -> byId.put(t.getId(), t));
        }
        return byId;
    }

    public record Command(CatalogActor actor, UUID productId, List<VariantAttributePair> combination, long price,
                          String sku) {
    }
}
