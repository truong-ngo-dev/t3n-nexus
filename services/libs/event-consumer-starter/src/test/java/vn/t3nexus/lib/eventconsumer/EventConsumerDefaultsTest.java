package vn.t3nexus.lib.eventconsumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Mặc định của bộ nhận: nhóm theo tên dịch vụ, đọc từ đầu cho nhóm mới, không tự commit offset; giá trị người dùng đặt được giữ. */
class EventConsumerDefaultsTest {

    private final EventConsumerAutoConfiguration config = new EventConsumerAutoConfiguration();

    private Map<String, Object> apply(Map<String, Object> initial, String appName) {
        MockEnvironment env = new MockEnvironment();
        if (appName != null) {
            env.setProperty("spring.application.name", appName);
        }
        DefaultKafkaConsumerFactory<Object, Object> factory = new DefaultKafkaConsumerFactory<>(new HashMap<>(initial));
        config.eventConsumerFactoryCustomizer(env).customize(factory);
        return factory.getConfigurationProperties();
    }

    @Test
    void appliesDefaultsWhenNothingIsConfigured() {
        Map<String, Object> props = apply(Map.of(), "catalog-service");

        assertThat(props).containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "catalog-service")
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    }

    @Test
    void keepsValuesTheServiceSetExplicitly() {
        Map<String, Object> props = apply(Map.of(
                ConsumerConfig.GROUP_ID_CONFIG, "custom-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"), "catalog-service");

        assertThat(props).containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "custom-group")
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    }

    @Test
    void neverAutoCommitsEvenIfConfiguredOtherwise() {
        Map<String, Object> props = apply(Map.of(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true), "catalog-service");

        assertThat(props).containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    }

    @Test
    void leavesGroupIdAloneWhenThereIsNoApplicationName() {
        Map<String, Object> props = apply(Map.of(), null);

        assertThat(props).doesNotContainKey(ConsumerConfig.GROUP_ID_CONFIG);
    }

    // ---- Hàng chờ lỗi (ADR-0010) ----

    private static String dlq(String configured, String appName) {
        EventConsumerProperties properties = new EventConsumerProperties();
        properties.setDlqTopic(configured);
        MockEnvironment env = new MockEnvironment();
        if (appName != null) {
            env.setProperty("spring.application.name", appName);
        }
        return EventConsumerAutoConfiguration.resolveDlqTopic(properties, env);
    }

    @Test
    void deadLetterTopicDefaultsToTheServiceName() {
        assertThat(dlq(null, "catalog-service")).isEqualTo("catalog-service.dlq");
    }

    @Test
    void explicitDeadLetterTopicOverridesTheDefault() {
        assertThat(dlq("email-worker-t1.dlq", "email-worker")).isEqualTo("email-worker-t1.dlq");
    }

    @Test
    void blankDeadLetterTopicFallsBackToTheServiceName() {
        assertThat(dlq("  ", "catalog-service")).isEqualTo("catalog-service.dlq");
    }

    @Test
    void deadLetterTopicCannotBeResolvedWithoutNameOrOverride() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dlq(null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
