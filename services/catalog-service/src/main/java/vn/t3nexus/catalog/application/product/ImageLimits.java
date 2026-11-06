package vn.t3nexus.catalog.application.product;

/** Số ảnh tối đa của một sản phẩm (INV-CAT-34, Q-CAT-01); cấu hình {@code catalog.limits.max-images}. Đếm ở ứng dụng dưới khóa dòng sản phẩm (ADR-0012). */
public record ImageLimits(int max) {

    public ImageLimits {
        if (max < 1) {
            throw new IllegalArgumentException("Giới hạn ảnh không hợp lệ: " + max);
        }
    }
}
