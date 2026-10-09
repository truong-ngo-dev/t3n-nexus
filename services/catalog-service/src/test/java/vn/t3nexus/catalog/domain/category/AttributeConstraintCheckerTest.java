package vn.t3nexus.catalog.domain.category;

import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeConstraintCheckerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    private static AttributeConstraints text(int maxLength) {
        return new AttributeConstraints(maxLength, null, null, null, null, null, null, null);
    }

    private static AttributeConstraints date(DatePrecision precision, String min, String max) {
        return new AttributeConstraints(null, null, null, null, precision, min, max, null);
    }

    @Test
    void textMaxLength() {
        assertThat(AttributeConstraintChecker.satisfies(text(5), InputType.TEXT, " abcde ", TODAY)).isTrue();
        assertThat(AttributeConstraintChecker.satisfies(text(5), InputType.TEXT, "abcdef", TODAY)).isFalse();
    }

    @Test
    void datePrecisionMustMatch() {
        AttributeConstraints c = date(DatePrecision.MONTH, null, null);
        assertThat(AttributeConstraintChecker.satisfies(c, InputType.DATE, "2026-09", TODAY)).isTrue();
        assertThat(AttributeConstraintChecker.satisfies(c, InputType.DATE, "2026-09-01", TODAY)).isFalse();
    }

    @Test
    void absoluteAndRelativeDateBounds() {
        AttributeConstraints c = date(null, "2020-01-01", "now");
        assertThat(AttributeConstraintChecker.satisfies(c, InputType.DATE, "2019", TODAY)).isFalse();
        assertThat(AttributeConstraintChecker.satisfies(c, InputType.DATE, "2026-09-29", TODAY)).isTrue();
        assertThat(AttributeConstraintChecker.satisfies(c, InputType.DATE, "2026-09-30", TODAY)).isFalse();

        AttributeConstraints recent = date(null, "now-30d", null);
        assertThat(AttributeConstraintChecker.satisfies(recent, InputType.DATE, "2026-08-30", TODAY)).isTrue();
        assertThat(AttributeConstraintChecker.satisfies(recent, InputType.DATE, "2026-08-29", TODAY)).isFalse();
    }

    @Test
    void noConstraintsAlwaysSatisfied() {
        assertThat(AttributeConstraintChecker.satisfies(AttributeConstraints.NONE, InputType.NUMBER, "-5", TODAY)).isTrue();
        assertThat(AttributeConstraintChecker.satisfiesSelectionCount(AttributeConstraints.NONE, 99)).isTrue();
    }
}
