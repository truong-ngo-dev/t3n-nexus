package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.catalog.application.brand.ListActiveBrands;
import vn.t3nexus.catalog.application.category.GetCategoryAttributes;
import vn.t3nexus.catalog.application.category.GetCategoryTree;
import vn.t3nexus.catalog.application.product.GetProduct;
import vn.t3nexus.catalog.application.variant.GetProductVariants;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.LocalCacheInvalidator;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.TwoLevelCacheManager;

import java.time.Duration;
import java.util.Set;

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

    // BUG (2026-09-16): GenericJacksonJsonRedisSerializer + ObjectMapper thường (không bật polymorphic
    // default-typing — cố tình, để không lộ tên class nội bộ ra JSON response REST dùng chung mapper
    // này) KHÔNG giữ lại type info trong JSON lưu ở Redis. Lúc đọc lại (cache hit), Jackson không biết
    // phải deserialize về type cụ thể nào -> trả LinkedHashMap thô -> @Cacheable proxy ép kiểu
    // (T) cachedValue về Result record thật -> ClassCastException ("LinkedHashMap cannot be cast to
    // ListActiveBrands$Result"). Miss trên L1 Caffeine (CATEGORY_TREE/PRODUCT/PRODUCT_VARIANTS) rơi
    // xuống L2 cũng dính y hệt lỗi này, chỉ hiếm gặp hơn vì L1 thường hit trước.
    //
    // Fix: mỗi cache dùng JacksonJsonRedisSerializer<T> (typed, biết chính xác class đích) thay vì
    // GenericJacksonJsonRedisSerializer (generic, phải suy luận type lúc đọc) — không cần bật
    // polymorphic typing, không đổi ObjectMapper dùng chung cho REST response.
    @Bean
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
                        base.entryTtl(Duration.ofMinutes(30))
                            .serializeValuesWith(SerializationPair.fromSerializer(
                                    new JacksonJsonRedisSerializer<>(objectMapper, ListActiveBrands.Result.class))))
                .withCacheConfiguration(CacheNames.CATEGORY_ATTRIBUTES,
                        base.entryTtl(Duration.ofHours(1))
                            .serializeValuesWith(SerializationPair.fromSerializer(
                                    new JacksonJsonRedisSerializer<>(objectMapper, GetCategoryAttributes.Result.class))))
                .withCacheConfiguration(CacheNames.CATEGORY_TREE,
                        base.entryTtl(Duration.ofHours(1))
                            .serializeValuesWith(SerializationPair.fromSerializer(
                                    new JacksonJsonRedisSerializer<>(objectMapper, GetCategoryTree.Result.class))))
                .withCacheConfiguration(CacheNames.PRODUCT,
                        base.entryTtl(Duration.ofMinutes(10))
                            .serializeValuesWith(SerializationPair.fromSerializer(
                                    new JacksonJsonRedisSerializer<>(objectMapper, GetProduct.Result.class))))
                .withCacheConfiguration(CacheNames.PRODUCT_VARIANTS,
                        base.entryTtl(Duration.ofMinutes(5))
                            .serializeValuesWith(SerializationPair.fromSerializer(
                                    new JacksonJsonRedisSerializer<>(objectMapper, GetProductVariants.Result.class))))
                .build();
    }

    // ── Primary: two-level cache manager ─────────────────────────────────────

    @Bean
    @Primary
    public TwoLevelCacheManager twoLevelCacheManager(
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
            RedisCacheManager redisCacheManager) {
        return new TwoLevelCacheManager(
                caffeineCacheManager,
                redisCacheManager,
                Set.of(CacheNames.CATEGORY_TREE, CacheNames.PRODUCT, CacheNames.PRODUCT_VARIANTS));
    }

    // ── Pub/Sub: broadcast L1 invalidation across fleet ──────────────────────

    @Bean
    public LocalCacheInvalidator localCacheInvalidator(
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager) {
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
