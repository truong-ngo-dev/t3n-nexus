package vn.t3nexus.identity;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import vn.t3nexus.identity.infrastructure.cross_cutting.config.WebConfig;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard: {@link GlobalExceptionHandler} (common-web) nằm ở package
 * {@code vn.t3nexus.lib.web.commons.exception} — NGOÀI base package
 * {@code @SpringBootApplication} ({@link IdentityServiceApplication}, {@code vn.t3nexus.identity})
 * quét mặc định. Không {@code @Import} tường minh thì mọi {@code DomainException} rơi vào default
 * error handling của Spring Boot (500) thay vì status đúng như thiết kế (404/409/400...) —
 * đã verify thực nghiệm trước khi fix (component-scan thật sự không tìm thấy bean này).
 *
 * <p>Test đơn giản hoá về mức reflection thuần (không boot Spring context — service này chưa có
 * hạ tầng test slice riêng) — chỉ cần xác nhận {@link WebConfig} còn giữ đúng {@code @Import}, đủ
 * để bắt regression nếu ai đó vô tình xoá.</p>
 */
class GlobalExceptionHandlerWiringTest {

    @Test
    void webConfig_importsGlobalExceptionHandler() {
        Import importAnnotation = WebConfig.class.getAnnotation(Import.class);

        assertThat(importAnnotation)
                .as("WebConfig phải có @Import — thiếu thì mọi DomainException rơi về 500 generic "
                        + "thay vì status đã document trong design.md từng feature")
                .isNotNull();
        assertThat(importAnnotation.value()).contains(GlobalExceptionHandler.class);
    }
}
