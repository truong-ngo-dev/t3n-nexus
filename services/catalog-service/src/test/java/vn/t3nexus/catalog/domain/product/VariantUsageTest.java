package vn.t3nexus.catalog.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;
import vn.t3nexus.catalog.domain.variant.VariantId;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cơ sở của khóa trục (INV-CAT-33): giá trị trục đang được dùng tính cả từ biến thể đã ngừng. */
class VariantUsageTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final AttributeTemplateId COLOR = AttributeTemplateId.of(UUID.randomUUID());
    private static final AttributeOptionId RED = AttributeOptionId.of(UUID.randomUUID());
    private static final AttributeOptionId BLUE = AttributeOptionId.of(UUID.randomUUID());

    private static Variant variant(AttributeOptionId option, boolean active) {
        Variant v = Variant.create(VariantId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), UUID.randomUUID(),
                List.of(new VariantAttributePair(COLOR, option)), 100_000L, null, ProductLimits.defaults(), NOW);
        if (!active) {
            v.deactivate(NOW);
        }
        return v;
    }

    @Test
    @DisplayName("Không có biến thể: không dùng gì")
    void noVariants() {
        VariantUsage usage = VariantUsage.of(List.of());
        assertThat(usage.hasVariants()).isFalse();
        assertThat(usage.hasActiveVariant()).isFalse();
        assertThat(usage.usedOptions()).isEmpty();
    }

    @Test
    @DisplayName("Giá trị trục dùng gồm cả biến thể đã ngừng; có biến thể hoạt động khi ít nhất một cái hoạt động")
    void inactiveVariantsStillUseTheirValues() {
        VariantUsage usage = VariantUsage.of(List.of(variant(RED, false), variant(BLUE, true)));
        assertThat(usage.hasVariants()).isTrue();
        assertThat(usage.hasActiveVariant()).isTrue();
        assertThat(usage.usedOptions().get(COLOR)).isEqualTo(Set.of(RED, BLUE));
    }

    @Test
    @DisplayName("Chỉ có biến thể đã ngừng: có biến thể nhưng không có biến thể hoạt động")
    void onlyInactive() {
        VariantUsage usage = VariantUsage.of(List.of(variant(RED, false)));
        assertThat(usage.hasVariants()).isTrue();
        assertThat(usage.hasActiveVariant()).isFalse();
        assertThat(usage.usedOptions().get(COLOR)).isEqualTo(Set.of(RED));
    }

    @Test
    @DisplayName("Biến thể mặc định (tổ hợp rỗng) có mặt nhưng không dùng giá trị trục nào")
    void defaultVariantUsesNoValues() {
        Variant v = Variant.create(VariantId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), UUID.randomUUID(),
                List.of(), 100_000L, null, ProductLimits.defaults(), NOW);
        VariantUsage usage = VariantUsage.of(List.of(v));
        assertThat(usage.hasVariants()).isTrue();
        assertThat(usage.usedOptions()).isEmpty();
    }
}
