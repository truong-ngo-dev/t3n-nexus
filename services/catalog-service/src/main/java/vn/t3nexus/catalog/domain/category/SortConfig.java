package vn.t3nexus.catalog.domain.category;

/** Sắp xếp theo thuộc tính — chỉ NUMBER/DATE (AGG-CAT-01). Sản phẩm thiếu giá trị xếp cuối (áp dụng ở search). */
public record SortConfig(String label, Direction direction) {

    public enum Direction { ASC, DESC }
}
