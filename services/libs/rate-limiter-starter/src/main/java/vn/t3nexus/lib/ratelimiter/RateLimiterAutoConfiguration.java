package vn.t3nexus.lib.ratelimiter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class RateLimiterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter(StringRedisTemplate redisTemplate) {
        return new RedisRateLimiter(redisTemplate);
    }
}
