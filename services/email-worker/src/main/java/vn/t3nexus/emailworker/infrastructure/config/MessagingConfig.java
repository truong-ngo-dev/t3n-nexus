package vn.t3nexus.emailworker.infrastructure.config;

import org.apache.kafka.common.TopicPartition;
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

@Configuration
public class MessagingConfig {

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
        // Bulk: slow retries — 60s × 3, avoid hammering external SMTP on transient failures, then DLQ
        DefaultErrorHandler tier2ErrorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(60_000L, 3));
        tier2ErrorHandler.setAckAfterHandle(true);
        factory.setCommonErrorHandler(tier2ErrorHandler);
        return factory;
    }
}
