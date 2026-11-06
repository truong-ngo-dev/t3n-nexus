package vn.t3nexus.catalog.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.category.CategoryId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductVisibilityTest {

    private static final Instant T0 = Instant.parse("2026-11-05T00:00:00Z");
    private static final UUID SELLER_ID = UUID.randomUUID();

    private static Product productWithStatus(ProductStatus status) {
        return Product.reconstitute(
                ProductId.of(UUID.randomUUID()),
                SELLER_ID,
                CategoryId.of(UUID.randomUUID()),
                null,
                "Sản phẩm test",
                "Mô tả sản phẩm",
                status,
                List.of(),
                List.of(),
                status == ProductStatus.PUBLISHED ? T0 : null,
                1L,
                T0,
                T0
        );
    }

    private static SellerState approvedSeller() {
        return new SellerState(SELLER_ID, SellerStatus.APPROVED, 1L);
    }

    private static SellerState rejectedSeller() {
        return new SellerState(SELLER_ID, SellerStatus.REJECTED, 1L);
    }

    @Test
    @DisplayName("PUBLISHED + APPROVED -> true")
    void publishedAndApproved() {
        Product p = productWithStatus(ProductStatus.PUBLISHED);
        SellerState s = approvedSeller();
        assertThat(ProductVisibility.isPubliclyVisible(p, s)).isTrue();
    }

    @Test
    @DisplayName("PUBLISHED + REJECTED -> false")
    void publishedAndRejected() {
        Product p = productWithStatus(ProductStatus.PUBLISHED);
        SellerState s = rejectedSeller();
        assertThat(ProductVisibility.isPubliclyVisible(p, s)).isFalse();
    }

    @Test
    @DisplayName("PUBLISHED + sellerState null -> false")
    void publishedAndSellerNull() {
        Product p = productWithStatus(ProductStatus.PUBLISHED);
        assertThat(ProductVisibility.isPubliclyVisible(p, null)).isFalse();
    }

    @Test
    @DisplayName("DRAFT + APPROVED -> false")
    void draftAndApproved() {
        Product p = productWithStatus(ProductStatus.DRAFT);
        SellerState s = approvedSeller();
        assertThat(ProductVisibility.isPubliclyVisible(p, s)).isFalse();
    }

    @Test
    @DisplayName("UNPUBLISHED + APPROVED -> false")
    void unpublishedAndApproved() {
        Product p = productWithStatus(ProductStatus.UNPUBLISHED);
        SellerState s = approvedSeller();
        assertThat(ProductVisibility.isPubliclyVisible(p, s)).isFalse();
    }
}
