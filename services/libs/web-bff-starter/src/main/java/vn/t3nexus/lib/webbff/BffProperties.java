package vn.t3nexus.lib.webbff;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cấu hình riêng của từng BFF (feature 10, {@code tech.md} Q3, Q6). Mọi thứ phụ thuộc tên ứng dụng suy ra từ {@link #appName}:
 * <ul>
 *   <li>mã đăng ký OAuth2: {@code <appName>-web} (vd {@code seller-web});</li>
 *   <li>tiền tố khóa Redis: {@code <appName>:} (vd {@code seller:sid:<sid>});</li>
 *   <li>phạm vi của token dịch vụ gọi {@code /bff/internal/**}: {@code <appName>.internal}.</li>
 * </ul>
 * {@link #routes}: khóa là tên dịch vụ (vd {@code catalog-service}); đường dẫn {@code /api/<khóa bỏ hậu tố -service>/**} được viết lại
 * thành {@code /api/**} và chuyển tới địa chỉ khai báo.
 */
@ConfigurationProperties("bff")
public class BffProperties {

    /** Tên ứng dụng: {@code storefront}, {@code seller}, {@code ops}. */
    private String appName;

    /** Tên dịch vụ → địa chỉ. */
    private Map<String, Route> routes = new LinkedHashMap<>();

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public Map<String, Route> getRoutes() { return routes; }
    public void setRoutes(Map<String, Route> routes) { this.routes = routes; }

    public String registrationId() { return appName + "-web"; }
    public String keyPrefix() { return appName + ":"; }
    public String internalScope() { return appName + ".internal"; }
    /** Tên cookie phiên của BFF: {@code STOREFRONT_SESSION}, {@code SELLER_SESSION}, {@code OPS_SESSION}; khác nhau giữa các ứng dụng (ADR-0006). */
    public String sessionCookieName() { return appName.toUpperCase(java.util.Locale.ROOT) + "_SESSION"; }

    public static class Route {
        private String uri;
        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
    }
}
