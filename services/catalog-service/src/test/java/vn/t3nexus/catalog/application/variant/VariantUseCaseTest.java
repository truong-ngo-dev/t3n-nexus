package vn.t3nexus.catalog.application.variant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.application.product.FakeProductRepository;
import vn.t3nexus.catalog.application.product.Patch;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;
import vn.t3nexus.catalog.domain.variant.VariantErrorCode;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantStatus;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * KT-03 cho biến thể: lệnh nào cũng kiểm vai, đọc KHÓA dòng sản phẩm của chính chủ, rồi mới đếm, kiểm quy tắc, ghi;
 * mỗi lệnh thành công tăng phiên bản sản phẩm đúng một (INV-CAT-36, ADR-0012). Kho giả.
 */
class VariantUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final ChangeContext ADMIN_CTX =
            new ChangeContext(new CatalogActor(UUID.randomUUID(), "ADMIN"), UUID.randomUUID(), NOW);
    private static final CatalogActor SELLER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000bb"), "USER");
    private static final CatalogActor OTHER_SELLER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000cc"), "USER");
    private static final CatalogActor ADMIN = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000aa"), "ADMIN");

    private FakeProductRepository products;
    private FakeVariantRepository variants;
    private AttributeTemplateRepository templates;
    private vn.t3nexus.catalog.application.product.ProductEventRecorder recorder;
    private AddVariant add;
    private UpdateVariant update;
    private DeactivateVariant deactivate;
    private ActivateVariant activate;

    private AttributeTemplate color;
    private AttributeTemplate size;
    private AttributeOptionId red;
    private AttributeOptionId blue;
    private AttributeOptionId m;
    private AttributeOptionId l;
    private ProductId productId;

    @BeforeEach
    void setUp() {
        build(ProductLimits.defaults());
    }

    private void build(ProductLimits limits) {
        products = new FakeProductRepository();
        variants = new FakeVariantRepository();
        templates = mock(AttributeTemplateRepository.class);
        IdGenerator ids = new UuidV7IdGenerator();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        recorder = mock(vn.t3nexus.catalog.application.product.ProductEventRecorder.class);
        add = new AddVariant(products, variants, templates, ids, clock, limits, recorder);
        update = new UpdateVariant(products, variants, clock, limits, recorder);
        deactivate = new DeactivateVariant(products, variants, clock, recorder);
        activate = new ActivateVariant(products, variants, clock, recorder);

        color = template("color");
        size = template("size_label");
        red = option(color, "red");
        blue = option(color, "blue");
        m = option(size, "m");
        l = option(size, "l");
        when(templates.findAllByIds(any())).thenAnswer(inv -> {
            java.util.Collection<AttributeTemplateId> wanted = inv.getArgument(0);
            return List.of(color, size).stream().filter(t -> wanted.contains(t.getId())).toList();
        });
        productId = saveProduct(SELLER, true);
    }

    private static AttributeTemplate template(String name) {
        return AttributeTemplate.create(AttributeTemplateId.of(UUID.randomUUID()), name, "Hiển thị " + name, null,
                InputType.SINGLE_SELECT, null, ADMIN_CTX);
    }

    private static AttributeOptionId option(AttributeTemplate t, String value) {
        AttributeOptionId id = AttributeOptionId.of(UUID.randomUUID());
        t.addOption(id, value, "Tùy chọn " + value, ADMIN_CTX);
        return id;
    }

    /** Sản phẩm nháp đã lưu (phiên bản 0). {@code withAxes}: hai trục màu {đỏ, xanh} và cỡ {M, L}; ngược lại không trục. */
    private ProductId saveProduct(CatalogActor owner, boolean withAxes) {
        List<ProductAttribute> attributes = withAxes
                ? List.of(ProductAttribute.ofOptions(color.getId(), true, List.of(red, blue)),
                          ProductAttribute.ofOptions(size.getId(), true, List.of(m, l)))
                : List.of();
        Product p = Product.create(ProductId.of(UUID.randomUUID()), owner.accountId(), CategoryId.of(UUID.randomUUID()),
                null, "Áo thun", null, attributes, ProductLimits.defaults(), NOW);
        products.save(p);
        return p.getId();
    }

    private List<VariantAttributePair> combo(AttributeOptionId c, AttributeOptionId s) {
        return List.of(new VariantAttributePair(color.getId(), c), new VariantAttributePair(size.getId(), s));
    }

    private VariantView addVariant(List<VariantAttributePair> combination, long price, String sku) {
        return add.handle(new AddVariant.Command(SELLER, productId.getValue(), combination, price, sku));
    }

    private long productVersion(ProductId id) {
        return products.stored(id.getValue()).version();
    }

    private static void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    // ---- thêm ----

    @Test
    @DisplayName("Thêm: vai không phải USER bị từ chối trước mọi đọc")
    void addRequiresSeller() {
        products.reads.clear();
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED, () -> add.handle(
                new AddVariant.Command(ADMIN, productId.getValue(), combo(red, m), 100_000L, null)));
        assertThat(products.reads).isEmpty();
        assertThat(variants.storedCount()).isZero();
    }

    @Test
    @DisplayName("Thêm: đọc khóa dòng sản phẩm, lưu biến thể ACTIVE, phiên bản sản phẩm tăng đúng một")
    void addLocksAndBumpsVersion() {
        products.reads.clear();
        VariantView view = addVariant(combo(red, m), 150_000L, "SKU-1");

        assertThat(products.reads).containsExactly("lock");
        assertThat(view.status()).isEqualTo(VariantStatus.ACTIVE);
        assertThat(view.price()).isEqualTo(150_000L);
        assertThat(view.sku()).isEqualTo("SKU-1");
        assertThat(view.productId()).isEqualTo(productId.getValue());
        assertThat(view.combination()).hasSize(2);
        assertThat(variants.stored(view.id()).getSellerId()).isEqualTo(SELLER.accountId());
        assertThat(productVersion(productId)).isEqualTo(1L);
        verify(recorder).onChanged(any(Product.class), eq(List.of(VariantId.of(view.id()))),
                eq(vn.t3nexus.catalog.application.product.ProductEventRecorder.Trigger.EDIT));
    }

    @Test
    @DisplayName("Thêm: sản phẩm của người khác và mã không có cùng là PRODUCT_NOT_FOUND; không ghi gì")
    void addOtherSellersProduct() {
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND, () -> add.handle(
                new AddVariant.Command(OTHER_SELLER, productId.getValue(), combo(red, m), 100_000L, null)));
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND, () -> add.handle(
                new AddVariant.Command(SELLER, UUID.randomUUID(), combo(red, m), 100_000L, null)));
        assertThat(variants.storedCount()).isZero();
        assertThat(productVersion(productId)).isZero();
    }

    @Test
    @DisplayName("Tổ hợp trùng bị từ chối, kể cả khi liệt kê trục theo thứ tự khác")
    void duplicateCombination() {
        addVariant(combo(red, m), 100_000L, null);
        assertCode(VariantErrorCode.VARIANT_COMBINATION_TAKEN, () -> addVariant(combo(red, m), 100_000L, null));
        assertCode(VariantErrorCode.VARIANT_COMBINATION_TAKEN, () -> addVariant(
                List.of(new VariantAttributePair(size.getId(), m), new VariantAttributePair(color.getId(), red)),
                100_000L, null));
        addVariant(combo(red, l), 100_000L, null);
        assertThat(variants.storedCount()).isEqualTo(2);
        assertThat(productVersion(productId)).isEqualTo(2L);
    }

    @Test
    @DisplayName("Tổ hợp sai trục hoặc ngoài tập bị từ chối; không ghi gì")
    void invalidCombination() {
        assertCode(VariantErrorCode.VARIANT_COMBINATION_INVALID,
                () -> addVariant(List.of(new VariantAttributePair(color.getId(), red)), 100_000L, null));
        assertCode(VariantErrorCode.VARIANT_COMBINATION_INVALID, () -> addVariant(List.of(), 100_000L, null));
        AttributeOptionId outside = option(color, "green");
        assertCode(VariantErrorCode.VARIANT_COMBINATION_INVALID, () -> addVariant(combo(outside, m), 100_000L, null));
        assertThat(variants.storedCount()).isZero();
        assertThat(productVersion(productId)).isZero();
    }

    @Test
    @DisplayName("Sản phẩm không có trục: biến thể mặc định với tổ hợp rỗng, chỉ một")
    void defaultVariant() {
        productId = saveProduct(SELLER, false);
        VariantView view = addVariant(List.of(), 99_000L, null);
        assertThat(view.combination()).isEmpty();
        assertCode(VariantErrorCode.VARIANT_COMBINATION_TAKEN, () -> addVariant(List.of(), 99_000L, null));
    }

    @Test
    @DisplayName("Tối đa số biến thể theo giới hạn: biến thể kế tiếp bị từ chối, kể cả khi đã có biến thể ngừng")
    void variantLimit() {
        build(new ProductLimits(5, 200, 5_000, 2, 2, 1_000_000_000L, 50));
        VariantView first = addVariant(combo(red, m), 100_000L, null);
        addVariant(combo(red, l), 100_000L, null);
        deactivate.handle(new DeactivateVariant.Command(SELLER, productId.getValue(), first.id()));
        org.mockito.Mockito.clearInvocations(recorder);
        assertCode(VariantErrorCode.VARIANT_LIMIT_REACHED, () -> addVariant(combo(blue, m), 100_000L, null));
        verifyNoInteractions(recorder);
        assertThat(variants.storedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Giá và SKU sai là 400, không ghi gì")
    void invalidPriceAndSku() {
        assertThatThrownBy(() -> addVariant(combo(red, m), 0L, null)).isInstanceOf(CatalogFieldException.class);
        assertThatThrownBy(() -> addVariant(combo(red, m), 1_000_000_001L, null)).isInstanceOf(CatalogFieldException.class);
        assertThatThrownBy(() -> addVariant(combo(red, m), 100_000L, "")).isInstanceOf(CatalogFieldException.class);
        assertThatThrownBy(() -> addVariant(combo(red, m), 100_000L, "x".repeat(51))).isInstanceOf(CatalogFieldException.class);
        assertThat(variants.storedCount()).isZero();
        assertThat(productVersion(productId)).isZero();
    }

    @Test
    @DisplayName("SKU trùng trong cùng người bán bị từ chối, kể cả sản phẩm khác; người bán khác dùng được; không SKU thì không trùng")
    void skuUniquePerSeller() {
        addVariant(combo(red, m), 100_000L, "SKU-1");
        assertCode(VariantErrorCode.VARIANT_SKU_TAKEN, () -> addVariant(combo(red, l), 100_000L, "SKU-1"));

        ProductId second = saveProduct(SELLER, false);
        assertCode(VariantErrorCode.VARIANT_SKU_TAKEN, () -> add.handle(
                new AddVariant.Command(SELLER, second.getValue(), List.of(), 100_000L, "SKU-1")));

        ProductId theirs = saveProduct(OTHER_SELLER, false);
        add.handle(new AddVariant.Command(OTHER_SELLER, theirs.getValue(), List.of(), 100_000L, "SKU-1"));

        addVariant(combo(blue, m), 100_000L, null);
        addVariant(combo(blue, l), 100_000L, null);
    }

    // ---- đổi giá, SKU ----

    @Test
    @DisplayName("Đổi giá, SKU: đọc khóa dòng, trường vắng giữ nguyên, phiên bản sản phẩm tăng một")
    void updatePriceAndSku() {
        VariantView v = addVariant(combo(red, m), 100_000L, "SKU-1");
        products.reads.clear();

        VariantView priceOnly = update.handle(new UpdateVariant.Command(SELLER, productId.getValue(), v.id(),
                Patch.of(120_000L), Patch.absent()));
        assertThat(products.reads).containsExactly("lock");
        assertThat(priceOnly.price()).isEqualTo(120_000L);
        assertThat(priceOnly.sku()).isEqualTo("SKU-1");
        assertThat(productVersion(productId)).isEqualTo(2L);

        VariantView skuChanged = update.handle(new UpdateVariant.Command(SELLER, productId.getValue(), v.id(),
                Patch.absent(), Patch.of("SKU-2")));
        assertThat(skuChanged.sku()).isEqualTo("SKU-2");
        assertThat(skuChanged.price()).isEqualTo(120_000L);

        VariantView cleared = update.handle(new UpdateVariant.Command(SELLER, productId.getValue(), v.id(),
                Patch.absent(), Patch.of(null)));
        assertThat(cleared.sku()).isNull();
        assertThat(productVersion(productId)).isEqualTo(4L);
    }

    @Test
    @DisplayName("Đổi SKU sang SKU của biến thể khác bị từ chối; đặt lại SKU của chính nó thì được")
    void updateSkuUniqueness() {
        VariantView first = addVariant(combo(red, m), 100_000L, "SKU-1");
        addVariant(combo(red, l), 100_000L, "SKU-2");

        assertCode(VariantErrorCode.VARIANT_SKU_TAKEN, () -> update.handle(new UpdateVariant.Command(SELLER,
                productId.getValue(), first.id(), Patch.absent(), Patch.of("SKU-2"))));
        update.handle(new UpdateVariant.Command(SELLER, productId.getValue(), first.id(), Patch.absent(), Patch.of("SKU-1")));
    }

    @Test
    @DisplayName("Đổi giá sai là 400; giá null cũng là 400; không gửi gì thì không ghi")
    void updateInvalidAndNoop() {
        VariantView v = addVariant(combo(red, m), 100_000L, null);
        assertThatThrownBy(() -> update.handle(new UpdateVariant.Command(SELLER, productId.getValue(), v.id(),
                Patch.of(0L), Patch.absent()))).isInstanceOf(CatalogFieldException.class);
        assertThatThrownBy(() -> update.handle(new UpdateVariant.Command(SELLER, productId.getValue(), v.id(),
                Patch.of(null), Patch.absent()))).isInstanceOf(CatalogFieldException.class);

        int saves = variants.saveCount;
        long version = productVersion(productId);
        update.handle(new UpdateVariant.Command(SELLER, productId.getValue(), v.id(), Patch.absent(), Patch.absent()));
        assertThat(variants.saveCount).isEqualTo(saves);
        assertThat(productVersion(productId)).isEqualTo(version);
    }

    @Test
    @DisplayName("Biến thể của sản phẩm khác là VARIANT_NOT_FOUND; sản phẩm của người khác là PRODUCT_NOT_FOUND")
    void updateOwnership() {
        VariantView v = addVariant(combo(red, m), 100_000L, null);
        ProductId another = saveProduct(SELLER, false);

        assertCode(VariantErrorCode.VARIANT_NOT_FOUND, () -> update.handle(new UpdateVariant.Command(SELLER,
                another.getValue(), v.id(), Patch.of(1L), Patch.absent())));
        assertCode(VariantErrorCode.VARIANT_NOT_FOUND, () -> update.handle(new UpdateVariant.Command(SELLER,
                productId.getValue(), UUID.randomUUID(), Patch.of(1L), Patch.absent())));
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND, () -> update.handle(new UpdateVariant.Command(OTHER_SELLER,
                productId.getValue(), v.id(), Patch.of(1L), Patch.absent())));
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED, () -> update.handle(new UpdateVariant.Command(ADMIN,
                productId.getValue(), v.id(), Patch.of(1L), Patch.absent())));
    }

    // ---- ngừng, bật lại ----

    @Test
    @DisplayName("Ngừng rồi bật lại: đọc khóa dòng, mỗi lệnh tăng phiên bản sản phẩm đúng một; lặp lại là 409 và không ghi")
    void deactivateActivate() {
        VariantView v = addVariant(combo(red, m), 100_000L, null);
        products.reads.clear();
        org.mockito.Mockito.clearInvocations(recorder);

        VariantView deactivated = deactivate.handle(new DeactivateVariant.Command(SELLER, productId.getValue(), v.id()));
        assertThat(deactivated.status()).isEqualTo(VariantStatus.INACTIVE);
        assertThat(productVersion(productId)).isEqualTo(2L);
        verify(recorder).onChanged(any(Product.class), eq(List.of(VariantId.of(v.id()))),
                eq(vn.t3nexus.catalog.application.product.ProductEventRecorder.Trigger.EDIT));

        long version = productVersion(productId);
        assertCode(CatalogErrorCode.CATALOG_INVALID_STATE,
                () -> deactivate.handle(new DeactivateVariant.Command(SELLER, productId.getValue(), v.id())));
        assertThat(productVersion(productId)).isEqualTo(version);

        org.mockito.Mockito.clearInvocations(recorder);
        VariantView activated = activate.handle(new ActivateVariant.Command(SELLER, productId.getValue(), v.id()));
        assertThat(activated.status()).isEqualTo(VariantStatus.ACTIVE);
        assertThat(productVersion(productId)).isEqualTo(3L);
        verify(recorder).onChanged(any(Product.class), eq(List.of(VariantId.of(v.id()))),
                eq(vn.t3nexus.catalog.application.product.ProductEventRecorder.Trigger.EDIT));
        assertCode(CatalogErrorCode.CATALOG_INVALID_STATE,
                () -> activate.handle(new ActivateVariant.Command(SELLER, productId.getValue(), v.id())));
        assertThat(products.reads).containsOnly("lock");
    }

    @Test
    @DisplayName("Ngừng và bật: vai, chủ và biến thể của sản phẩm khác kiểm như các lệnh còn lại")
    void deactivateOwnership() {
        VariantView v = addVariant(combo(red, m), 100_000L, null);
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED,
                () -> deactivate.handle(new DeactivateVariant.Command(ADMIN, productId.getValue(), v.id())));
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND,
                () -> deactivate.handle(new DeactivateVariant.Command(OTHER_SELLER, productId.getValue(), v.id())));
        assertCode(VariantErrorCode.VARIANT_NOT_FOUND,
                () -> activate.handle(new ActivateVariant.Command(SELLER, productId.getValue(), UUID.randomUUID())));
    }
}
