package vn.t3nexus.catalog.domain.product;

/**
 * Giới hạn của sản phẩm và biến thể (Q-CAT-01). Mặc định theo phân tích; cấu hình {@code catalog.limits.*} chỉ được
 * <b>siết</b> các giới hạn có ràng buộc ở cơ sở dữ liệu ({@code V2}: độ dài tiêu đề, mô tả, SKU, trần giá, số trục), không
 * được nới quá chúng, vì khi đó dữ liệu hợp lệ với ứng dụng sẽ bị cơ sở dữ liệu từ chối. Số biến thể tối đa không có ràng
 * buộc ở cơ sở dữ liệu nên nới được; giữ nguyên tắc đếm dưới khóa dòng sản phẩm (ADR-0012).
 *
 * @param maxAxes số trục tối đa; không quá 2 vì khóa tổ hợp và cách đặt trục
 */
public record ProductLimits(int titleMin, int titleMax, int descriptionMax, int maxAxes, int maxVariants,
                            long priceMax, int skuMax) {

    /** Giá niêm yết nhỏ nhất (đồng). */
    public static final long PRICE_MIN = 1L;
    /** SKU ngắn nhất (ký tự), khi có SKU. */
    public static final int SKU_MIN = 1;

    private static final int TITLE_MIN_FLOOR = 5;
    private static final int TITLE_MAX_CEILING = 200;
    private static final int DESCRIPTION_MAX_CEILING = 5_000;
    private static final int MAX_AXES_CEILING = 2;
    private static final long PRICE_MAX_CEILING = 1_000_000_000L;
    private static final int SKU_MAX_CEILING = 50;

    public ProductLimits {
        require(titleMin >= TITLE_MIN_FLOOR, "titleMin", titleMin);
        require(titleMax >= titleMin && titleMax <= TITLE_MAX_CEILING, "titleMax", titleMax);
        require(descriptionMax >= 1 && descriptionMax <= DESCRIPTION_MAX_CEILING, "descriptionMax", descriptionMax);
        require(maxAxes >= 0 && maxAxes <= MAX_AXES_CEILING, "maxAxes", maxAxes);
        require(maxVariants >= 1, "maxVariants", maxVariants);
        require(priceMax >= PRICE_MIN && priceMax <= PRICE_MAX_CEILING, "priceMax", priceMax);
        require(skuMax >= SKU_MIN && skuMax <= SKU_MAX_CEILING, "skuMax", skuMax);
    }

    public static ProductLimits defaults() {
        return new ProductLimits(TITLE_MIN_FLOOR, TITLE_MAX_CEILING, DESCRIPTION_MAX_CEILING, MAX_AXES_CEILING, 50,
                PRICE_MAX_CEILING, SKU_MAX_CEILING);
    }

    private static void require(boolean ok, String name, Object value) {
        if (!ok) {
            throw new IllegalArgumentException("Giới hạn " + name + " không hợp lệ: " + value);
        }
    }
}
