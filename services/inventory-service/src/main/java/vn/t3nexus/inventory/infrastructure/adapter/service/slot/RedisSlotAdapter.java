package vn.t3nexus.inventory.infrastructure.adapter.service.slot;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import vn.t3nexus.inventory.domain.limitedoffer.SlotCheckResult;
import vn.t3nexus.inventory.domain.limitedoffer.SlotService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSlotAdapter implements SlotService {

    private static final String SLOT_KEY_PATTERN   = "inventory:limited:%s:slots";
    private static final String BLOOM_FILTER_KEY   = "inventory:sold-out:bloom";
    private static final long   BLOOM_CAPACITY     = 5_000_000L;
    private static final double BLOOM_FALSE_RATE   = 0.01;

    /**
     * Atomically checks if slots remain, then decrements.
     * Returns -1 when no key exists or counter <= 0 (sold out).
     * This avoids the race between GET and DECR.
     */
    private static final String LUA_DECR_IF_POSITIVE =
            "local v = redis.call('GET', KEYS[1]) " +
            "if v == false or tonumber(v) <= 0 then return -1 end " +
            "return redis.call('DECR', KEYS[1])";

    private final RedissonClient redissonClient;
    private RBloomFilter<String> bloomFilter;

    @PostConstruct
    void initBloomFilter() {
        bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_KEY);
        bloomFilter.tryInit(BLOOM_CAPACITY, BLOOM_FALSE_RATE);
    }

    @Override
    public void initSlots(String skuId, int maxQuantity) {
        redissonClient.getAtomicLong(slotKey(skuId)).set(maxQuantity);
        // Reset entire Bloom Filter so stale sold-out entries from prior offer windows
        // don't cause false SOLD_OUT responses for this newly activated offer.
        bloomFilter.delete();
        bloomFilter.tryInit(BLOOM_CAPACITY, BLOOM_FALSE_RATE);
        log.debug("[RedisSlotAdapter] initSlots skuId={} maxQty={}", skuId, maxQuantity);
    }

    @Override
    public void clearSlots(String skuId) {
        redissonClient.getAtomicLong(slotKey(skuId)).delete();
        log.debug("[RedisSlotAdapter] clearSlots skuId={}", skuId);
    }

    @Override
    public SlotCheckResult tryDecrementSlot(String skuId) {
        String key = slotKey(skuId);

        // No Redis key → SKU has no active limited offer
        if (!redissonClient.getBucket(key).isExists()) {
            return SlotCheckResult.NO_LIMIT;
        }

        // Bloom Filter fast-fail — avoid hitting Redis counter for known sold-out SKUs
        if (bloomFilter.contains(skuId)) {
            log.debug("[RedisSlotAdapter] bloom filter hit SOLD_OUT skuId={}", skuId);
            return SlotCheckResult.SOLD_OUT;
        }

        // Atomic check-and-decrement via Lua
        Long newCount = redissonClient.getScript().eval(
                RScript.Mode.READ_WRITE,
                LUA_DECR_IF_POSITIVE,
                RScript.ReturnType.INTEGER,
                List.of(key));

        if (newCount == null || newCount < 0) {
            return SlotCheckResult.SOLD_OUT;
        }
        if (newCount == 0) {
            bloomFilter.add(skuId);
            log.debug("[RedisSlotAdapter] last slot taken, added to bloom filter skuId={}", skuId);
        }
        return SlotCheckResult.SLOT_GRANTED;
    }

    @Override
    public void returnSlot(String skuId) {
        // Only increment if the key still exists (offer might have been deactivated)
        if (redissonClient.getBucket(slotKey(skuId)).isExists()) {
            redissonClient.getAtomicLong(slotKey(skuId)).incrementAndGet();
            log.debug("[RedisSlotAdapter] returnSlot skuId={}", skuId);
        }
    }

    private String slotKey(String skuId) {
        return String.format(SLOT_KEY_PATTERN, skuId);
    }
}
