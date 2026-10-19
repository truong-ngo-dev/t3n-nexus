package vn.t3nexus.apigateway.infrastructure.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình giới hạn tần suất của cổng (nhóm 1, bị động ở biên; `rate-limiting-layers.md`): mức chung theo địa chỉ và danh sách luật theo
 * (host, phương thức, đường dẫn). Chỉ là dữ liệu thô; {@link RateLimitPolicy} kiểm hợp lệ lúc khởi động rồi dựng đối tượng bất biến.
 *
 * <pre>
 * app.ratelimit.default.limit=300
 * app.ratelimit.default.window-seconds=60
 * app.ratelimit.rules[0].name=register
 * app.ratelimit.rules[0].host=auth            (khóa host: auth, shop, ops)
 * app.ratelimit.rules[0].method=POST
 * app.ratelimit.rules[0].path=/register
 * app.ratelimit.rules[0].limit=10
 * app.ratelimit.rules[0].window-seconds=3600
 * app.ratelimit.rules[0].enabled=true
 * </pre>
 */
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    private Default defaultLimit = new Default();
    private List<Rule> rules = new ArrayList<>();

    /** Thuộc tính {@code app.ratelimit.default.*}. */
    public Default getDefault() {
        return defaultLimit;
    }

    public void setDefault(Default defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    /** Mức chung theo địa chỉ mạng; mặc định 300 yêu cầu mỗi 60 giây. */
    @Data
    public static class Default {
        private int limit = 300;
        private long windowSeconds = 60;
    }

    /** Một luật theo đường dẫn. */
    @Data
    public static class Rule {
        private String name;
        private String host;
        private String method;
        private String path;
        private int limit;
        private long windowSeconds;
        private boolean enabled = true;
    }
}
