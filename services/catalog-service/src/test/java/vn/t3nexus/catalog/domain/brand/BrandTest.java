package vn.t3nexus.catalog.domain.brand;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AGG-CAT-02 — analysis.md §6.2. */
class BrandTest {

    private static final BrandId ID = BrandId.of("01BRAND0000000000000000000");

    /** INV-CAT-022 — slug là 1 phần URL, bất biến, nên sai định dạng phải chặn ngay lúc tạo. */
    @ParameterizedTest
    @ValueSource(strings = {"Apple", "apple store", "điện-thoại", "-apple", "apple-", "apple--store", ""})
    void rejectsInvalidSlug(String slug) {
        assertThatThrownBy(() -> Brand.create(ID, "Apple", slug))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(BrandErrorCode.BRAND_SLUG_INVALID);
    }

    @Test
    void rejectsTooLongSlug() {
        String slug = "a".repeat(Brand.SLUG_MAX_LENGTH + 1);

        assertThatThrownBy(() -> Brand.create(ID, "Apple", slug))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"apple", "samsung-electronics", "3m"})
    void acceptsValidSlug(String slug) {
        assertThat(Brand.create(ID, "Apple", slug).getSlug()).isEqualTo(slug);
    }

    /** Ma trận hành động × trạng thái: đổi tên được cả khi đã ngừng; slug không đổi. */
    @Test
    void renameAllowedWhenInactiveAndSlugUnchanged() {
        Brand brand = Brand.create(ID, "Aple", "apple");
        brand.deactivate();

        brand.update("Apple");

        assertThat(brand.getName()).isEqualTo("Apple");
        assertThat(brand.getSlug()).isEqualTo("apple");
        assertThat(brand.getStatus()).isEqualTo(BrandStatus.INACTIVE);
    }
}
