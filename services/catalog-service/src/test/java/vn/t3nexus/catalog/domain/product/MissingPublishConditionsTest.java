package vn.t3nexus.catalog.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.product.MissingPublishCondition.Code;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RM-CAT-04 phần của feature 07: điều kiện còn thiếu để đăng — thuộc tính bắt buộc và biến thể hoạt động (INV-CAT-91). */
class MissingPublishConditionsTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final ChangeContext CTX =
            new ChangeContext(new CatalogActor(UUID.randomUUID(), "ADMIN"), UUID.randomUUID(), NOW);

    private static AttributeTemplate template(String name, InputType type) {
        return AttributeTemplate.create(AttributeTemplateId.of(UUID.randomUUID()), name, "Hiển thị " + name, null, type,
                null, CTX);
    }

    private static CategoryAttributeAssignment required(AttributeTemplate t, int order) {
        return CategoryAttributeAssignment.create(t.getId(), t.getInputType(), true, order, AttributeConstraints.NONE);
    }

    private static CategoryAttributeAssignment optional(AttributeTemplate t, int order) {
        return CategoryAttributeAssignment.create(t.getId(), t.getInputType(), false, order, AttributeConstraints.NONE);
    }

    private static Product product(List<ProductAttribute> attributes) {
        Product p = Product.create(ProductId.of(UUID.randomUUID()), UUID.randomUUID(), CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, attributes, ProductLimits.defaults(), NOW);
        p.addImage(ProductImage.of(ProductImageId.of(UUID.randomUUID()), "key", "image/png", 1024L, 0), 10, NOW);
        return p;
    }

    private static Product productWithoutImage(List<ProductAttribute> attributes) {
        return Product.create(ProductId.of(UUID.randomUUID()), UUID.randomUUID(), CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, attributes, ProductLimits.defaults(), NOW);
    }

    private static Map<AttributeTemplateId, AttributeTemplate> byId(AttributeTemplate... templates) {
        Map<AttributeTemplateId, AttributeTemplate> map = new HashMap<>();
        for (AttributeTemplate t : templates) map.put(t.getId(), t);
        return map;
    }

    @Test
    @DisplayName("Thiếu thuộc tính bắt buộc đang dùng được: báo từng thuộc tính theo thứ tự hiển thị")
    void requiredMissingInDisplayOrder() {
        AttributeTemplate model = template("model", InputType.TEXT);
        AttributeTemplate brandName = template("origin", InputType.TEXT);
        List<MissingPublishCondition> missing = MissingPublishConditions.of(product(List.of()),
                List.of(required(model, 2), required(brandName, 1)), byId(model, brandName), true);
        assertThat(missing).containsExactly(
                new MissingPublishCondition(Code.REQUIRED_ATTRIBUTE_MISSING, brandName.getId()),
                new MissingPublishCondition(Code.REQUIRED_ATTRIBUTE_MISSING, model.getId()));
    }

    @Test
    @DisplayName("Đã điền thì không báo; thuộc tính tùy chọn không bao giờ báo")
    void filledOrOptionalNotReported() {
        AttributeTemplate model = template("model", InputType.TEXT);
        AttributeTemplate origin = template("origin", InputType.TEXT);
        Product p = product(List.of(ProductAttribute.ofValue(model.getId(), "abc")));
        assertThat(MissingPublishConditions.of(p, List.of(required(model, 1), optional(origin, 2)),
                byId(model, origin), true)).isEmpty();
    }

    @Test
    @DisplayName("Thuộc tính bắt buộc đã làm trục tính là đã điền")
    void axisCountsAsFilled() {
        AttributeTemplate color = template("color", InputType.SINGLE_SELECT);
        AttributeOptionId red = AttributeOptionId.of(UUID.randomUUID());
        color.addOption(red, "red", "Đỏ", CTX);
        Product p = product(List.of(ProductAttribute.ofOptions(color.getId(), true, List.of(red))));
        assertThat(MissingPublishConditions.of(p, List.of(required(color, 1)), byId(color), true)).isEmpty();
    }

    @Test
    @DisplayName("Mẫu bắt buộc không còn dùng được thì không ép điền (người bán không bị kẹt)")
    void unusableRequiredNotEnforced() {
        AttributeTemplate model = template("model", InputType.TEXT);
        model.deactivate(CTX);
        AttributeTemplate empty = template("storage", InputType.SINGLE_SELECT);
        assertThat(MissingPublishConditions.of(product(List.of()),
                List.of(required(model, 1), required(empty, 2)), byId(model, empty), true)).isEmpty();
    }

    @Test
    @DisplayName("Chưa có biến thể hoạt động: báo NO_ACTIVE_VARIANT sau các thuộc tính thiếu")
    void noActiveVariant() {
        AttributeTemplate model = template("model", InputType.TEXT);
        List<MissingPublishCondition> missing = MissingPublishConditions.of(product(List.of()),
                List.of(required(model, 1)), byId(model), false);
        assertThat(missing).containsExactly(
                new MissingPublishCondition(Code.REQUIRED_ATTRIBUTE_MISSING, model.getId()),
                new MissingPublishCondition(Code.NO_ACTIVE_VARIANT, null));
        assertThat(MissingPublishConditions.of(product(List.of()), List.of(), byId(), true)).isEmpty();
    }

    @Test
    @DisplayName("Sản phẩm không có ảnh: báo NO_IMAGE sau NO_ACTIVE_VARIANT")
    void noImageCondition() {
        AttributeTemplate model = template("model", InputType.TEXT);
        List<MissingPublishCondition> missing = MissingPublishConditions.of(productWithoutImage(List.of()),
                List.of(required(model, 1)), byId(model), false);
        assertThat(missing).containsExactly(
                new MissingPublishCondition(Code.REQUIRED_ATTRIBUTE_MISSING, model.getId()),
                new MissingPublishCondition(Code.NO_ACTIVE_VARIANT, null),
                new MissingPublishCondition(Code.NO_IMAGE, null));
    }

    @Test
    @DisplayName("Có ảnh thì không có NO_IMAGE")
    void hasImageNoImageCondition() {
        Product p = product(List.of());
        List<MissingPublishCondition> missing = MissingPublishConditions.of(p, List.of(), byId(), false);
        assertThat(missing).containsExactly(
                new MissingPublishCondition(Code.NO_ACTIVE_VARIANT, null));
    }

    @Test
    @DisplayName("Đủ điều kiện: có ảnh, có biến thể hoạt động, không thiếu thuộc tính thì danh sách rỗng")
    void allConditionsMet() {
        Product p = product(List.of());
        assertThat(MissingPublishConditions.of(p, List.of(), byId(), true)).isEmpty();
    }

    @Test
    @DisplayName("Người bán chưa duyệt có SELLER_NOT_APPROVED")
    void sellerNotApprovedCondition() {
        Product p = product(List.of());
        List<MissingPublishCondition> missing = MissingPublishConditions.of(p, List.of(), byId(), true, false, true);
        assertThat(missing).containsExactly(
                new MissingPublishCondition(Code.SELLER_NOT_APPROVED, null)
        );
    }

    @Test
    @DisplayName("Danh mục không dùng được có CATEGORY_NOT_USABLE")
    void categoryNotUsableCondition() {
        Product p = product(List.of());
        List<MissingPublishCondition> missing = MissingPublishConditions.of(p, List.of(), byId(), true, true, false);
        assertThat(missing).containsExactly(
                new MissingPublishCondition(Code.CATEGORY_NOT_USABLE, null)
        );
    }

    @Test
    @DisplayName("Thiếu CẢ năm điều kiện cùng lúc ra đủ năm theo đúng thứ tự trên")
    void allFiveConditionsMissingInOrder() {
        AttributeTemplate model = template("model", InputType.TEXT);
        Product p = productWithoutImage(List.of()); // thiếu image

        List<MissingPublishCondition> missing = MissingPublishConditions.of(
                p,
                List.of(required(model, 1)), // thiếu attribute
                byId(model),
                false,                       // thiếu active variant
                false,                       // người bán chưa duyệt
                false                        // danh mục không dùng được
        );

        assertThat(missing).containsExactly(
                new MissingPublishCondition(Code.REQUIRED_ATTRIBUTE_MISSING, model.getId()),
                new MissingPublishCondition(Code.NO_ACTIVE_VARIANT, null),
                new MissingPublishCondition(Code.NO_IMAGE, null),
                new MissingPublishCondition(Code.SELLER_NOT_APPROVED, null),
                new MissingPublishCondition(Code.CATEGORY_NOT_USABLE, null)
        );
    }

    @Test
    @DisplayName("Đủ điều kiện (sellerApproved và categoryUsable true) không thêm gì")
    void allConditionsMet6Params() {
        Product p = product(List.of());
        List<MissingPublishCondition> missing = MissingPublishConditions.of(p, List.of(), byId(), true, true, true);
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("Hàm 4 tham số vẫn như cũ (không có hai mã mới)")
    void fourParamsMethodPreservesOldBehavior() {
        AttributeTemplate model = template("model", InputType.TEXT);
        Product p = productWithoutImage(List.of());
        List<MissingPublishCondition> missing = MissingPublishConditions.of(
                p,
                List.of(required(model, 1)),
                byId(model),
                false
        );
        assertThat(missing).containsExactly(
                new MissingPublishCondition(Code.REQUIRED_ATTRIBUTE_MISSING, model.getId()),
                new MissingPublishCondition(Code.NO_ACTIVE_VARIANT, null),
                new MissingPublishCondition(Code.NO_IMAGE, null)
        );
    }
}
