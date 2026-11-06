package vn.t3nexus.catalog.application.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.catalog.application.brand.FakeBrandRepository;
import vn.t3nexus.catalog.application.category.FakeCategoryRepository;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryLevel;
import vn.t3nexus.catalog.domain.product.MissingPublishCondition.Code;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductImage;
import vn.t3nexus.catalog.domain.product.ProductImageId;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.catalog.domain.product.ProductStatus;
import vn.t3nexus.catalog.domain.variant.Variant;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * KT-01, KT-02, KT-04 (feature 09): đăng, gỡ, đăng lại, xóa nháp, tạo nháp cần người bán đã duyệt. Thứ tự: vai, chủ, khóa,
 * trạng thái, điều kiện (đủ cả danh sách), đổi trạng thái, lưu, ghi sự kiện. Kho giả; dữ liệu dựng bằng hàm của miền.
 */
class PublishUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final ChangeContext ADMIN_CTX =
            new ChangeContext(new CatalogActor(UUID.randomUUID(), "ADMIN"), UUID.randomUUID(), NOW);
    private static final CatalogActor SELLER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000bb"), "USER");
    private static final CatalogActor OTHER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000cc"), "USER");
    private static final CatalogActor ADMIN = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000aa"), "ADMIN");

    private FakeProductRepository products;
    private FakeCategoryRepository categories;
    private FakeSellerStateRepository sellerStates;
    private VariantRepository variants;
    private ProductEventRecorder recorder;
    private UploadSlots uploads;
    private PublishProduct publish;
    private UnpublishProduct unpublish;
    private DeleteDraft deleteDraft;
    private CreateProduct create;

    private Category leaf;
    private AttributeTemplate model;
    private final List<Variant> variantList = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        products = new FakeProductRepository();
        categories = new FakeCategoryRepository();
        sellerStates = new FakeSellerStateRepository();
        sellerStates.approve(SELLER.accountId());
        sellerStates.approve(OTHER.accountId());
        AttributeTemplateRepository templates = mock(AttributeTemplateRepository.class);
        variants = mock(VariantRepository.class);
        when(variants.findByProductId(any())).thenAnswer(inv -> List.copyOf(variantList));
        IdGenerator ids = new UuidV7IdGenerator();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        uploads = mock(UploadSlots.class);
        when(uploads.urlOf(any(), any())).thenAnswer(a -> "http://cdn/" + a.getArgument(1));
        recorder = mock(ProductEventRecorder.class);
        ProductAssembler assembler = new ProductAssembler(templates, uploads, sellerStates, categories);
        publish = new PublishProduct(products, variants, categories, assembler, clock, recorder);
        unpublish = new UnpublishProduct(products, variants, categories, assembler, clock, recorder);
        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        deleteDraft = new DeleteDraft(products, variants, uploads, new TransactionTemplate(ptm));
        create = new CreateProduct(products, categories, new FakeBrandRepository(), templates, sellerStates, ids, clock,
                ProductLimits.defaults(), uploads);

        model = AttributeTemplate.create(AttributeTemplateId.of(UUID.randomUUID()), "model", "Mẫu máy", null,
                InputType.TEXT, null, ADMIN_CTX);
        when(templates.findAllByIds(any())).thenAnswer(inv -> {
            java.util.Collection<AttributeTemplateId> wanted = inv.getArgument(0);
            return List.of(model).stream().filter(t -> wanted.contains(t.getId())).toList();
        });
        Category l1 = Category.createRoot(CategoryId.of(UUID.randomUUID()), "Điện tử", 0, ADMIN_CTX);
        Category l2 = Category.createChild(CategoryId.of(UUID.randomUUID()), "Điện thoại", l1.getId(), CategoryLevel.L1, 0, ADMIN_CTX);
        leaf = Category.createChild(CategoryId.of(UUID.randomUUID()), "Smartphone", l2.getId(), CategoryLevel.L2, 0, ADMIN_CTX);
        leaf.replaceAssignments(List.of(
                CategoryAttributeAssignment.create(model.getId(), InputType.TEXT, true, 1, AttributeConstraints.NONE)),
                ADMIN_CTX);
        categories.save(l1);
        categories.save(l2);
        categories.save(leaf);
    }

    // ---- dựng dữ liệu ----

    /** Sản phẩm nháp của người bán trong kho giả: có thuộc tính bắt buộc nếu {@code withAttribute}. */
    private Product draft(CatalogActor owner, boolean withAttribute, boolean withImage, boolean withVariant) {
        List<vn.t3nexus.catalog.domain.product.ProductAttribute> attributes = withAttribute
                ? List.of(vn.t3nexus.catalog.domain.product.ProductAttribute.ofValue(model.getId(), "X1"))
                : List.of();
        Product product = Product.create(ProductId.of(UUID.randomUUID()), owner.accountId(), leaf.getId(), null,
                "Điện thoại X", "Mô tả", attributes, ProductLimits.defaults(), NOW);
        if (withImage) {
            product.addImage(ProductImage.of(ProductImageId.of(UUID.randomUUID()), "products/" + product.getId().getValue() + "/k1",
                    "image/jpeg", 1000L, 0), 9, NOW);
        }
        if (withVariant) {
            variantList.clear();
            variantList.add(Variant.create(VariantId.of(UUID.randomUUID()), product.getId(), owner.accountId(), List.of(),
                    100_000L, null, ProductLimits.defaults(), NOW));
        }
        products.save(product);
        return product;
    }

    private Product ready() {
        return draft(SELLER, true, true, true);
    }

    private static void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private ProductView doPublish(Product p) {
        return publish.handle(new PublishProduct.Command(SELLER, p.getId().getValue()));
    }

    // ---- đăng ----

    @Test
    @DisplayName("Đăng: DRAFT -> PUBLISHED, publishedAt đặt, lưu một lần, đọc qua đường khóa, ghi sự kiện PUBLISH")
    void publishHappyPath() {
        Product p = ready();
        products.reads.clear();
        int saves = products.saveCount;

        ProductView view = doPublish(p);

        assertThat(view.status()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(view.publishedAt()).isEqualTo(NOW);
        assertThat(view.missingPublishConditions()).isEmpty();
        assertThat(products.stored(p.getId().getValue()).getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(products.saveCount).isEqualTo(saves + 1);
        assertThat(products.reads).containsOnly("lock");
        verify(recorder).onChanged(any(Product.class), org.mockito.ArgumentMatchers.eq(List.of()),
                org.mockito.ArgumentMatchers.eq(ProductEventRecorder.Trigger.PUBLISH));
    }

    @Test
    @DisplayName("Đăng khi đang PUBLISHED -> CATALOG_INVALID_STATE, không lưu, không ghi sự kiện")
    void publishTwice() {
        Product p = ready();
        doPublish(p);
        int saves = products.saveCount;
        org.mockito.Mockito.clearInvocations(recorder);

        assertCode(CatalogErrorCode.CATALOG_INVALID_STATE, () -> doPublish(p));

        assertThat(products.saveCount).isEqualTo(saves);
        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("Đăng thiếu cả bốn điều kiện: ném PublishNotAllowedException với ĐỦ danh sách, không lưu, không ghi sự kiện")
    void publishReportsAllMissing() {
        Product p = draft(SELLER, false, false, false);
        sellerStates.reject(SELLER.accountId());
        leaf.deactivate(ADMIN_CTX);
        categories.save(leaf);
        int saves = products.saveCount;

        assertThatThrownBy(() -> doPublish(p)).isInstanceOfSatisfying(PublishNotAllowedException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_NOT_PUBLISHABLE);
            assertThat(e.missing()).extracting(m -> m.code()).containsExactly(
                    Code.REQUIRED_ATTRIBUTE_MISSING, Code.NO_ACTIVE_VARIANT, Code.NO_IMAGE,
                    Code.SELLER_NOT_APPROVED, Code.CATEGORY_NOT_USABLE);
        });
        assertThat(products.saveCount).isEqualTo(saves);
        assertThat(products.stored(p.getId().getValue()).getStatus()).isEqualTo(ProductStatus.DRAFT);
        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("Người bán chưa có bản sao trạng thái -> thiếu SELLER_NOT_APPROVED")
    void publishWithoutSellerState() {
        Product p = draft(SELLER, true, true, true);
        FakeSellerStateRepository empty = new FakeSellerStateRepository();
        ProductAssembler assembler = new ProductAssembler(mock(AttributeTemplateRepository.class), uploads, empty, categories);
        PublishProduct noState = new PublishProduct(products, variants, categories, assembler,
                Clock.fixed(NOW, ZoneOffset.UTC), recorder);

        assertThatThrownBy(() -> noState.handle(new PublishProduct.Command(SELLER, p.getId().getValue())))
                .isInstanceOfSatisfying(PublishNotAllowedException.class,
                        e -> assertThat(e.missing()).extracting(m -> m.code()).contains(Code.SELLER_NOT_APPROVED));
    }

    @Test
    @DisplayName("Đăng: ADMIN -> CATALOG_ROLE_NOT_ALLOWED không đụng kho; sản phẩm người khác hoặc không có -> PRODUCT_NOT_FOUND")
    void publishRoleAndOwnership() {
        Product p = ready();
        products.reads.clear();

        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED,
                () -> publish.handle(new PublishProduct.Command(ADMIN, p.getId().getValue())));
        assertThat(products.reads).isEmpty();
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND,
                () -> publish.handle(new PublishProduct.Command(OTHER, p.getId().getValue())));
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND,
                () -> publish.handle(new PublishProduct.Command(SELLER, UUID.randomUUID())));
        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("Gỡ rồi đăng lại: publishedAt giữ lần đầu; đăng lại khi danh mục ngừng dùng bị chặn")
    void unpublishAndRepublish() {
        Product p = ready();
        doPublish(p);

        ProductView off = unpublish.handle(new UnpublishProduct.Command(SELLER, p.getId().getValue()));
        assertThat(off.status()).isEqualTo(ProductStatus.UNPUBLISHED);
        assertThat(off.publishedAt()).isEqualTo(NOW);
        verify(recorder).onChanged(any(Product.class), org.mockito.ArgumentMatchers.eq(List.of()),
                org.mockito.ArgumentMatchers.eq(ProductEventRecorder.Trigger.UNPUBLISH));

        assertThat(doPublish(p).publishedAt()).isEqualTo(NOW);

        unpublish.handle(new UnpublishProduct.Command(SELLER, p.getId().getValue()));
        leaf.deactivate(ADMIN_CTX);
        categories.save(leaf);
        assertThatThrownBy(() -> doPublish(p)).isInstanceOfSatisfying(PublishNotAllowedException.class,
                e -> assertThat(e.missing()).extracting(m -> m.code()).containsExactly(Code.CATEGORY_NOT_USABLE));
        assertThat(products.stored(p.getId().getValue()).getStatus()).isEqualTo(ProductStatus.UNPUBLISHED);
    }

    @Test
    @DisplayName("Gỡ: từ DRAFT hoặc UNPUBLISHED -> CATALOG_INVALID_STATE; người khác -> PRODUCT_NOT_FOUND")
    void unpublishRules() {
        Product p = ready();
        assertCode(CatalogErrorCode.CATALOG_INVALID_STATE,
                () -> unpublish.handle(new UnpublishProduct.Command(SELLER, p.getId().getValue())));
        doPublish(p);
        unpublish.handle(new UnpublishProduct.Command(SELLER, p.getId().getValue()));
        assertCode(CatalogErrorCode.CATALOG_INVALID_STATE,
                () -> unpublish.handle(new UnpublishProduct.Command(SELLER, p.getId().getValue())));
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND,
                () -> unpublish.handle(new UnpublishProduct.Command(OTHER, p.getId().getValue())));
    }

    // ---- xóa nháp ----

    @Test
    @DisplayName("Xóa nháp chưa từng đăng: sản phẩm và biến thể bị xóa, đối tượng ảnh được hủy SAU khi xóa, không ghi sự kiện")
    void deleteDraftHappy() {
        Product p = ready();
        String key = p.getImages().get(0).getObjectKey();

        deleteDraft.handle(new DeleteDraft.Command(SELLER, p.getId().getValue()));

        assertThat(products.stored(p.getId().getValue())).isNull();
        assertThat(products.deleted).containsExactly(p.getId().getValue());
        verify(variants).deleteByProductId(p.getId());
        verify(uploads).discard("product-image", key);
        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("Xóa sản phẩm đã từng đăng (PUBLISHED hoặc UNPUBLISHED) -> PRODUCT_NOT_DELETABLE, không xóa, không hủy ảnh")
    void deleteRejectsPublishedOnce() {
        Product p = ready();
        doPublish(p);
        assertCode(ProductErrorCode.PRODUCT_NOT_DELETABLE,
                () -> deleteDraft.handle(new DeleteDraft.Command(SELLER, p.getId().getValue())));
        unpublish.handle(new UnpublishProduct.Command(SELLER, p.getId().getValue()));
        assertCode(ProductErrorCode.PRODUCT_NOT_DELETABLE,
                () -> deleteDraft.handle(new DeleteDraft.Command(SELLER, p.getId().getValue())));

        assertThat(products.deleted).isEmpty();
        verify(uploads, never()).discard(any(), any());
        verify(variants, never()).deleteByProductId(any());
    }

    @Test
    @DisplayName("Xóa nháp: ADMIN bị từ chối; người khác hoặc không có -> PRODUCT_NOT_FOUND")
    void deleteRoleAndOwnership() {
        Product p = ready();
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED,
                () -> deleteDraft.handle(new DeleteDraft.Command(ADMIN, p.getId().getValue())));
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND,
                () -> deleteDraft.handle(new DeleteDraft.Command(OTHER, p.getId().getValue())));
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND,
                () -> deleteDraft.handle(new DeleteDraft.Command(SELLER, UUID.randomUUID())));
        assertThat(products.deleted).isEmpty();
    }

    // ---- tạo nháp cần người bán đã duyệt ----

    @Test
    @DisplayName("Tạo nháp: chưa có bản sao, bị từ chối -> SELLER_NOT_APPROVED và không lưu; đã duyệt -> tạo được; không ghi sự kiện")
    void createNeedsApproval() {
        CatalogActor stranger = new CatalogActor(UUID.randomUUID(), "USER");
        CreateProduct.Command forStranger = new CreateProduct.Command(stranger, "Điện thoại X", "Mô tả",
                leaf.getId().getValue(), null, List.of());
        int saves = products.saveCount;

        assertCode(ProductErrorCode.SELLER_NOT_APPROVED, () -> create.handle(forStranger));
        sellerStates.reject(stranger.accountId());
        assertCode(ProductErrorCode.SELLER_NOT_APPROVED, () -> create.handle(forStranger));
        assertThat(products.saveCount).isEqualTo(saves);

        sellerStates.approve(stranger.accountId());
        ProductView view = create.handle(forStranger);
        assertThat(view.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(products.saveCount).isEqualTo(saves + 1);
        verifyNoInteractions(recorder);
    }
}
