package vn.t3nexus.catalog.domain.product;

import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AGG-CAT-04 — analysis.md §6.4 (ma trận hành động × trạng thái, INV-CAT-041…044). */
class ProductTest {

    private static final String SELLER = "01SELLER000000000000000000";

    private static Product draft() {
        return Product.create(ProductId.of("01PRODUCT00000000000000000"), SELLER,
                CategoryId.of("01CAT"), BrandId.of("01BRAND"), "Áo thun", "Mô tả", null, List.of());
    }

    private static Product published() {
        Product p = draft();
        p.publish("Brand", List.of("01SKU"));
        p.clearDomainEvents();
        return p;
    }

    private static void assertCode(Runnable action, ProductErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(code);
    }

    // ── INV-CAT-044 ──

    @Test
    void publiclyVisibleOnlyWhenPublishedAndNotBlocked() {
        assertThat(draft().isPubliclyVisible()).isFalse();

        Product p = published();
        assertThat(p.isPubliclyVisible()).isTrue();

        p.block("vi phạm");
        assertThat(p.isPubliclyVisible()).isFalse();

        p.unblock(); // bỏ chặn không cần "đăng lại" — trục A vẫn đang bán
        assertThat(p.isPubliclyVisible()).isTrue();
    }

    // ── Ranh giới sở hữu ──

    @Test
    void rejectsOtherSeller() {
        assertCode(() -> draft().assertOwnedBy("01OTHER"), ProductErrorCode.NOT_PRODUCT_OWNER);
    }

    // ── Chặn chỉ khoá "đưa ra công khai" ──

    @Test
    void blockedProductCanStillBeEditedAndUnpublished() {
        Product p = published();
        p.block("vi phạm");

        p.update("Áo thun mới", "Đã sửa", null, List.of());
        p.addImage(ProductImage.create(ProductImageId.of("01IMG"), "products/x/1", 0));
        p.removeImage(ProductImageId.of("01IMG"));
        p.unpublish();

        assertThat(p.getStatus()).isEqualTo(ProductStatus.UNPUBLISHED);
        assertThat(p.isAdminBlocked()).isTrue();
    }

    /** INV-CAT-042 */
    @Test
    void cannotPublishWhileBlocked() {
        Product p = draft();
        p.block("vi phạm");

        assertCode(() -> p.publish("Brand", List.of("01SKU")), ProductErrorCode.PRODUCT_BLOCKED);
    }

    // ── Trạng thái nguồn ──

    @Test
    void cannotUnpublishDraft() {
        assertCode(() -> draft().unpublish(), ProductErrorCode.INVALID_PRODUCT_TRANSITION);
    }

    @Test
    void cannotPublishTwice() {
        assertCode(() -> published().publish("Brand", List.of("01SKU")), ProductErrorCode.INVALID_PRODUCT_TRANSITION);
    }

    @Test
    void cannotBlockTwiceOrUnblockWhenNotBlocked() {
        Product p = draft();
        assertCode(p::unblock, ProductErrorCode.INVALID_PRODUCT_TRANSITION);
        p.block("vi phạm");
        assertCode(() -> p.block("lần 2"), ProductErrorCode.INVALID_PRODUCT_TRANSITION);
    }

    @Test
    void republishKeepsFirstPublishedAt() {
        Product p = published();
        var first = p.getPublishedAt();
        p.unpublish();

        p.publish("Brand", List.of("01SKU"));

        assertThat(p.getPublishedAt()).isEqualTo(first);
    }

    // ── INV-CAT-043, EVT-CAT-046 ──

    @Test
    void onlyDraftCanBeDeleted() {
        assertCode(() -> published().markDeleted(), ProductErrorCode.PRODUCT_NOT_DRAFT);

        Product p = draft();
        p.markDeleted();
        assertThat(p.getDomainEvents()).hasSize(1).first().isInstanceOf(ProductDeletedEvent.class);
    }

    // ── EVT-CAT-045 gồm cả thay đổi ảnh ──

    @Test
    void imageChangesRaiseProductUpdated() {
        Product p = draft();
        p.addImage(ProductImage.create(ProductImageId.of("01IMG"), "products/x/1", 0));
        p.removeImage(ProductImageId.of("01IMG"));

        assertThat(p.getDomainEvents()).hasSize(2).allMatch(e -> e instanceof ProductUpdatedEvent);
    }
}
