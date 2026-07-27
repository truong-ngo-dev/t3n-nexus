package vn.t3nexus.lib.ratelimiter.reactive;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass(ReactiveStringRedisTemplate.class)
public class ReactiveRateLimiterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveRateLimiter reactiveRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        return new ReactiveRedisRateLimiter(redisTemplate);
    }
}
