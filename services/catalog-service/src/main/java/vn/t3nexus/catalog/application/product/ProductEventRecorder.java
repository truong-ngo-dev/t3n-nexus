package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.catalog.domain.product.ProductImage;
import vn.t3nexus.catalog.domain.product.ProductSnapshotEvent;
import vn.t3nexus.catalog.domain.product.ProductVisibility;
import vn.t3nexus.catalog.domain.product.SellableUnitChangedEvent;
import vn.t3nexus.catalog.domain.product.SellerState;
import vn.t3nexus.catalog.domain.product.SellerStateRepository;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.outbox.OutboxEventStore;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bộ ghi sự kiện cho sản phẩm theo ADR-0014 (hai sự kiện: bản chụp sản phẩm và thay đổi đơn vị bán được).
 * Ghi vào OutboxEventStore trong cùng giao dịch với thay đổi sản phẩm/biến thể.
 */
@Component
public class ProductEventRecorder {

    public enum Trigger { EDIT, PUBLISH, UNPUBLISH }

    private final OutboxEventStore outbox;
    private final VariantRepository variants;
    private final SellerStateRepository sellerStates;
    private final AttributeTemplateRepository templates;
    private final IdGenerator ids;
    private final Clock clock;

    public ProductEventRecorder(OutboxEventStore outbox, VariantRepository variants,
                                SellerStateRepository sellerStates, AttributeTemplateRepository templates,
                                IdGenerator ids, Clock clock) {
        this.outbox = outbox;
        this.variants = variants;
        this.sellerStates = sellerStates;
        this.templates = templates;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Ghi nhận thay đổi sản phẩm và phát các sự kiện tương ứng ra hộp thư outbox.
     *
     * @param product      sản phẩm sau khi đã lưu
     * @param changedUnits danh sách các mã biến thể thay đổi (chỉ dùng cho Trigger.EDIT)
     * @param trigger      loại kích hoạt (EDIT, PUBLISH, UNPUBLISH)
     */
    public void onChanged(Product product, Collection<VariantId> changedUnits, Trigger trigger) {
        if (trigger == Trigger.EDIT && !product.isPublished()) {
            return;
        }

        Instant now = clock.instant();
        SellerState sellerState = sellerStates.find(product.getSellerId()).orElse(null);
        boolean visible = ProductVisibility.isPubliclyVisible(product, sellerState);
        long version = product.nextVersion();

        List<Variant> allVariants = variants.findByProductId(product.getId());

        // 1. Chuẩn bị và phát ProductSnapshotEvent
        ProductSnapshotEvent snapshotEvent = buildSnapshotEvent(product, allVariants, visible, version, now);
        outbox.store(snapshotEvent);

        // 2. Phát SellableUnitChangedEvent cho các biến thể tương ứng
        if (trigger == Trigger.PUBLISH || trigger == Trigger.UNPUBLISH) {
            for (Variant v : allVariants) {
                SellableUnitChangedEvent unitEvent = new SellableUnitChangedEvent(
                        ids.generate(),
                        now,
                        v.getId(),
                        product.getId(),
                        product.getSellerId(),
                        v.isActive(),
                        visible,
                        version
                );
                outbox.store(unitEvent);
            }
        } else if (trigger == Trigger.EDIT && changedUnits != null && !changedUnits.isEmpty()) {
            Set<VariantId> changedSet = new LinkedHashSet<>(changedUnits);
            for (Variant v : allVariants) {
                if (changedSet.contains(v.getId())) {
                    SellableUnitChangedEvent unitEvent = new SellableUnitChangedEvent(
                            ids.generate(),
                            now,
                            v.getId(),
                            product.getId(),
                            product.getSellerId(),
                            v.isActive(),
                            visible,
                            version
                    );
                    outbox.store(unitEvent);
                }
            }
        }
    }

    private ProductSnapshotEvent buildSnapshotEvent(Product product, List<Variant> allVariants,
                                                    boolean visible, long version, Instant now) {
        // Thu thập thông tin mẫu thuộc tính
        Set<AttributeTemplateId> templateIds = new LinkedHashSet<>();
        for (ProductAttribute attr : product.getAttributes()) {
            templateIds.add(attr.templateId());
        }
        Map<AttributeTemplateId, AttributeTemplate> templateMap = new HashMap<>();
        if (!templateIds.isEmpty()) {
            templates.findAllByIds(templateIds).forEach(t -> templateMap.put(t.getId(), t));
        }

        // 1. attributes: {templateId, inputType, axis, values: danh sách chuỗi}
        List<Map<String, Object>> attributeList = new ArrayList<>();
        for (ProductAttribute attr : product.getAttributes()) {
            Map<String, Object> attrMap = new LinkedHashMap<>();
            attrMap.put("templateId", attr.templateId().getValue().toString());
            AttributeTemplate template = templateMap.get(attr.templateId());
            String inputTypeStr = (template != null && template.getInputType() != null)
                    ? template.getInputType().name()
                    : "UNKNOWN";
            attrMap.put("inputType", inputTypeStr);
            attrMap.put("axis", Boolean.valueOf(attr.axis()));

            List<String> values = new ArrayList<>();
            if (attr.value() != null) {
                values.add(attr.value());
            } else if (attr.optionIds() != null) {
                for (AttributeOptionId optId : attr.optionIds()) {
                    values.add(optId.getValue().toString());
                }
            }
            attrMap.put("values", values);
            attributeList.add(attrMap);
        }

        // 2. images: {id, objectKey, position}
        List<Map<String, Object>> imageList = new ArrayList<>();
        for (ProductImage img : product.getImages()) {
            Map<String, Object> imgMap = new LinkedHashMap<>();
            imgMap.put("id", img.getId().getValue().toString());
            imgMap.put("objectKey", img.getObjectKey());
            imgMap.put("position", Integer.valueOf(img.getPosition()));
            imageList.add(imgMap);
        }

        // 3. variants: {id, combination: List<Map{templateId, optionId}>, price, active} (KHÔNG có SKU)
        List<Map<String, Object>> variantList = new ArrayList<>();
        for (Variant v : allVariants) {
            Map<String, Object> varMap = new LinkedHashMap<>();
            varMap.put("id", v.getId().getValue().toString());

            List<Map<String, Object>> combList = new ArrayList<>();
            for (VariantAttributePair pair : v.getCombination()) {
                Map<String, Object> pairMap = new LinkedHashMap<>();
                pairMap.put("templateId", pair.templateId().getValue().toString());
                pairMap.put("optionId", pair.optionId().getValue().toString());
                combList.add(pairMap);
            }
            varMap.put("combination", combList);
            varMap.put("price", Long.valueOf(v.getPrice()));
            varMap.put("active", Boolean.valueOf(v.isActive()));
            variantList.add(varMap);
        }

        return new ProductSnapshotEvent(
                ids.generate(),
                now,
                product.getId(),
                version,
                product.getSellerId(),
                visible,
                product.getTitle(),
                product.getDescription(),
                product.getBrandId() == null ? null : product.getBrandId().getValue(),
                product.getCategoryId().getValue(),
                attributeList,
                imageList,
                variantList
        );
    }
}
