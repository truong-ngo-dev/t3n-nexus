package vn.t3nexus.catalog.domain.attributetemplate;

/**
 * Kiểu giá trị của 1 thuộc tính. Bất biến sau khi tạo template. Quy tắc từng kiểu (số giá trị, guard mặc định,
 * khả năng search/filter/sort): {@code docs/service/catalog-service/analysis.md} AGG-CAT-01.
 */
public enum InputType {
    SINGLE_SELECT,
    MULTI_SELECT,
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE;

    /** Kiểu có danh sách option ({@link AttributeOption}) — giá trị lưu là {@code optionId}. */
    public boolean isSelect() {
        return this == SINGLE_SELECT || this == MULTI_SELECT;
    }
}
