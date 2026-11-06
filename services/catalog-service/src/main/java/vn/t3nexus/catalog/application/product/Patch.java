package vn.t3nexus.catalog.application.product;

/**
 * Một trường của lệnh sửa một phần (PATCH, ADR-0011 mục 4): vắng thì giữ nguyên, gửi giá trị thì ghi đè, gửi {@code null}
 * thì xóa. Cục bộ của Danh mục; cùng ý với kiểu của Người bán (chờ quyết định nâng lên thư viện dùng chung).
 *
 * @param <T> kiểu dữ liệu của trường
 */
public record Patch<T>(boolean present, T value) {

    public static <T> Patch<T> absent() {
        return new Patch<>(false, null);
    }

    public static <T> Patch<T> of(T value) {
        return new Patch<>(true, value);
    }

    public boolean isAbsent() {
        return !present;
    }
}
