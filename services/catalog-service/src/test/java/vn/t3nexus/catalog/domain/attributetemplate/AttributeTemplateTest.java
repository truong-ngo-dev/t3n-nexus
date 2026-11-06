package vn.t3nexus.catalog.domain.attributetemplate;

import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-01 cho AGG-CAT-02: mẫu thuộc tính và tùy chọn (INV-CAT-10, 11, 12). */
class AttributeTemplateTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-7000-8000-0000000000aa");

    private static ChangeContext ctx() {
        return new ChangeContext(new CatalogActor(ADMIN, "ADMIN"), UUID.randomUUID(), NOW);
    }

    private static AttributeTemplate template(InputType type, String unit) {
        return AttributeTemplate.create(AttributeTemplateId.of(UUID.randomUUID()), "color", "Màu sắc", null, type, unit, ctx());
    }

    private static AttributeTemplate select() {
        AttributeTemplate t = template(InputType.SINGLE_SELECT, null);
        t.clearDomainEvents();
        return t;
    }

    private static AttributeOptionId newId() {
        return AttributeOptionId.of(UUID.randomUUID());
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private static AttributeTemplateChanged lastEvent(AttributeTemplate t) {
        List<DomainEvent> events = List.copyOf(t.getDomainEvents());
        return (AttributeTemplateChanged) events.get(events.size() - 1);
    }

    // ---- tạo ----

    @Test
    void createsActiveTemplateKeepingCodeAndTidyingDisplayName() {
        AttributeTemplate t = AttributeTemplate.create(AttributeTemplateId.of(UUID.randomUUID()),
                "origin_country", " Xuất   xứ ", "  Chọn nước ", InputType.SINGLE_SELECT, null, ctx());

        assertThat(t.getName()).isEqualTo("origin_country");
        assertThat(t.getDisplayName()).isEqualTo("Xuất xứ");
        assertThat(t.getHint()).isEqualTo("Chọn nước");
        assertThat(t.getStatus()).isEqualTo(AttributeTemplateStatus.ACTIVE);
        assertThat(t.version()).isNull();
        assertThat(t.getOptions()).isEmpty();
    }

    @Test
    void creationEmitsEventWithVersionZero() {
        AttributeTemplate t = template(InputType.NUMBER, "GB");

        AttributeTemplateChanged event = lastEvent(t);
        assertThat(event.change()).isEqualTo(AttributeTemplateChanged.Change.CREATED);
        assertThat(event.version()).isZero();
        assertThat(event.actorId()).isEqualTo(ADMIN);
        assertThat(event.inputType()).isEqualTo(InputType.NUMBER);
        assertThat(event.value()).isEqualTo("color");
    }

    @Test
    void unitOnlyAllowedForNumber() {
        assertThatThrownBy(() -> template(InputType.TEXT, "GB"))
                .isInstanceOf(CatalogFieldException.class)
                .extracting(e -> ((CatalogFieldException) e).field()).isEqualTo("unit");
        assertThat(template(InputType.NUMBER, "GB").getUnit()).isEqualTo("GB");
    }

    @Test
    void rejectsBlankOrTooLongFields() {
        AttributeTemplateId id = AttributeTemplateId.of(UUID.randomUUID());
        assertThatThrownBy(() -> AttributeTemplate.create(id, " ", "Màu", null, InputType.TEXT, null, ctx()))
                .isInstanceOf(CatalogFieldException.class);
        assertThatThrownBy(() -> AttributeTemplate.create(id, "color", "", null, InputType.TEXT, null, ctx()))
                .isInstanceOf(CatalogFieldException.class);
        assertThatThrownBy(() -> AttributeTemplate.create(id, "a".repeat(101), "Màu", null, InputType.TEXT, null, ctx()))
                .isInstanceOf(CatalogFieldException.class);
        assertThatThrownBy(() -> AttributeTemplate.create(id, "color", "Màu", "h".repeat(201), InputType.TEXT, null, ctx()))
                .isInstanceOf(CatalogFieldException.class);
        assertThatThrownBy(() -> AttributeTemplate.create(id, "color", "Màu", null, null, null, ctx()))
                .isInstanceOf(CatalogFieldException.class);
    }

    /** INV-CAT-10: tên mẫu bắt buộc chữ thường không dấu, chữ số, gạch dưới; sai thì từ chối chứ không tự sửa. */
    @Test
    void rejectsNameOutsideCodeFormat() {
        for (String bad : List.of("Color", "mau sắc", "màu", "mau sac", "mau-sac", "_color", "color_", "a__b", "color ", " color", "a.b")) {
            assertThatThrownBy(() -> AttributeTemplate.validName(bad)).as(bad)
                    .isInstanceOf(CatalogFieldException.class)
                    .extracting(e -> ((CatalogFieldException) e).field()).isEqualTo("name");
        }
        assertThatThrownBy(() -> AttributeTemplate.validName(null)).isInstanceOf(CatalogFieldException.class);
    }

    @Test
    void acceptsNameInCodeFormat() {
        for (String good : List.of("color", "screen_size", "ram_8gb", "a", "a1_b2_c3", "7")) {
            assertThat(AttributeTemplate.validName(good)).isEqualTo(good);
        }
        assertThat(AttributeTemplate.validName("a".repeat(100))).hasSize(100);
        assertThatThrownBy(() -> AttributeTemplate.validName("a".repeat(101))).isInstanceOf(CatalogFieldException.class);
    }

    // ---- tùy chọn ----

    @Test
    void addsOptionsAtTheEndInOrder() {
        AttributeTemplate t = select();
        t.addOption(newId(), "red", "Đỏ", ctx());
        t.addOption(newId(), "blue", "Xanh", ctx());

        assertThat(t.getOptionsInOrder()).extracting(AttributeOption::getValue).containsExactly("red", "blue");
        assertThat(t.getOptionsInOrder()).extracting(AttributeOption::getSortOrder).containsExactly(0, 1);
    }

    /** INV-CAT-11: giá trị duy nhất trong mẫu (so thẳng), kể cả với tùy chọn đã ngừng. */
    @Test
    void rejectsDuplicateValue() {
        AttributeTemplate t = select();
        t.addOption(newId(), "red_dark", "Đỏ tươi", ctx());

        assertCode(() -> t.addOption(newId(), "red_dark", "Đỏ", ctx()),
                AttributeTemplateErrorCode.ATTRIBUTE_OPTION_VALUE_TAKEN);
    }

    /** Giá trị bắt buộc đúng dạng mã: chữ thường không dấu, chữ số, gạch dưới hoặc gạch ngang. */
    @Test
    void rejectsValueOutsideCodeFormat() {
        AttributeTemplate t = select();
        for (String bad : List.of("Red", "đỏ", "do tuoi", "-red", "red-", "red_", "a--b", "a_-b", "red ", " red", "a.b", "")) {
            assertThatThrownBy(() -> t.addOption(newId(), bad, "Đỏ", ctx())).as(bad)
                    .isInstanceOf(CatalogFieldException.class)
                    .extracting(e -> ((CatalogFieldException) e).field()).isEqualTo("value");
        }
        assertThatThrownBy(() -> t.addOption(newId(), "a".repeat(101), "Đỏ", ctx())).isInstanceOf(CatalogFieldException.class);
        assertThat(t.getOptions()).isEmpty();
    }

    @Test
    void acceptsValueWithUnderscoreAndHyphen() {
        AttributeTemplate t = select();
        for (String good : List.of("red", "usb-c", "x_large", "wi-fi_6", "8gb")) {
            t.addOption(newId(), good, "Nhãn", ctx());
        }
        assertThat(t.getOptions()).extracting(AttributeOption::getValue)
                .containsExactly("red", "usb-c", "x_large", "wi-fi_6", "8gb");
    }

    /** {@code usb-c} và {@code usb_c} là hai giá trị khác nhau; người quản trị chịu trách nhiệm đặt nhất quán. */
    @Test
    void hyphenAndUnderscoreAreDistinctValues() {
        AttributeTemplate t = select();
        t.addOption(newId(), "usb-c", "USB-C", ctx());
        t.addOption(newId(), "usb_c", "USB C", ctx());
        assertThat(t.getOptions()).hasSize(2);
    }

    @Test
    void rejectsDuplicateOfDeactivatedOption() {
        AttributeTemplate t = select();
        AttributeOptionId first = newId();
        t.addOption(first, "red", "Đỏ", ctx());
        t.deactivateOption(first, ctx());

        assertCode(() -> t.addOption(newId(), "red", "Đỏ", ctx()),
                AttributeTemplateErrorCode.ATTRIBUTE_OPTION_VALUE_TAKEN);
    }

    @Test
    void rejectsOptionOnInactiveTemplate() {
        AttributeTemplate t = select();
        t.deactivate(ctx());

        assertCode(() -> t.addOption(newId(), "red", "Đỏ", ctx()),
                AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_INACTIVE);
    }

    @Test
    void rejectsOptionOnNonSelectTemplate() {
        AttributeTemplate t = template(InputType.TEXT, null);

        assertCode(() -> t.addOption(newId(), "red", "Đỏ", ctx()),
                AttributeTemplateErrorCode.ATTRIBUTE_INPUT_TYPE_NOT_SELECT);
    }

    @Test
    void unknownOptionIsNotFound() {
        AttributeTemplate t = select();
        assertCode(() -> t.deactivateOption(newId(), ctx()), AttributeTemplateErrorCode.ATTRIBUTE_OPTION_NOT_FOUND);
        assertCode(() -> t.updateOptionDisplayValue(newId(), "x", ctx()),
                AttributeTemplateErrorCode.ATTRIBUTE_OPTION_NOT_FOUND);
    }

    /** Sửa nhãn, ngừng, bật lại không bị trạng thái của mẫu chi phối. */
    @Test
    void editsAreNotBlockedByTemplateStatus() {
        AttributeTemplate t = select();
        AttributeOptionId red = newId();
        AttributeOptionId blue = newId();
        t.addOption(red, "red", "Đỏ", ctx());
        t.addOption(blue, "blue", "Xanh", ctx());
        t.deactivate(ctx());

        t.updateOptionDisplayValue(red, "Đỏ đậm", ctx());
        t.deactivateOption(blue, ctx());
        t.activateOption(blue, ctx());
        t.activate(ctx());

        assertThat(t.getOptionsInOrder()).extracting(AttributeOption::getDisplayValue).containsExactly("Đỏ đậm", "Xanh");
        assertThat(t.getOptionsInOrder().get(0).getValue()).isEqualTo("red");
    }

    @Test
    void reorderRequiresEveryOptionExactlyOnce() {
        AttributeTemplate t = select();
        AttributeOptionId red = newId();
        AttributeOptionId blue = newId();
        t.addOption(red, "red", "Đỏ", ctx());
        t.addOption(blue, "blue", "Xanh", ctx());

        assertCode(() -> t.reorderOptions(List.of(red), ctx()), AttributeTemplateErrorCode.ATTRIBUTE_INVALID_OPTION_ORDER);
        assertCode(() -> t.reorderOptions(List.of(red, red), ctx()), AttributeTemplateErrorCode.ATTRIBUTE_INVALID_OPTION_ORDER);
        assertCode(() -> t.reorderOptions(List.of(red, newId()), ctx()), AttributeTemplateErrorCode.ATTRIBUTE_INVALID_OPTION_ORDER);

        t.reorderOptions(List.of(blue, red), ctx());
        assertThat(t.getOptionsInOrder()).extracting(AttributeOption::getValue).containsExactly("blue", "red");
    }

    // ---- trạng thái ----

    @Test
    void deactivatingOrActivatingTwiceIsInvalidState() {
        AttributeTemplate t = select();
        assertCode(() -> t.activate(ctx()), CatalogErrorCode.CATALOG_INVALID_STATE);
        t.deactivate(ctx());
        assertCode(() -> t.deactivate(ctx()), CatalogErrorCode.CATALOG_INVALID_STATE);

        AttributeTemplate u = select();
        AttributeOptionId o = newId();
        u.addOption(o, "red", "Đỏ", ctx());
        assertCode(() -> u.activateOption(o, ctx()), CatalogErrorCode.CATALOG_INVALID_STATE);
        u.deactivateOption(o, ctx());
        assertCode(() -> u.deactivateOption(o, ctx()), CatalogErrorCode.CATALOG_INVALID_STATE);
    }

    @Test
    void failedChangeEmitsNoEvent() {
        AttributeTemplate t = select();
        assertCode(() -> t.activate(ctx()), CatalogErrorCode.CATALOG_INVALID_STATE);

        assertThat(t.getDomainEvents()).isEmpty();
    }

    @Test
    void selectWithoutActiveOptionIsNotUsable() {
        AttributeTemplate t = select();
        assertThat(t.isUsable()).isFalse();

        AttributeOptionId o = newId();
        t.addOption(o, "red", "Đỏ", ctx());
        assertThat(t.isUsable()).isTrue();

        // INV-CAT-11: ngừng tùy chọn cuối cùng được phép; mẫu thành không dùng được
        t.deactivateOption(o, ctx());
        assertThat(t.isUsable()).isFalse();
    }

    @Test
    void inactiveTemplateIsNotUsable() {
        AttributeTemplate t = template(InputType.TEXT, null);
        assertThat(t.isUsable()).isTrue();
        t.deactivate(ctx());
        assertThat(t.isUsable()).isFalse();
    }

    // ---- sự kiện ----

    @Test
    void eventVersionIsStoredVersionPlusOne() {
        AttributeTemplate fresh = select();
        AttributeTemplate stored = AttributeTemplate.reconstitute(fresh.getId(), fresh.getName(),
                fresh.getDisplayName(), null, fresh.getInputType(), null, AttributeTemplateStatus.ACTIVE, List.of(), 4L,
                NOW, NOW);

        stored.deactivate(ctx());

        assertThat(lastEvent(stored).version()).isEqualTo(5L);
        assertThat(stored.nextVersion()).isEqualTo(5L);
        assertThat(lastEvent(stored).change()).isEqualTo(AttributeTemplateChanged.Change.DEACTIVATED);
    }

    @Test
    void optionChangesCarryOptionIdAndValue() {
        AttributeTemplate t = select();
        AttributeOptionId o = newId();
        t.addOption(o, "red", "Đỏ", ctx());

        AttributeTemplateChanged added = lastEvent(t);
        assertThat(added.change()).isEqualTo(AttributeTemplateChanged.Change.OPTION_ADDED);
        assertThat(added.optionId()).isEqualTo(o.getValue());
        assertThat(added.value()).isEqualTo("red");
    }

    @Test
    void outboxPayloadHasOnlyIdVersionAndChange() {
        AttributeTemplate t = select();
        t.updateDetails("Màu mới", "gợi ý", ctx());

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = (java.util.Map<String, Object>) lastEvent(t).getPayload();
        assertThat(payload).containsOnlyKeys("templateId", "version", "change", "occurredAt");
        assertThat(payload.get("change")).isEqualTo("DETAILS_UPDATED");
        assertThat(lastEvent(t).getRoutingKey()).isEqualTo("catalog.attribute-template.changed");
    }

    @Test
    void updateDetailsWorksOnInactiveTemplate() {
        AttributeTemplate t = select();
        t.deactivate(ctx());

        t.updateDetails("Màu mới", null, ctx());

        assertThat(t.getDisplayName()).isEqualTo("Màu mới");
        assertThat(t.getHint()).isNull();
    }

    // ---- kiểm định dạng theo kiểu ----

    @Test
    void numberAllowsAtMostFourDecimals() {
        AttributeTemplate t = template(InputType.NUMBER, "kg");
        assertThat(t.isValidRawValue("12.3456")).isTrue();
        assertThat(t.isValidRawValue("12.34567")).isFalse();
        assertThat(t.isValidRawValue("abc")).isFalse();
    }

    @Test
    void dateAcceptsReducedIsoAndRejectsImpossibleDates() {
        AttributeTemplate t = template(InputType.DATE, null);
        assertThat(t.isValidRawValue("2026")).isTrue();
        assertThat(t.isValidRawValue("2026-02")).isTrue();
        assertThat(t.isValidRawValue("2026-02-28")).isTrue();
        assertThat(t.isValidRawValue("2026-02-30")).isFalse();
        assertThat(t.isValidRawValue("2026-13")).isFalse();
        assertThat(t.isValidRawValue("0999")).isFalse();
    }

    @Test
    void booleanAcceptsOnlyTrueFalse() {
        AttributeTemplate t = template(InputType.BOOLEAN, null);
        assertThat(t.isValidRawValue("true")).isTrue();
        assertThat(t.isValidRawValue("FALSE")).isTrue();
        assertThat(t.isValidRawValue("yes")).isFalse();
    }

    @Test
    void textMustBeNonBlankAndWithinLimit() {
        AttributeTemplate t = template(InputType.TEXT, null);
        assertThat(t.isValidRawValue("abc")).isTrue();
        assertThat(t.isValidRawValue("   ")).isFalse();
        assertThat(t.isValidRawValue("a".repeat(256))).isFalse();
    }
}
