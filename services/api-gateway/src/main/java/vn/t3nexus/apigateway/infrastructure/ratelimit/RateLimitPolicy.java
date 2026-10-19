package vn.t3nexus.apigateway.infrastructure.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import vn.t3nexus.apigateway.infrastructure.configuration.GatewayProperties;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chính sách giới hạn tần suất đã kiểm hợp lệ (`tech.md` T1): nạp một lần lúc khởi động từ {@link RateLimitProperties}, bất biến.
 * Cấu hình sai (mức hoặc cửa sổ không dương, thiếu tên, trùng tên, thiếu phương thức hay đường dẫn, khóa host lạ) thì <b>không cho khởi
 * động</b> thay vì chạy với luật hỏng. Luật tắt ({@code enabled=false}) không có trong chính sách. Khớp luật: host (không phân biệt chữ
 * hoa, bỏ cổng), phương thức (không phân biệt chữ hoa) và đường dẫn chính xác sau khi bỏ dấu {@code /} cuối; đường dẫn có tiền tố giống
 * ({@code /register-x}) không khớp.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitPolicy {

    private final int defaultLimit;
    private final Duration defaultWindow;
    private final List<PathRateLimitRule> rules;

    public RateLimitPolicy(RateLimitProperties properties, GatewayProperties gateway) {
        RateLimitProperties.Default general = properties.getDefault();
        if (general.getLimit() <= 0 || general.getWindowSeconds() <= 0) {
            throw new IllegalStateException("app.ratelimit.default: limit và window-seconds phải lớn hơn 0");
        }
        this.defaultLimit = general.getLimit();
        this.defaultWindow = Duration.ofSeconds(general.getWindowSeconds());

        Map<String, String> hostsByKey = Map.of("auth", String.valueOf(gateway.hosts().auth()), "shop", String.valueOf(gateway.hosts().shop()),
                "ops", String.valueOf(gateway.hosts().ops()));
        Set<String> names = new HashSet<>();
        this.rules = properties.getRules().stream().map(rule -> validated(rule, hostsByKey, names)).filter(java.util.Objects::nonNull).toList();
    }

    private static PathRateLimitRule validated(RateLimitProperties.Rule rule, Map<String, String> hostsByKey, Set<String> names) {
        String name = rule.getName() == null ? "" : rule.getName().strip();
        if (name.isEmpty()) throw new IllegalStateException("app.ratelimit.rules: luật thiếu tên");
        if (!names.add(name)) throw new IllegalStateException("app.ratelimit.rules: trùng tên luật '" + name + "'");
        if (rule.getLimit() <= 0) throw new IllegalStateException("Luật '" + name + "': limit phải lớn hơn 0");
        if (rule.getWindowSeconds() <= 0) throw new IllegalStateException("Luật '" + name + "': window-seconds phải lớn hơn 0");
        if (rule.getMethod() == null || rule.getMethod().isBlank()) throw new IllegalStateException("Luật '" + name + "': thiếu method");
        if (rule.getPath() == null || rule.getPath().isBlank()) throw new IllegalStateException("Luật '" + name + "': thiếu path");
        String hostKey = rule.getHost() == null ? "" : rule.getHost().strip().toLowerCase(Locale.ROOT);
        String host = hostsByKey.get(hostKey);
        if (host == null) {
            throw new IllegalStateException("Luật '" + name + "': host '" + rule.getHost() + "' không có trong danh sách (auth, shop, ops)");
        }
        if (!rule.isEnabled()) return null;
        return new PathRateLimitRule(name, hostName(host), rule.getMethod().strip().toUpperCase(Locale.ROOT), normalize(rule.getPath().strip()),
                rule.getLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
    }

    /** Luật khớp với yêu cầu (luật đầu tiên khớp), rỗng nếu không có. */
    public Optional<PathRateLimitRule> ruleFor(String hostHeader, String method, String path) {
        if (hostHeader == null || method == null || path == null) return Optional.empty();
        String host = hostName(hostHeader);
        String normalizedPath = normalize(path);
        String upperMethod = method.toUpperCase(Locale.ROOT);
        return rules.stream()
                .filter(r -> r.hostName().equals(host) && r.method().equals(upperMethod) && r.path().equals(normalizedPath))
                .findFirst();
    }

    public int defaultLimit() {
        return defaultLimit;
    }

    public Duration defaultWindow() {
        return defaultWindow;
    }

    private static String normalize(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /** Tên máy chữ thường, không cổng (cùng quy tắc với định tuyến theo host). */
    private static String hostName(String hostHeader) {
        String host = hostHeader.strip().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end < 0 ? host : host.substring(0, end + 1);
        }
        int colon = host.indexOf(':');
        return colon < 0 ? host : host.substring(0, colon);
    }
}
