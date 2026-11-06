package vn.t3nexus.catalog.application.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductImage;
import vn.t3nexus.catalog.domain.product.ProductImageId;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.upload.UploadErrorCode;
import vn.t3nexus.lib.upload.UploadSlots;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final CatalogActor SELLER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000bb"), "USER");
    private static final CatalogActor OTHER_SELLER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000cc"), "USER");
    private static final CatalogActor ADMIN = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000aa"), "ADMIN");

    private List<String> calls;
    private FakeProductRepository products;
    private UploadSlots uploads;
    private Clock clock;
    private IdGenerator ids;
    private ImageLimits imageLimits;
    private TransactionTemplate txTemplate;
    private ProductEventRecorder recorder;

    private RequestImageUpload requestUpload;
    private ConfirmImage confirmImage;
    private ReorderImages reorderImages;
    private RemoveImage removeImage;

    private ProductId productId;

    @BeforeEach
    void setUp() {
        calls = new ArrayList<>();
        products = new FakeProductRepository() {
            @Override
            public java.util.Optional<Product> findOwned(ProductId id, UUID sellerId) {
                calls.add("plain");
                return super.findOwned(id, sellerId);
            }

            @Override
            public java.util.Optional<Product> findOwnedForUpdate(ProductId id, UUID sellerId) {
                calls.add("lock");
                return super.findOwnedForUpdate(id, sellerId);
            }

            @Override
            public void save(Product p) {
                calls.add("save");
                super.save(p);
            }
        };

        uploads = mock(UploadSlots.class);
        doAnswer(inv -> {
            calls.add("verify");
            UUID slotId = inv.getArgument(0);
            String purpose = inv.getArgument(1);
            return new UploadSlots.Verified(slotId, purpose, "key-" + slotId, "image/jpeg", 1024L);
        }).when(uploads).verify(any(), any(), any(), any());

        doAnswer(inv -> {
            calls.add("consume");
            UploadSlots.Verified v = inv.getArgument(0);
            return new UploadSlots.Claimed(v.objectKey(), v.contentType(), v.size());
        }).when(uploads).consume(any());

        doAnswer(inv -> {
            calls.add("discard");
            return null;
        }).when(uploads).discard(anyString(), anyString());

        when(uploads.urlOf(any(), any())).thenAnswer(inv -> "http://cdn/" + inv.getArgument(1));

        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AtomicLong counter = new AtomicLong(1);
        ids = () -> new UUID(0L, counter.getAndIncrement());
        imageLimits = new ImageLimits(2);

        PlatformTransactionManager ptm = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) throws TransactionException {
            }

            @Override
            public void rollback(TransactionStatus status) throws TransactionException {
            }
        };
        txTemplate = new TransactionTemplate(ptm);
        recorder = mock(ProductEventRecorder.class);

        requestUpload = new RequestImageUpload(products, uploads, imageLimits);
        confirmImage = new ConfirmImage(products, uploads, ids, clock, imageLimits, txTemplate, recorder);
        reorderImages = new ReorderImages(products, uploads, clock, recorder);
        removeImage = new RemoveImage(products, uploads, clock, txTemplate, recorder);

        productId = ProductId.of(UUID.fromString("00000000-0000-7000-8000-000000000001"));
        Product p = Product.create(productId, SELLER.accountId(),
                vn.t3nexus.catalog.domain.category.CategoryId.of(UUID.randomUUID()),
                null, "Sản phẩm test", "Mô tả test", List.of(), ProductLimits.defaults(), NOW);
        products.save(p);
        calls.clear();
        products.reads.clear();
        products.saveCount = 0;
    }

    private Product addImageToProduct(ProductId pId, String key) {
        Product p = products.findById(pId).orElseThrow();
        p.addImage(ProductImage.of(ProductImageId.of(ids.generate()), key, "image/jpeg", 1024L, 0),
                imageLimits.max(), NOW);
        products.save(p);
        return p;
    }

    // ──────────────── RequestImageUpload ────────────────

    @Test
    @DisplayName("RequestImageUpload: đạt gọi issue đúng tham số và trả UploadSlotView")
    void requestUploadSuccess() {
        UUID slotId = UUID.randomUUID();
        when(uploads.issue(any())).thenReturn(
                new UploadSlots.Issued(slotId, "key-1", "http://upload", Map.of("k", "v"), NOW.plusSeconds(300), 5000L)
        );

        UploadSlotView view = requestUpload.handle(new RequestImageUpload.Command(SELLER, productId.getValue(), "image/jpeg", 1000L));

        assertThat(view.slotId()).isEqualTo(slotId);
        assertThat(view.url()).isEqualTo("http://upload");
        assertThat(view.fields()).containsEntry("k", "v");
        assertThat(view.maxSize()).isEqualTo(5000L);
        verify(uploads).issue(new UploadSlots.IssueRequest("product-image", SELLER.accountId(), productId.getValue(), "image/jpeg", 1000L));
    }

    @Test
    @DisplayName("RequestImageUpload: vai ADMIN -> CATALOG_ROLE_NOT_ALLOWED không đụng kho")
    void requestUploadAdminForbidden() {
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED, () ->
                requestUpload.handle(new RequestImageUpload.Command(ADMIN, productId.getValue(), "image/jpeg", 1000L))
        );
        assertThat(products.reads).isEmpty();
        verify(uploads, never()).issue(any());
    }

    @Test
    @DisplayName("RequestImageUpload: sản phẩm người khác -> PRODUCT_NOT_FOUND")
    void requestUploadOtherSellerNotFound() {
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND, () ->
                requestUpload.handle(new RequestImageUpload.Command(OTHER_SELLER, productId.getValue(), "image/jpeg", 1000L))
        );
        verify(uploads, never()).issue(any());
    }

    @Test
    @DisplayName("RequestImageUpload: đã đủ ảnh -> IMAGE_LIMIT_REACHED và KHÔNG gọi issue")
    void requestUploadLimitReached() {
        addImageToProduct(productId, "img-1");
        addImageToProduct(productId, "img-2");

        assertCode(ProductErrorCode.IMAGE_LIMIT_REACHED, () ->
                requestUpload.handle(new RequestImageUpload.Command(SELLER, productId.getValue(), "image/jpeg", 1000L))
        );
        verify(uploads, never()).issue(any());
    }

    // ──────────────── ConfirmImage ────────────────

    @Test
    @DisplayName("ConfirmImage: đạt (ảnh được thêm ở cuối, products.saveCount tăng, consume được gọi, trả ImageView); THỨ TỰ: plain -> verify -> lock -> consume")
    void confirmImageSuccess() {
        UUID slotId = UUID.randomUUID();
        int savesBefore = products.saveCount;

        ImageView view = confirmImage.handle(new ConfirmImage.Command(SELLER, productId.getValue(), slotId));

        assertThat(view.position()).isEqualTo(0);
        assertThat(view.url()).isEqualTo("http://cdn/key-" + slotId);
        assertThat(products.saveCount).isEqualTo(savesBefore + 1);
        verify(recorder).onChanged(any(Product.class), eq(List.of()), eq(ProductEventRecorder.Trigger.EDIT));

        // Thứ tự gọi: plain -> verify -> lock -> consume
        assertThat(calls).containsSubsequence("plain", "verify", "lock", "consume");
        int idxPlain = calls.indexOf("plain");
        int idxVerify = calls.indexOf("verify");
        int idxLock = calls.indexOf("lock");
        int idxConsume = calls.indexOf("consume");

        assertThat(idxPlain).isLessThan(idxVerify);
        assertThat(idxVerify).isLessThan(idxLock);
        assertThat(idxLock).isLessThan(idxConsume);
    }

    @Test
    @DisplayName("ConfirmImage: ADMIN -> 403 mã và không gọi verify")
    void confirmImageAdminForbidden() {
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED, () ->
                confirmImage.handle(new ConfirmImage.Command(ADMIN, productId.getValue(), UUID.randomUUID()))
        );
        verify(uploads, never()).verify(any(), any(), any(), any());
    }

    @Test
    @DisplayName("ConfirmImage: sản phẩm người khác -> PRODUCT_NOT_FOUND và không gọi verify")
    void confirmImageOtherSellerNotFound() {
        assertCode(ProductErrorCode.PRODUCT_NOT_FOUND, () ->
                confirmImage.handle(new ConfirmImage.Command(OTHER_SELLER, productId.getValue(), UUID.randomUUID()))
        );
        verify(uploads, never()).verify(any(), any(), any(), any());
    }

    @Test
    @DisplayName("ConfirmImage: verify ném DomainException(UPLOAD_SLOT_NOT_FOUND) -> ném lại, không lock, không discard")
    void confirmImageVerifyThrows() {
        when(uploads.verify(any(), any(), any(), any()))
                .thenThrow(new DomainException(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND));

        assertCode(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND, () ->
                confirmImage.handle(new ConfirmImage.Command(SELLER, productId.getValue(), UUID.randomUUID()))
        );
        assertThat(calls).doesNotContain("lock");
        assertThat(calls).doesNotContain("discard");
    }

    @Test
    @DisplayName("ConfirmImage: đủ ảnh ở pha 2 -> IMAGE_LIMIT_REACHED, discard được gọi với khóa đối tượng, products không lưu")
    void confirmImageLimitReachedInPhase2() {
        addImageToProduct(productId, "img-1");
        addImageToProduct(productId, "img-2");
        calls.clear();
        int savesBefore = products.saveCount;

        UUID slotId = UUID.randomUUID();
        assertCode(ProductErrorCode.IMAGE_LIMIT_REACHED, () ->
                confirmImage.handle(new ConfirmImage.Command(SELLER, productId.getValue(), slotId))
        );

        assertThat(calls).contains("discard");
        verify(uploads).discard("product-image", "key-" + slotId);
        assertThat(products.saveCount).isEqualTo(savesBefore);
    }

    @Test
    @DisplayName("ConfirmImage: consume ném UPLOAD_SLOT_NOT_FOUND (yêu cầu khác đã dùng chỗ và giữ đối tượng) -> ném lại và discard KHÔNG được gọi")
    void confirmImageConsumeThrows() {
        UUID slotId = UUID.randomUUID();
        doAnswer(inv -> {
            calls.add("consume");
            throw new DomainException(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND);
        }).when(uploads).consume(any());

        assertCode(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND, () ->
                confirmImage.handle(new ConfirmImage.Command(SELLER, productId.getValue(), slotId))
        );

        assertThat(calls).doesNotContain("discard");
        verify(uploads, never()).discard(any(), any());
    }

    @Test
    @DisplayName("ConfirmImage: ngoại lệ RuntimeException lạ ở pha 2 -> ném lại, discard KHÔNG gọi")
    void confirmImagePhase2UnknownException() {
        UUID slotId = UUID.randomUUID();
        doAnswer(inv -> {
            calls.add("consume");
            throw new IllegalStateException("Database down");
        }).when(uploads).consume(any());

        assertThatThrownBy(() -> confirmImage.handle(new ConfirmImage.Command(SELLER, productId.getValue(), slotId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database down");

        assertThat(calls).doesNotContain("discard");
    }

    // ──────────────── ReorderImages ────────────────

    @Test
    @DisplayName("ReorderImages: đổi thứ tự lưu (saveCount tăng, danh sách trả theo thứ tự mới)")
    void reorderImagesSuccess() {
        addImageToProduct(productId, "img-1");
        addImageToProduct(productId, "img-2");
        Product p = products.findById(productId).orElseThrow();
        UUID id1 = p.getImages().get(0).getId().getValue();
        UUID id2 = p.getImages().get(1).getId().getValue();

        calls.clear();
        int savesBefore = products.saveCount;

        List<ImageView> result = reorderImages.handle(new ReorderImages.Command(SELLER, productId.getValue(), List.of(id2, id1)));

        assertThat(products.saveCount).isEqualTo(savesBefore + 1);
        assertThat(result.get(0).id()).isEqualTo(id2);
        assertThat(result.get(0).position()).isEqualTo(0);
        assertThat(result.get(1).id()).isEqualTo(id1);
        assertThat(result.get(1).position()).isEqualTo(1);
        assertThat(calls).contains("lock");
    }

    @Test
    @DisplayName("ReorderImages: cùng thứ tự không lưu (saveCount không đổi)")
    void reorderImagesNoChange() {
        addImageToProduct(productId, "img-1");
        addImageToProduct(productId, "img-2");
        Product p = products.findById(productId).orElseThrow();
        UUID id1 = p.getImages().get(0).getId().getValue();
        UUID id2 = p.getImages().get(1).getId().getValue();

        int savesBefore = products.saveCount;
        List<ImageView> result = reorderImages.handle(new ReorderImages.Command(SELLER, productId.getValue(), List.of(id1, id2)));

        assertThat(products.saveCount).isEqualTo(savesBefore);
        assertThat(result.get(0).id()).isEqualTo(id1);
    }

    @Test
    @DisplayName("ReorderImages: danh sách sai -> IMAGE_ORDER_INVALID")
    void reorderImagesInvalid() {
        addImageToProduct(productId, "img-1");

        assertCode(ProductErrorCode.IMAGE_ORDER_INVALID, () ->
                reorderImages.handle(new ReorderImages.Command(SELLER, productId.getValue(), List.of(UUID.randomUUID())))
        );
    }

    @Test
    @DisplayName("ReorderImages: luôn dùng đường khóa (lock)")
    void reorderImagesUsesLock() {
        addImageToProduct(productId, "img-1");
        Product p = products.findById(productId).orElseThrow();
        UUID id1 = p.getImages().get(0).getId().getValue();

        calls.clear();
        reorderImages.handle(new ReorderImages.Command(SELLER, productId.getValue(), List.of(id1)));
        assertThat(calls).contains("lock");
        assertThat(calls).doesNotContain("plain");
    }

    // ──────────────── RemoveImage ────────────────

    @Test
    @DisplayName("RemoveImage: bỏ ảnh lưu sản phẩm, trả ảnh còn lại dồn vị trí, discard gọi đúng một lần với khóa ảnh bị bỏ SAU khi lưu")
    void removeImageSuccess() {
        addImageToProduct(productId, "img-1");
        addImageToProduct(productId, "img-2");
        Product p = products.findById(productId).orElseThrow();
        UUID id1 = p.getImages().get(0).getId().getValue();
        UUID id2 = p.getImages().get(1).getId().getValue();

        calls.clear();
        int savesBefore = products.saveCount;

        List<ImageView> result = removeImage.handle(new RemoveImage.Command(SELLER, productId.getValue(), id1));

        assertThat(products.saveCount).isEqualTo(savesBefore + 1);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id2);
        assertThat(result.get(0).position()).isEqualTo(0);

        verify(uploads).discard("product-image", "img-1");
        assertThat(calls).containsSubsequence("lock", "save", "discard");
    }

    @Test
    @DisplayName("RemoveImage: ảnh không có -> IMAGE_NOT_FOUND và KHÔNG discard")
    void removeImageNotFound() {
        assertCode(ProductErrorCode.IMAGE_NOT_FOUND, () ->
                removeImage.handle(new RemoveImage.Command(SELLER, productId.getValue(), UUID.randomUUID()))
        );
        verify(uploads, never()).discard(any(), any());
    }

    @Test
    @DisplayName("RemoveImage: dùng đường khóa")
    void removeImageUsesLock() {
        addImageToProduct(productId, "img-1");
        Product p = products.findById(productId).orElseThrow();
        UUID id1 = p.getImages().get(0).getId().getValue();

        calls.clear();
        removeImage.handle(new RemoveImage.Command(SELLER, productId.getValue(), id1));
        assertThat(calls).contains("lock");
    }

    @Test
    @DisplayName("RemoveImage: ADMIN -> 403 mã")
    void removeImageAdminForbidden() {
        assertCode(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED, () ->
                removeImage.handle(new RemoveImage.Command(ADMIN, productId.getValue(), UUID.randomUUID()))
        );
    }

    private void assertCode(ErrorCode expected, org.junit.jupiter.api.function.Executable exec) {
        assertThatThrownBy(exec::execute)
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode()).isEqualTo(expected));
    }
}
