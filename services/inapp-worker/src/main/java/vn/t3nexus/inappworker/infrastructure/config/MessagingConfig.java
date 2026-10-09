package vn.t3nexus.inappworker.infrastructure.config;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Cùng dạng {@code email-worker.MessagingConfig} (tier1) — in-app cũng time-sensitive (real-time UX),
 * retry nhanh rồi DLQ, không cần backoff dài kiểu bulk-email.
 */
@Configuration
public class MessagingConfig {

    private static final Logger log = LoggerFactory.getLogger(MessagingConfig.class);

    @Value("${app.kafka.concurrency.inapp}") private int inappConcurrency;
    @Value("${app.kafka.topic.dlq}")         private String dlqTopic;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> inappKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(dlqTopic, -1));
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(inappConcurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3));
        errorHandler.setAckAfterHandle(true);
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.error("[Kafka][inapp] consumer error, attempt={}/3, topic={}, offset={}, partition={}",
                        deliveryAttempt, record.topic(), record.offset(), record.partition(), ex));
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
