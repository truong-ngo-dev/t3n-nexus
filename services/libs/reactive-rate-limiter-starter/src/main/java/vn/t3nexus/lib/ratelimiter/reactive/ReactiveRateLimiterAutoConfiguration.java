package vn.t3nexus.lib.ratelimiter.reactive;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Duration;

@AutoConfiguration
@ConditionalOnClass(ReactiveStringRedisTemplate.class)
public class ReactiveRateLimiterAutoConfiguration {

    /** {@code app.ratelimit.redis-timeout-ms}: thời gian chờ của lệnh đếm Redis (mặc định 200 ms); quá thời gian thì mở cửa kèm cảnh báo. */
    @Bean
    @ConditionalOnMissingBean
    public ReactiveRateLimiter reactiveRateLimiter(ReactiveStringRedisTemplate redisTemplate,
                                                   @Value("${app.ratelimit.redis-timeout-ms:200}") long redisTimeoutMs) {
        return new ReactiveRedisRateLimiter(redisTemplate, Duration.ofMillis(redisTimeoutMs));
    }

    /**
     * Mở sẵn kết nối Redis lúc khởi động (thời gian chờ rộng, lỗi chỉ cảnh báo): nếu để lần đếm đầu tiên mở kết nối thì nó chậm hơn thời gian chờ ngắn của
     * bộ đếm, yêu cầu đầu tiên bị mở cửa nhầm và không được tính (đã gặp ở lần chạy thật đầu tiên).
     */
    @Bean
    @ConditionalOnMissingBean(name = "reactiveRateLimiterWarmUp")
    public ApplicationRunner reactiveRateLimiterWarmUp(ReactiveStringRedisTemplate redisTemplate) {
        return args -> {
            try {
                redisTemplate.execute(connection -> connection.ping()).blockLast(Duration.ofSeconds(5));
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(ReactiveRateLimiterAutoConfiguration.class)
                        .warn("[ReactiveRateLimiter] could not warm up the Redis connection: {}", e.toString());
            }
        };
    }
}
