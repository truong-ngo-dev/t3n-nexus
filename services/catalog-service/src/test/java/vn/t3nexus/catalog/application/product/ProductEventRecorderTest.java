package vn.t3nexus.catalog.application.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductImage;
import vn.t3nexus.catalog.domain.product.ProductImageId;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.catalog.domain.product.ProductSnapshotEvent;
import vn.t3nexus.catalog.domain.product.ProductStatus;
import vn.t3nexus.catalog.domain.product.SellableUnitChangedEvent;
import vn.t3nexus.catalog.application.variant.FakeVariantRepository;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.outbox.OutboxEventStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductEventRecorderTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

    private OutboxEventStore outbox;
    private VariantRepository variantRepository;
    private FakeSellerStateRepository sellerStates;
    private AttributeTemplateRepository templates;
    private IdGenerator ids;
    private Clock clock;
    private ProductEventRecorder recorder;

    private AttributeTemplate colorTemplate;
    private AttributeTemplate textTemplate;
    private AttributeOptionId redOptionId;

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxEventStore.class);
        variantRepository = new FakeVariantRepository();
        sellerStates = new FakeSellerStateRepository();
        templates = mock(AttributeTemplateRepository.class);

        AtomicLong counter = new AtomicLong(1);
        ids = () -> new UUID(0L, counter.getAndIncrement());
        clock = Clock.fixed(NOW, ZoneOffset.UTC);

        recorder = new ProductEventRecorder(outbox, variantRepository, sellerStates, templates, ids, clock);

        AttributeTemplateId colorId = AttributeTemplateId.of(UUID.fromString("00000000-0000-7000-8000-000000000010"));
        redOptionId = AttributeOptionId.of(UUID.fromString("00000000-0000-7000-8000-000000000011"));
        colorTemplate = AttributeTemplate.reconstitute(colorId, "color", "Màu sắc", null,
                InputType.SINGLE_SELECT, null, vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus.ACTIVE,
                List.of(), 1L, NOW, NOW);

        AttributeTemplateId textId = AttributeTemplateId.of(UUID.fromString("00000000-0000-7000-8000-000000000020"));
        textTemplate = AttributeTemplate.reconstitute(textId, "material", "Chất liệu", null,
                InputType.TEXT, null, vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus.ACTIVE,
                List.of(), 1L, NOW, NOW);

        when(templates.findAllByIds(any())).thenAnswer(inv -> {
            java.util.Collection<AttributeTemplateId> coll = inv.getArgument(0);
            List<AttributeTemplate> list = new ArrayList<>();
            if (coll.contains(colorId)) list.add(colorTemplate);
            if (coll.contains(textId)) list.add(textTemplate);
            return list;
        });
    }

    private Product createProduct(ProductStatus status, Long version) {
        ProductId pid = ProductId.of(UUID.fromString("00000000-0000-7000-8000-000000000001"));
        CategoryId cid = CategoryId.of(UUID.fromString("00000000-0000-7000-8000-000000000002"));

        List<ProductAttribute> attrs = List.of(
                ProductAttribute.ofOptions(colorTemplate.getId(), true, List.of(redOptionId)),
                ProductAttribute.ofValue(textTemplate.getId(), "Cotton")
        );
        List<ProductImage> imgs = List.of(
                ProductImage.of(ProductImageId.of(UUID.fromString("00000000-0000-7000-8000-000000000030")), "key-img-1", "image/jpeg", 1024L, 0)
        );

        Instant pubAt = status == ProductStatus.PUBLISHED ? NOW : null;
        return Product.reconstitute(pid, SELLER_ID, cid, null, "Sản phẩm A", "Mô tả A",
                status, attrs, imgs, pubAt, version, NOW, NOW);
    }

    private Variant createVariant(ProductId productId, VariantId vid, boolean active) {
        Variant v = Variant.create(vid, productId, SELLER_ID,
                List.of(new VariantAttributePair(colorTemplate.getId(), redOptionId)),
                100_000L, "SKU-001", ProductLimits.defaults(), NOW);
        if (!active) {
            v.deactivate(NOW);
        }
        variantRepository.save(v);
        return v;
    }

    @Test
    @DisplayName("nháp + EDIT -> không phát (verifyNoInteractions)")
    void draftEditNoEvent() {
        Product draft = createProduct(ProductStatus.DRAFT, null);
        recorder.onChanged(draft, List.of(), ProductEventRecorder.Trigger.EDIT);
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("UNPUBLISHED + EDIT -> không phát")
    void unpublishedEditNoEvent() {
        Product unpublished = createProduct(ProductStatus.UNPUBLISHED, 2L);
        recorder.onChanged(unpublished, List.of(), ProductEventRecorder.Trigger.EDIT);
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("PUBLISHED + EDIT rỗng -> đúng 1 snapshot, visible=true (người bán đã duyệt), version = product.nextVersion()")
    void publishedEditEmptyChangedUnits() {
        sellerStates.approve(SELLER_ID);
        Product published = createProduct(ProductStatus.PUBLISHED, 3L);

        recorder.onChanged(published, List.of(), ProductEventRecorder.Trigger.EDIT);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, times(1)).store(captor.capture());

        DomainEvent event = captor.getValue();
        assertThat(event).isInstanceOf(ProductSnapshotEvent.class);
        assertThat(event.getRoutingKey()).isEqualTo("catalog.product.snapshot");
        assertThat(event.getAggregateId()).isEqualTo(published.getId().getValue().toString());
        assertThat(event.getAggregateType()).isEqualTo("Product");

        ProductSnapshotEvent snapshot = (ProductSnapshotEvent) event;
        assertThat(snapshot.visible()).isTrue();
        assertThat(snapshot.version()).isEqualTo(published.nextVersion()); // 4L
    }

    @Test
    @DisplayName("PUBLISHED + EDIT với 1 biến thể đổi -> snapshot + đúng 1 sự kiện đơn vị của biến thể đó (active đúng, không có khóa price trong payload)")
    void publishedEditWithOneChangedUnit() {
        sellerStates.approve(SELLER_ID);
        Product published = createProduct(ProductStatus.PUBLISHED, 5L);

        Variant v1 = createVariant(published.getId(), VariantId.of(UUID.fromString("00000000-0000-7000-8000-000000000041")), true);
        Variant v2 = createVariant(published.getId(), VariantId.of(UUID.fromString("00000000-0000-7000-8000-000000000042")), true);

        recorder.onChanged(published, List.of(v1.getId()), ProductEventRecorder.Trigger.EDIT);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, times(2)).store(captor.capture());

        List<DomainEvent> events = captor.getAllValues();
        assertThat(events.get(0)).isInstanceOf(ProductSnapshotEvent.class);
        assertThat(events.get(1)).isInstanceOf(SellableUnitChangedEvent.class);

        SellableUnitChangedEvent unitEvent = (SellableUnitChangedEvent) events.get(1);
        assertThat(unitEvent.getRoutingKey()).isEqualTo("catalog.sellable-unit.changed");
        assertThat(unitEvent.getAggregateId()).isEqualTo(published.getId().getValue().toString());
        assertThat(unitEvent.variantId()).isEqualTo(v1.getId());
        assertThat(unitEvent.active()).isTrue();
        assertThat(unitEvent.visible()).isTrue();
        assertThat(unitEvent.version()).isEqualTo(published.nextVersion());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) unitEvent.getPayload();
        assertThat(payload).doesNotContainKey("price");
        assertThat(payload).containsEntry("variantId", v1.getId().getValue().toString());
        assertThat(payload).containsEntry("productId", published.getId().getValue().toString());
        assertThat(payload).containsEntry("sellerId", SELLER_ID.toString());
        assertThat(payload).containsEntry("active", true);
        assertThat(payload).containsEntry("visible", true);
        assertThat(payload).containsEntry("version", 6L);
        assertThat(payload).containsKey("occurredAt");
    }

    @Test
    @DisplayName("PUBLISH với 3 biến thể (1 đã ngừng) -> 1 snapshot (visible=true) + 3 sự kiện đơn vị cùng version, có cả biến thể active=false")
    void publishWithThreeVariantsOneInactive() {
        sellerStates.approve(SELLER_ID);
        Product product = createProduct(ProductStatus.PUBLISHED, 1L);

        Variant v1 = createVariant(product.getId(), VariantId.of(UUID.fromString("00000000-0000-7000-8000-000000000051")), true);
        Variant v2 = createVariant(product.getId(), VariantId.of(UUID.fromString("00000000-0000-7000-8000-000000000052")), false);
        Variant v3 = createVariant(product.getId(), VariantId.of(UUID.fromString("00000000-0000-7000-8000-000000000053")), true);

        recorder.onChanged(product, List.of(), ProductEventRecorder.Trigger.PUBLISH);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, times(4)).store(captor.capture());

        List<DomainEvent> events = captor.getAllValues();
        assertThat(events.get(0)).isInstanceOf(ProductSnapshotEvent.class);
        ProductSnapshotEvent snapshot = (ProductSnapshotEvent) events.get(0);
        assertThat(snapshot.visible()).isTrue();
        assertThat(snapshot.version()).isEqualTo(product.nextVersion());

        List<SellableUnitChangedEvent> unitEvents = events.subList(1, 4).stream()
                .map(e -> (SellableUnitChangedEvent) e).toList();
        assertThat(unitEvents).hasSize(3);
        assertThat(unitEvents).allMatch(e -> e.version() == product.nextVersion());
        assertThat(unitEvents).allMatch(SellableUnitChangedEvent::visible);

        SellableUnitChangedEvent inactiveEvent = unitEvents.stream()
                .filter(e -> e.variantId().equals(v2.getId()))
                .findFirst().orElseThrow();
        assertThat(inactiveEvent.active()).isFalse();
    }

    @Test
    @DisplayName("UNPUBLISH -> snapshot visible=false + 3 sự kiện đơn vị visible=false")
    void unpublishWithThreeVariants() {
        sellerStates.approve(SELLER_ID);
        Product product = createProduct(ProductStatus.UNPUBLISHED, 4L);

        Variant v1 = createVariant(product.getId(), VariantId.of(UUID.fromString("00000000-0000-7000-8000-000000000061")), true);
        Variant v2 = createVariant(product.getId(), VariantId.of(UUID.fromString("00000000-0000-7000-8000-000000000062")), false);
        Variant v3 = createVariant(product.getId(), VariantId.of(UUID.fromString("00000000-0000-7000-8000-000000000063")), true);

        recorder.onChanged(product, List.of(), ProductEventRecorder.Trigger.UNPUBLISH);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, times(4)).store(captor.capture());

        List<DomainEvent> events = captor.getAllValues();
        ProductSnapshotEvent snapshot = (ProductSnapshotEvent) events.get(0);
        assertThat(snapshot.visible()).isFalse();

        List<SellableUnitChangedEvent> unitEvents = events.subList(1, 4).stream()
                .map(e -> (SellableUnitChangedEvent) e).toList();
        assertThat(unitEvents).allMatch(e -> !e.visible());
    }

    @Test
    @DisplayName("PUBLISH khi người bán KHÔNG có bản sao -> visible=false")
    void publishSellerNotFound() {
        // sellerStates không approve SELLER_ID
        Product product = createProduct(ProductStatus.PUBLISHED, 1L);
        createVariant(product.getId(), VariantId.of(UUID.fromString("00000000-0000-7000-8000-000000000071")), true);

        recorder.onChanged(product, List.of(), ProductEventRecorder.Trigger.PUBLISH);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, times(2)).store(captor.capture());

        ProductSnapshotEvent snapshot = (ProductSnapshotEvent) captor.getAllValues().get(0);
        assertThat(snapshot.visible()).isFalse();

        SellableUnitChangedEvent unitEvent = (SellableUnitChangedEvent) captor.getAllValues().get(1);
        assertThat(unitEvent.visible()).isFalse();
    }

    @Test
    @DisplayName("payload snapshot: có images (id, objectKey, position), variants có price nhưng KHÔNG có sku, attributes có inputType và axis; routing key đúng hai chủ đề; mọi UUID trong payload là chuỗi")
    void snapshotPayloadDetails() {
        sellerStates.approve(SELLER_ID);
        Product product = createProduct(ProductStatus.PUBLISHED, 2L);
        Variant v = createVariant(product.getId(), VariantId.of(UUID.fromString("00000000-0000-7000-8000-000000000081")), true);

        recorder.onChanged(product, List.of(), ProductEventRecorder.Trigger.PUBLISH);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, times(2)).store(captor.capture());

        ProductSnapshotEvent snapshot = (ProductSnapshotEvent) captor.getAllValues().get(0);
        assertThat(snapshot.getRoutingKey()).isEqualTo("catalog.product.snapshot");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) snapshot.getPayload();

        // Kiểm tra thứ tự và kiểu chuỗi cho UUID
        assertThat(payload.keySet()).containsExactly(
                "productId", "version", "sellerId", "visible", "title", "description",
                "brandId", "categoryId", "attributes", "images", "variants", "occurredAt"
        );
        assertThat(payload.get("productId")).isInstanceOf(String.class);
        assertThat(payload.get("sellerId")).isInstanceOf(String.class);
        assertThat(payload.get("categoryId")).isInstanceOf(String.class);

        // attributes
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attrs = (List<Map<String, Object>>) payload.get("attributes");
        assertThat(attrs).hasSize(2);
        Map<String, Object> attr0 = attrs.get(0);
        assertThat(attr0.get("templateId")).isEqualTo(colorTemplate.getId().getValue().toString());
        assertThat(attr0.get("inputType")).isEqualTo("SINGLE_SELECT");
        assertThat(attr0.get("axis")).isEqualTo(true);
        assertThat(attr0.get("values")).isEqualTo(List.of(redOptionId.getValue().toString()));

        Map<String, Object> attr1 = attrs.get(1);
        assertThat(attr1.get("templateId")).isEqualTo(textTemplate.getId().getValue().toString());
        assertThat(attr1.get("inputType")).isEqualTo("TEXT");
        assertThat(attr1.get("axis")).isEqualTo(false);
        assertThat(attr1.get("values")).isEqualTo(List.of("Cotton"));

        // images
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> imgs = (List<Map<String, Object>>) payload.get("images");
        assertThat(imgs).hasSize(1);
        Map<String, Object> img0 = imgs.get(0);
        assertThat(img0.get("id")).isEqualTo("00000000-0000-7000-8000-000000000030");
        assertThat(img0.get("objectKey")).isEqualTo("key-img-1");
        assertThat(img0.get("position")).isEqualTo(0);

        // variants
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vars = (List<Map<String, Object>>) payload.get("variants");
        assertThat(vars).hasSize(1);
        Map<String, Object> var0 = vars.get(0);
        assertThat(var0.get("id")).isEqualTo(v.getId().getValue().toString());
        assertThat(var0.get("price")).isEqualTo(100_000L);
        assertThat(var0.get("active")).isEqualTo(true);
        assertThat(var0).doesNotContainKey("sku");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comb = (List<Map<String, Object>>) var0.get("combination");
        assertThat(comb).hasSize(1);
        assertThat(comb.get(0).get("templateId")).isEqualTo(colorTemplate.getId().getValue().toString());
        assertThat(comb.get(0).get("optionId")).isEqualTo(redOptionId.getValue().toString());
    }
}
