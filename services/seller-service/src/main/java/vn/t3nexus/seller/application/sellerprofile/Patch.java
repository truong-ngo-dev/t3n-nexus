package vn.t3nexus.seller.application.sellerprofile;

/**
 * Mô tả một trường của lệnh cập nhật một phần.
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

    public T resolve(T current) {
        return present ? value : current;
    }
}
