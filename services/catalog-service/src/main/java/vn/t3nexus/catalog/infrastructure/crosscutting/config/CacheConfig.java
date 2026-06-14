package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.LocalCacheInvalidator;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    // ── L1: Caffeine (W-TinyLFU, per-instance) ───────────────────────────────

    @Bean("caffeineCacheManager")
    public CaffeineCacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(false);

        // category:tree — singleton entry, long TTL, very static
        manager.registerCustomCache(CacheNames.CATEGORY_TREE,
                Caffeine.newBuilder()
                        .maximumSize(1)
                        .expireAfterWrite(Duration.ofMinutes(30))
                        .build());

        // product — top hot products, W-TinyLFU keeps most valuable 10K
        manager.registerCustomCache(CacheNames.PRODUCT,
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(2))
                        .build());

        // product-variants — same footprint as product
        manager.registerCustomCache(CacheNames.PRODUCT_VARIANTS,
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(2))
                        .build());

        return manager;
    }

    // ── L2: Redis (shared across all instances) ───────────────────────────────

    @Bean
    @Primary
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                               ObjectMapper objectMapper) {
        GenericJacksonJsonRedisSerializer jsonSerializer =
                new GenericJacksonJsonRedisSerializer(objectMapper);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withCacheConfiguration(CacheNames.BRANDS_ACTIVE,
                        base.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration(CacheNames.CATEGORY_ATTRIBUTES,
                        base.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration(CacheNames.CATEGORY_TREE,
                        base.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration(CacheNames.PRODUCT,
                        base.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration(CacheNames.PRODUCT_VARIANTS,
                        base.entryTtl(Duration.ofMinutes(5)))
                .build();
    }

    // ── Pub/Sub: broadcast L1 invalidation across fleet ──────────────────────

    @Bean
    public LocalCacheInvalidator localCacheInvalidator(
            @org.springframework.beans.factory.annotation.Qualifier("caffeineCacheManager")
            CacheManager caffeineCacheManager) {
        return new LocalCacheInvalidator(caffeineCacheManager);
    }

    @Bean
    public MessageListenerAdapter cacheInvalidationListener(LocalCacheInvalidator invalidator) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(invalidator, "onInvalidate");
        adapter.setSerializer(new StringRedisSerializer());
        return adapter;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter cacheInvalidationListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                cacheInvalidationListener,
                new ChannelTopic(CacheNames.INVALIDATION_CHANNEL));
        return container;
    }
}
