package vn.t3nexus.catalog.domain.variant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AGG-CAT-05 — analysis.md §6.5. */
class VariantTest {

    private static final VariantCombination RED = new VariantCombination(List.of(
            new VariantAttributePair(AttributeTemplateId.of("01COLOR"), AttributeOptionId.of("red"))));

    private static Variant variant(long price) {
        return Variant.create(VariantId.of("01SKU"), "01P", RED, "SKU-1", price);
    }

    // ── INV-CAT-054 ──

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void nonPositivePriceRejectedOnCreate(long price) {
        assertThatThrownBy(() -> variant(price))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.VARIANT_PRICE_INVALID);
    }

    @Test
    void nonPositivePriceRejectedOnChange() {
        Variant v = variant(100_000);

        assertThatThrownBy(() -> v.changePrice(0)).isInstanceOf(DomainException.class);
        assertThat(v.getPrice()).isEqualTo(100_000);
    }

    @Test
    void changePriceRaisesEvent() {
        Variant v = variant(100_000);

        v.changePrice(120_000);

        assertThat(v.getDomainEvents()).hasSize(1).first().isInstanceOf(VariantPriceChangedEvent.class);
    }

    // ── Ma trận hành động × trạng thái: bật/tắt và sửa được ở cả 2 trạng thái ──

    @Test
    void toggleIsIdempotentAndRaisesEventOnlyOnChange() {
        Variant v = variant(100_000);
        v.deactivate();
        v.deactivate();
        v.activate();
        v.activate();

        assertThat(v.getDomainEvents()).hasSize(2);
        assertThat(v.isActive()).isTrue();
    }

    @Test
    void editableWhenInactive() {
        Variant v = variant(100_000);
        v.deactivate();

        v.changePrice(90_000);
        v.updateSkuCode("SKU-2");

        assertThat(v.getPrice()).isEqualTo(90_000);
        assertThat(v.getSkuCode()).isEqualTo("SKU-2");
    }

    // ── INV-CAT-051: tổ hợp bất biến, không so sánh theo thứ tự ──

    @Test
    void combinationEqualityIgnoresOrder() {
        VariantAttributePair color = new VariantAttributePair(AttributeTemplateId.of("01COLOR"), AttributeOptionId.of("red"));
        VariantAttributePair size = new VariantAttributePair(AttributeTemplateId.of("01SIZE"), AttributeOptionId.of("m"));

        assertThat(new VariantCombination(List.of(color, size)))
                .isEqualTo(new VariantCombination(List.of(size, color)));
        assertThat(new VariantCombination(List.of(color, size)).toHash())
                .isEqualTo(new VariantCombination(List.of(size, color)).toHash());
    }
}
