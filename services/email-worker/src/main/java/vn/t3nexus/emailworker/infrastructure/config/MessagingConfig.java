package vn.t3nexus.emailworker.infrastructure.config;

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
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class MessagingConfig {

    private static final Logger log = LoggerFactory.getLogger(MessagingConfig.class);

    @Value("${app.kafka.concurrency.tier1}") private int tier1Concurrency;
    @Value("${app.kafka.concurrency.tier2}") private int tier2Concurrency;
    @Value("${app.kafka.topic.dlq}")         private String dlqTopic;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> tier1KafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(dlqTopic, -1));
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(tier1Concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // Transactional: fast retries — 2s × 3, then DLQ
        DefaultErrorHandler tier1ErrorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3));
        tier1ErrorHandler.setAckAfterHandle(true);
        tier1ErrorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.error("[Kafka][tier1] consumer error, attempt={}/3, topic={}, offset={}, partition={}",
                        deliveryAttempt, record.topic(), record.offset(), record.partition(), ex));
        factory.setCommonErrorHandler(tier1ErrorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> tier2KafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(dlqTopic, -1));
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(tier2Concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // Bulk: exponential backoff — 30s initial, ×2, capped 5min/attempt, 20min total elapsed, then DLQ.
        // Sized for realistic transient-failure recovery (SES throttle/network blips self-resolve in
        // seconds-to-minutes), NOT for the ~4h queue-drain time under flash-sale backlog — that's a
        // consumer-lag phenomenon (rate limiter blocking in Tier2EmailConsumer), unrelated to retry-on-error.
        ExponentialBackOff tier2BackOff = new ExponentialBackOff(30_000L, 2.0);
        tier2BackOff.setMaxInterval(300_000L);       // 5 min cap per retry
        tier2BackOff.setMaxElapsedTime(1_200_000L);  // 20 min total before DLQ
        DefaultErrorHandler tier2ErrorHandler = new DefaultErrorHandler(recoverer, tier2BackOff);
        tier2ErrorHandler.setAckAfterHandle(true);
        tier2ErrorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.error("[Kafka][tier2] consumer error, attempt={}, topic={}, offset={}, partition={}",
                        deliveryAttempt, record.topic(), record.offset(), record.partition(), ex));
        factory.setCommonErrorHandler(tier2ErrorHandler);
        return factory;
    }
}
