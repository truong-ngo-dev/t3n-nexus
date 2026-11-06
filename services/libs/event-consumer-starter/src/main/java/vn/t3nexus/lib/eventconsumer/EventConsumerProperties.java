package vn.t3nexus.lib.eventconsumer;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code app.event-consumer.retry.*}: thử lại giãn cách tăng dần cho lỗi tạm. {@code max-retries} là số lần thử lại
 * <b>sau lần xử lý đầu</b> (3 nghĩa là tối đa 4 lần xử lý). Hết lượt thì chuyển sang chủ đề {@code dlq-topic}; không đặt thì
 * dùng {@code <spring.application.name>.dlq} (ADR-0010). Chỉ đặt {@code dlq-topic} khi dịch vụ cần tách hàng chờ lỗi.
 */
@ConfigurationProperties("app.event-consumer")
public class EventConsumerProperties {

    private final Retry retry = new Retry();
    private String dlqTopic;

    public Retry getRetry() {
        return retry;
    }

    public String getDlqTopic() {
        return dlqTopic;
    }

    public void setDlqTopic(String dlqTopic) {
        this.dlqTopic = dlqTopic;
    }

    public static class Retry {
        private int maxRetries = 3;
        private Duration initialInterval = Duration.ofSeconds(1);
        private double multiplier = 2.0;
        private Duration maxInterval = Duration.ofSeconds(30);

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public Duration getInitialInterval() { return initialInterval; }
        public void setInitialInterval(Duration initialInterval) { this.initialInterval = initialInterval; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
        public Duration getMaxInterval() { return maxInterval; }
        public void setMaxInterval(Duration maxInterval) { this.maxInterval = maxInterval; }
    }
}
