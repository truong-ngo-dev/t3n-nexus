package vn.t3nexus.lib.webbff;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Nạp toàn bộ cơ chế BFF của thư viện. Không phải tự cấu hình của Boot: cấu hình ở đây ghi đè bean của WebFlux và Session (vd
 * {@code webSessionIdResolver}), nên phải được xử lý cùng đợt với cấu hình người dùng, trước các tự cấu hình. Dùng qua {@link EnableWebBff}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BffProperties.class)
@ComponentScan("vn.t3nexus.lib.webbff")
class WebBffConfiguration {
}
