package vn.t3nexus.catalog.application.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.application.brand.FakeBrandRepository;
import vn.t3nexus.catalog.application.category.FakeCategoryRepository;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryLevel;
import vn.t3nexus.catalog.domain.product.AttributeInput;
import vn.t3nexus.catalog.domain.product.MissingPublishCondition;
import vn.t3nexus.catalog.domain.product.MissingPublishCondition.Code;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.catalog.domain.product.ProductAttributeException;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.catalog.domain.product.ProductStatus;
import vn.t3nexus.catalog.domain.product.ProductSummary;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;
import vn.t3nexus.lib.upload.UploadSlots;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
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
 * KT-03 cho sản phẩm: thứ tự kiểm vai, chủ, quy tắc, lưu; sản phẩm của người khác như không có; lệnh sửa thông số đọc
 * khóa dòng, lệnh sửa thông tin thì không (ADR-0012). Kho giả; dữ liệu danh mục, thương hiệu, mẫu dựng bằng hàm của miền.
 */
class ProductUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final ChangeContext ADMIN_CTX =
            new ChangeContext(new CatalogActor(UUID.randomUUID(), "ADMIN"), UUID.randomUUID(), NOW);
    private static final CatalogActor SELLER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000bb"), "USER");
    private static final CatalogActor OTHER_SELLER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000cc"), "USER");
    private static final CatalogActor ADMIN = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000aa"), "ADMIN");

    private FakeProductRepository products;
    private FakeCategoryRepository categories;
    private FakeBrandRepository brands;
    private FakeSellerStateRepository sellerStates;
    private AttributeTemplateRepository templates;
    private VariantRepository variants;
    private ProductEventRecorder recorder;
    private CreateProduct create;
    private UpdateProduct update;
    private GetMyProduct get;
    private ListMyProducts list;

    private Category l1;
    private Category l2;
    private Category l3;
    private AttributeTemplate model;
    private AttributeTemplate color;
    private AttributeOptionId red;
    private AttributeOptionId blue;

    @BeforeEach
    void setUp() {
        products = new FakeProductRepository();
        categories = new FakeCategoryRepository();
        brands = new FakeBrandRepository();
        sellerStates = new FakeSellerStateRepository();
        sellerStates.approve(SELLER.accountId());
        sellerStates.approve(OTHER_SELLER.accountId());
        templates = mock(AttributeTemplateRepository.class);
        variants = mock(VariantRepository.class);
        when(variants.findByProductId(any())).thenReturn(List.of());
        IdGenerator ids = new UuidV7IdGenerator();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ProductLimits limits = ProductLimits.defaults();
        UploadSlots uploads = mock(UploadSlots.class);
        when(uploads.urlOf(any(), any())).thenAnswer(a -> "http://cdn/" + a.getArgument(1));
        recorder = mock(ProductEventRecorder.class);
        create = new CreateProduct(products, categories, brands, templates, sellerStates, ids, clock, limits, uploads);
        update = new UpdateProduct(products, categories, brands, templates, variants, clock, limits, uploads, sellerStates, recorder);
        get = new GetMyProduct(products, categories, templates, variants, uploads, sellerStates);
        list = new ListMyProducts(products);

        model = AttributeTemplate.create(AttributeTemplateId.of(UUID.randomUUID()), "model", "Mẫu máy", null,
                InputType.TEXT, null, ADMIN_CTX);
        color = AttributeTemplate.create(AttributeTemplateId.of(UUID.randomUUID()), "color", "Màu", null,
                InputType.SINGLE_SELECT, null, ADMIN_CTX);
        red = AttributeOptionId.of(UUID.randomUUID());
        blue = AttributeOptionId.of(UUID.randomUUID());
        color.addOption(red, "red", "Đỏ", ADMIN_CTX);
        color.addOption(blue, "blue", "Xanh", ADMIN_CTX);
        when(templates.findAllByIds(any())).thenAnswer(inv -> {
            java.util.Collection<AttributeTemplateId> wanted = inv.getArgument(0);
            return List.of(model, color).stream().filter(t -> wanted.contains(t.getId())).toList();
        });

        l1 = Category.createRoot(CategoryId.of(UUID.randomUUID()), "Điện tử", 0, ADMIN_CTX);
        l2 = Category.createChild(CategoryId.of(UUID.randomUUID()), "Điện thoại", l1.getId(), CategoryLevel.L1, 0, ADMIN_CTX);
        l3 = Category.createChild(CategoryId.of(UUID.randomUUID()), "Smartphone", l2.getId(), CategoryLevel.L2, 0, ADMIN_CTX);
        l3.replaceAssignments(List.of(
                CategoryAttributeAssignment.create(model.getId(), InputType.TEXT, true, 1, AttributeConstraints.NONE),
                CategoryAttributeAssignment.create(color.getId(), InputType.SINGLE_SELECT, false, 2, AttributeConstraints.NONE)),
                ADMIN_CTX);
        categories.save(l1);
        categories.save(l2);
        categories.save(l3);
    }

    // ---- dựng dữ liệu ----

    private AttributeInput text(AttributeTemplate t, String value) {
        return new AttributeInput(t.getId(), List.of(value), false);
    }

    private AttributeInput axisOf(AttributeTemplate t, AttributeOptionId... options) {
        return new AttributeInput(t.getId(), Arrays.stream(options).map(o -> o.getValue().toString()).toList(), true);
    }

    private CreateProduct.Command createCommand(CatalogActor actor, Category category, UUID brandId,
                                                AttributeInput... attributes) {
        return new CreateProduct.Command(actor, "Điện thoại X", "Mô tả", category.getId().getValue(), brandId,
                List.of(attributes));
    }

    private ProductView created(AttributeInput... attributes) {
        return create.handle(createCommand(SELLER, l3, null, attributes));
    }

    private Variant variantUsing(AttributeOptionId option) {
        return Variant.create(VariantId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), SELLER.accountId(),
                List.of(new VariantAttributePair(color.getId(), option)), 100_000L, null, ProductLimits.defaults(), NOW);
    }

    private Variant defaultVariant() {
        return Variant.create(VariantId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), SELLER.accountId(),
                List.of(), 100_000L, null, ProductLimits.defaults(), NOW);
    }

    private UUID activeBrand() {
        Brand brand = Brand.create(BrandId.of(UUID.randomUUID()), "Apple", ADMIN_CTX);
        brands.save(brand);
        return brand.getId().getValue();
    }

    private UUID inactiveBrand() {
        Brand brand = Brand.create(BrandId.of(UUID.randomUUID()), "Cũ", ADMIN_CTX);
        brand.deactivate(ADMIN_CTX);
        brands.save(brand);
        return brand.getId().getValue();
    }

    private static void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private UpdateProduct.Command patch(CatalogActor actor, UUID id, Patch<String> title, Patch<String> description,
                                        Patch<UUID> brand, Patch<List<AttributeInput>> attributes) {
        return new UpdateProduct.Command(actor, id, title, description, brand, attributes);
    }

    private UpdateProduct.Command onlyTitle(UUID id, String title) {
        return patch(SELLER, id, Patch.of(title), Patch.absent(), Patch.absent(), Patch.absent());
    }

    // ---- tạo ----

    @Test
    @DisplayName("Tạo: vai không phải USER (kể cả ADMIN) bị từ chối trước mọi đọc và ghi")
    void createRequiresSeller() {
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED,
                () -> create.handle(createCommand(ADMIN, l3, null, text(model, "X1"))));
        assertThat(products.storedCount()).isZero();
    }

    @Test
    @DisplayName("Tạo: sản phẩm nháp của người gọi (chủ lấy từ token), phiên bản 0, thuộc tính kèm tên và tùy chọn")
    void createDraft() {
        ProductView view = created(text(model, "X1"), axisOf(color, red, blue));

        assertThat(view.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(view.version()).isZero();
        assertThat(view.variants()).isEmpty();
        assertThat(view.title()).isEqualTo("Điện thoại X");
        assertThat(view.categoryId()).isEqualTo(l3.getId().getValue());
        assertThat(view.brandId()).isNull();
        assertThat(view.attributes()).extracting(AttributeView::name).containsExactly("Mẫu máy", "Màu");
        AttributeView axis = view.attributes().get(1);
        assertThat(axis.axis()).isTrue();
        assertThat(axis.options()).extracting(AttributeView.OptionView::displayValue).containsExactlyInAnyOrder("Đỏ", "Xanh");
        assertThat(view.attributes().get(0).value()).isEqualTo("X1");

        var stored = products.stored(view.id());
        assertThat(stored.getSellerId()).isEqualTo(SELLER.accountId());
        assertThat(stored.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(products.saveCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Tạo: kèm điều kiện còn thiếu để đăng (thuộc tính bắt buộc, chưa có biến thể)")
    void createReportsMissingConditions() {
        ProductView view = created();
        assertThat(view.missingPublishConditions()).containsExactly(
                new MissingPublishCondition(Code.REQUIRED_ATTRIBUTE_MISSING, model.getId()),
                new MissingPublishCondition(Code.NO_ACTIVE_VARIANT, null),
                new MissingPublishCondition(Code.NO_IMAGE, null));

        ProductView filled = created(text(model, "X1"));
        assertThat(filled.missingPublishConditions())
                .containsExactly(new MissingPublishCondition(Code.NO_ACTIVE_VARIANT, null),
                        new MissingPublishCondition(Code.NO_IMAGE, null));
    }

    @Test
    @DisplayName("Tạo: danh mục không có là 404; không phải lá, ngừng dùng, tổ tiên ngừng dùng là PRODUCT_CATEGORY_NOT_USABLE")
    void createCategoryRules() {
        assertThatThrownBy(() -> create.handle(new CreateProduct.Command(SELLER, "Điện thoại X", null,
                UUID.randomUUID(), null, List.of())))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND));

        assertCode(ProductErrorCode.PRODUCT_CATEGORY_NOT_USABLE, () -> create.handle(createCommand(SELLER, l1, null)));
        assertCode(ProductErrorCode.PRODUCT_CATEGORY_NOT_USABLE, () -> create.handle(createCommand(SELLER, l2, null)));

        l2.deactivate(ADMIN_CTX);
        categories.save(l2);
        assertCode(ProductErrorCode.PRODUCT_CATEGORY_NOT_USABLE, () -> create.handle(createCommand(SELLER, l3, null)));
        l2.activate(ADMIN_CTX);
        categories.save(l2);

        l3.deactivate(ADMIN_CTX);
        categories.save(l3);
        assertCode(ProductErrorCode.PRODUCT_CATEGORY_NOT_USABLE, () -> create.handle(createCommand(SELLER, l3, null)));
        assertThat(products.storedCount()).isZero();
    }

    @Test
    @DisplayName("Tạo: thương hiệu tùy chọn; có thì phải tồn tại (404) và đang dùng (PRODUCT_BRAND_NOT_USABLE)")
    void createBrandRules() {
        assertThat(created().brandId()).isNull();

        UUID brand = activeBrand();
        assertThat(create.handle(createCommand(SELLER, l3, brand)).brandId()).isEqualTo(brand);

        assertCode(BrandErrorCode.BRAND_NOT_FOUND, () -> create.handle(createCommand(SELLER, l3, UUID.randomUUID())));
        assertCode(ProductErrorCode.PRODUCT_BRAND_NOT_USABLE, () -> create.handle(createCommand(SELLER, l3, inactiveBrand())));
        assertThat(products.storedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Tạo: thuộc tính sai hoặc tiêu đề sai thì không lưu gì")
    void createInvalidSavesNothing() {
        assertThatThrownBy(() -> created(new AttributeInput(color.getId(), List.of("khong-phai-uuid"), false)))
                .isInstanceOf(ProductAttributeException.class);
        assertThatThrownBy(() -> create.handle(new CreateProduct.Command(SELLER, "abc", null,
                l3.getId().getValue(), null, List.of()))).isInstanceOf(CatalogFieldException.class);
        assertThat(products.storedCount()).isZero();
        assertThat(products.saveCount).isZero();
    }

    // ---- sửa ----

    @Test
    @DisplayName("Sửa: vai không phải USER bị từ chối trước mọi đọc")
    void updateRequiresSeller() {
        UUID id = created().id();
        products.reads.clear();
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED, () -> update.handle(
                patch(ADMIN, id, Patch.of("Tiêu đề mới"), Patch.absent(), Patch.absent(), Patch.absent())));
        assertThat(products.reads).isEmpty();
    }

    @Test
    @DisplayName("Sửa: sản phẩm của người khác và mã không tồn tại cùng là PRODUCT_NOT_FOUND")
    void updateOtherSellersProductIsNotFound() {
        UUID id = created().id();
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND, () -> update.handle(patch(OTHER_SELLER, id,
                Patch.of("Tiêu đề mới"), Patch.absent(), Patch.absent(), Patch.absent())));
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND, () -> update.handle(patch(SELLER, UUID.randomUUID(),
                Patch.of("Tiêu đề mới"), Patch.absent(), Patch.absent(), Patch.absent())));
        assertThat(products.stored(id).getTitle()).isEqualTo("Điện thoại X");
    }

    @Test
    @DisplayName("Sửa thông tin (tiêu đề, mô tả, thương hiệu): đọc thường không khóa dòng, trường vắng giữ nguyên, phiên bản tăng")
    void updateDetailsReadsWithoutLock() {
        UUID brand = activeBrand();
        UUID id = create.handle(createCommand(SELLER, l3, brand, text(model, "X1"))).id();
        products.reads.clear();

        ProductView view = update.handle(onlyTitle(id, "Tiêu đề mới"));

        assertThat(products.reads).containsExactly("plain");
        assertThat(view.title()).isEqualTo("Tiêu đề mới");
        assertThat(view.description()).isEqualTo("Mô tả");
        assertThat(view.brandId()).isEqualTo(brand);
        assertThat(view.attributes()).hasSize(1);
        assertThat(view.version()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Sửa: gửi null cho mô tả hoặc thương hiệu thì xóa; tiêu đề null là 400; không gửi gì thì không ghi")
    void updateNullAndNoop() {
        UUID id = create.handle(createCommand(SELLER, l3, activeBrand())).id();

        ProductView cleared = update.handle(patch(SELLER, id, Patch.absent(), Patch.of(null), Patch.of(null), Patch.absent()));
        assertThat(cleared.description()).isNull();
        assertThat(cleared.brandId()).isNull();

        assertThatThrownBy(() -> update.handle(patch(SELLER, id, Patch.of(null), Patch.absent(), Patch.absent(), Patch.absent())))
                .isInstanceOfSatisfying(CatalogFieldException.class, e -> assertThat(e.field()).isEqualTo("title"));

        int saves = products.saveCount;
        ProductView same = update.handle(patch(SELLER, id, Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent()));
        assertThat(products.saveCount).isEqualTo(saves);
        assertThat(same.version()).isEqualTo(cleared.version());
    }

    @Test
    @DisplayName("Sửa thương hiệu: chọn thương hiệu ngừng dùng bị từ chối; giữ thương hiệu cũ đã ngừng thì được")
    void updateBrandRules() {
        UUID brand = activeBrand();
        UUID id = create.handle(createCommand(SELLER, l3, brand)).id();

        assertCode(ProductErrorCode.PRODUCT_BRAND_NOT_USABLE, () -> update.handle(
                patch(SELLER, id, Patch.absent(), Patch.absent(), Patch.of(inactiveBrand()), Patch.absent())));
        assertCode(BrandErrorCode.BRAND_NOT_FOUND, () -> update.handle(
                patch(SELLER, id, Patch.absent(), Patch.absent(), Patch.of(UUID.randomUUID()), Patch.absent())));

        Brand stored = brands.findById(BrandId.of(brand)).orElseThrow();
        stored.deactivate(ADMIN_CTX);
        brands.save(stored);
        ProductView view = update.handle(patch(SELLER, id, Patch.of("Tiêu đề khác"), Patch.absent(), Patch.of(brand), Patch.absent()));
        assertThat(view.brandId()).isEqualTo(brand);
        assertThat(view.title()).isEqualTo("Tiêu đề khác");
    }

    @Test
    @DisplayName("Sửa thông số: đọc KHÓA dòng (vì có thể đổi trục), thay toàn bộ danh sách, giá trị cũ không đổi được miễn kiểm")
    void updateAttributesLocksRow() {
        UUID id = created(text(model, "X1"), axisOf(color, red)).id();
        products.reads.clear();

        ProductView view = update.handle(patch(SELLER, id, Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.of(List.of(text(model, "X1"), axisOf(color, red, blue)))));

        assertThat(products.reads).containsExactly("lock");
        assertThat(view.attributes()).hasSize(2);
        assertThat(view.attributes().get(1).options()).hasSize(2);
        assertThat(view.version()).isEqualTo(1L);

        ProductView emptied = update.handle(patch(SELLER, id, Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.of(List.of())));
        assertThat(emptied.attributes()).isEmpty();
    }

    @Test
    @DisplayName("Sửa thông số khi đã có biến thể: đổi tập trục bị từ chối, không ghi gì")
    void updateAttributesRespectsVariantUsage() {
        UUID id = created(text(model, "X1"), axisOf(color, red, blue)).id();
        when(variants.findByProductId(any())).thenReturn(List.of(variantUsing(red)));
        int saves = products.saveCount;

        assertCode(ProductErrorCode.PRODUCT_AXES_LOCKED, () -> update.handle(patch(SELLER, id, Patch.absent(),
                Patch.absent(), Patch.absent(), Patch.of(List.of(text(model, "X1"))))));
        assertCode(ProductErrorCode.PRODUCT_AXIS_VALUE_IN_USE, () -> update.handle(patch(SELLER, id, Patch.absent(),
                Patch.absent(), Patch.absent(), Patch.of(List.of(text(model, "X1"), axisOf(color, blue))))));
        assertThat(products.saveCount).isEqualTo(saves);
    }

    @Test
    @DisplayName("Sửa thông số: thuộc tính sai thì từ chối và không ghi")
    void updateInvalidAttributeSavesNothing() {
        UUID id = created(text(model, "X1")).id();
        int saves = products.saveCount;
        assertThatThrownBy(() -> update.handle(patch(SELLER, id, Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.of(List.of(new AttributeInput(color.getId(), List.of(UUID.randomUUID().toString()), false))))))
                .isInstanceOf(ProductAttributeException.class);
        assertThat(products.saveCount).isEqualTo(saves);
    }

    @Test
    @DisplayName("Sửa: gọi recorder.onChanged với Trigger.EDIT và changedUnits rỗng; Tạo không gọi recorder")
    void updateCallsRecorderAndCreateDoesNot() {
        ProductView view = created(text(model, "X1"));
        verifyNoInteractions(recorder);

        update.handle(onlyTitle(view.id(), "Tiêu đề mới"));
        verify(recorder).onChanged(any(Product.class), eq(List.of()), eq(ProductEventRecorder.Trigger.EDIT));
    }

    // ---- đọc ----

    @Test
    @DisplayName("Xem: chỉ chủ; người khác và ADMIN không xem được; điều kiện thiếu phản ánh biến thể hoạt động")
    void getMyProduct() {
        UUID id = created(text(model, "X1")).id();

        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND, () -> get.handle(new GetMyProduct.Query(OTHER_SELLER, id)));
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED, () -> get.handle(new GetMyProduct.Query(ADMIN, id)));

        assertThat(get.handle(new GetMyProduct.Query(SELLER, id)).missingPublishConditions())
                .containsExactly(new MissingPublishCondition(Code.NO_ACTIVE_VARIANT, null),
                        new MissingPublishCondition(Code.NO_IMAGE, null));

        when(variants.findByProductId(any())).thenReturn(List.of(defaultVariant()));
        ProductView withVariant = get.handle(new GetMyProduct.Query(SELLER, id));
        assertThat(withVariant.missingPublishConditions())
                .containsExactly(new MissingPublishCondition(Code.NO_IMAGE, null));
        assertThat(withVariant.variants()).hasSize(1);
        assertThat(products.reads).doesNotContain("lock");
    }

    @Test
    @DisplayName("Danh sách: chỉ cho USER, trả trang và tổng từ kho")
    void listMyProducts() {
        ProductSummary summary = new ProductSummary(UUID.randomUUID(), "Áo thun", ProductStatus.DRAFT, 2L, 3L,
                100_000L, 150_000L, NOW);
        products.summaries = List.of(summary);
        products.total = 7L;

        ListMyProducts.Result result = list.handle(new ListMyProducts.Query(SELLER, null, 0, 20));
        assertThat(result.items()).containsExactly(summary);
        assertThat(result.total()).isEqualTo(7L);
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED, () -> list.handle(new ListMyProducts.Query(ADMIN, null, 0, 20)));
    }

    @Test
    @DisplayName("Không có sự kiện, không ghi kiểm toán, không hộp thư: sản phẩm không phát event ở feature 07")
    void noDomainEvents() {
        UUID id = created(text(model, "X1")).id();
        update.handle(onlyTitle(id, "Tiêu đề mới"));
        assertThat(products.stored(id).getDomainEvents()).isEmpty();
        ProductId productId = ProductId.of(id);
        assertThat(products.findById(productId)).isPresent();
        ProductAttribute first = products.stored(id).getAttributes().get(0);
        assertThat(first.value()).isEqualTo("X1");
    }
}
