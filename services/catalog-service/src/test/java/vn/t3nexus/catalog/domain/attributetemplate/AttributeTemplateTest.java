package vn.t3nexus.catalog.domain.attributetemplate;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AGG-CAT-01 — analysis.md §6.1. */
class AttributeTemplateTest {

    private static final AttributeTemplateId ID = AttributeTemplateId.of("01TEMPLATE00000000000000000");

    private static AttributeTemplate select() {
        return AttributeTemplate.create(ID, "color", "Màu sắc", null, InputType.SINGLE_SELECT, null);
    }

    private static AttributeOptionId option(String id) {
        return AttributeOptionId.of(id);
    }


    @Test
    void unitOnlyAllowedForNumber() {
        assertThatThrownBy(() -> AttributeTemplate.create(ID, "os", "Hệ điều hành", null, InputType.TEXT, "GB"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(AttributeTemplateErrorCode.UNIT_ONLY_FOR_NUMBER);
    }

    @Test
    void newTemplateIsActive() {
        assertThat(select().getStatus()).isEqualTo(AttributeTemplateStatus.ACTIVE);
    }

    /** INV-CAT-012 — mã giá trị chuẩn duy nhất trong 1 thuộc tính, không phân biệt hoa/thường, kể cả giá trị đã tắt. */

    @Test
    void rejectsDuplicateIgnoringCase() {
        AttributeTemplate template = select();
        template.addOption(option("o1"), "red", "Đỏ");

        assertThatThrownBy(() -> template.addOption(option("o2"), "RED", "Đỏ đậm"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(AttributeTemplateErrorCode.OPTION_VALUE_EXISTS);
    }

    @Test
    void rejectsDuplicateOfDeactivatedOption() {
        AttributeTemplate template = select();
        template.addOption(option("o1"), "red", "Đỏ");
        template.deactivateOption(option("o1"));

        assertThatThrownBy(() -> template.addOption(option("o2"), "red", "Đỏ"))
                .isInstanceOf(DomainException.class);
    }

    /** INV-CAT-013 — chỉ thêm giá trị chuẩn mới khi thuộc tính đang dùng. */

    @Test
    void rejectsWhenTemplateInactive() {
        AttributeTemplate template = select();
        template.deactivate();

        assertThatThrownBy(() -> template.addOption(option("o1"), "red", "Đỏ"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(AttributeTemplateErrorCode.TEMPLATE_INACTIVE);
    }

    /** Không kiểm "còn ≥1 giá trị đang mở" — nếu kiểm, thuộc tính hết giá trị sẽ không cứu được. */
    @Test
    void allowedWhenActiveButAllOptionsDeactivated() {
        AttributeTemplate template = select();
        template.addOption(option("o1"), "red", "Đỏ");
        template.deactivateOption(option("o1"));

        template.addOption(option("o2"), "blue", "Xanh");

        assertThat(template.isUsable()).isTrue();
    }

    @Test
    void rejectsForNonSelect() {
        AttributeTemplate template = AttributeTemplate.create(ID, "os", "Hệ điều hành", null, InputType.TEXT, null);

        assertThatThrownBy(() -> template.addOption(option("o1"), "android", "Android"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(AttributeTemplateErrorCode.INPUT_TYPE_NOT_SELECT);
    }

    /** Ma trận hành động × trạng thái (Trục A): sửa nhãn, sắp thứ tự, bật/tắt giá trị đều được khi đã ngừng. */

    @Test
    void editsAreNotBlockedByStatus() {
        AttributeTemplate template = select();
        template.addOption(option("o1"), "red", "Đỏ");
        template.addOption(option("o2"), "blue", "Xanh");
        template.deactivate();

        template.updateDetails("Màu", "Chọn màu");
        template.updateOptionDisplayValue(option("o1"), "Đỏ tươi");
        template.reorderOptions(List.of(option("o2"), option("o1")));
        template.deactivateOption(option("o2"));
        template.activateOption(option("o2"));
        template.activate();

        assertThat(template.getOptionsInOrder()).extracting(AttributeOption::getValue)
                .containsExactly("blue", "red");
        assertThat(template.getStatus()).isEqualTo(AttributeTemplateStatus.ACTIVE);
    }


    @Test
    void selectWithoutActiveOptionIsNotUsable() {
        AttributeTemplate template = select();
        template.addOption(option("o1"), "red", "Đỏ");
        template.deactivateOption(option("o1"));

        assertThat(template.isUsable()).isFalse();
    }

    @Test
    void inactiveTemplateIsNotUsable() {
        AttributeTemplate template = AttributeTemplate.create(ID, "os", "Hệ điều hành", null, InputType.TEXT, null);
        template.deactivate();

        assertThat(template.isUsable()).isFalse();
    }


    @Test
    void requiresEveryOptionExactlyOnce() {
        AttributeTemplate template = select();
        template.addOption(option("o1"), "red", "Đỏ");
        template.addOption(option("o2"), "blue", "Xanh");

        assertThatThrownBy(() -> template.reorderOptions(List.of(option("o1"))))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> template.reorderOptions(List.of(option("o1"), option("o1"))))
                .isInstanceOf(DomainException.class);
    }


    @Test
    void numberAllowsAtMostFourDecimals() {
        AttributeTemplate number = AttributeTemplate.create(ID, "ram", "RAM", null, InputType.NUMBER, "GB");

        assertThat(number.isValidRawValue("8")).isTrue();
        assertThat(number.isValidRawValue("8.1234")).isTrue();
        assertThat(number.isValidRawValue("8.12345")).isFalse();
        assertThat(number.isValidRawValue("large")).isFalse();
    }

    @Test
    void dateAcceptsReducedIsoAndRejectsImpossibleDates() {
        AttributeTemplate date = AttributeTemplate.create(ID, "release", "Ngày ra mắt", null, InputType.DATE, null);

        assertThat(date.isValidRawValue("2024")).isTrue();
        assertThat(date.isValidRawValue("2024-02")).isTrue();
        assertThat(date.isValidRawValue("2024-02-29")).isTrue();
        assertThat(date.isValidRawValue("2023-02-29")).isFalse();
        assertThat(date.isValidRawValue("0999")).isFalse();
    }

    @Test
    void booleanAcceptsOnlyTrueFalse() {
        AttributeTemplate bool = AttributeTemplate.create(ID, "waterproof", "Chống nước", null, InputType.BOOLEAN, null);

        assertThat(bool.isValidRawValue("TRUE")).isTrue();
        assertThat(bool.isValidRawValue("maybe")).isFalse();
    }
}
