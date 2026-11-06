package vn.t3nexus.lib.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import vn.t3nexus.lib.events.EventEnvelopeFactory;
import vn.t3nexus.lib.events.SensitiveFieldException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chính sách kiểm tên trường nhạy cảm của cổng ghi chọn bằng cấu hình; mặc định là nghiêm. */
class OutboxEnvelopeConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withUserConfiguration(OutboxEnvelopeConfiguration.class);

    private static void create(EventEnvelopeFactory f, Map<String, Object> payload) {
        f.create("e", "A", "id", "T", Instant.parse("2026-10-24T00:00:00Z"), null, null, 1, payload);
    }

    @Test
    void defaultIsStrict() {
        runner.run(ctx -> {
            EventEnvelopeFactory f = ctx.getBean(EventEnvelopeFactory.class);

            assertThatThrownBy(() -> create(f, Map.of("email", "a@b.c"))).isInstanceOf(SensitiveFieldException.class);
            assertThatThrownBy(() -> create(f, Map.of("password", "x"))).isInstanceOf(SensitiveFieldException.class);
        });
    }

    @Test
    void secretsOnlyAllowsPersonalIdentifiersButNotSecrets() {
        runner.withPropertyValues("app.outbox.sensitive-field-policy=secrets-only").run(ctx -> {
            EventEnvelopeFactory f = ctx.getBean(EventEnvelopeFactory.class);

            create(f, Map.of("email", "a@b.c"));
            assertThatThrownBy(() -> create(f, Map.of("password", "x"))).isInstanceOf(SensitiveFieldException.class);
        });
    }

    @Test
    void offAllowsEverything() {
        runner.withPropertyValues("app.outbox.sensitive-field-policy=off").run(ctx -> {
            EventEnvelopeFactory f = ctx.getBean(EventEnvelopeFactory.class);

            create(f, Map.of("email", "a@b.c", "token", "t"));
            assertThat(ctx).hasSingleBean(EventEnvelopeFactory.class);
        });
    }

    @Test
    void anUnknownPolicyValueFailsStartup() {
        runner.withPropertyValues("app.outbox.sensitive-field-policy=lenient")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
