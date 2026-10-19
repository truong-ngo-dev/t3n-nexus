package vn.t3nexus.apigateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.apigateway.infrastructure.configuration.GatewayProperties;
import vn.t3nexus.apigateway.infrastructure.ratelimit.PathRateLimitRule;
import vn.t3nexus.apigateway.infrastructure.ratelimit.RateLimitPolicy;
import vn.t3nexus.apigateway.infrastructure.ratelimit.RateLimitProperties;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Luật giới hạn tần suất theo đường dẫn (feature 06, nhóm A, `tech.md` T1): khớp theo (host, phương thức, đường dẫn) và kiểm hợp lệ
 * lúc nạp cấu hình. KT-06 (cấu hình sai không cho khởi động, luật tắt không áp) và KT-07 (cách khớp).
 */
class RateLimitPolicyTest {

    private static final GatewayProperties GATEWAY = new GatewayProperties(
            new GatewayProperties.Hosts("auth.localhost", "shop.localhost", "ops.localhost"),
            new GatewayProperties.Frontends("http://localhost:4200"));

    private static RateLimitProperties.Rule rule(String name, String host, String method, String path, int limit, long windowSeconds) {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setName(name);
        rule.setHost(host);
        rule.setMethod(method);
        rule.setPath(path);
        rule.setLimit(limit);
        rule.setWindowSeconds(windowSeconds);
        return rule;
    }

    private static RateLimitPolicy policy(RateLimitProperties.Rule... rules) {
        RateLimitProperties props = new RateLimitProperties();
        props.getRules().addAll(List.of(rules));
        return new RateLimitPolicy(props, GATEWAY);
    }

    // ───────────── KT-07: cách khớp ─────────────

    @Test
    void a_rule_matches_host_method_and_path_ignoring_port_host_case_method_case_and_a_trailing_slash() {
        RateLimitPolicy policy = policy(rule("register", "auth", "POST", "/register", 10, 3600));

        for (String path : List.of("/register", "/register/")) {
            Optional<PathRateLimitRule> match = policy.ruleFor("auth.localhost:8000", "POST", path);
            assertThat(match).as(path).isPresent();
            assertThat(match.get().name()).isEqualTo("register");
            assertThat(match.get().limit()).isEqualTo(10);
            assertThat(match.get().window()).isEqualTo(Duration.ofHours(1));
        }
        assertThat(policy.ruleFor("AUTH.Localhost", "post", "/register")).isPresent();
    }

    @Test
    void a_rule_does_not_match_another_method_a_lookalike_path_or_another_host() {
        RateLimitPolicy policy = policy(rule("register", "auth", "POST", "/register", 10, 3600));

        assertThat(policy.ruleFor("auth.localhost:8000", "GET", "/register")).isEmpty();
        assertThat(policy.ruleFor("auth.localhost:8000", "PUT", "/register")).isEmpty();
        assertThat(policy.ruleFor("auth.localhost:8000", "POST", "/register-x")).isEmpty();
        assertThat(policy.ruleFor("auth.localhost:8000", "POST", "/register/x")).isEmpty();
        assertThat(policy.ruleFor("auth.localhost:8000", "POST", "/login")).isEmpty();
        assertThat(policy.ruleFor("shop.localhost:8000", "POST", "/register")).isEmpty();
        assertThat(policy.ruleFor("auth.localhost.evil.example", "POST", "/register")).isEmpty();
        assertThat(policy.ruleFor(null, "POST", "/register")).isEmpty();
    }

    // ───────────── KT-06: cấu hình ─────────────

    @Test
    void a_disabled_rule_is_not_applied() {
        RateLimitProperties.Rule disabled = rule("register", "auth", "POST", "/register", 10, 3600);
        disabled.setEnabled(false);

        assertThat(policy(disabled).ruleFor("auth.localhost", "POST", "/register")).isEmpty();
    }

    @Test
    void with_no_rules_only_the_general_limit_remains_with_the_documented_default() {
        RateLimitPolicy policy = policy();

        assertThat(policy.ruleFor("auth.localhost", "POST", "/register")).isEmpty();
        assertThat(policy.defaultLimit()).isEqualTo(300);
        assertThat(policy.defaultWindow()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void an_invalid_configuration_is_refused() {
        assertThatThrownBy(() -> policy(rule("r", "auth", "POST", "/register", 0, 60))).isInstanceOf(IllegalStateException.class).hasMessageContaining("r");
        assertThatThrownBy(() -> policy(rule("r", "auth", "POST", "/register", -1, 60))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(rule("r", "auth", "POST", "/register", 5, 0))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(rule("", "auth", "POST", "/register", 5, 60))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(rule("r", "auth", "", "/register", 5, 60))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(rule("r", "auth", "POST", "", 5, 60))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(rule("r", "khong-co", "POST", "/register", 5, 60))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("khong-co");
        assertThatThrownBy(() -> policy(rule("trung", "auth", "POST", "/a", 5, 60), rule("trung", "auth", "POST", "/b", 5, 60)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("trung");
    }

    @Test
    void an_invalid_general_limit_is_refused() {
        RateLimitProperties props = new RateLimitProperties();
        props.getDefault().setLimit(0);

        assertThatThrownBy(() -> new RateLimitPolicy(props, GATEWAY)).isInstanceOf(IllegalStateException.class);
    }

    @Configuration
    @EnableConfigurationProperties({RateLimitProperties.class, GatewayProperties.class})
    static class PolicyConfiguration {
        @Bean
        RateLimitPolicy rateLimitPolicy(RateLimitProperties properties, GatewayProperties gateway) {
            return new RateLimitPolicy(properties, gateway);
        }
    }

    @Test
    void the_application_context_does_not_start_with_a_bad_rule_and_starts_with_a_good_one() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PolicyConfiguration.class)
                .withPropertyValues("app.gateway.hosts.auth=auth.localhost", "app.gateway.hosts.shop=shop.localhost",
                        "app.gateway.hosts.ops=ops.localhost", "app.gateway.frontends.shop=http://localhost:4200");

        runner.withPropertyValues("app.ratelimit.rules[0].name=register", "app.ratelimit.rules[0].host=auth",
                        "app.ratelimit.rules[0].method=POST", "app.ratelimit.rules[0].path=/register",
                        "app.ratelimit.rules[0].limit=10", "app.ratelimit.rules[0].window-seconds=3600")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RateLimitPolicy.class).ruleFor("auth.localhost", "POST", "/register")).isPresent();
                });

        runner.withPropertyValues("app.ratelimit.rules[0].name=register", "app.ratelimit.rules[0].host=auth",
                        "app.ratelimit.rules[0].method=POST", "app.ratelimit.rules[0].path=/register",
                        "app.ratelimit.rules[0].limit=0", "app.ratelimit.rules[0].window-seconds=3600")
                .run(context -> assertThat(context).hasFailed());
    }
}
