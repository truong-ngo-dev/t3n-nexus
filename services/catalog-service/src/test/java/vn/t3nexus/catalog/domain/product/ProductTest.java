package vn.t3nexus.catalog.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-01 cho AGG-CAT-04: sản phẩm nháp (INV-CAT-30, 31, 33, 36) — tiêu đề, mô tả, thương hiệu, thuộc tính và khóa trục. */
class ProductTest {

    private static final Instant T0 = Instant.parse("2026-11-05T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-05T01:00:00Z");
    private static final UUID SELLER = UUID.fromString("00000000-0000-7000-8000-0000000000bb");
    private static final ProductLimits LIMITS = ProductLimits.defaults();

    private static final AttributeTemplateId COLOR = AttributeTemplateId.of(UUID.randomUUID());
    private static final AttributeTemplateId SIZE = AttributeTemplateId.of(UUID.randomUUID());
    private static final AttributeTemplateId NOTE = AttributeTemplateId.of(UUID.randomUUID());
    private static final AttributeOptionId RED = AttributeOptionId.of(UUID.randomUUID());
    private static final AttributeOptionId BLUE = AttributeOptionId.of(UUID.randomUUID());
    private static final AttributeOptionId M = AttributeOptionId.of(UUID.randomUUID());

    private static Product product(String title, String description, List<ProductAttribute> attributes) {
        return Product.create(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                title, description, attributes, LIMITS, T0);
    }

    private static Product product() {
        return product("Áo thun cotton", "Mô tả", List.of());
    }

    private static ProductAttribute color(boolean axis, AttributeOptionId... options) {
        return ProductAttribute.ofOptions(COLOR, axis, List.of(options));
    }

    private static ProductAttribute size(boolean axis, AttributeOptionId... options) {
        return ProductAttribute.ofOptions(SIZE, axis, List.of(options));
    }

    private static void assertCode(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static void assertField(String field, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CatalogFieldException.class,
                e -> assertThat(e.field()).isEqualTo(field));
    }

    // ---- tạo ----

    @Test
    @DisplayName("Tạo: DRAFT, chủ là người bán, không phiên bản (0 khi lưu), thương hiệu rỗng được")
    void createDraft() {
        Product p = product();
        assertThat(p.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(p.getSellerId()).isEqualTo(SELLER);
        assertThat(p.getBrandId()).isNull();
        assertThat(p.version()).isNull();
        assertThat(p.nextVersion()).isEqualTo(0L);
        assertThat(p.getAttributes()).isEmpty();
        assertThat(p.getCreatedAt()).isEqualTo(T0);
        assertThat(p.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Tiêu đề: cắt khoảng trắng, 5 đến 200 ký tự; ngoài giới hạn là 400 trường title")
    void titleBounds() {
        assertThat(product("  Áo thun  ", null, List.of()).getTitle()).isEqualTo("Áo thun");
        assertThat(product("a".repeat(5), null, List.of()).getTitle()).hasSize(5);
        assertThat(product("a".repeat(200), null, List.of()).getTitle()).hasSize(200);
        assertField("title", () -> product("abcd", null, List.of()));
        assertField("title", () -> product("a".repeat(201), null, List.of()));
        assertField("title", () -> product("   ", null, List.of()));
        assertField("title", () -> product(null, null, List.of()));
    }

    @Test
    @DisplayName("Giới hạn cấu hình được áp: tiêu đề tối thiểu 10 thì 9 ký tự bị từ chối")
    void configuredLimitsApply() {
        ProductLimits tight = new ProductLimits(10, 100, 100, 2, 50, 1_000_000_000L, 50);
        assertField("title", () -> Product.create(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()),
                null, "a".repeat(9), null, List.of(), tight, T0));
        assertField("description", () -> Product.create(ProductId.of(UUID.randomUUID()), SELLER,
                CategoryId.of(UUID.randomUUID()), null, "a".repeat(10), "d".repeat(101), List.of(), tight, T0));
    }

    @Test
    @DisplayName("Mô tả: tối đa 5.000; rỗng thành không có; ký tự đặc biệt lưu nguyên như chữ")
    void descriptionRules() {
        assertThat(product("Áo thun", "d".repeat(5_000), List.of()).getDescription()).hasSize(5_000);
        assertField("description", () -> product("Áo thun", "d".repeat(5_001), List.of()));
        assertThat(product("Áo thun", "   ", List.of()).getDescription()).isNull();
        assertThat(product("Áo thun", null, List.of()).getDescription()).isNull();
        String special = "<script>alert('x')</script> & \"quote\"";
        assertThat(product("Áo thun", special, List.of()).getDescription()).isEqualTo(special);
    }

    @Test
    @DisplayName("Quyền chủ: ownedBy đúng người bán")
    void ownership() {
        Product p = product();
        assertThat(p.ownedBy(SELLER)).isTrue();
        assertThat(p.ownedBy(UUID.randomUUID())).isFalse();
    }

    // ---- sửa thông tin ----

    @Test
    @DisplayName("Đổi tiêu đề, mô tả, thương hiệu: kiểm như lúc tạo và cập nhật thời điểm sửa")
    void changeDetails() {
        Product p = product();
        p.changeTitle("Áo thun mới", LIMITS, T1);
        p.changeDescription("Mô tả mới", LIMITS, T1);
        BrandId brand = BrandId.of(UUID.randomUUID());
        p.changeBrand(brand, T1);
        assertThat(p.getTitle()).isEqualTo("Áo thun mới");
        assertThat(p.getDescription()).isEqualTo("Mô tả mới");
        assertThat(p.getBrandId()).isEqualTo(brand);
        assertThat(p.getUpdatedAt()).isEqualTo(T1);
        assertThat(p.getCreatedAt()).isEqualTo(T0);

        p.changeBrand(null, T1);
        p.changeDescription(null, LIMITS, T1);
        assertThat(p.getBrandId()).isNull();
        assertThat(p.getDescription()).isNull();

        assertField("title", () -> p.changeTitle("abc", LIMITS, T1));
        assertField("description", () -> p.changeDescription("d".repeat(5_001), LIMITS, T1));
        assertThat(p.getTitle()).isEqualTo("Áo thun mới");
    }

    // ---- phiên bản chung với biến thể ----

    @Test
    @DisplayName("Phiên bản SAU thay đổi là version + 1; recordChange làm dòng sản phẩm đổi (thời điểm sửa)")
    void versioning() {
        Product p = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.DRAFT, List.of(), 3L, T0, T0);
        assertThat(p.nextVersion()).isEqualTo(4L);
        p.recordChange(T1);
        assertThat(p.getUpdatedAt()).isEqualTo(T1);
        assertThat(p.version()).isEqualTo(3L);
    }

    // ---- thuộc tính và trục ----

    @Test
    @DisplayName("Chưa có biến thể: thay danh sách thuộc tính, kể cả đổi trục, tự do")
    void replaceAttributesFreeWithoutVariants() {
        Product p = product("Áo thun", null, List.of(color(true, RED)));
        p.replaceAttributes(List.of(size(true, M), color(false, RED)), VariantUsage.none(), T1);
        assertThat(p.getAttributes()).containsExactly(size(true, M), color(false, RED));
        assertThat(p.getUpdatedAt()).isEqualTo(T1);
        p.replaceAttributes(List.of(), VariantUsage.none(), T1);
        assertThat(p.getAttributes()).isEmpty();
    }

    @Test
    @DisplayName("Đã có biến thể: đổi tập mẫu làm trục bị từ chối PRODUCT_AXES_LOCKED")
    void axisSetLockedAfterVariants() {
        VariantUsage used = new VariantUsage(true, true, Map.of(COLOR, Set.of(RED)));
        Product p = product("Áo thun", null, List.of(color(true, RED, BLUE)));

        // thêm trục thứ hai
        assertCode(ProductErrorCode.PRODUCT_AXES_LOCKED,
                () -> p.replaceAttributes(List.of(color(true, RED, BLUE), size(true, M)), used, T1));
        // bỏ cờ trục
        assertCode(ProductErrorCode.PRODUCT_AXES_LOCKED,
                () -> p.replaceAttributes(List.of(color(false, RED, BLUE)), used, T1));
        // bỏ hẳn thuộc tính làm trục
        assertCode(ProductErrorCode.PRODUCT_AXES_LOCKED, () -> p.replaceAttributes(List.of(), used, T1));
        // đổi sang trục khác
        assertCode(ProductErrorCode.PRODUCT_AXES_LOCKED,
                () -> p.replaceAttributes(List.of(size(true, M)), used, T1));
        assertThat(p.getAttributes()).containsExactly(color(true, RED, BLUE));
    }

    @Test
    @DisplayName("Sản phẩm có biến thể mặc định (không trục): không thêm trục được")
    void cannotAddAxisAfterDefaultVariant() {
        VariantUsage defaultVariant = new VariantUsage(true, true, Map.of());
        Product p = product();
        assertCode(ProductErrorCode.PRODUCT_AXES_LOCKED,
                () -> p.replaceAttributes(List.of(color(true, RED)), defaultVariant, T1));
        p.replaceAttributes(List.of(color(false, RED)), defaultVariant, T1);
        assertThat(p.getAttributes()).containsExactly(color(false, RED));
    }

    @Test
    @DisplayName("Đã có biến thể: thêm giá trị trục và bỏ giá trị chưa dùng được; bỏ giá trị đang dùng bị từ chối")
    void axisValuesWithVariants() {
        VariantUsage used = new VariantUsage(true, false, Map.of(COLOR, Set.of(RED)));
        Product p = product("Áo thun", null, List.of(color(true, RED, BLUE)));

        p.replaceAttributes(List.of(color(true, RED)), used, T1);
        assertThat(p.getAttributes()).containsExactly(color(true, RED));

        assertCode(ProductErrorCode.PRODUCT_AXIS_VALUE_IN_USE,
                () -> p.replaceAttributes(List.of(color(true, BLUE)), used, T1));
        assertThat(p.getAttributes()).containsExactly(color(true, RED));

        p.replaceAttributes(List.of(color(true, RED, BLUE, M)), used, T1);
        assertThat(p.getAttributes()).containsExactly(color(true, RED, BLUE, M));
    }

    @Test
    @DisplayName("Đã có biến thể: sửa thông số không phải trục vẫn tự do")
    void nonAxisAttributesFreeWithVariants() {
        VariantUsage used = new VariantUsage(true, true, Map.of(COLOR, Set.of(RED)));
        ProductAttribute note = ProductAttribute.ofValue(NOTE, "cũ");
        Product p = product("Áo thun", null, List.of(color(true, RED), note));
        p.replaceAttributes(List.of(color(true, RED), ProductAttribute.ofValue(NOTE, "mới")), used, T1);
        assertThat(p.getAttributes()).containsExactly(color(true, RED), ProductAttribute.ofValue(NOTE, "mới"));
    }

    @Test
    @DisplayName("Danh sách thuộc tính trả về không sửa được từ ngoài")
    void attributesAreImmutableView() {
        Product p = product("Áo thun", null, List.of(color(false, RED)));
        assertThatThrownBy(() -> p.getAttributes().add(color(false, BLUE)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- ảnh sản phẩm ----

    private static ProductImage sampleImage(UUID id) {
        return ProductImage.of(ProductImageId.of(id), "products/" + id + ".jpg", "image/jpeg", 2048L, 999);
    }

    @Test
    @DisplayName("addImage thêm ở cuối và position liền nhau từ 0")
    void addImageAppendsAndConsecutivePosition() {
        Product p = product();
        ProductImage img1 = sampleImage(UUID.randomUUID());
        ProductImage img2 = sampleImage(UUID.randomUUID());

        p.addImage(img1, 5, T1);
        p.addImage(img2, 5, T1);

        assertThat(p.getImages()).hasSize(2);
        assertThat(p.getImages().get(0).getId()).isEqualTo(img1.getId());
        assertThat(p.getImages().get(0).getPosition()).isEqualTo(0);
        assertThat(p.getImages().get(1).getId()).isEqualTo(img2.getId());
        assertThat(p.getImages().get(1).getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("Đủ maxImages (maxImages = 2) thì ảnh thứ 3 báo IMAGE_LIMIT_REACHED và danh sách không đổi")
    void addImageLimitReached() {
        Product p = product();
        ProductImage img1 = sampleImage(UUID.randomUUID());
        ProductImage img2 = sampleImage(UUID.randomUUID());
        ProductImage img3 = sampleImage(UUID.randomUUID());

        p.addImage(img1, 2, T1);
        p.addImage(img2, 2, T1);

        assertCode(ProductErrorCode.IMAGE_LIMIT_REACHED, () -> p.addImage(img3, 2, T1));
        assertThat(p.getImages()).hasSize(2);
        assertThat(p.getImages().get(0).getId()).isEqualTo(img1.getId());
        assertThat(p.getImages().get(1).getId()).isEqualTo(img2.getId());
    }

    @Test
    @DisplayName("addImage tăng updatedAt nghiêm ngặt")
    void addImageStrictlyIncreasesUpdatedAt() {
        Product p = product();
        Instant before = p.getUpdatedAt();

        // thêm với mốc thời gian quá khứ hoặc bằng before vẫn tăng nghiêm ngặt qua recordChange
        p.addImage(sampleImage(UUID.randomUUID()), 5, before);
        assertThat(p.getUpdatedAt()).isAfter(before);

        Instant next = p.getUpdatedAt();
        p.addImage(sampleImage(UUID.randomUUID()), 5, next);
        assertThat(p.getUpdatedAt()).isAfter(next);
    }

    @Test
    @DisplayName("removeImage giữa danh sách dồn position, trả ảnh đã xóa; ảnh không có báo IMAGE_NOT_FOUND và không đổi gì")
    void removeImageShiftsPositionAndNotFound() {
        Product p = product();
        ProductImage img1 = sampleImage(UUID.randomUUID());
        ProductImage img2 = sampleImage(UUID.randomUUID());
        ProductImage img3 = sampleImage(UUID.randomUUID());

        p.addImage(img1, 5, T1);
        p.addImage(img2, 5, T1);
        p.addImage(img3, 5, T1);

        // Xóa ảnh ở giữa (img2)
        ProductImage removed = p.removeImage(img2.getId(), T1);
        assertThat(removed.getId()).isEqualTo(img2.getId());
        assertThat(p.getImages()).hasSize(2);
        assertThat(p.getImages().get(0).getId()).isEqualTo(img1.getId());
        assertThat(p.getImages().get(0).getPosition()).isEqualTo(0);
        assertThat(p.getImages().get(1).getId()).isEqualTo(img3.getId());
        assertThat(p.getImages().get(1).getPosition()).isEqualTo(1);

        // Không tìm thấy ảnh
        ProductImageId unknownId = ProductImageId.of(UUID.randomUUID());
        assertCode(ProductErrorCode.IMAGE_NOT_FOUND, () -> p.removeImage(unknownId, T1));
        assertThat(p.getImages()).hasSize(2);
        assertThat(p.getImages().get(0).getId()).isEqualTo(img1.getId());
        assertThat(p.getImages().get(1).getId()).isEqualTo(img3.getId());
    }

    @Test
    @DisplayName("reorderImages đổi thứ tự trả true và position khớp; cùng thứ tự trả false và updatedAt KHÔNG đổi")
    void reorderImagesReorderingAndSameOrder() {
        Product p = product();
        ProductImage img1 = sampleImage(UUID.randomUUID());
        ProductImage img2 = sampleImage(UUID.randomUUID());
        ProductImage img3 = sampleImage(UUID.randomUUID());

        p.addImage(img1, 5, T1);
        p.addImage(img2, 5, T1);
        p.addImage(img3, 5, T1);

        Instant timeBeforeSame = p.getUpdatedAt();
        boolean changedSame = p.reorderImages(List.of(img1.getId(), img2.getId(), img3.getId()), T1.plusSeconds(10));
        assertThat(changedSame).isFalse();
        assertThat(p.getUpdatedAt()).isEqualTo(timeBeforeSame);

        // Đổi thứ tự: [img3, img1, img2]
        Instant timeBeforeReorder = p.getUpdatedAt();
        boolean changedDiff = p.reorderImages(List.of(img3.getId(), img1.getId(), img2.getId()), T1.plusSeconds(20));
        assertThat(changedDiff).isTrue();
        assertThat(p.getUpdatedAt()).isAfter(timeBeforeReorder);

        List<ProductImage> current = p.getImages();
        assertThat(current).hasSize(3);
        assertThat(current.get(0).getId()).isEqualTo(img3.getId());
        assertThat(current.get(0).getPosition()).isEqualTo(0);
        assertThat(current.get(1).getId()).isEqualTo(img1.getId());
        assertThat(current.get(1).getPosition()).isEqualTo(1);
        assertThat(current.get(2).getId()).isEqualTo(img2.getId());
        assertThat(current.get(2).getPosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("reorderImages: thiếu một mã, thừa mã lạ, trùng mã, danh sách rỗng khi đang có ảnh -> IMAGE_ORDER_INVALID và thứ tự cũ giữ nguyên")
    void reorderImagesValidation() {
        Product p = product();
        ProductImage img1 = sampleImage(UUID.randomUUID());
        ProductImage img2 = sampleImage(UUID.randomUUID());
        p.addImage(img1, 5, T1);
        p.addImage(img2, 5, T1);

        ProductImageId stranger = ProductImageId.of(UUID.randomUUID());

        // Thiếu một mã
        assertCode(ProductErrorCode.IMAGE_ORDER_INVALID, () -> p.reorderImages(List.of(img1.getId()), T1));
        // Thừa mã lạ
        assertCode(ProductErrorCode.IMAGE_ORDER_INVALID, () -> p.reorderImages(List.of(img1.getId(), img2.getId(), stranger), T1));
        // Thay mã bằng mã lạ (cùng kích thước)
        assertCode(ProductErrorCode.IMAGE_ORDER_INVALID, () -> p.reorderImages(List.of(img1.getId(), stranger), T1));
        // Trùng mã
        assertCode(ProductErrorCode.IMAGE_ORDER_INVALID, () -> p.reorderImages(List.of(img1.getId(), img1.getId()), T1));
        // Danh sách rỗng khi đang có ảnh
        assertCode(ProductErrorCode.IMAGE_ORDER_INVALID, () -> p.reorderImages(List.of(), T1));
        // Chứa null
        assertCode(ProductErrorCode.IMAGE_ORDER_INVALID, () -> {
            java.util.List<ProductImageId> withNull = new java.util.ArrayList<>();
            withNull.add(img1.getId());
            withNull.add(null);
            p.reorderImages(withNull, T1);
        });

        // Thứ tự cũ vẫn giữ nguyên
        assertThat(p.getImages().get(0).getId()).isEqualTo(img1.getId());
        assertThat(p.getImages().get(0).getPosition()).isEqualTo(0);
        assertThat(p.getImages().get(1).getId()).isEqualTo(img2.getId());
        assertThat(p.getImages().get(1).getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("reconstitute quá tải giữ ảnh")
    void reconstituteOverloadKeepsImages() {
        ProductId id = ProductId.of(UUID.randomUUID());
        ProductImage img = sampleImage(UUID.randomUUID()).withPosition(0);
        Product p = Product.reconstitute(id, SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun cotton", "Mô tả", ProductStatus.DRAFT, List.of(), List.of(img), 1L, T0, T1);

        assertThat(p.getImages()).hasSize(1);
        assertThat(p.getImages().get(0).getId()).isEqualTo(img.getId());
        assertThat(p.getImages().get(0).getPosition()).isEqualTo(0);
    }

    @Test
    @DisplayName("publish từ DRAFT đặt status PUBLISHED, publishedAt = now, nextVersion/updatedAt tăng")
    void publishFromDraft() {
        Product p = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.DRAFT, List.of(), List.of(), null, 1L, T0, T0);
        assertThat(p.nextVersion()).isEqualTo(2L);

        p.publish(T1);

        assertThat(p.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(p.isPublished()).isTrue();
        assertThat(p.getPublishedAt()).isEqualTo(T1);
        assertThat(p.getUpdatedAt()).isEqualTo(T1);
        assertThat(p.nextVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("publish lần hai khi PUBLISHED -> CATALOG_INVALID_STATE")
    void publishWhenAlreadyPublished() {
        Product p = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.PUBLISHED, List.of(), List.of(), T0, 1L, T0, T0);
        assertCode(CatalogErrorCode.CATALOG_INVALID_STATE, () -> p.publish(T1));
    }

    @Test
    @DisplayName("unpublish từ PUBLISHED -> UNPUBLISHED, publishedAt giữ nguyên")
    void unpublishFromPublished() {
        Product p = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.PUBLISHED, List.of(), List.of(), T0, 1L, T0, T0);

        p.unpublish(T1);

        assertThat(p.getStatus()).isEqualTo(ProductStatus.UNPUBLISHED);
        assertThat(p.isPublished()).isFalse();
        assertThat(p.getPublishedAt()).isEqualTo(T0);
        assertThat(p.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("unpublish khi DRAFT hoặc UNPUBLISHED -> CATALOG_INVALID_STATE")
    void unpublishWhenDraftOrUnpublished() {
        Product draft = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.DRAFT, List.of(), List.of(), null, 1L, T0, T0);
        assertCode(CatalogErrorCode.CATALOG_INVALID_STATE, () -> draft.unpublish(T1));

        Product unpublished = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.UNPUBLISHED, List.of(), List.of(), T0, 1L, T0, T0);
        assertCode(CatalogErrorCode.CATALOG_INVALID_STATE, () -> unpublished.unpublish(T1));
    }

    @Test
    @DisplayName("publish lại từ UNPUBLISHED giữ publishedAt của lần đầu")
    void republishKeepsInitialPublishedAt() {
        Instant t2 = Instant.parse("2026-11-05T02:00:00Z");
        Product p = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.UNPUBLISHED, List.of(), List.of(), T0, 1L, T0, T1);

        p.publish(t2);

        assertThat(p.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(p.getPublishedAt()).isEqualTo(T0);
        assertThat(p.getUpdatedAt()).isEqualTo(t2);
    }

    @Test
    @DisplayName("assertDeletable: DRAFT chưa đăng không ném; PUBLISHED ném; UNPUBLISHED ném; DRAFT nhưng publishedAt != null ném")
    void assertDeletableRules() {
        Product draftClean = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.DRAFT, List.of(), List.of(), null, 1L, T0, T0);
        draftClean.assertDeletable(); // Không ném

        Product published = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.PUBLISHED, List.of(), List.of(), T0, 1L, T0, T0);
        assertCode(ProductErrorCode.PRODUCT_NOT_DELETABLE, published::assertDeletable);

        Product unpublished = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.UNPUBLISHED, List.of(), List.of(), T0, 1L, T0, T0);
        assertCode(ProductErrorCode.PRODUCT_NOT_DELETABLE, unpublished::assertDeletable);

        Product draftWithPublishedAt = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.DRAFT, List.of(), List.of(), T0, 1L, T0, T0);
        assertCode(ProductErrorCode.PRODUCT_NOT_DELETABLE, draftWithPublishedAt::assertDeletable);
    }

    @Test
    @DisplayName("removeImage ảnh cuối khi PUBLISHED -> PRODUCT_LAST_IMAGE và danh sách ảnh không đổi")
    void removeLastImageWhenPublished() {
        ProductImage img = sampleImage(UUID.randomUUID()).withPosition(0);
        Product p = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.PUBLISHED, List.of(), List.of(img), T0, 1L, T0, T0);

        assertCode(ProductErrorCode.PRODUCT_LAST_IMAGE, () -> p.removeImage(img.getId(), T1));
        assertThat(p.getImages()).hasSize(1);
        assertThat(p.getImages().get(0).getId()).isEqualTo(img.getId());
    }

    @Test
    @DisplayName("removeImage ảnh cuối khi DRAFT vẫn được")
    void removeLastImageWhenDraft() {
        ProductImage img = sampleImage(UUID.randomUUID()).withPosition(0);
        Product p = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.DRAFT, List.of(), List.of(img), null, 1L, T0, T0);

        ProductImage removed = p.removeImage(img.getId(), T1);
        assertThat(removed.getId()).isEqualTo(img.getId());
        assertThat(p.getImages()).isEmpty();
    }

    @Test
    @DisplayName("removeImage khi PUBLISHED còn 2 ảnh được")
    void removeImageWhenPublishedWithMultipleImages() {
        ProductImage img1 = sampleImage(UUID.randomUUID()).withPosition(0);
        ProductImage img2 = sampleImage(UUID.randomUUID()).withPosition(1);
        Product p = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.PUBLISHED, List.of(), List.of(img1, img2), T0, 1L, T0, T0);

        ProductImage removed = p.removeImage(img1.getId(), T1);
        assertThat(removed.getId()).isEqualTo(img1.getId());
        assertThat(p.getImages()).hasSize(1);
        assertThat(p.getImages().get(0).getId()).isEqualTo(img2.getId());
        assertThat(p.getImages().get(0).getPosition()).isEqualTo(0);
    }

    @Test
    @DisplayName("removeImage ảnh không tồn tại khi PUBLISHED với 1 ảnh -> IMAGE_NOT_FOUND")
    void removeNonExistentImageWhenPublishedWithOneImage() {
        ProductImage img = sampleImage(UUID.randomUUID()).withPosition(0);
        Product p = Product.reconstitute(ProductId.of(UUID.randomUUID()), SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, ProductStatus.PUBLISHED, List.of(), List.of(img), T0, 1L, T0, T0);

        ProductImageId stranger = ProductImageId.of(UUID.randomUUID());
        assertCode(ProductErrorCode.IMAGE_NOT_FOUND, () -> p.removeImage(stranger, T1));
        assertThat(p.getImages()).hasSize(1);
    }

    @Test
    @DisplayName("reconstitute 13 tham số giữ publishedAt")
    void reconstitute13ParamsKeepsPublishedAt() {
        ProductId id = ProductId.of(UUID.randomUUID());
        ProductImage img = sampleImage(UUID.randomUUID()).withPosition(0);
        Product p = Product.reconstitute(id, SELLER, CategoryId.of(UUID.randomUUID()), null,
                "Áo thun cotton", "Mô tả", ProductStatus.PUBLISHED, List.of(), List.of(img), T0, 1L, T0, T1);

        assertThat(p.getPublishedAt()).isEqualTo(T0);
        assertThat(p.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(p.getImages()).hasSize(1);
    }
}
