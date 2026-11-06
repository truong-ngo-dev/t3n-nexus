package vn.t3nexus.lib.eventconsumer;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.config.ContainerCustomizer;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.events.EventEnvelopeReader;
import vn.t3nexus.lib.events.InvalidEventEnvelopeException;

import java.util.Map;

/**
 * Cấu hình phía nhận event (ADR-0008, feature 01 C1a): nhóm tiêu thụ theo tên dịch vụ, đọc từ đầu cho nhóm mới, không tự
 * commit offset, commit sau mỗi bản ghi; thử lại giãn cách cho lỗi tạm; hàng chờ lỗi; log {@code EVENT_DEAD_LETTERED}
 * và bộ đếm {@code events.dead_lettered}.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(EventConsumerProperties.class)
public class EventConsumerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EventConsumerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public EventEnvelopeReader eventEnvelopeReader(ObjectMapper objectMapper) {
        return new EventEnvelopeReader(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventEnvelopeConsumer eventEnvelopeConsumer(EventEnvelopeReader reader) {
        return new EventEnvelopeConsumer(reader);
    }

    /** Mặc định cho consumer; giá trị dịch vụ đã đặt rõ (nhóm, vị trí bắt đầu) được giữ, chỉ tự commit luôn bị tắt. */
    @Bean
    public DefaultKafkaConsumerFactoryCustomizer eventConsumerFactoryCustomizer(Environment environment) {
        return factory -> {
            Map<String, Object> current = factory.getConfigurationProperties();
            Map<String, Object> defaults = new java.util.HashMap<>();
            String application = environment.getProperty("spring.application.name");
            if (!current.containsKey(ConsumerConfig.GROUP_ID_CONFIG) && application != null) {
                defaults.put(ConsumerConfig.GROUP_ID_CONFIG, application);
            }
            if (!current.containsKey(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)) {
                defaults.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            }
            defaults.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            factory.updateConfigs(defaults);
        };
    }

    /** Commit offset sau mỗi bản ghi xử lý xong (sau khi giao dịch của handler đã commit). */
    @Bean
    public ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>> eventConsumerContainerCustomizer() {
        return container -> container.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    }

    @Bean
    @ConditionalOnMissingBean(DefaultErrorHandler.class)
    public DefaultErrorHandler eventConsumerErrorHandler(KafkaTemplate<Object, Object> template,
                                                         EventConsumerProperties properties,
                                                         Environment environment,
                                                         ObjectProvider<MeterRegistry> meters) {
        EventConsumerProperties.Retry retry = properties.getRetry();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.getMaxRetries());
        backOff.setInitialInterval(retry.getInitialInterval().toMillis());
        backOff.setMultiplier(retry.getMultiplier());
        backOff.setMaxInterval(retry.getMaxInterval().toMillis());

        DeadLetteringRecoverer recoverer = new DeadLetteringRecoverer(template, resolveDlqTopic(properties, environment), meters.getIfAvailable());
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventEnvelopeException.class, NonRetryableEventException.class);
        handler.setCommitRecovered(true);
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                log.warn("[EventConsumer] EVENT_DELIVERY_FAILED attempt={} topic={} partition={} offset={} eventId={} error={}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset(),
                        DeadLetteringRecoverer.eventId(record), DeadLetteringRecoverer.errorClass(ex));
            }
        });
        return handler;
    }

    /** Một hàng chờ lỗi mỗi dịch vụ nhận (ADR-0010): {@code app.event-consumer.dlq-topic}, mặc định {@code <tên dịch vụ>.dlq}. */
    static String resolveDlqTopic(EventConsumerProperties properties, Environment environment) {
        String configured = properties.getDlqTopic();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String application = environment.getProperty("spring.application.name");
        if (application == null || application.isBlank()) {
            throw new IllegalStateException("Thiếu spring.application.name hoặc app.event-consumer.dlq-topic: không xác định được hàng chờ lỗi");
        }
        return application.trim() + ".dlq";
    }
}
