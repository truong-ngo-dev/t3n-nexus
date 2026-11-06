package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import vn.t3nexus.catalog.application.product.ImageLimits;
import vn.t3nexus.catalog.domain.product.ProductLimits;

/** {@code catalog.limits.*}: ghi đè giới hạn sản phẩm, biến thể. Giá trị không khai lấy mặc định của {@link ProductLimits}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "catalog.limits")
public class CatalogLimitsProperties {

    private int titleMin = ProductLimits.defaults().titleMin();
    private int titleMax = ProductLimits.defaults().titleMax();
    private int descriptionMax = ProductLimits.defaults().descriptionMax();
    private int maxAxes = ProductLimits.defaults().maxAxes();
    private int maxVariants = ProductLimits.defaults().maxVariants();
    private long priceMax = ProductLimits.defaults().priceMax();
    private int skuMax = ProductLimits.defaults().skuMax();
    private int maxImages = 9;

    public ImageLimits toImageLimits() {
        return new ImageLimits(maxImages);
    }

    public ProductLimits toLimits() {
        return new ProductLimits(titleMin, titleMax, descriptionMax, maxAxes, maxVariants, priceMax, skuMax);
    }
}
