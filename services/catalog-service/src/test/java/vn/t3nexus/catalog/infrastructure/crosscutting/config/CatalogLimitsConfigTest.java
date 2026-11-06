package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.t3nexus.catalog.domain.product.ProductLimits;

import static org.assertj.core.api.Assertions.assertThat;

/** Cấu hình {@code catalog.limits.*}: mặc định khi không khai, ghi đè được, khai sai thì dịch vụ không khởi động. */
class CatalogLimitsConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CatalogLimitsConfig.class);

    @Test
    @DisplayName("Không khai gì: mặc định")
    void defaultsWhenAbsent() {
        runner.run(ctx -> assertThat(ctx.getBean(ProductLimits.class)).isEqualTo(ProductLimits.defaults()));
    }

    @Test
    @DisplayName("Khai catalog.limits.*: ghi đè từng giá trị, giá trị còn lại giữ mặc định")
    void overrides() {
        runner.withPropertyValues("catalog.limits.max-variants=10", "catalog.limits.max-axes=1", "catalog.limits.sku-max=20")
                .run(ctx -> {
                    ProductLimits l = ctx.getBean(ProductLimits.class);
                    assertThat(l.maxVariants()).isEqualTo(10);
                    assertThat(l.maxAxes()).isEqualTo(1);
                    assertThat(l.skuMax()).isEqualTo(20);
                    assertThat(l.titleMax()).isEqualTo(200);
                });
    }

    @Test
    @DisplayName("Khai quá ràng buộc V2 (3 trục): ngữ cảnh không dựng được, lỗi nêu tên giới hạn")
    void invalidFailsStartup() {
        runner.withPropertyValues("catalog.limits.max-axes=3").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .rootCause().hasMessageContaining("maxAxes");
        });
    }
}
