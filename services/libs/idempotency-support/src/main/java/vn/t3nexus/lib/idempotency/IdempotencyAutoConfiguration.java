package vn.t3nexus.lib.idempotency;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyGuard idempotencyGuard(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyGuard(redisTemplate);
    }
}
