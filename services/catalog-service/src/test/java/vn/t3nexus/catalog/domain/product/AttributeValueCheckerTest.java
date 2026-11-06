package vn.t3nexus.catalog.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.DatePrecision;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KT-01 cho giá trị thuộc tính (INV-CAT-32, 33): kiểm theo kiểu nhập, tùy chọn, ràng buộc riêng của danh mục, trục;
 * giá trị đã lưu và không đổi được miễn kiểm (grandfathering). Hàm thuần: dữ liệu đưa vào, không gọi kho.
 */
class AttributeValueCheckerTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final ChangeContext CTX =
            new ChangeContext(new CatalogActor(UUID.randomUUID(), "ADMIN"), UUID.randomUUID(), NOW);
    private static final AttributeConstraints NONE = AttributeConstraints.NONE;

    // ---- dựng dữ liệu ----

    private static AttributeTemplate template(String name, InputType type) {
        return AttributeTemplate.create(AttributeTemplateId.of(UUID.randomUUID()), name, "Hiển thị " + name, null, type,
                null, CTX);
    }

    private static AttributeOptionId option(AttributeTemplate t, String value) {
        AttributeOptionId id = AttributeOptionId.of(UUID.randomUUID());
        t.addOption(id, value, "Tùy chọn " + value, CTX);
        return id;
    }

    private static CategoryAttributeAssignment assign(AttributeTemplate t, AttributeConstraints c) {
        return CategoryAttributeAssignment.create(t.getId(), t.getInputType(), false, 0, c);
    }

    private static AttributeInput input(AttributeTemplate t, boolean axis, String... values) {
        return new AttributeInput(t.getId(), List.of(values), axis);
    }

    private static String id(AttributeOptionId option) {
        return option.getValue().toString();
    }

    private static List<ProductAttribute> check(List<AttributeInput> submitted, List<ProductAttribute> previous,
                                                List<CategoryAttributeAssignment> assignments,
                                                AttributeTemplate... templates) {
        return check(ProductLimits.defaults(), submitted, previous, assignments, templates);
    }

    private static List<ProductAttribute> check(ProductLimits limits, List<AttributeInput> submitted,
                                                List<ProductAttribute> previous,
                                                List<CategoryAttributeAssignment> assignments,
                                                AttributeTemplate... templates) {
        Map<AttributeTemplateId, AttributeTemplate> byId = new HashMap<>();
        for (AttributeTemplate t : templates) byId.put(t.getId(), t);
        return AttributeValueChecker.check(submitted, previous, assignments, byId, limits, TODAY);
    }

    private static List<ProductAttribute> checkNew(AttributeInput in, CategoryAttributeAssignment a,
                                                   AttributeTemplate t) {
        return check(List.of(in), List.of(), List.of(a), t);
    }

    private static void assertInvalid(ErrorCode code, String attribute, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ProductAttributeException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(code);
            assertThat(e.attribute()).isEqualTo(attribute);
        });
    }

    // ---- kiểu chữ, số, đúng/sai, ngày: giá trị chuẩn hóa ----

    @Test
    @DisplayName("Chữ: cắt khoảng trắng đầu cuối, lưu nguyên ký tự đặc biệt")
    void textIsTrimmedAndKeptAsText() {
        AttributeTemplate t = template("note", InputType.TEXT);
        List<ProductAttribute> result = checkNew(input(t, false, "  <b>Vải dày & mềm</b> "), assign(t, NONE), t);
        assertThat(result).containsExactly(ProductAttribute.ofValue(t.getId(), "<b>Vải dày & mềm</b>"));
    }

    @Test
    @DisplayName("Chữ rỗng hoặc quá 255 ký tự bị từ chối")
    void textBounds() {
        AttributeTemplate t = template("note", InputType.TEXT);
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "note", () -> checkNew(input(t, false, "   "), assign(t, NONE), t));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "note",
                () -> checkNew(input(t, false, "x".repeat(256)), assign(t, NONE), t));
    }

    @Test
    @DisplayName("Số: lưu dạng chuẩn (bỏ số 0 thừa); tối đa 4 chữ số thập phân; chữ không phải số bị từ chối")
    void numberCanonical() {
        AttributeTemplate t = template("screen_size", InputType.NUMBER);
        assertThat(checkNew(input(t, false, "6.50"), assign(t, NONE), t))
                .containsExactly(ProductAttribute.ofValue(t.getId(), "6.5"));
        assertThat(checkNew(input(t, false, "100"), assign(t, NONE), t))
                .containsExactly(ProductAttribute.ofValue(t.getId(), "100"));
        assertThat(checkNew(input(t, false, "0.0"), assign(t, NONE), t))
                .containsExactly(ProductAttribute.ofValue(t.getId(), "0"));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "screen_size",
                () -> checkNew(input(t, false, "6.00001"), assign(t, NONE), t));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "screen_size",
                () -> checkNew(input(t, false, "abc"), assign(t, NONE), t));
    }

    @Test
    @DisplayName("Đúng/sai: true hoặc false không phân biệt hoa thường, lưu chữ thường; giá trị khác bị từ chối")
    void booleanCanonical() {
        AttributeTemplate t = template("waterproof", InputType.BOOLEAN);
        assertThat(checkNew(input(t, false, "TRUE"), assign(t, NONE), t))
                .containsExactly(ProductAttribute.ofValue(t.getId(), "true"));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "waterproof",
                () -> checkNew(input(t, false, "yes"), assign(t, NONE), t));
    }

    @Test
    @DisplayName("Ngày: yyyy, yyyy-MM, yyyy-MM-dd giữ nguyên độ chính xác; ngày không có thật bị từ chối")
    void dateFormats() {
        AttributeTemplate t = template("release_date", InputType.DATE);
        for (String value : new String[]{"2025", "2025-09", "2025-09-30"}) {
            assertThat(checkNew(input(t, false, value), assign(t, NONE), t))
                    .containsExactly(ProductAttribute.ofValue(t.getId(), value));
        }
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "release_date",
                () -> checkNew(input(t, false, "2025-13"), assign(t, NONE), t));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "release_date",
                () -> checkNew(input(t, false, "2025-02-30"), assign(t, NONE), t));
    }

    @Test
    @DisplayName("Kiểu vô hướng phải có đúng một giá trị")
    void scalarNeedsExactlyOneValue() {
        AttributeTemplate t = template("note", InputType.TEXT);
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "note",
                () -> checkNew(new AttributeInput(t.getId(), List.of(), false), assign(t, NONE), t));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "note",
                () -> checkNew(input(t, false, "a", "b"), assign(t, NONE), t));
    }

    // ---- ràng buộc riêng của danh mục ----

    @Test
    @DisplayName("Chữ: maxLength; số: min, max, integerOnly; ngày: inputPrecision, minDate, maxDate")
    void categoryConstraints() {
        AttributeTemplate text = template("model", InputType.TEXT);
        CategoryAttributeAssignment maxLen = assign(text, new AttributeConstraints(5, null, null, null, null, null, null, null));
        assertThat(checkNew(input(text, false, "abcde"), maxLen, text)).hasSize(1);
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "model", () -> checkNew(input(text, false, "abcdef"), maxLen, text));

        AttributeTemplate number = template("size", InputType.NUMBER);
        CategoryAttributeAssignment range = assign(number,
                new AttributeConstraints(null, new BigDecimal("3"), new BigDecimal("8"), true, null, null, null, null));
        assertThat(checkNew(input(number, false, "5"), range, number)).hasSize(1);
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "size", () -> checkNew(input(number, false, "2"), range, number));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "size", () -> checkNew(input(number, false, "9"), range, number));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "size", () -> checkNew(input(number, false, "5.5"), range, number));

        AttributeTemplate date = template("made_at", InputType.DATE);
        CategoryAttributeAssignment dates = assign(date,
                new AttributeConstraints(null, null, null, null, DatePrecision.MONTH, "2020-01-01", "now", null));
        assertThat(checkNew(input(date, false, "2025-09"), dates, date)).hasSize(1);
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "made_at", () -> checkNew(input(date, false, "2025"), dates, date));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "made_at", () -> checkNew(input(date, false, "2019-12"), dates, date));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "made_at", () -> checkNew(input(date, false, "2026-12"), dates, date));
        assertThat(checkNew(input(date, false, "2026-11"), dates, date)).hasSize(1);
    }

    // ---- kiểu chọn ----

    @Test
    @DisplayName("Chọn một: đúng một tùy chọn đang dùng của mẫu")
    void singleSelect() {
        AttributeTemplate t = template("storage", InputType.SINGLE_SELECT);
        AttributeOptionId o128 = option(t, "gb128");
        AttributeOptionId o256 = option(t, "gb256");
        assertThat(checkNew(input(t, false, id(o128)), assign(t, NONE), t))
                .containsExactly(ProductAttribute.ofOptions(t.getId(), false, List.of(o128)));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "storage",
                () -> checkNew(input(t, false, id(o128), id(o256)), assign(t, NONE), t));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "storage",
                () -> checkNew(input(t, false, UUID.randomUUID().toString()), assign(t, NONE), t));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "storage",
                () -> checkNew(input(t, false, "khong-phai-uuid"), assign(t, NONE), t));
    }

    @Test
    @DisplayName("Tùy chọn đã ngừng: giá trị mới bị từ chối")
    void inactiveOptionRejectedWhenNew() {
        AttributeTemplate t = template("storage", InputType.SINGLE_SELECT);
        AttributeOptionId o128 = option(t, "gb128");
        option(t, "gb256");
        t.deactivateOption(o128, CTX);
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "storage",
                () -> checkNew(input(t, false, id(o128)), assign(t, NONE), t));
    }

    @Test
    @DisplayName("Chọn nhiều: không trùng, không rỗng, không vượt maxSelections")
    void multiSelect() {
        AttributeTemplate t = template("features", InputType.MULTI_SELECT);
        AttributeOptionId a = option(t, "nfc");
        AttributeOptionId b = option(t, "wifi");
        AttributeOptionId c = option(t, "gps");
        CategoryAttributeAssignment max2 = assign(t, new AttributeConstraints(null, null, null, null, null, null, null, 2));
        assertThat(checkNew(input(t, false, id(a), id(b)), max2, t))
                .containsExactly(ProductAttribute.ofOptions(t.getId(), false, List.of(a, b)));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "features", () -> checkNew(input(t, false, id(a), id(b), id(c)), max2, t));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "features", () -> checkNew(input(t, false, id(a), id(a)), max2, t));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "features",
                () -> checkNew(new AttributeInput(t.getId(), List.of(), false), max2, t));
    }

    // ---- miễn kiểm giá trị đã lưu (grandfathering) ----

    @Test
    @DisplayName("Giá trị không đổi được miễn kiểm dù tùy chọn đã ngừng, mẫu đã ngừng, ràng buộc bị siết hay bị bỏ gán")
    void unchangedValuesAreExempt() {
        AttributeTemplate color = template("color", InputType.SINGLE_SELECT);
        AttributeOptionId red = option(color, "red");
        option(color, "blue");
        AttributeTemplate model = template("model", InputType.TEXT);
        AttributeTemplate size = template("size", InputType.NUMBER);
        AttributeTemplate dropped = template("dropped", InputType.TEXT);

        List<ProductAttribute> previous = List.of(
                ProductAttribute.ofOptions(color.getId(), false, List.of(red)),
                ProductAttribute.ofValue(model.getId(), "abcdefgh"),
                ProductAttribute.ofValue(size.getId(), "20"),
                ProductAttribute.ofValue(dropped.getId(), "cũ"));

        // sau đó: tùy chọn đỏ ngừng, mẫu model ngừng, ràng buộc size siết còn 3-8, mẫu dropped bị bỏ gán
        color.deactivateOption(red, CTX);
        model.deactivate(CTX);
        List<CategoryAttributeAssignment> assignments = List.of(
                assign(color, NONE),
                assign(model, new AttributeConstraints(5, null, null, null, null, null, null, null)),
                assign(size, new AttributeConstraints(null, new BigDecimal("3"), new BigDecimal("8"), null, null, null, null, null)));

        List<AttributeInput> submitted = List.of(
                input(color, false, id(red)), input(model, false, "abcdefgh"), input(size, false, "20.0"),
                input(dropped, false, "cũ"));

        assertThat(check(submitted, previous, assignments, color, model, size, dropped)).isEqualTo(previous);
    }

    @Test
    @DisplayName("Giá trị đổi bị kiểm theo trạng thái hiện tại: mẫu đã ngừng, tùy chọn đã ngừng, ràng buộc đã siết")
    void changedValuesAreChecked() {
        AttributeTemplate model = template("model", InputType.TEXT);
        model.deactivate(CTX);
        List<ProductAttribute> previous = List.of(ProductAttribute.ofValue(model.getId(), "abc"));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "model",
                () -> check(List.of(input(model, false, "xyz")), previous, List.of(assign(model, NONE)), model));

        AttributeTemplate size = template("size", InputType.NUMBER);
        CategoryAttributeAssignment tight = assign(size,
                new AttributeConstraints(null, new BigDecimal("3"), new BigDecimal("8"), null, null, null, null, null));
        List<ProductAttribute> old = List.of(ProductAttribute.ofValue(size.getId(), "20"));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "size",
                () -> check(List.of(input(size, false, "21")), old, List.of(tight), size));
    }

    @Test
    @DisplayName("Chọn nhiều: tùy chọn cũ đã ngừng được giữ, thêm tùy chọn đã ngừng khác bị từ chối")
    void multiSelectPerValueExemption() {
        AttributeTemplate t = template("features", InputType.MULTI_SELECT);
        AttributeOptionId a = option(t, "nfc");
        AttributeOptionId b = option(t, "wifi");
        AttributeOptionId c = option(t, "gps");
        List<ProductAttribute> previous = List.of(ProductAttribute.ofOptions(t.getId(), false, List.of(a)));
        t.deactivateOption(a, CTX);
        t.deactivateOption(c, CTX);

        assertThat(check(List.of(input(t, false, id(a), id(b))), previous, List.of(assign(t, NONE)), t))
                .containsExactly(ProductAttribute.ofOptions(t.getId(), false, List.of(a, b)));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "features",
                () -> check(List.of(input(t, false, id(a), id(c))), previous, List.of(assign(t, NONE)), t));
    }

    @Test
    @DisplayName("maxSelections đã siết: tập không đổi được miễn, tập đổi bị kiểm")
    void maxSelectionsOnlyWhenSetChanges() {
        AttributeTemplate t = template("features", InputType.MULTI_SELECT);
        AttributeOptionId a = option(t, "nfc");
        AttributeOptionId b = option(t, "wifi");
        AttributeOptionId c = option(t, "gps");
        List<ProductAttribute> previous = List.of(ProductAttribute.ofOptions(t.getId(), false, List.of(a, b, c)));
        CategoryAttributeAssignment max2 = assign(t, new AttributeConstraints(null, null, null, null, null, null, null, 2));

        assertThat(check(List.of(input(t, false, id(c), id(b), id(a))), previous, List.of(max2), t)).isEqualTo(previous);
        AttributeOptionId d = option(t, "bt");
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "features",
                () -> check(List.of(input(t, false, id(a), id(b), id(c), id(d))), previous, List.of(max2), t));
    }

    @Test
    @DisplayName("Thuộc tính không thuộc danh mục: thêm hoặc đổi bị từ chối, giữ nguyên hoặc bỏ hẳn thì được")
    void notAssignedToCategory() {
        AttributeTemplate t = template("note", InputType.TEXT);
        AttributeTemplate other = template("other", InputType.TEXT);
        List<CategoryAttributeAssignment> assignments = List.of(assign(other, NONE));

        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "note",
                () -> check(List.of(input(t, false, "x")), List.of(), assignments, t, other));

        List<ProductAttribute> previous = List.of(ProductAttribute.ofValue(t.getId(), "x"));
        assertThat(check(List.of(input(t, false, "x")), previous, assignments, t, other)).isEqualTo(previous);
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "note",
                () -> check(List.of(input(t, false, "y")), previous, assignments, t, other));
        assertThat(check(List.of(), previous, assignments, t, other)).isEmpty();
    }

    @Test
    @DisplayName("Mẫu đã ngừng: giá trị mới bị từ chối; mẫu chọn hết tùy chọn đang dùng cũng không dùng được")
    void unusableTemplateRejectedWhenNew() {
        AttributeTemplate inactive = template("model", InputType.TEXT);
        inactive.deactivate(CTX);
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "model",
                () -> checkNew(input(inactive, false, "abc"), assign(inactive, NONE), inactive));

        AttributeTemplate empty = template("storage", InputType.SINGLE_SELECT);
        AttributeOptionId only = option(empty, "gb128");
        empty.deactivateOption(only, CTX);
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "storage",
                () -> checkNew(input(empty, false, id(only)), assign(empty, NONE), empty));
    }

    // ---- cấu trúc yêu cầu ----

    @Test
    @DisplayName("Một mẫu hai lần trong yêu cầu bị từ chối; mẫu không tồn tại là ATTRIBUTE_TEMPLATE_NOT_FOUND")
    void duplicatesAndUnknownTemplates() {
        AttributeTemplate t = template("note", InputType.TEXT);
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "note",
                () -> check(List.of(input(t, false, "a"), input(t, false, "b")), List.of(), List.of(assign(t, NONE)), t));

        AttributeInput unknown = new AttributeInput(AttributeTemplateId.of(UUID.randomUUID()), List.of("a"), false);
        assertThatThrownBy(() -> check(List.of(unknown), List.of(), List.of(), t))
                .isInstanceOfSatisfying(DomainException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_NOT_FOUND));
    }

    @Test
    @DisplayName("Danh sách rỗng hợp lệ (bỏ hết thuộc tính)")
    void emptyListIsValid() {
        assertThat(check(List.of(), List.of(), List.of())).isEmpty();
    }

    // ---- trục ----

    @Test
    @DisplayName("Trục: thuộc tính chọn một với một tập tùy chọn")
    void axisIsSingleSelectWithASet() {
        AttributeTemplate color = template("color", InputType.SINGLE_SELECT);
        AttributeOptionId red = option(color, "red");
        AttributeOptionId blue = option(color, "blue");
        assertThat(checkNew(input(color, true, id(red), id(blue)), assign(color, NONE), color))
                .containsExactly(ProductAttribute.ofOptions(color.getId(), true, List.of(red, blue)));
    }

    @Test
    @DisplayName("Trục sai: không phải chọn một, không có giá trị, giá trị trùng, tùy chọn ngoài mẫu")
    void invalidAxes() {
        AttributeTemplate multi = template("features", InputType.MULTI_SELECT);
        AttributeOptionId f = option(multi, "nfc");
        assertInvalid(ProductErrorCode.PRODUCT_AXIS_INVALID, "features",
                () -> checkNew(input(multi, true, id(f)), assign(multi, NONE), multi));

        AttributeTemplate text = template("note", InputType.TEXT);
        assertInvalid(ProductErrorCode.PRODUCT_AXIS_INVALID, "note",
                () -> checkNew(input(text, true, "x"), assign(text, NONE), text));

        AttributeTemplate color = template("color", InputType.SINGLE_SELECT);
        AttributeOptionId red = option(color, "red");
        assertInvalid(ProductErrorCode.PRODUCT_AXIS_INVALID, "color",
                () -> checkNew(new AttributeInput(color.getId(), List.of(), true), assign(color, NONE), color));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "color",
                () -> checkNew(input(color, true, id(red), id(red)), assign(color, NONE), color));
        assertInvalid(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, "color",
                () -> checkNew(input(color, true, id(red), UUID.randomUUID().toString()), assign(color, NONE), color));
    }

    @Test
    @DisplayName("Tối đa 2 trục theo giới hạn cấu hình; vượt thì PRODUCT_AXIS_INVALID")
    void axisCountLimit() {
        AttributeTemplate color = template("color", InputType.SINGLE_SELECT);
        AttributeTemplate size = template("size_label", InputType.SINGLE_SELECT);
        AttributeTemplate fit = template("fit", InputType.SINGLE_SELECT);
        List<AttributeInput> three = new ArrayList<>();
        List<CategoryAttributeAssignment> assignments = new ArrayList<>();
        for (AttributeTemplate t : List.of(color, size, fit)) {
            three.add(input(t, true, id(option(t, "v1"))));
            assignments.add(assign(t, NONE));
        }
        assertInvalid(ProductErrorCode.PRODUCT_AXIS_INVALID, "fit",
                () -> check(three, List.of(), assignments, color, size, fit));
        assertThat(check(three.subList(0, 2), List.of(), assignments, color, size, fit)).hasSize(2);

        ProductLimits oneAxis = new ProductLimits(5, 200, 5_000, 1, 50, 1_000_000_000L, 50);
        assertInvalid(ProductErrorCode.PRODUCT_AXIS_INVALID, "size_label",
                () -> check(oneAxis, three.subList(0, 2), List.of(), assignments, color, size, fit));
    }

    @Test
    @DisplayName("Thứ tự kết quả giữ theo yêu cầu; cờ trục giữ nguyên trong kết quả")
    void orderAndAxisFlagKept() {
        AttributeTemplate note = template("note", InputType.TEXT);
        AttributeTemplate color = template("color", InputType.SINGLE_SELECT);
        AttributeOptionId red = option(color, "red");
        List<ProductAttribute> result = check(
                List.of(input(note, false, "a"), input(color, true, id(red))), List.of(),
                List.of(assign(note, NONE), assign(color, NONE)), note, color);
        assertThat(result).extracting(ProductAttribute::templateId).containsExactly(note.getId(), color.getId());
        assertThat(result).extracting(ProductAttribute::axis).containsExactly(false, true);
    }
}
